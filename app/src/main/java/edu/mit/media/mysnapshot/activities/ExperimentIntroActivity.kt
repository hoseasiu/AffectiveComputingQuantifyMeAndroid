package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.engine.ExperimentType

class ExperimentIntroActivity : PermissionCheckingAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val typeName = intent.extras?.getString(EXPERIMENT_TYPE_EXTRA)
        val experimentType = ExperimentType.fromTypeKey(typeName ?: "")

        setContentView(experimentType.introLayout)

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
