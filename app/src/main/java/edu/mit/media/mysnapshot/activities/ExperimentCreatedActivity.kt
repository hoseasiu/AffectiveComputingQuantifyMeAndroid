package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import edu.mit.media.mysnapshot.R

class ExperimentCreatedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_experiment_created)

        findViewById<android.view.View>(R.id.done_button).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    companion object {
        const val LOGTAG = "ExperimentCreatedActivity"

        @JvmStatic
        fun startActivity(context: Context) {
            context.startActivity(Intent(context, ExperimentCreatedActivity::class.java))
        }
    }
}
