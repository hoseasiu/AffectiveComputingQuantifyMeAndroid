package edu.mit.media.mysnapshot;


import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.HttpSenderConfigurationBuilder;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import edu.mit.media.mysnapshot.backend.BackendAPIFactory;


public class MyApplication extends Application {

    @Override public void onCreate() {
        super.onCreate();
        // LeakCanary.install(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        String baseURL = BackendAPIFactory.baseURL(base);

        String acraUser = base.getResources().getString(R.string.ACRA_USER);
        String acraPassword = base.getResources().getString(R.string.ACRA_PASSWORD);

        HashMap<String, String> headers = new HashMap<>();
        URL host = null;
        try {
            host = new URL(baseURL);
            headers.put("HTTP_HOST", host.getHost());
        } catch (MalformedURLException e) {
        }

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder()
                .withReportFormat(StringFormat.JSON)
                .withPluginConfigurations(
                        new HttpSenderConfigurationBuilder()
                                .withEnabled(true)
                                .withUri(baseURL + "acra/report/")
                                .withHttpMethod(HttpSender.Method.POST)
                                .withBasicAuthLogin(acraUser)
                                .withBasicAuthPassword(acraPassword)
                                .withHttpHeaders(headers)
                                .build()
                );

        // The following line triggers the initialization of ACRA
        ACRA.init(this, builder);
    }
}
