package edu.mit.media.mysnapshot.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.QuantifyMeDatabase
import edu.mit.media.mysnapshot.engine.CustomSignalDef
import edu.mit.media.mysnapshot.engine.CustomValueKind
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.engine.ExperimentTypeRegistry
import edu.mit.media.mysnapshot.engine.FormatKind
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
 * Coverage for [CheckinViewModel] -- the ViewModel introduced for AGENT_PLANS/IMPROVEMENTS.md
 * item 3.1, which now owns [edu.mit.media.mysnapshot.activities.ExperimentCheckinActivity]'s
 * data loading, progressive-reveal wizard state, and `submitCheckin()` call (previously the
 * legacy `QuestionActivity`/`ViewPager` + `QuestionRadioGroupFragment`/`QuestionSpinnerFragment`
 * framework, driven imperatively from the Activity).
 *
 * The submit test below is the load-bearing one: it asserts that each 7-button radio scale's
 * 0-based selected index (`ColoredRadioGroup.getSelectedIndex()` semantics -- verified against
 * the legacy widget) flows through to [ExperimentRepository.submitCheckin] completely unchanged,
 * and that the leisure step's selected *value* string (not its display label) is what gets
 * `.toInt()`-ed and submitted -- exactly the legacy `leisure.value.toInt()` contract.
 *
 * See `ExperimentRepositoryTest`'s class doc for why a real `HealthConnectManager` is safe
 * under Robolectric and why these tests are scoped to the checkin-only "leisurehappiness" type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CheckinViewModelTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var repository: ExperimentRepository
    private lateinit var viewModel: CheckinViewModel

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
        viewModel = CheckinViewModel(repository, healthConnect, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun load_leisureHappinessExperiment_populatesTypeAndPlainIntroText() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()

        viewModel.load(id)
        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals("leisurehappiness", state.experimentType.typeKey)
        assertTrue(
            "leisurehappiness doesn't use sleep data, so no sleep-night explanation should be appended",
            state.introText.contains("You only need to check in once a day!") &&
                !state.introText.contains("We counted your sleep")
        )
        assertEquals(1, state.revealedSteps)
        assertEquals(CheckinStep.INTRO.ordinal, state.currentStep)
    }

    @Test
    fun load_noCurrentExperiment_emitsNavigateToMain() = runBlocking {
        viewModel.load(-1)

        val event = viewModel.events.first()

        assertEquals(CheckinEvent.NavigateToMain, event)
    }

    @Test
    fun load_inactiveExperiment_emitsNavigateToComplete() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()
        repository.cancelExperiment(id)

        viewModel.load(id)

        val event = viewModel.events.first()
        assertEquals(CheckinEvent.NavigateToComplete(id), event)
    }

    @Test
    fun submit_leisureHappinessExperiment_mapsScaleIndicesAndLeisureMinutesUnchanged() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()

        viewModel.load(id)
        viewModel.uiState.first { !it.isLoading }

        // Answer all six steps in the legacy fragment order: intro, didFollowDirections,
        // leisure, happy, stress, productivity. Every scale value below is the 0-based index
        // ColoredRadioGroup.getSelectedIndex() would have returned for that button position.
        viewModel.onIntroContinue()
        viewModel.onDidFollowDirectionsSelected(0)
        viewModel.onLeisureSelected("45")
        viewModel.onHappySelected(6)
        viewModel.onStressSelected(3)
        viewModel.onProductivitySelected(5)

        val event = viewModel.events.first()

        val checkin = repository.getCheckinsForExperiment(id).first().single()
        assertEquals("didFollowInstructions must be the raw 0-based scale index", 0, checkin.didFollowInstructions)
        assertEquals("happiness must be the raw 0-based scale index", 6, checkin.happiness)
        assertEquals("stress must be the raw 0-based scale index (button position, not the color it renders)", 3, checkin.stress)
        assertEquals("productivity must be the raw 0-based scale index", 5, checkin.productivity)
        assertEquals("leisureTime must be the selected value's minutes, not the display label", 45, checkin.leisureTime)

        // A brand-new experiment's first check-in (same day it was created) stays in baseline,
        // so the outcome is "generate instructions", not "experiment complete".
        assertTrue(
            "expected a NavigateToInstructions event for a still-in-baseline experiment, got $event",
            event is CheckinEvent.NavigateToInstructions
        )
        val instructionsEvent = event as CheckinEvent.NavigateToInstructions
        assertEquals(id, instructionsEvent.experimentId)

        val finalState = viewModel.uiState.first()
        assertTrue("isSubmitting must be reset to false once the repository call completes", !finalState.isSubmitting)
    }

    // Issue #32: custom-signal experiments (issue #31) append 1-2 extra wizard pages after the
    // five builtin ones. These tests register a custom type via the same
    // ExperimentTypeRegistry.refreshCustomTypes() seam ExperimentTypeConfigTest uses.

    @Test
    fun load_customSignalExperiment_appendsOneCustomQuestionPerCustomSlot() = runBlocking {
        val type = waterIntakeMoodType()
        try {
            ExperimentTypeRegistry.refreshCustomTypes(listOf(type))
            val id = repository.createExperiment(type, 3, 3, 3).toInt()

            viewModel.load(id)
            val state = viewModel.uiState.first { !it.isLoading }

            assertEquals(1, state.customQuestions.size)
            assertEquals("custom_input", state.customQuestions.single().id)
            assertEquals(CheckinStep.entries.size + 1, state.totalSteps)
        } finally {
            ExperimentTypeRegistry.refreshCustomTypes(emptyList())
        }
    }

    @Test
    fun submit_singleCustomSlot_answersRouteToCustomInputValueAndSubmitCompletes() = runBlocking {
        val type = waterIntakeMoodType()
        try {
            ExperimentTypeRegistry.refreshCustomTypes(listOf(type))
            val id = repository.createExperiment(type, 3, 3, 3).toInt()

            viewModel.load(id)
            viewModel.uiState.first { !it.isLoading }

            viewModel.onIntroContinue()
            viewModel.onDidFollowDirectionsSelected(0)
            viewModel.onLeisureSelected("45")
            viewModel.onHappySelected(6)
            viewModel.onStressSelected(3)
            // Productivity is no longer necessarily the last step -- one custom page follows it.
            viewModel.onProductivitySelected(5)

            assertTrue(
                "answering productivity should reveal the custom page, not submit yet",
                repository.getCheckinsForExperiment(id).first().isEmpty()
            )
            assertEquals(0, viewModel.uiState.value.customAnswers.size)

            viewModel.onCustomAnswer("custom_input", 4f)

            val event = viewModel.events.first()
            val checkin = repository.getCheckinsForExperiment(id).first().single()
            assertEquals("custom_input answer must land in customInputValue", 4f, checkin.customInputValue)
            assertEquals("a builtin output slot leaves customOutputValue null", null, checkin.customOutputValue)
            assertTrue(event is CheckinEvent.NavigateToInstructions)
        } finally {
            ExperimentTypeRegistry.refreshCustomTypes(emptyList())
        }
    }

    @Test
    fun submit_bothSlotsCustom_requiresBothCustomAnswersBeforeSubmitting() = runBlocking {
        val type = cupsOfCoffeeFocusType()
        try {
            ExperimentTypeRegistry.refreshCustomTypes(listOf(type))
            val id = repository.createExperiment(type, 3, 3, 3).toInt()

            viewModel.load(id)
            val loaded = viewModel.uiState.first { !it.isLoading }
            assertEquals(listOf("custom_input", "custom_output"), loaded.customQuestions.map { it.id })
            assertEquals(CheckinStep.entries.size + 2, loaded.totalSteps)

            viewModel.onIntroContinue()
            viewModel.onDidFollowDirectionsSelected(0)
            viewModel.onLeisureSelected("45")
            viewModel.onHappySelected(6)
            viewModel.onStressSelected(3)
            viewModel.onProductivitySelected(5)

            viewModel.onCustomAnswer("custom_input", 3f)
            assertTrue(
                "only one of two custom questions answered -- must not submit yet",
                repository.getCheckinsForExperiment(id).first().isEmpty()
            )

            viewModel.onCustomAnswer("custom_output", 6f)

            val event = viewModel.events.first()
            val checkin = repository.getCheckinsForExperiment(id).first().single()
            assertEquals(3f, checkin.customInputValue)
            assertEquals(6f, checkin.customOutputValue)
            assertTrue(event is CheckinEvent.NavigateToInstructions)
        } finally {
            ExperimentTypeRegistry.refreshCustomTypes(emptyList())
        }
    }

    private fun waterIntakeMoodType() = ExperimentType(
        typeKey = "waterintakemood_checkinvmtest",
        name = "How does my water intake affect my mood?",
        ranges = RangeTable(under = 1f, n1 = 2f, n2 = 3f, n3 = 4f, over = 5f),
        rangeSize = 1f,
        stableRange = 1f,
        useVariability = false,
        shouldMinimizeResult = false,
        usesSleepData = false,
        inputSignal = SignalRef.Custom(
            CustomSignalDef(
                label = "Water",
                question = "How many glasses of water did you drink today?",
                kind = CustomValueKind.COUNT,
                unitLabel = "glasses"
            )
        ),
        outputSignal = SignalRef.Builtin(SignalSource.CHECKIN_HAPPINESS),
        targetFormatKind = FormatKind.RAW_NUMBER,
        resultFormatKind = FormatKind.RAW_NUMBER,
        instructionTemplate = "Try to drink {value} glasses of water today",
        resultTemplate = "Try to drink {value} glasses of water each day",
        targetTemplate = "{value} Glasses"
    )

    private fun cupsOfCoffeeFocusType() = ExperimentType(
        typeKey = "cupsofcoffeefocus_checkinvmtest",
        name = "How does my coffee intake affect my focus?",
        ranges = RangeTable(under = 1f, n1 = 2f, n2 = 3f, n3 = 4f, over = 5f),
        rangeSize = 1f,
        stableRange = 1f,
        useVariability = false,
        shouldMinimizeResult = false,
        usesSleepData = false,
        inputSignal = SignalRef.Custom(
            CustomSignalDef(
                label = "Coffee",
                question = "How many cups of coffee did you drink today?",
                kind = CustomValueKind.COUNT,
                unitLabel = "cups"
            )
        ),
        outputSignal = SignalRef.Custom(
            CustomSignalDef(
                label = "Focus",
                question = "How focused did you feel today?",
                kind = CustomValueKind.SCALE_1_7,
                lowLabel = "Distracted",
                highLabel = "Laser-focused"
            )
        ),
        targetFormatKind = FormatKind.RAW_NUMBER,
        resultFormatKind = FormatKind.RAW_NUMBER,
        instructionTemplate = "Try to drink {value} cups of coffee today",
        resultTemplate = "Try to drink {value} cups of coffee each day",
        targetTemplate = "{value} Cups"
    )
}
