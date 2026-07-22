package edu.mit.media.mysnapshot.activities

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.data.hasSetUserData
import edu.mit.media.mysnapshot.notifications.AdherenceNudgeWorker
import edu.mit.media.mysnapshot.notifications.CheckinReminderWorker
import edu.mit.media.mysnapshot.viewmodel.MainEvent
import edu.mit.media.mysnapshot.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasSetUserData(PreferenceManager.getDefaultSharedPreferences(this))) {
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    MainEvent.NavigateToChoose -> {
                        startActivity(Intent(this@MainActivity, ExperimentChooseActivity::class.java))
                        finish()
                    }
                    is MainEvent.NavigateToComplete -> {
                        ExperimentCompleteActivity.startActivity(this@MainActivity, event.experimentId)
                        finish()
                    }
                    is MainEvent.NavigateToCheckin -> {
                        ExperimentCheckinActivity.startActivity(this@MainActivity, event.experimentId)
                        finish()
                    }
                    is MainEvent.NavigateToInstructions -> {
                        ExperimentInstructionsActivity.startActivity(this@MainActivity, event.experimentId)
                        finish()
                    }
                }
            }
        }

        viewModel.route(FORCE_CHECKIN)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        clearNotifications(intent)
    }

    fun clearNotifications(intent: Intent) {
        if (CLICK_NOTIFICATION_ACTION == intent.action) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(CheckinReminderWorker.NOTIFICATION_ID)
            notificationManager.cancel(AdherenceNudgeWorker.NOTIFICATION_ID)
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
