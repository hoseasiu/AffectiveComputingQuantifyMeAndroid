package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.ExperimentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class ExperimentCompleteActivity : PermissionCheckingAppCompatActivity() {

    @Inject
    lateinit var repository: ExperimentRepository

    private var experiment: ExperimentEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val experimentId = intent.getIntExtra(EXPERIMENT_ID_EXTRA, -1)
        experiment = runBlocking {
            if (experimentId != -1) repository.getExperimentById(experimentId).first()
            else repository.getLatestExperiment().first()
        }

        val current = experiment
        if (current == null || current.isActive || current.resultValue == null) {
            return
        }

        val experimentType = ExperimentType.fromTypeKey(current.type)

        setContentView(R.layout.activity_experiment_complete)

        findViewById<android.view.View>(R.id.choose_button).setOnClickListener {
            startActivity(Intent(this, ExperimentChooseActivity::class.java))
            finish()
        }

        val resultView = findViewById<TextView>(R.id.result)
        resultView.text = experimentType.formatResult(current.resultValue!!)

        val confidenceView = findViewById<TextView>(R.id.confidence)
        confidenceView.text = "(With a " + Math.round((current.resultConfidence ?: 0f) * 100) + "% confidence)"

        findViewById<android.view.View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()

        val current = experiment
        if (current == null || current.isActive || current.resultValue == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val LOGTAG = "ExperimentCompleteActivity"
        const val EXPERIMENT_ID_EXTRA = "experiment_id"

        @JvmStatic
        fun startActivity(context: Context, experimentId: Int) {
            val intent = Intent(context, ExperimentCompleteActivity::class.java)
            intent.putExtra(EXPERIMENT_ID_EXTRA, experimentId)
            context.startActivity(intent)
        }
    }
}
