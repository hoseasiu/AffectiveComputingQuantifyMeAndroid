package edu.mit.media.mysnapshot

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.engine.ExperimentTypeRegistry
import edu.mit.media.mysnapshot.notifications.AdherenceNudgeScheduler
import edu.mit.media.mysnapshot.notifications.CheckinReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var experimentRepository: ExperimentRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Must run before any ExperimentType.fromTypeKey/getAllTypes call anywhere in the
        // app (Phase 5.4's JSON-config-backed registry); Application.onCreate() always runs
        // before the first Activity, so every other call site can assume this already ran.
        ExperimentTypeRegistry.load(this)

        // Merges in user-created experiment types (#31) alongside the bundled config. This
        // is necessarily async (Room access needs a coroutine) -- a screen that renders
        // before it completes just won't show custom types yet on this cold start, the same
        // way any other Flow-backed screen renders its loading state first.
        applicationScope.launch {
            experimentRepository.loadCustomTypes()
        }

        // Re-enqueue the daily check-in reminder on every process start -- the old
        // AlarmManager version only (re)armed on BOOT_COMPLETED, so nothing scheduled it on
        // first run or right after a Settings change until the device happened to reboot.
        // enqueueUniquePeriodicWork's UPDATE policy makes this a no-op if the schedule
        // already matches, so calling it here doesn't fight with SettingsActivity's own call.
        CheckinReminderScheduler.schedule(this)
        AdherenceNudgeScheduler.schedule(this)
    }
}
