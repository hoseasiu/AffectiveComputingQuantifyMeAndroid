package edu.mit.media.mysnapshot

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import edu.mit.media.mysnapshot.notifications.CheckinReminderScheduler
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Re-enqueue the daily check-in reminder on every process start -- the old
        // AlarmManager version only (re)armed on BOOT_COMPLETED, so nothing scheduled it on
        // first run or right after a Settings change until the device happened to reboot.
        // enqueueUniquePeriodicWork's UPDATE policy makes this a no-op if the schedule
        // already matches, so calling it here doesn't fight with SettingsActivity's own call.
        CheckinReminderScheduler.schedule(this)
    }
}
