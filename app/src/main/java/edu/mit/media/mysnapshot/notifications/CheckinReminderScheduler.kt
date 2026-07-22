package edu.mit.media.mysnapshot.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import edu.mit.media.mysnapshot.data.NotificationData
import edu.mit.media.mysnapshot.data.loadUserData
import edu.mit.media.mysnapshot.data.parseNotificationTime
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * (Re)computes and (re)enqueues the daily check-in reminder as a WorkManager periodic
 * request -- replaces AlarmReceiver.setRecurringAlarm's AlarmManager.setInexactRepeating call
 * (AGENT_PLANS/MODERNIZE.md Phase 4). Call whenever the user's notification preference is
 * saved (SettingsActivity.onFinish) and once at process start (MyApplication.onCreate) so the
 * reminder is actually scheduled without requiring a device reboot first -- the old
 * AlarmManager version only ever (re)armed on BOOT_COMPLETED, so nothing scheduled it on
 * first run or immediately after a Settings change.
 */
object CheckinReminderScheduler {

    private const val WORK_NAME = "checkin_reminder"

    fun schedule(context: Context) {
        val userData = loadUserData(context).userData
        val notificationData = userData.notificationData ?: NotificationData()

        val workManager = WorkManager.getInstance(context)

        if (!notificationData.notificationSet || notificationData.notificationTime == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val notificationTime = parseNotificationTime(notificationData.notificationTime)
            ?: return

        val target = Calendar.getInstance()
        val now = target.timeInMillis
        target.set(Calendar.HOUR_OF_DAY, notificationTime.hourOfDay)
        target.set(Calendar.MINUTE, notificationTime.minuteOfHour)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        if (target.timeInMillis <= now) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelayMillis = target.timeInMillis - now

        val request = PeriodicWorkRequestBuilder<CheckinReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
