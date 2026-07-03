package edu.mit.media.mysnapshot

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.HttpSenderConfigurationBuilder
import org.acra.data.StringFormat
import org.acra.sender.HttpSender
import java.net.MalformedURLException
import java.net.URL
import java.util.HashMap

@HiltAndroidApp
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
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
