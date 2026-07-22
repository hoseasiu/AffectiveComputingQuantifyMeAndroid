package edu.mit.media.mysnapshot.notifications

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.gson.Gson
import edu.mit.media.mysnapshot.data.NotificationData
import edu.mit.media.mysnapshot.data.USERDATAPREF
import edu.mit.media.mysnapshot.data.UserData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Coverage for [CheckinReminderScheduler.schedule]'s target-time computation -- the
 * "next reminder fires today if the time hasn't passed yet, otherwise tomorrow" logic
 * (AGENT_PLANS/IMPROVEMENTS.md item 5, priority 3 target: "its target-time computation, if
 * it is or can trivially be made testable"). This isn't a pure function (it reads
 * `SettingsActivity`'s SharedPreferences-backed UserData and drives a real `WorkManager`
 * instance), so it's exercised end-to-end against WorkManager's test harness rather than
 * refactored out of production code -- see the class-level note in that file for why no
 * production code changed here.
 *
 * Expected delays are computed with the exact same `Calendar` recipe `schedule()` uses,
 * rather than hardcoded wall-clock times, so the test is not flaky around real-world
 * midnight/DST boundaries.
 *
 * `@Config(application = Application::class)` avoids booting `MyApplication`'s Hilt graph
 * for the same reason as the Room DAO tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CheckinReminderSchedulerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun saveUserData(notificationSet: Boolean, notificationTime: String?) {
        val userData = UserData()
        val notificationData = NotificationData()
        notificationData.notificationSet = notificationSet
        if (notificationTime != null) {
            notificationData.notificationTime = notificationTime
        }
        userData.notificationData = notificationData

        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(USERDATAPREF, Gson().toJson(userData))
            .commit()
    }

    /** Mirrors schedule()'s own Calendar-based target-time computation, for assertions. */
    private fun expectedDelayMillis(hourOfDay: Int, minute: Int): Long {
        val target = Calendar.getInstance()
        val now = target.timeInMillis
        target.set(Calendar.HOUR_OF_DAY, hourOfDay)
        target.set(Calendar.MINUTE, minute)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        if (target.timeInMillis <= now) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now
    }

    private fun soonCalendar(minutesFromNow: Int): Calendar {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, minutesFromNow)
        return cal
    }

    private fun getWorkInfos(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork("checkin_reminder").get()

    @Test
    fun schedule_notificationDisabled_doesNotEnqueueWork() {
        saveUserData(notificationSet = false, notificationTime = "09:30")

        CheckinReminderScheduler.schedule(context)

        val infos = getWorkInfos().filter { it.state != WorkInfo.State.CANCELLED }
        assertTrue("expected no active reminder work, got $infos", infos.isEmpty())
    }

    @Test
    fun schedule_noUserDataSaved_usesDefaultNotificationData() {
        // No saveUserData() call: SettingsActivity.loadUserData falls back to a default
        // UserData(), whose NotificationData defaults to notificationSet = true. Assert
        // schedule() tolerates a completely absent preference (first app run) without
        // crashing, and enqueues using that default rather than silently no-op'ing.
        CheckinReminderScheduler.schedule(context)

        // Default NotificationData() has notificationSet = true, DEFAULT_TIME = "09:30", so
        // the scheduler is expected to enqueue using that default -- confirms schedule()
        // tolerates a completely absent preference (first app run) without crashing.
        val infos = getWorkInfos()
        assertEquals(1, infos.count { it.state != WorkInfo.State.CANCELLED })
    }

    @Test
    fun schedule_timeLaterToday_enqueuesWithDelayUntilThatTimeToday() {
        val target = soonCalendar(minutesFromNow = 10)
        val timeString = String.format("%02d:%02d", target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE))
        val expected = expectedDelayMillis(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE))

        saveUserData(notificationSet = true, notificationTime = timeString)
        CheckinReminderScheduler.schedule(context)

        val infos = getWorkInfos().filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, infos.size)
        // The target time (later today) must be well under 24h away, confirming the "don't
        // add a day" branch was taken.
        assertTrue(infos[0].initialDelayMillis < TimeUnit.HOURS.toMillis(24))
        assertTrue(Math.abs(infos[0].initialDelayMillis - expected) < TimeUnit.MINUTES.toMillis(2))
    }

    @Test
    fun schedule_timeEarlierToday_wrapsToTomorrow() {
        val target = soonCalendar(minutesFromNow = -10)
        val timeString = String.format("%02d:%02d", target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE))
        val expected = expectedDelayMillis(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE))

        saveUserData(notificationSet = true, notificationTime = timeString)
        CheckinReminderScheduler.schedule(context)

        val infos = getWorkInfos().filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, infos.size)
        // A time 10 minutes in the past must roll over to tomorrow -- delay should be close
        // to 24h, not close to zero/negative.
        assertTrue(infos[0].initialDelayMillis > TimeUnit.HOURS.toMillis(23))
        assertTrue(Math.abs(infos[0].initialDelayMillis - expected) < TimeUnit.MINUTES.toMillis(2))
    }

    @Test
    fun schedule_calledTwice_updatesRatherThanDuplicatingWork() {
        saveUserData(notificationSet = true, notificationTime = "09:30")
        CheckinReminderScheduler.schedule(context)
        CheckinReminderScheduler.schedule(context)

        val infos = getWorkInfos().filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, infos.size)
    }

    @Test
    fun schedule_thenDisableNotifications_cancelsExistingWork() {
        saveUserData(notificationSet = true, notificationTime = "09:30")
        CheckinReminderScheduler.schedule(context)
        assertEquals(1, getWorkInfos().count { it.state != WorkInfo.State.CANCELLED })

        saveUserData(notificationSet = false, notificationTime = "09:30")
        CheckinReminderScheduler.schedule(context)

        assertTrue(getWorkInfos().none { it.state != WorkInfo.State.CANCELLED })
    }
}
