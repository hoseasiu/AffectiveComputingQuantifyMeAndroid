package edu.mit.media.mysnapshot.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.data.ExperimentStageState
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
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for [ExperimentProgressViewModel] -- the ViewModel introduced for
 * AGENT_PLANS/IMPROVEMENTS.md item 2.2 that now owns
 * [edu.mit.media.mysnapshot.activities.ExperimentProgressActivity]'s repository access
 * (previously a `LaunchedEffect(experimentId)` calling the repository directly from Compose).
 *
 * See `ExperimentRepositoryTest`'s class doc for why a real `HealthConnectManager` is safe
 * under Robolectric and why these tests are scoped to the checkin-only "leisurehappiness" type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ExperimentProgressViewModelTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var repository: ExperimentRepository
    private lateinit var viewModel: ExperimentProgressViewModel

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
        viewModel = ExperimentProgressViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun load_freshExperiment_populatesTypeWithNoStagesCompletedYet() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()

        viewModel.load(id)
        val state = viewModel.uiState.first { it.stages != null }

        assertEquals("leisurehappiness", state.experimentType?.typeKey)
        assertTrue("a still-in-baseline experiment has no completed stages yet", state.stages!!.isEmpty())
    }

    @Test
    fun load_afterAdvancingPastBaseline_exposesTheSameStageProgressAsTheRepository() = runBlocking {
        val today = LocalDate.now()
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()

        // Backdate into stage 1 (mirrors ExperimentRepositoryTest's getProgressSummary test):
        // the repository has no clock-injection seam, so stage-state JSON is edited directly.
        val entity = db.experimentDao().getById(id).first()!!
        val state = ExperimentStageState.fromJson(
            entity.stageDatesJson, entity.stageTargetValuesJson, entity.stageRestartCountJson
        ).withStageDates(1, today.minusDays(3), today.plusDays(4))
        val (datesJson, targetsJson, restartJson) = state.toJson()
        db.experimentDao().update(
            entity.copy(currentStage = 1, stageDatesJson = datesJson, stageTargetValuesJson = targetsJson, stageRestartCountJson = restartJson)
        )

        viewModel.load(id)
        val uiState = viewModel.uiState.first { it.stages != null }
        val expected = repository.getProgressSummary(id)

        assertEquals(expected, uiState.stages)
        assertEquals(1, uiState.stages!!.size)
        assertEquals(1, uiState.stages!!.single().stage)
    }
}
