package edu.mit.media.mysnapshot.activities

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.engine.ExperimentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ExperimentChooseActivity : PermissionCheckingAppCompatActivity() {

    @Inject
    lateinit var repository: ExperimentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_experiment_choose)

        initButton(R.id.leisurehappiness, ExperimentType.LeisureHappiness)
        initButton(R.id.stepssleepefficiency, ExperimentType.StepsSleepEfficiency)
        initButton(R.id.sleepdurationproductivity, ExperimentType.SleepDurationProductivity)
        initButton(R.id.sleepvariabilitystress, ExperimentType.SleepVariabilityStress)
    }

    private fun initButton(buttonId: Int, experimentType: ExperimentType) {
        findViewById<android.view.View>(buttonId).setOnClickListener {
            ExperimentIntroActivity.startActivity(this, experimentType)
        }
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            val experiment = repository.getLatestExperiment().first()
            if (experiment != null && !experiment.isCancelled) {
                startActivity(Intent(this@ExperimentChooseActivity, MainActivity::class.java))
                finish()
                overridePendingTransition(0, 0)
            }
        }
    }

    companion object {
        const val LOGTAG = "ExperimentChooseActivity"
    }
}
