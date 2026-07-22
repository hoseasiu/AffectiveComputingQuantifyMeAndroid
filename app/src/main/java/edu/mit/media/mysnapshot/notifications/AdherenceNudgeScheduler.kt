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
 * (Re)computes and (re)enqueues the mid-day adherence nudge as a WorkManager periodic
 * request (AGENT_PLANS/MODERNIZE.md Phase 5.3), scheduled relative to the user's own
 * check-in reminder time rather than a fixed clock time -- by the time the offset elapses,
 * that morning's check-in has (normally) already set today's target, so there's something
 * concrete for [AdherenceNudgeWorker] to remind the user about. Follows the same
 * call-site pattern as [CheckinReminderScheduler] (Settings save + process start) for the
 * same reason: WorkManager's own persistence needs an explicit first enqueue, since there's
 * no boot receiver arming it.
 */
object AdherenceNudgeScheduler {

    private const val WORK_NAME = "adherence_nudge"
    private const val OFFSET_HOURS_AFTER_CHECKIN = 6

    fun schedule(context: Context) {
        val userData = loadUserData(context).userData
        val notificationData = userData.notificationData ?: NotificationData()

        val workManager = WorkManager.getInstance(context)

        if (!notificationData.notificationSet || notificationData.notificationTime == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val checkinTime = parseNotificationTime(notificationData.notificationTime)
            ?: return

        val target = Calendar.getInstance()
        val now = target.timeInMillis
        target.set(Calendar.HOUR_OF_DAY, checkinTime.hourOfDay)
        target.set(Calendar.MINUTE, checkinTime.minuteOfHour)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        target.add(Calendar.HOUR_OF_DAY, OFFSET_HOURS_AFTER_CHECKIN)
        if (target.timeInMillis <= now) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelayMillis = target.timeInMillis - now

        val request = PeriodicWorkRequestBuilder<AdherenceNudgeWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
