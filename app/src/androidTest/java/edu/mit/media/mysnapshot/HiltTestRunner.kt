package edu.mit.media.mysnapshot

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps in [HiltTestApplication] for the real, `@HiltAndroidApp`-annotated [MyApplication] --
 * required for any `@HiltAndroidTest` instrumentation test that launches an
 * `@AndroidEntryPoint` activity (AGENT_PLANS/IMPROVEMENTS.md item 5, issue #21). Since this
 * bypasses `MyApplication.onCreate()` entirely, any test launching a screen that depends on
 * its side effects (currently just `ExperimentTypeRegistry.load()`) must replicate them itself
 * -- see `ExperimentCheckinScreenTest`'s `@Before`.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
