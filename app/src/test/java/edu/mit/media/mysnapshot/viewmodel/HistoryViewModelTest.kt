package edu.mit.media.mysnapshot.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import edu.mit.media.mysnapshot.data.ExperimentExporter
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.QuantifyMeDatabase
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for [HistoryViewModel] -- the ViewModel introduced for
 * AGENT_PLANS/IMPROVEMENTS.md item 2.2 (no presentation-layer architecture) that now owns
 * [edu.mit.media.mysnapshot.activities.HistoryActivity]'s repository access and the
 * cancel/export side effects previously driven ad hoc from `lifecycleScope.launch`.
 *
 * Follows the same real-in-memory-Room + real-`HealthConnectManager` pattern as
 * `ExperimentRepositoryTest` (see that file's class doc for why a real `HealthConnectManager`
 * is safe under Robolectric, and why these tests are scoped to the
 * checkin-only "leisurehappiness" type). `Dispatchers.setMain` is overridden with an
 * `UnconfinedTestDispatcher` so `viewModelScope` (which defaults to `Dispatchers.Main.immediate`)
 * doesn't depend on Robolectric's paused main Looper.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class HistoryViewModelTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var repository: ExperimentRepository
    private lateinit var viewModel: HistoryViewModel

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
        viewModel = HistoryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun uiState_reflectsExperimentsFromRepositoryOnceLoaded() = runBlocking {
        repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3)

        val state = viewModel.uiState.first { it.experiments != null }

        assertEquals(1, state.experiments!!.size)
        assertFalse(state.isCancelling)
    }

    @Test
    fun cancelExperiment_marksCancelledInDbAndSettlesBusyFlagBackToFalse() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()
        val experiment = db.experimentDao().getById(id).first()!!

        // Establish the initial (not-yet-cancelled) reactive subscription first, matching how
        // HistoryActivity always has the list rendered before the user can tap "Quit".
        viewModel.uiState.first { it.experiments?.isNotEmpty() == true }

        viewModel.cancelExperiment(experiment)

        val updated = viewModel.uiState.first { state -> state.experiments?.any { it.isCancelled } == true }
        assertTrue(updated.experiments!!.single().isCancelled)
        assertFalse("isCancelling must settle back to false once the write completes", updated.isCancelling)

        val entity = db.experimentDao().getById(id).first()!!
        assertTrue(entity.isCancelled)
        assertFalse(entity.isActive)
    }

    @Test
    fun exportExperiment_emitsExportReadyEventWithTheBuiltJson() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()
        val experiment = db.experimentDao().getById(id).first()!!

        viewModel.exportExperiment(experiment)
        val event = viewModel.events.first() as HistoryEvent.ExportReady

        assertEquals(experiment.id, event.experiment.id)
        val expectedJson = ExperimentExporter.buildExportJson(experiment, emptyList())
        assertEquals(expectedJson, event.json)
    }
}
