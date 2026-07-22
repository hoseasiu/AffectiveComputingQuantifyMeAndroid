package edu.mit.media.mysnapshot.viewmodel

import android.preference.PreferenceManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.CheckinEntity
import edu.mit.media.mysnapshot.database.QuantifyMeDatabase
import edu.mit.media.mysnapshot.engine.CheckinOutcome
import edu.mit.media.mysnapshot.engine.ExperimentEngine
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.engine.ExperimentTypeRegistry
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
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for [ExperimentInstructionsViewModel] -- the ViewModel introduced for
 * AGENT_PLANS/IMPROVEMENTS.md item 2.2 that now owns
 * [edu.mit.media.mysnapshot.activities.ExperimentInstructionsActivity]'s data load, the 5.1/5.2
 * new-stage/failed-stage dialogs (previously shown at most once per composition via a plain
 * Activity field), and the 5.3 progress/target-preview opt-in dialogs and their SharedPreferences
 * gate. Hoisting all of this here is the point of the migration: it now survives activity
 * recreation instead of resetting on rotation.
 *
 * See `ExperimentRepositoryTest`'s class doc for why a real `HealthConnectManager` is safe
 * under Robolectric and why these tests are scoped to the checkin-only "leisurehappiness" type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ExperimentInstructionsViewModelTest {

    private lateinit var context: android.content.Context
    private lateinit var db: QuantifyMeDatabase
    private lateinit var repository: ExperimentRepository
    private lateinit var viewModel: ExperimentInstructionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ExperimentTypeRegistry.loadForTest(readBundledExperimentTypesJson())

        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, QuantifyMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val healthConnect = HealthConnectManager(context)
        repository = ExperimentRepository(context, db, healthConnect)
        viewModel = ExperimentInstructionsViewModel(repository, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun newExperiment(): Int =
        repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()

    private suspend fun addCheckin(experimentId: Int) {
        db.checkinDao().insert(
            CheckinEntity(
                experimentId = experimentId,
                checkinDate = OffsetDateTime.now(),
                didFollowInstructions = 3,
                happiness = 3,
                stress = 3,
                productivity = 3,
                leisureTime = 30
            )
        )
    }

    @Test
    fun load_noExperiment_emitsNavigateToMain() = runBlocking {
        viewModel.load(-1, pendingOutcome = null, forceNewStageDialog = false)

        assertEquals(InstructionsEvent.NavigateToMain, viewModel.events.first())
    }

    @Test
    fun load_inactiveExperiment_emitsNavigateToComplete() = runBlocking {
        val id = newExperiment()
        repository.cancelExperiment(id)

        viewModel.load(id, pendingOutcome = null, forceNewStageDialog = false)

        assertEquals(InstructionsEvent.NavigateToComplete(id), viewModel.events.first())
    }

    @Test
    fun load_activeExperimentWithNoCheckinsYet_isFirstDay() = runBlocking {
        val id = newExperiment()

        viewModel.load(id, pendingOutcome = null, forceNewStageDialog = false)
        val state = viewModel.uiState.first { !it.isLoading }

        assertTrue(state.isFirstDay)
        assertNull(state.outcome)
        assertNull(withTimeoutOrNull(100) { viewModel.events.first() })
    }

    @Test
    fun load_withCheckinsAndNoPendingOutcome_refreshesInstructionsFromRepository() = runBlocking {
        val id = newExperiment()
        addCheckin(id)

        viewModel.load(id, pendingOutcome = null, forceNewStageDialog = false)
        val state = viewModel.uiState.first { !it.isLoading }

        assertFalse(state.isFirstDay)
        assertEquals("leisurehappiness", state.experimentType?.typeKey)
        assertEquals(repository.refreshInstructions(id), state.outcome)
        assertFalse("a plain refresh (no pending outcome) must never trigger the new-stage dialog", state.newStageDialogVisible)
        assertFalse(state.failedStageDialogVisible)
    }

    @Test
    fun load_withPendingOutcomeSignalingNewStage_showsNewStageDialogExactlyOnce() = runBlocking {
        val id = newExperiment()
        addCheckin(id)
        val outcome = CheckinOutcome(newStage = true, currentStage = 1)

        viewModel.load(id, pendingOutcome = outcome, forceNewStageDialog = false)
        val state = viewModel.uiState.first { !it.isLoading }
        assertTrue(state.newStageDialogVisible)
        assertEquals(outcome, state.outcome)

        viewModel.onNewStageDialogDismissed()
        assertFalse(viewModel.uiState.first().newStageDialogVisible)

        // A later resume must not resurrect the dialog -- it's already been shown once.
        viewModel.onResume()
        val afterResume = viewModel.uiState.first { !it.isFirstDay && it.outcome != null }
        assertFalse(
            "the new-stage dialog must only ever be shown once per screen instance, even across resumes",
            afterResume.newStageDialogVisible
        )
    }

    @Test
    fun load_withPendingOutcomeSignalingRestart_showsFailedStageDialogWithReason() = runBlocking {
        val id = newExperiment()
        addCheckin(id)
        val outcome = CheckinOutcome(
            restartedStage = true,
            restartReason = ExperimentEngine.RestartReason.TOO_MANY_MISSED_DAYS,
            currentStage = 1
        )

        viewModel.load(id, pendingOutcome = outcome, forceNewStageDialog = false)
        val state = viewModel.uiState.first { !it.isLoading }

        assertTrue(state.failedStageDialogVisible)
        assertEquals(ExperimentEngine.RestartReason.TOO_MANY_MISSED_DAYS, state.failedStageReason)
        assertFalse(
            "restartedStage takes priority over newStage in the legacy if/else-if",
            state.newStageDialogVisible
        )
    }

    @Test
    fun load_forceNewStageDialogTrue_showsDialogEvenWithoutOutcomeSignalingIt() = runBlocking {
        val id = newExperiment()
        addCheckin(id)

        viewModel.load(id, pendingOutcome = null, forceNewStageDialog = true)
        val state = viewModel.uiState.first { !it.isLoading }

        assertTrue(state.newStageDialogVisible)
    }

    @Test
    fun onRefreshClick_updatesOutcomeAndClearsIsRefreshing() = runBlocking {
        val id = newExperiment()
        addCheckin(id)
        viewModel.load(id, pendingOutcome = null, forceNewStageDialog = false)
        viewModel.uiState.first { !it.isLoading }

        viewModel.onRefreshClick()
        val state = viewModel.uiState.first { !it.isRefreshing }

        assertEquals(repository.refreshInstructions(id), state.outcome)
    }

    @Test
    fun onProgressClick_prefNotSet_showsOptInDialogInsteadOfNavigating() = runBlocking {
        val id = newExperiment()
        addCheckin(id)
        viewModel.load(id, pendingOutcome = null, forceNewStageDialog = false)
        viewModel.uiState.first { !it.isLoading }

        viewModel.onProgressClick()

        assertTrue(viewModel.uiState.first().progressOptInDialogVisible)
        assertNull(withTimeoutOrNull(100) { viewModel.events.first() })
    }

    @Test
    fun onProgressOptInConfirmed_persistsPrefAndNavigatesToProgress() = runBlocking {
        val id = newExperiment()
        addCheckin(id)
        viewModel.load(id, pendingOutcome = null, forceNewStageDialog = false)
        viewModel.uiState.first { !it.isLoading }

        viewModel.onProgressClick()
        viewModel.onProgressOptInConfirmed()

        assertEquals(InstructionsEvent.NavigateToProgress(id), viewModel.events.first())
        assertFalse(viewModel.uiState.first().progressOptInDialogVisible)

        // Once opted in, a later click skips the dialog entirely.
        viewModel.onProgressClick()
        assertEquals(InstructionsEvent.NavigateToProgress(id), viewModel.events.first())
        assertFalse(viewModel.uiState.first().progressOptInDialogVisible)
    }

    @Test
    fun onPreviewTargetsClick_prefNotSet_showsOptInDialog() = runBlocking {
        val id = newExperiment()
        addCheckin(id)
        viewModel.load(id, pendingOutcome = null, forceNewStageDialog = false)
        viewModel.uiState.first { !it.isLoading }

        viewModel.onPreviewTargetsClick()

        assertTrue(viewModel.uiState.first().targetPreviewOptInDialogVisible)
        assertTrue(viewModel.uiState.first().upcomingTargets.isEmpty())
    }

    @Test
    fun onTargetPreviewOptInConfirmed_persistsPrefAndShowsPreviewDialog() = runBlocking {
        val id = newExperiment()
        addCheckin(id)
        viewModel.load(id, pendingOutcome = null, forceNewStageDialog = false)
        viewModel.uiState.first { !it.isLoading }

        viewModel.onPreviewTargetsClick()
        viewModel.onTargetPreviewOptInConfirmed()
        val state = viewModel.uiState.first { it.targetPreviewDialogVisible }

        assertFalse(state.targetPreviewOptInDialogVisible)
        assertEquals(repository.getUpcomingTargetPreview(id), state.upcomingTargets)

        // Once opted in, a later click loads straight into the preview dialog.
        viewModel.onTargetPreviewDismissed()
        viewModel.onPreviewTargetsClick()
        assertTrue(viewModel.uiState.first { it.targetPreviewDialogVisible }.targetPreviewDialogVisible)
    }

    @Test
    fun prefs_useTheAppsDefaultSharedPreferences_soOptInsPersistAcrossViewModelInstances() = runBlocking {
        // Guards against accidentally reading/writing a differently-scoped SharedPreferences
        // instance than the rest of the app (e.g. SettingsActivity) uses.
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("show_progress_during_experiment", true).apply()

        val id = newExperiment()
        addCheckin(id)
        viewModel.load(id, pendingOutcome = null, forceNewStageDialog = false)
        viewModel.uiState.first { !it.isLoading }

        viewModel.onProgressClick()

        assertEquals(InstructionsEvent.NavigateToProgress(id), viewModel.events.first())
    }
}
