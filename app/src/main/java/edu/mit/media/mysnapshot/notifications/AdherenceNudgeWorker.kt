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
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.data.loadUserData
import edu.mit.media.mysnapshot.engine.ExperimentType
import kotlinx.coroutines.flow.first

/**
 * Fires a mid-day encouragement/reminder nudge for the day's already-known target
 * (AGENT_PLANS/MODERNIZE.md Phase 5.3, paper §6.4: only 1/13 participants completed a
 * full 4-stage experiment because objective adherence to the target itself (22.5%) was
 * far below check-in adherence (75.6%) -- reminding people once in the morning clearly
 * wasn't enough). Distinct from [CheckinReminderWorker], which asks the user to check in
 * about *yesterday*; this instead re-surfaces *today's* target, mid-day, when there's
 * still time left to act on it. Scheduling lives in [AdherenceNudgeScheduler].
 */
@HiltWorker
class AdherenceNudgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ExperimentRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userData = loadUserData(applicationContext).userData
        if (!userData.notificationData!!.notificationSet) {
            return Result.success()
        }

        val experiment = repository.getCurrentExperiment().first() ?: return Result.success()
        if (experiment.currentStage == 0) {
            // Baseline stage has no personal target yet to encourage adherence to --
            // matches ExperimentInstructionsActivity's own currentStage != 0 gate.
            return Result.success()
        }

        val outcome = repository.refreshInstructions(experiment.id)
        val target = outcome.target ?: return Result.success()
        val type = ExperimentType.fromTypeKey(experiment.type)

        createNotification(applicationContext, type.formatInstruction(target))
        return Result.success()
    }

    private fun createNotification(context: Context, message: String) {
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
            500001,
            resultIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ADHERENCE_NUDGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.art_icon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle(context.resources.getString(R.string.adherence_nudge_title))
            .setContentText(message)
            .setContentIntent(resultPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ADHERENCE_NUDGE_CHANNEL_ID,
                context.resources.getString(R.string.adherence_nudge_title),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 4257246
        const val ADHERENCE_NUDGE_CHANNEL_ID = "adherence_nudges"
    }
}
