package edu.mit.media.mysnapshot.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * Coverage for [ExperimentCompleteViewModel] -- the ViewModel introduced for
 * AGENT_PLANS/IMPROVEMENTS.md item 2.2 that now owns
 * [edu.mit.media.mysnapshot.activities.ExperimentCompleteActivity]'s data load and the
 * "redirect away if this isn't a valid completed experiment" guard that used to run both
 * right after the async load and again on every `onResume()`.
 *
 * See `ExperimentRepositoryTest`'s class doc for why a real `HealthConnectManager` is safe
 * under Robolectric and why these tests are scoped to the checkin-only "leisurehappiness" type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ExperimentCompleteViewModelTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var repository: ExperimentRepository
    private lateinit var viewModel: ExperimentCompleteViewModel

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
        viewModel = ExperimentCompleteViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun completeExperiment(resultValue: Float = 8f, resultConfidence: Float = 0.9f): Int {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()
        val entity = db.experimentDao().getById(id).first()!!
        db.experimentDao().update(
            entity.copy(isActive = false, resultValue = resultValue, resultConfidence = resultConfidence)
        )
        return id
    }

    @Test
    fun load_completedExperimentById_populatesResultAndEmitsNoNavigation() = runBlocking {
        val id = completeExperiment(resultValue = 8f, resultConfidence = 0.9f)

        viewModel.load(id)
        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(id, state.experiment?.id)
        assertEquals("leisurehappiness", state.experimentType?.typeKey)
        val event = withTimeoutOrNull(100) { viewModel.events.first() }
        assertNull("a valid completed experiment must not redirect away", event)
    }

    @Test
    fun load_latestExperiment_whenNoIdGiven_fallsBackToGetLatestExperiment() = runBlocking {
        val id = completeExperiment()

        viewModel.load(-1)
        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(id, state.experiment?.id)
    }

    @Test
    fun load_noExperiment_emitsNavigateToMain() = runBlocking {
        viewModel.load(-1)

        assertEquals(ExperimentCompleteEvent.NavigateToMain, viewModel.events.first())
    }

    @Test
    fun load_stillActiveExperiment_emitsNavigateToMain() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()

        viewModel.load(id)

        assertEquals(
            "an active (not-yet-completed) experiment must redirect back to MainActivity",
            ExperimentCompleteEvent.NavigateToMain,
            viewModel.events.first()
        )
    }

    @Test
    fun load_inactiveWithNoResultValue_emitsNavigateToMain() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()
        val entity = db.experimentDao().getById(id).first()!!
        db.experimentDao().update(entity.copy(isActive = false, resultValue = null))

        viewModel.load(id)

        assertEquals(ExperimentCompleteEvent.NavigateToMain, viewModel.events.first())
    }

    @Test
    fun onResume_beforeLoadCompletes_doesNothing() = runBlocking {
        // Guards the legacy `isLoading` early-return in onResume(): calling it before any load()
        // must not throw or emit, since there's nothing loaded yet to validate.
        viewModel.onResume()

        val event = withTimeoutOrNull(100) { viewModel.events.first() }
        assertNull(event)
    }

    @Test
    fun onResume_afterExperimentBecameInvalid_emitsNavigateToMain() = runBlocking {
        val id = completeExperiment()
        viewModel.load(id)
        viewModel.uiState.first { !it.isLoading }
        assertNull(withTimeoutOrNull(100) { viewModel.events.first() })

        // Experiment gets cancelled/re-activated elsewhere while this screen is backgrounded.
        val entity = db.experimentDao().getById(id).first()!!
        db.experimentDao().update(entity.copy(resultValue = null))

        viewModel.onResume()

        assertEquals(ExperimentCompleteEvent.NavigateToMain, viewModel.events.first())
    }

    @Test
    fun load_calledTwiceWithSameId_doesNotReFetch() = runBlocking {
        val id = completeExperiment()

        viewModel.load(id)
        viewModel.uiState.first { !it.isLoading }
        viewModel.load(id)

        // No crash / no duplicate NavigateToMain -- state stays as loaded from the first call.
        val state = viewModel.uiState.first()
        assertTrue(!state.isLoading)
        assertEquals(id, state.experiment?.id)
    }
}
