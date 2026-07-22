package edu.mit.media.mysnapshot.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.CheckinEntity
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
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for [MainViewModel] -- the ViewModel introduced for AGENT_PLANS/IMPROVEMENTS.md item
 * 2.2 that now owns [edu.mit.media.mysnapshot.activities.MainActivity]'s routing decision
 * (previously an inline `lifecycleScope.launch` in the Activity). `route()` runs in
 * `viewModelScope` rather than the Activity's `lifecycleScope` specifically so it survives a
 * config change mid-decision instead of restarting from scratch.
 *
 * See `ExperimentRepositoryTest`'s class doc for why a real `HealthConnectManager` is safe
 * under Robolectric and why these tests are scoped to the checkin-only "leisurehappiness" type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class MainViewModelTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var repository: ExperimentRepository
    private lateinit var viewModel: MainViewModel

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
        viewModel = MainViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun route_noExperiment_emitsNavigateToChoose() = runBlocking {
        viewModel.route(forceCheckin = false)

        assertEquals(MainEvent.NavigateToChoose, viewModel.events.first())
    }

    @Test
    fun route_cancelledExperiment_emitsNavigateToChoose() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()
        repository.cancelExperiment(id)

        viewModel.route(forceCheckin = false)

        assertEquals(MainEvent.NavigateToChoose, viewModel.events.first())
    }

    @Test
    fun route_completedExperiment_emitsNavigateToComplete() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()
        val entity = db.experimentDao().getById(id).first()!!
        db.experimentDao().update(entity.copy(isActive = false, resultValue = 10f, resultConfidence = 0.9f))

        viewModel.route(forceCheckin = false)

        assertEquals(MainEvent.NavigateToComplete(id), viewModel.events.first())
    }

    @Test
    fun route_activeNotCheckedInToday_forceCheckinFalse_emitsNavigateToCheckin() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()
        val entity = db.experimentDao().getById(id).first()!!
        db.experimentDao().update(entity.copy(startTime = OffsetDateTime.now().minusDays(2)))

        viewModel.route(forceCheckin = false)

        assertEquals(
            "started more than a day ago with no check-in recorded today -- must route to checkin",
            MainEvent.NavigateToCheckin(id),
            viewModel.events.first()
        )
    }

    @Test
    fun route_activeAlreadyCheckedInToday_forceCheckinFalse_emitsNavigateToInstructions() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()
        val entity = db.experimentDao().getById(id).first()!!
        db.experimentDao().update(entity.copy(startTime = OffsetDateTime.now().minusDays(2)))
        db.checkinDao().insert(
            CheckinEntity(
                experimentId = id,
                checkinDate = OffsetDateTime.now(),
                didFollowInstructions = 3,
                happiness = 3,
                stress = 3,
                productivity = 3,
                leisureTime = 30
            )
        )

        viewModel.route(forceCheckin = false)

        assertEquals(MainEvent.NavigateToInstructions(id), viewModel.events.first())
    }

    @Test
    fun route_forceCheckinTrue_routesToCheckinEvenThoughAlreadyCheckedInToday() = runBlocking {
        // Freshly created -- "started today" alone would already satisfy hadCheckinToday, so this
        // isolates that forceCheckin overrides that guard exactly like the legacy `FORCE_CHECKIN` flag.
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()

        viewModel.route(forceCheckin = true)

        assertEquals(MainEvent.NavigateToCheckin(id), viewModel.events.first())
    }

    @Test
    fun route_calledTwice_onlyRoutesOnce() = runBlocking {
        viewModel.route(forceCheckin = false)
        viewModel.events.first()

        viewModel.route(forceCheckin = false)
        val secondEvent = withTimeoutOrNull(100) { viewModel.events.first() }

        assertNull(
            "route() must be idempotent across calls (e.g. a config change) -- the second call must not re-fetch and re-emit",
            secondEvent
        )
    }
}
