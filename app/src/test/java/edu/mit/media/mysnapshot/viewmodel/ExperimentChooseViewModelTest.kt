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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for [ExperimentChooseViewModel] -- the ViewModel introduced for
 * AGENT_PLANS/IMPROVEMENTS.md item 2.2 that now owns
 * [edu.mit.media.mysnapshot.activities.ExperimentChooseActivity]'s "redirect to MainActivity
 * if an experiment already exists" `onResume` guard (previously an inline
 * `lifecycleScope.launch` in the Activity). `checkForExistingExperiment()` returns its
 * launched `Job` purely so these tests can `join()` it before asserting on the event
 * channel, rather than racing a real background Room read with a fixed sleep/timeout.
 *
 * See `ExperimentRepositoryTest`'s class doc for why a real `HealthConnectManager` is safe
 * under Robolectric and why these tests are scoped to the checkin-only "leisurehappiness" type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ExperimentChooseViewModelTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var repository: ExperimentRepository
    private lateinit var viewModel: ExperimentChooseViewModel

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
        viewModel = ExperimentChooseViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun checkForExistingExperiment_noExperiment_emitsNoNavigationEvent() = runBlocking {
        viewModel.checkForExistingExperiment().join()

        val event = withTimeoutOrNull(100) { viewModel.events.first() }
        assertNull("no active experiment exists yet -- must not redirect away from the choose screen", event)
    }

    @Test
    fun checkForExistingExperiment_activeExperimentExists_emitsNavigateToMain() = runBlocking {
        repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3)

        viewModel.checkForExistingExperiment().join()
        val event = viewModel.events.first()

        assertEquals(ExperimentChooseEvent.NavigateToMain, event)
    }

    @Test
    fun checkForExistingExperiment_latestExperimentWasCancelled_emitsNoNavigationEvent() = runBlocking {
        val id = repository.createExperiment(ExperimentType.fromTypeKey("leisurehappiness"), 3, 3, 3).toInt()
        repository.cancelExperiment(id)

        viewModel.checkForExistingExperiment().join()
        val event = withTimeoutOrNull(100) { viewModel.events.first() }

        assertNull("a cancelled experiment must let the user pick a new one, not bounce to MainActivity", event)
    }
}
