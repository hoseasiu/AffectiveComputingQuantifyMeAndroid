package edu.mit.media.mysnapshot.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.activities.MainActivity
import edu.mit.media.mysnapshot.activities.SettingsActivity

/**
 * Fires the daily check-in reminder notification. Replaces the old AlarmReceiver
 * broadcast-on-alarm-fire step (AGENT_PLANS/MODERNIZE.md Phase 4); scheduling/cancellation
 * lives in [CheckinReminderScheduler].
 */
@HiltWorker
class CheckinReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userData = SettingsActivity.loadUserData(applicationContext).userData
        if (!userData.notificationData!!.notificationSet) {
            // Preference changed to "off" since this periodic work was enqueued (e.g. the
            // work fired before a cancelUniqueWork request from a later Settings save could
            // take effect) -- skip firing rather than surprise the user with a stale reminder.
            return Result.success()
        }

        createNotification(applicationContext)
        return Result.success()
    }

    private fun createNotification(context: Context) {
        createNotificationChannel(context)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val resultIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.CLICK_NOTIFICATION_ACTION
        }
        val resultPendingIntent = android.app.PendingIntent.getActivity(
            context,
            500000,
            resultIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHECKIN_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.art_icon)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentTitle(context.resources.getString(R.string.notification_title))
            .setContentText(context.resources.getString(R.string.notification_content))
            .setContentIntent(resultPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHECKIN_REMINDER_CHANNEL_ID,
                context.resources.getString(R.string.notification_title),
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 4257245
        const val CHECKIN_REMINDER_CHANNEL_ID = "checkin_reminders"
    }
}
