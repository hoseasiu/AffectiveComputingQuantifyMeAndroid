package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.engine.ExperimentTypeRegistry
import edu.mit.media.mysnapshot.engine.describe

class ExperimentIntroActivity : PermissionCheckingAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val typeName = intent.extras?.getString(EXPERIMENT_TYPE_EXTRA)
        val experimentType = ExperimentType.fromTypeKey(typeName ?: "")

        setContentView(experimentType.introLayout)

        // Every dedicated per-type layout bakes its title/body copy in statically; the
        // generic fallback (custom types, #31) has none, so populate it here instead.
        if (!ExperimentTypeRegistry.hasDedicatedResources(experimentType.typeKey)) {
            findViewById<TextView>(R.id.title)?.text = experimentType.name
            findViewById<TextView>(R.id.text)?.text = getString(
                R.string.generic_experiment_intro_text,
                experimentType.inputSignal.describe(),
                experimentType.outputSignal.describe()
            )
        }

        findViewById<android.view.View>(R.id.done_button).setOnClickListener {
            ExperimentConfigActivity.startActivity(this, experimentType)
            finish()
        }
    }

    companion object {
        const val LOGTAG = "ExperimentIntroActivity"
        const val EXPERIMENT_TYPE_EXTRA = "OUNAEGUONEGouanAENUGAE"

        @JvmStatic
        fun startActivity(context: Context, experimentType: ExperimentType) {
            val intent = Intent(context, ExperimentIntroActivity::class.java)
            intent.putExtra(EXPERIMENT_TYPE_EXTRA, experimentType.typeKey)
            context.startActivity(intent)
        }
    }
}
