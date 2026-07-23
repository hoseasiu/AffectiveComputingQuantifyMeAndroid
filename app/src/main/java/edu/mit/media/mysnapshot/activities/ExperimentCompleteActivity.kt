package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.viewmodel.ExperimentCompleteEvent
import edu.mit.media.mysnapshot.viewmodel.ExperimentCompleteViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExperimentCompleteActivity : AppCompatActivity() {

    private val viewModel: ExperimentCompleteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No setContentView() call yet: the Room read happens off the main thread below, and the
        // window simply shows its themed background (the existing "invalid state" behavior below
        // was already to leave the window without content) until the result is ready to render.
        val experimentId = intent.getIntExtra(EXPERIMENT_ID_EXTRA, -1)
        viewModel.load(experimentId)

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    ExperimentCompleteEvent.NavigateToMain -> {
                        startActivity(Intent(this@ExperimentCompleteActivity, MainActivity::class.java))
                        finish()
                        overridePendingTransition(0, 0)
                    }
                }
            }
        }

        lifecycleScope.launch {
            val state = viewModel.uiState.first { !it.isLoading }
            val experiment = state.experiment
            val experimentType = state.experimentType
            if (experiment != null && !experiment.isActive && experiment.resultValue != null && experimentType != null) {
                renderResult(experiment, experimentType)
            }
            // Otherwise the ViewModel's own validity check already queued a NavigateToMain event,
            // handled by the collector above.
        }
    }

    private fun renderResult(experiment: ExperimentEntity, experimentType: ExperimentType) {
        setContentView(R.layout.activity_experiment_complete)

        findViewById<android.view.View>(R.id.choose_button).setOnClickListener {
            startActivity(Intent(this, ExperimentChooseActivity::class.java))
            finish()
        }

        val resultView = findViewById<TextView>(R.id.result)
        resultView.text = experimentType.formatResult(experiment.resultValue!!)

        val confidenceView = findViewById<TextView>(R.id.confidence)
        confidenceView.text = "(With a " + Math.round((experiment.resultConfidence ?: 0f) * 100) + "% confidence)"

        findViewById<android.view.View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
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
