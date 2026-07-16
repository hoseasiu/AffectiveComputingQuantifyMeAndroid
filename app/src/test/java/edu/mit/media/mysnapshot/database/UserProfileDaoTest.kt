package edu.mit.media.mysnapshot.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In-memory Room DAO coverage for [UserProfileDao]'s single-row table. See
 * AGENT_PLANS/IMPROVEMENTS.md item 5 (test coverage backlog) -- priority 2 target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class UserProfileDaoTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var dao: UserProfileDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuantifyMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.userProfileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getUserProfile_returnsNull_whenNothingInserted() = runBlocking {
        assertNull(dao.getUserProfile().first())
    }

    @Test
    fun insert_thenGetUserProfile_returnsSameData() = runBlocking {
        dao.insert(
            UserProfileEntity(
                timezone = "America/New_York",
                dateOfBirth = "1990-01-01",
                gender = "female",
                baselineHappiness = 5
            )
        )

        val loaded = dao.getUserProfile().first()

        assertEquals("America/New_York", loaded?.timezone)
        assertEquals(5, loaded?.baselineHappiness)
        assertEquals(1, loaded?.id) // single-row table, fixed id
    }

    @Test
    fun update_persistsChangedFields() = runBlocking {
        dao.insert(UserProfileEntity(timezone = "UTC"))

        dao.update(UserProfileEntity(timezone = "Europe/London", baselineStress = 3))

        val updated = dao.getUserProfile().first()
        assertEquals("Europe/London", updated?.timezone)
        assertEquals(3, updated?.baselineStress)
    }

    @Test
    fun deleteProfile_removesTheRow() = runBlocking {
        dao.insert(UserProfileEntity(timezone = "UTC"))

        dao.deleteProfile()

        assertNull(dao.getUserProfile().first())
    }
}
