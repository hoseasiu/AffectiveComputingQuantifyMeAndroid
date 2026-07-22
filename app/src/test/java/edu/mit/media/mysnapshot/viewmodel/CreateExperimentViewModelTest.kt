package edu.mit.media.mysnapshot.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.QuantifyMeDatabase
import edu.mit.media.mysnapshot.engine.CustomRangePresets
import edu.mit.media.mysnapshot.engine.CustomValueKind
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.engine.ExperimentTypeRegistry
import edu.mit.media.mysnapshot.engine.RangeTable
import edu.mit.media.mysnapshot.engine.SignalRef
import edu.mit.media.mysnapshot.engine.SignalSource
import edu.mit.media.mysnapshot.engine.readBundledExperimentTypesJson
import edu.mit.media.mysnapshot.health.HealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for [CreateExperimentViewModel] (issue #33): the "create your own experiment"
 * wizard that authors a brand new [ExperimentType] entirely on-device. See
 * `ExperimentTypeConfigTest`'s custom-type round-trip tests and `CheckinViewModelTest`'s
 * class doc for the conventions this borrows (`ExperimentTypeRegistry.loadForTest`/
 * `refreshCustomTypes` seam, in-memory Room, a real `HealthConnectManager` under Robolectric).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CreateExperimentViewModelTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var repository: ExperimentRepository
    private lateinit var viewModel: CreateExperimentViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ExperimentTypeRegistry.loadForTest(readBundledExperimentTypesJson())

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuantifyMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val healthConnect = HealthConnectManager(context)
        repository = ExperimentRepository(context, db, healthConnect)
        viewModel = CreateExperimentViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
        ExperimentTypeRegistry.refreshCustomTypes(emptyList())
    }

    @Test
    fun typeKey_blankName_isBlank() {
        assertEquals("", viewModel.uiState.value.typeKey)
    }

    @Test
    fun typeKey_collidesWithBundledType_getsUniqueNumericSuffix() {
        viewModel.onNameChanged("Leisure Happiness")

        assertEquals("leisurehappiness2", viewModel.uiState.value.typeKey)
    }

    @Test
    fun onInputSignalSelected_custom_insertsCustomFormStepAndAppliesPreset() {
        viewModel.onNameChanged("Coffee Focus")
        viewModel.onNameContinue()

        viewModel.onInputSignalSelected(CUSTOM_SIGNAL_SENTINEL)

        val state = viewModel.uiState.value
        assertTrue(state.steps.contains(CreateExperimentStep.INPUT_CUSTOM_FORM))
        // No CustomValueKind chosen yet -- nothing to derive a preset from.
        assertNull(state.ranges)

        viewModel.onInputCustomKindSelected(CustomValueKind.COUNT)
        viewModel.onInputCustomLabelChanged("Coffee")
        viewModel.onInputCustomQuestionChanged("How many cups of coffee did you drink today?")
        viewModel.onInputCustomFormContinue()

        val preset = CustomRangePresets.presetFor(CustomValueKind.COUNT)
        val afterState = viewModel.uiState.value
        assertEquals(preset.ranges, afterState.ranges)
        assertEquals(preset.rangeSize, afterState.rangeSize)
        assertEquals(preset.stableRange, afterState.stableRange)
    }

    @Test
    fun onCreateClicked_missingRequiredFields_doesNotSubmit() = runBlocking {
        viewModel.onCreateClicked()

        val event = withTimeoutOrNull(100) { viewModel.events.first() }
        assertNull("no name/signals/ranges set yet -- must not create anything", event)
    }

    @Test
    fun fullBuiltinFlow_createsCustomTypeAndEmitsNavigateToIntro() = runBlocking {
        viewModel.onNameChanged("My Custom Experiment")
        viewModel.onNameContinue()

        viewModel.onInputSignalSelected(SignalSource.HEALTH_CONNECT_STEPS.name)
        viewModel.onOutputSignalSelected(SignalSource.CHECKIN_HAPPINESS.name)
        viewModel.onTargetRangeContinue()
        viewModel.onCreateClicked()

        val event = viewModel.events.first()
        assertTrue(event is CreateExperimentEvent.NavigateToIntro)
        val type = (event as CreateExperimentEvent.NavigateToIntro).type

        assertEquals("mycustomexperiment", type.typeKey)
        assertEquals(SignalRef.Builtin(SignalSource.HEALTH_CONNECT_STEPS), type.inputSignal)
        assertEquals(SignalRef.Builtin(SignalSource.CHECKIN_HAPPINESS), type.outputSignal)
        assertEquals(CustomRangePresets.presetFor(CustomValueKind.COUNT).ranges, type.ranges)

        // loadCustomTypes() must have run so the new type is immediately usable.
        assertEquals(type, ExperimentType.fromTypeKey("mycustomexperiment"))
    }

    @Test
    fun fullCustomSignalFlow_bothSlotsCustom_buildsExpectedSignalDefs() = runBlocking {
        viewModel.onNameChanged("Coffee Focus")
        viewModel.onNameContinue()

        viewModel.onInputSignalSelected(CUSTOM_SIGNAL_SENTINEL)
        viewModel.onInputCustomLabelChanged("Coffee")
        viewModel.onInputCustomQuestionChanged("How many cups of coffee did you drink today?")
        viewModel.onInputCustomKindSelected(CustomValueKind.COUNT)
        viewModel.onInputCustomUnitLabelChanged("cups")
        viewModel.onInputCustomFormContinue()

        viewModel.onOutputSignalSelected(CUSTOM_SIGNAL_SENTINEL)
        viewModel.onOutputCustomLabelChanged("Focus")
        viewModel.onOutputCustomQuestionChanged("How focused did you feel today?")
        viewModel.onOutputCustomKindSelected(CustomValueKind.SCALE_1_7)
        viewModel.onOutputCustomLowLabelChanged("Distracted")
        viewModel.onOutputCustomHighLabelChanged("Laser-focused")
        viewModel.onOutputCustomFormContinue()

        viewModel.onTargetRangeContinue()
        viewModel.onCreateClicked()

        val event = viewModel.events.first() as CreateExperimentEvent.NavigateToIntro
        val type = event.type

        val input = type.inputSignal as SignalRef.Custom
        assertEquals("Coffee", input.definition.label)
        assertEquals(CustomValueKind.COUNT, input.definition.kind)
        assertEquals("cups", input.definition.unitLabel)

        val output = type.outputSignal as SignalRef.Custom
        assertEquals("Focus", output.definition.label)
        assertEquals(CustomValueKind.SCALE_1_7, output.definition.kind)
        assertEquals("Distracted", output.definition.lowLabel)
        assertEquals("Laser-focused", output.definition.highLabel)
    }

    @Test
    fun manualRangeOverride_survivesIntoFinalExperimentType() = runBlocking {
        viewModel.onNameChanged("Custom Ranges")
        viewModel.onNameContinue()
        viewModel.onInputSignalSelected(SignalSource.HEALTH_CONNECT_STEPS.name)
        viewModel.onOutputSignalSelected(SignalSource.CHECKIN_HAPPINESS.name)

        // Override the auto-derived preset before continuing past the target-range step.
        viewModel.onRangeUnderChanged(100f)
        viewModel.onRangeN1Changed(200f)
        viewModel.onRangeN2Changed(300f)
        viewModel.onRangeN3Changed(400f)
        viewModel.onRangeOverChanged(500f)
        viewModel.onRangeSizeChanged(50f)
        viewModel.onStableRangeChanged(5f)
        viewModel.onTargetRangeContinue()
        viewModel.onCreateClicked()

        val event = viewModel.events.first() as CreateExperimentEvent.NavigateToIntro
        assertEquals(RangeTable(under = 100f, n1 = 200f, n2 = 300f, n3 = 400f, over = 500f), event.type.ranges)
        assertEquals(50f, event.type.rangeSize)
        assertEquals(5f, event.type.stableRange)
    }

    @Test
    fun selectingInputSignalAfterRevealingLaterSteps_truncatesRevealedFrontier() {
        viewModel.onNameChanged("Reselect Test")
        viewModel.onNameContinue()
        viewModel.onInputSignalSelected(SignalSource.HEALTH_CONNECT_STEPS.name)
        viewModel.onOutputSignalSelected(SignalSource.CHECKIN_HAPPINESS.name)

        val beforeState = viewModel.uiState.value
        assertTrue(
            "expected TARGET_RANGE to already be revealed before the reselect",
            beforeState.revealedSteps >= beforeState.steps.indexOf(CreateExperimentStep.TARGET_RANGE) + 1
        )

        // Changing the input signal to a custom one inserts a new page and must invalidate
        // whatever was revealed past the input-signal step -- only the newly-inserted custom
        // form page should now be revealed, not the (shifted) later steps.
        viewModel.onInputSignalSelected(CUSTOM_SIGNAL_SENTINEL)

        val state = viewModel.uiState.value
        val customFormIdx = state.steps.indexOf(CreateExperimentStep.INPUT_CUSTOM_FORM)
        val outputSignalIdx = state.steps.indexOf(CreateExperimentStep.OUTPUT_SIGNAL)
        assertEquals(customFormIdx + 1, state.revealedSteps)
        assertTrue("output-signal step must no longer be revealed", outputSignalIdx >= state.revealedSteps)
    }
}
