package edu.mit.media.mysnapshot.activities

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.notifications.CheckinReminderWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.joda.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: ExperimentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SettingsActivity.hasSetUserData(PreferenceManager.getDefaultSharedPreferences(this))) {
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            val experiment = repository.getLatestExperiment().first()

            if (experiment == null || experiment.isCancelled) {
                startActivity(Intent(this@MainActivity, ExperimentChooseActivity::class.java))
                finish()
                return@launch
            }

            if (!experiment.isActive) {
                ExperimentCompleteActivity.startActivity(this@MainActivity, experiment.id)
                finish()
                return@launch
            }

            val checkins = repository.getCheckinsForExperiment(experiment.id).first()
            val today = LocalDate.now()
            val startedToday = LocalDate(experiment.startTime) == today
            val checkedInToday = checkins.any { LocalDate(it.checkinDate) == today }
            val hadCheckinToday = startedToday || checkedInToday

            if (FORCE_CHECKIN || !hadCheckinToday) {
                ExperimentCheckinActivity.startActivity(this@MainActivity, experiment.id)
            } else {
                ExperimentInstructionsActivity.startActivity(this@MainActivity, experiment.id)
            }
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        clearNotifications(intent)
    }

    fun clearNotifications(intent: Intent) {
        if (CLICK_NOTIFICATION_ACTION == intent.action) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(CheckinReminderWorker.NOTIFICATION_ID)
        }
    }

    companion object {
        const val LOGTAG = "MainActivity"
        const val CLICK_NOTIFICATION_ACTION = "ClickedonNotification,man"

        // Debug flag preserved from the pre-Room implementation: always route through the
        // daily check-in screen instead of straight to instructions, even if already
        // checked in today.
        const val FORCE_CHECKIN = true
        const val FORCE_NEW_STAGE_DIALOG = false
    }
}
