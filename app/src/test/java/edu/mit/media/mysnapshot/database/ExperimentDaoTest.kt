package edu.mit.media.mysnapshot.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
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
 * In-memory Room DAO CRUD coverage for [ExperimentDao], including the ordering guarantees
 * `ExperimentRepository`/`HistoryActivity` depend on (`getLatestExperiment`, active/cancelled
 * filtering) and the `CheckinEntity` cascade-delete foreign key. See
 * AGENT_PLANS/IMPROVEMENTS.md item 5 (test coverage backlog) -- priority 2 target.
 *
 * `@Config(application = Application::class)` bypasses `MyApplication`'s `@HiltAndroidApp`
 * so Robolectric doesn't try to boot the real Hilt DI graph (Room prod DB, Health Connect,
 * ACRA, WorkManager) just to get a Context -- these tests only need a plain Context to build
 * an in-memory database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ExperimentDaoTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var dao: ExperimentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuantifyMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.experimentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun experiment(
        type: String = "leisurehappiness",
        startTime: DateTime = DateTime.now(DateTimeZone.UTC),
        isActive: Boolean = true,
        isCancelled: Boolean = false
    ) = ExperimentEntity(
        type = type,
        startTime = startTime,
        isActive = isActive,
        isCancelled = isCancelled,
        selfEfficacy = 3,
        appEfficacy = 3,
        experimentEfficacy = 3,
        stageDatesJson = "[]",
        stageTargetValuesJson = "[]",
        stageRestartCountJson = "[]"
    )

    @Test
    fun insert_thenGetById_returnsSameData() = runBlocking {
        val id = dao.insert(experiment(type = "sleepdurationproductivity"))

        val loaded = dao.getById(id.toInt()).first()

        assertEquals("sleepdurationproductivity", loaded?.type)
        assertEquals(3, loaded?.selfEfficacy)
        assertTrue(loaded?.isActive == true)
    }

    @Test
    fun update_persistsChangedFields() = runBlocking {
        val id = dao.insert(experiment())
        val original = dao.getById(id.toInt()).first()!!

        dao.update(original.copy(currentStage = 2, resultValue = 42f))

        val updated = dao.getById(id.toInt()).first()
        assertEquals(2, updated?.currentStage)
        assertEquals(42f, updated?.resultValue)
    }

    @Test
    fun delete_removesRow() = runBlocking {
        val id = dao.insert(experiment())
        val row = dao.getById(id.toInt()).first()!!

        dao.delete(row)

        assertNull(dao.getById(id.toInt()).first())
    }

    @Test
    fun deleteById_removesOnlyMatchingRow() = runBlocking {
        val id1 = dao.insert(experiment())
        val id2 = dao.insert(experiment())

        dao.deleteById(id1.toInt())

        assertNull(dao.getById(id1.toInt()).first())
        assertTrue(dao.getById(id2.toInt()).first() != null)
    }

    @Test
    fun getCurrentExperiment_returnsOnlyActiveRow() = runBlocking {
        dao.insert(experiment(isActive = false))
        val activeId = dao.insert(experiment(isActive = true))

        val current = dao.getCurrentExperiment().first()

        assertEquals(activeId.toInt(), current?.id)
    }

    @Test
    fun getCurrentExperiment_returnsNull_whenNoneActive() = runBlocking {
        dao.insert(experiment(isActive = false))

        assertNull(dao.getCurrentExperiment().first())
    }

    @Test
    fun getLatestExperiment_ordersByStartTimeDescending_regardlessOfActiveState() = runBlocking {
        val older = experiment(startTime = DateTime(2020, 1, 1, 12, 0, DateTimeZone.UTC), isActive = false)
        val newer = experiment(startTime = DateTime(2024, 1, 1, 12, 0, DateTimeZone.UTC), isActive = false)
        dao.insert(older)
        val newerId = dao.insert(newer)

        val latest = dao.getLatestExperiment().first()

        // Matches the old CURRENT_EXPERIMENT_PREF pointer semantics: "latest" means most
        // recently started, active or not, so a just-finished experiment stays reachable.
        assertEquals(newerId.toInt(), latest?.id)
    }

    @Test
    fun getAllExperiments_ordersByStartTimeDescending() = runBlocking {
        // Converters.toDateTime() re-materializes stored rows in the JVM's default time
        // zone (see ConvertersTest), so a stored instant's calendar year as read back can
        // differ from the year it was constructed with if that instant is near a year
        // boundary in UTC. Use a safely mid-year date (June 15) so the `.year` assertion
        // below is stable regardless of the machine's default time zone.
        val first = experiment(startTime = DateTime(2021, 6, 15, 12, 0, DateTimeZone.UTC))
        val second = experiment(startTime = DateTime(2022, 6, 15, 12, 0, DateTimeZone.UTC))
        val third = experiment(startTime = DateTime(2023, 6, 15, 12, 0, DateTimeZone.UTC))
        dao.insert(first)
        dao.insert(third)
        dao.insert(second)

        val all = dao.getAllExperiments().first()

        assertEquals(listOf(2023, 2022, 2021), all.map { it.startTime.year })
    }

    @Test
    fun getCompletedExperiments_includesFinishedAndCancelled_excludesActive() = runBlocking {
        dao.insert(experiment(isActive = true, isCancelled = false)) // still running
        dao.insert(experiment(isActive = false, isCancelled = false)) // finished normally
        dao.insert(experiment(isActive = false, isCancelled = true)) // cancelled
        // Active *and* cancelled shouldn't normally happen together, but the query is an OR,
        // so it should still surface a row where isCancelled=true even if isActive=true.
        dao.insert(experiment(isActive = true, isCancelled = true))

        val completed = dao.getCompletedExperiments().first()

        assertEquals(3, completed.size)
        assertTrue(completed.none { it.isActive && !it.isCancelled })
    }

    @Test
    fun deletingExperiment_cascadesToItsCheckins() = runBlocking {
        val checkinDao = db.checkinDao()
        val experimentId = dao.insert(experiment()).toInt()
        checkinDao.insert(
            CheckinEntity(
                experimentId = experimentId,
                checkinDate = DateTime.now(DateTimeZone.UTC),
                didFollowInstructions = 1,
                happiness = 5,
                stress = 2,
                productivity = 4,
                leisureTime = 60
            )
        )
        assertEquals(1, checkinDao.getCheckinsForExperiment(experimentId).first().size)

        dao.deleteById(experimentId)

        assertTrue(checkinDao.getCheckinsForExperiment(experimentId).first().isEmpty())
    }
}
