package edu.mit.media.mysnapshot

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import edu.mit.media.mysnapshot.engine.ExperimentTypeRegistry
import edu.mit.media.mysnapshot.notifications.CheckinReminderScheduler
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.HttpSenderConfigurationBuilder
import org.acra.data.StringFormat
import org.acra.sender.HttpSender
import java.net.MalformedURLException
import java.net.URL
import java.util.HashMap
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

        // Must run before any ExperimentType.fromTypeKey/getAllTypes call anywhere in the
        // app (Phase 5.4's JSON-config-backed registry); Application.onCreate() always runs
        // before the first Activity, so every other call site can assume this already ran.
        ExperimentTypeRegistry.load(this)

        // Re-enqueue the daily check-in reminder on every process start -- the old
        // AlarmManager version only (re)armed on BOOT_COMPLETED, so nothing scheduled it on
        // first run or right after a Settings change until the device happened to reboot.
        // enqueueUniquePeriodicWork's UPDATE policy makes this a no-op if the schedule
        // already matches, so calling it here doesn't fight with SettingsActivity's own call.
        CheckinReminderScheduler.schedule(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        try {
            val baseURL = base.resources.getString(R.string.BASE_URL)
            val acraUser = base.resources.getString(R.string.ACRA_USER)
            val acraPassword = base.resources.getString(R.string.ACRA_PASSWORD)

            val headers = HashMap<String, String>()
            try {
                val host = URL(baseURL)
                headers["HTTP_HOST"] = host.host
            } catch (e: MalformedURLException) {
                // Continue without host header if URL is invalid
            }

            val builder = CoreConfigurationBuilder()
                .withReportFormat(StringFormat.JSON)
                .withPluginConfigurations(
                    HttpSenderConfigurationBuilder()
                        .withEnabled(true)
                        .withUri(baseURL + "acra/report/")
                        .withHttpMethod(HttpSender.Method.POST)
                        .withBasicAuthLogin(acraUser)
                        .withBasicAuthPassword(acraPassword)
                        .withHttpHeaders(headers)
                        .build()
                )

            ACRA.init(this, builder)
        } catch (e: Exception) {
            // Silently fail if ACRA setup fails (e.g., BASE_URL not configured)
        }
    }
}
