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
 * In-memory Room DAO CRUD coverage for [CheckinDao]. See
 * AGENT_PLANS/IMPROVEMENTS.md item 5 (test coverage backlog) -- priority 2 target.
 *
 * All fixture timestamps use noon UTC: [Converters] re-materializes stored rows in the
 * JVM's default time zone (see `ConvertersTest`), so a midnight-ish UTC instant could read
 * back as the previous/next calendar day depending on the machine's default zone. Noon UTC
 * keeps every `.dayOfMonth` assertion below stable regardless of where this runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CheckinDaoTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var checkinDao: CheckinDao
    private lateinit var experimentDao: ExperimentDao
    private var experimentId: Int = 0
    private var otherExperimentId: Int = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuantifyMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        checkinDao = db.checkinDao()
        experimentDao = db.experimentDao()

        experimentId = experimentDao.insert(baseExperiment()).toInt()
        otherExperimentId = experimentDao.insert(baseExperiment()).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun baseExperiment() = ExperimentEntity(
        type = "leisurehappiness",
        startTime = DateTime.now(DateTimeZone.UTC),
        selfEfficacy = 3,
        appEfficacy = 3,
        experimentEfficacy = 3,
        stageDatesJson = "[]",
        stageTargetValuesJson = "[]",
        stageRestartCountJson = "[]"
    )

    private fun checkin(
        experimentId: Int = this.experimentId,
        date: DateTime,
        happiness: Int = 5
    ) = CheckinEntity(
        experimentId = experimentId,
        checkinDate = date,
        didFollowInstructions = 1,
        happiness = happiness,
        stress = 2,
        productivity = 4,
        leisureTime = 60
    )

    @Test
    fun insert_thenGetById_returnsSameData() = runBlocking {
        val id = checkinDao.insert(checkin(date = DateTime(2024, 1, 1, 12, 0, DateTimeZone.UTC), happiness = 7))

        val loaded = checkinDao.getById(id.toInt()).first()

        assertEquals(7, loaded?.happiness)
        assertEquals(experimentId, loaded?.experimentId)
    }

    @Test
    fun update_persistsChangedFields() = runBlocking {
        val id = checkinDao.insert(checkin(date = DateTime(2024, 1, 1, 12, 0, DateTimeZone.UTC)))
        val original = checkinDao.getById(id.toInt()).first()!!

        checkinDao.update(original.copy(happiness = 1, stress = 5))

        val updated = checkinDao.getById(id.toInt()).first()
        assertEquals(1, updated?.happiness)
        assertEquals(5, updated?.stress)
    }

    @Test
    fun delete_removesRow() = runBlocking {
        val id = checkinDao.insert(checkin(date = DateTime(2024, 1, 1, 12, 0, DateTimeZone.UTC)))
        val row = checkinDao.getById(id.toInt()).first()!!

        checkinDao.delete(row)

        assertNull(checkinDao.getById(id.toInt()).first())
    }

    @Test
    fun getCheckinsForExperiment_ordersDescendingAndExcludesOtherExperiments() = runBlocking {
        checkinDao.insert(checkin(date = DateTime(2024, 1, 1, 12, 0, DateTimeZone.UTC)))
        checkinDao.insert(checkin(date = DateTime(2024, 1, 3, 12, 0, DateTimeZone.UTC)))
        checkinDao.insert(checkin(date = DateTime(2024, 1, 2, 12, 0, DateTimeZone.UTC)))
        checkinDao.insert(checkin(experimentId = otherExperimentId, date = DateTime(2024, 1, 5, 12, 0, DateTimeZone.UTC)))

        val result = checkinDao.getCheckinsForExperiment(experimentId).first()

        assertEquals(listOf(3, 2, 1), result.map { it.checkinDate.dayOfMonth })
        assertTrue(result.all { it.experimentId == experimentId })
    }

    @Test
    fun getCheckinsForExperimentSince_filtersAndOrdersAscending() = runBlocking {
        checkinDao.insert(checkin(date = DateTime(2024, 1, 1, 12, 0, DateTimeZone.UTC)))
        checkinDao.insert(checkin(date = DateTime(2024, 1, 3, 12, 0, DateTimeZone.UTC)))
        checkinDao.insert(checkin(date = DateTime(2024, 1, 5, 12, 0, DateTimeZone.UTC)))

        val since = checkinDao.getCheckinsForExperimentSince(
            experimentId, DateTime(2024, 1, 2, 0, 0, DateTimeZone.UTC)
        ).first()

        assertEquals(listOf(3, 5), since.map { it.checkinDate.dayOfMonth })
    }

    @Test
    fun getLastCheckin_returnsMostRecentAcrossAllExperiments() = runBlocking {
        checkinDao.insert(checkin(date = DateTime(2024, 1, 1, 12, 0, DateTimeZone.UTC)))
        checkinDao.insert(checkin(experimentId = otherExperimentId, date = DateTime(2024, 1, 9, 12, 0, DateTimeZone.UTC)))
        checkinDao.insert(checkin(date = DateTime(2024, 1, 4, 12, 0, DateTimeZone.UTC)))

        val last = checkinDao.getLastCheckin().first()

        assertEquals(9, last?.checkinDate?.dayOfMonth)
        assertEquals(otherExperimentId, last?.experimentId)
    }

    @Test
    fun deleteForExperiment_removesOnlyThatExperimentsCheckins() = runBlocking {
        checkinDao.insert(checkin(date = DateTime(2024, 1, 1, 12, 0, DateTimeZone.UTC)))
        checkinDao.insert(checkin(experimentId = otherExperimentId, date = DateTime(2024, 1, 2, 12, 0, DateTimeZone.UTC)))

        checkinDao.deleteForExperiment(experimentId)

        assertTrue(checkinDao.getCheckinsForExperiment(experimentId).first().isEmpty())
        assertEquals(1, checkinDao.getCheckinsForExperiment(otherExperimentId).first().size)
    }
}
