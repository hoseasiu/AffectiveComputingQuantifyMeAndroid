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
 * Coverage for [AdherenceNudgeScheduler.schedule]'s target-time computation -- the same
 * "next reminder fires today if the time hasn't passed yet, otherwise tomorrow" recipe as
 * [CheckinReminderScheduler], but offset 6 hours past the user's check-in time rather than
 * fired exactly at it (AGENT_PLANS/IMPROVEMENTS.md item 5, [issue #21](https://github.com/hoseasiu/AffectiveComputingQuantifyMeAndroid/issues/21)).
 * As with `CheckinReminderSchedulerTest`, this isn't a pure function -- it reads
 * `SettingsActivity`'s SharedPreferences-backed UserData and drives a real `WorkManager`
 * instance -- so it's exercised end-to-end against WorkManager's test harness. Expected delays
 * are computed with the exact same `Calendar` recipe `schedule()` uses (checkin time + 6h,
 * rolled to tomorrow if already past), rather than hardcoded wall-clock times, so the test
 * isn't flaky around real-world midnight/DST boundaries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AdherenceNudgeSchedulerTest {

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

    /** Mirrors schedule()'s own Calendar-based target-time computation (checkin time + 6h). */
    private fun expectedDelayMillis(hourOfDay: Int, minute: Int): Long {
        val target = Calendar.getInstance()
        val now = target.timeInMillis
        target.set(Calendar.HOUR_OF_DAY, hourOfDay)
        target.set(Calendar.MINUTE, minute)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        target.add(Calendar.HOUR_OF_DAY, 6)
        if (target.timeInMillis <= now) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now
    }

    private fun calendarAt(hourOfDay: Int, minute: Int): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun getWorkInfos(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork("adherence_nudge").get()

    @Test
    fun schedule_notificationDisabled_doesNotEnqueueWork() {
        saveUserData(notificationSet = false, notificationTime = "09:30")

        AdherenceNudgeScheduler.schedule(context)

        val infos = getWorkInfos().filter { it.state != WorkInfo.State.CANCELLED }
        assertTrue("expected no active nudge work, got $infos", infos.isEmpty())
    }

    @Test
    fun schedule_noUserDataSaved_usesDefaultNotificationDataOffsetBySixHours() {
        // No saveUserData() call: SettingsActivity.loadUserData falls back to a default
        // UserData(), whose NotificationData defaults to notificationSet = true and
        // notificationTime = "09:30". schedule() should tolerate a completely absent
        // preference (first app run) and enqueue using 09:30 + 6h = 15:30 as the target.
        AdherenceNudgeScheduler.schedule(context)

        val expected = expectedDelayMillis(9, 30)
        val infos = getWorkInfos().filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, infos.size)
        assertTrue(Math.abs(infos[0].initialDelayMillis - expected) < TimeUnit.MINUTES.toMillis(2))
    }

    @Test
    fun schedule_sixHourOffsetStillLaterToday_enqueuesWithDelayUntilThatTimeToday() {
        // Checkin time is 10 minutes from now, so +6h is comfortably later today.
        val checkin = calendarAt(
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            Calendar.getInstance().get(Calendar.MINUTE)
        ).apply { add(Calendar.MINUTE, 10) }
        val timeString = String.format("%02d:%02d", checkin.get(Calendar.HOUR_OF_DAY), checkin.get(Calendar.MINUTE))
        val expected = expectedDelayMillis(checkin.get(Calendar.HOUR_OF_DAY), checkin.get(Calendar.MINUTE))

        saveUserData(notificationSet = true, notificationTime = timeString)
        AdherenceNudgeScheduler.schedule(context)

        val infos = getWorkInfos().filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, infos.size)
        // checkin (~10 min away) + 6h must land well under 24h away, confirming the
        // "don't add a day" branch was taken.
        assertTrue(infos[0].initialDelayMillis < TimeUnit.HOURS.toMillis(24))
        assertTrue(Math.abs(infos[0].initialDelayMillis - expected) < TimeUnit.MINUTES.toMillis(2))
    }

    @Test
    fun schedule_sixHourOffsetAlreadyPassedToday_wrapsToTomorrow() {
        // Checkin time is 10 minutes in the past, so checkin+6h has almost certainly also
        // already passed today (true for any checkin time before 18:00 "now", i.e. nearly
        // always) -- schedule() must roll the target to tomorrow.
        val checkin = Calendar.getInstance().apply { add(Calendar.MINUTE, -10) }
        val timeString = String.format("%02d:%02d", checkin.get(Calendar.HOUR_OF_DAY), checkin.get(Calendar.MINUTE))
        val expected = expectedDelayMillis(checkin.get(Calendar.HOUR_OF_DAY), checkin.get(Calendar.MINUTE))

        saveUserData(notificationSet = true, notificationTime = timeString)
        AdherenceNudgeScheduler.schedule(context)

        val infos = getWorkInfos().filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, infos.size)
        assertTrue(Math.abs(infos[0].initialDelayMillis - expected) < TimeUnit.MINUTES.toMillis(2))
    }

    @Test
    fun schedule_checkinTimeItselfHasntPassedButPlusSixHoursHasAlreadyWrapped_rollsToTomorrow() {
        // Regression guard for evaluating the +6h target against `now`, not against the
        // (still-future) checkin time itself: pick a checkin time far enough in the future
        // that checkin+6h would cross past midnight and land *before* "now" was it evaluated
        // naively -- e.g. checkin 30 minutes from now, but if "now" is already within 6h of
        // midnight, checkin+6h wraps to tomorrow while checkin itself is still today.
        val now = Calendar.getInstance()
        val checkin = (now.clone() as Calendar).apply { add(Calendar.MINUTE, 30) }
        val timeString = String.format("%02d:%02d", checkin.get(Calendar.HOUR_OF_DAY), checkin.get(Calendar.MINUTE))
        val expected = expectedDelayMillis(checkin.get(Calendar.HOUR_OF_DAY), checkin.get(Calendar.MINUTE))

        saveUserData(notificationSet = true, notificationTime = timeString)
        AdherenceNudgeScheduler.schedule(context)

        val infos = getWorkInfos().filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, infos.size)
        assertTrue(Math.abs(infos[0].initialDelayMillis - expected) < TimeUnit.MINUTES.toMillis(2))
    }

    @Test
    fun schedule_calledTwice_updatesRatherThanDuplicatingWork() {
        saveUserData(notificationSet = true, notificationTime = "09:30")
        AdherenceNudgeScheduler.schedule(context)
        AdherenceNudgeScheduler.schedule(context)

        val infos = getWorkInfos().filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, infos.size)
    }

    @Test
    fun schedule_thenDisableNotifications_cancelsExistingWork() {
        saveUserData(notificationSet = true, notificationTime = "09:30")
        AdherenceNudgeScheduler.schedule(context)
        assertEquals(1, getWorkInfos().count { it.state != WorkInfo.State.CANCELLED })

        saveUserData(notificationSet = false, notificationTime = "09:30")
        AdherenceNudgeScheduler.schedule(context)

        assertTrue(getWorkInfos().none { it.state != WorkInfo.State.CANCELLED })
    }
}
