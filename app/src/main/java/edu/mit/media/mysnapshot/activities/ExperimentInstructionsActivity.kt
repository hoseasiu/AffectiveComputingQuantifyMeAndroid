package edu.mit.media.mysnapshot.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.activities.fragments.FailedStageFragment
import edu.mit.media.mysnapshot.activities.fragments.NewStageFragment
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.CheckinOutcome
import edu.mit.media.mysnapshot.engine.ExperimentEngine
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.view.FontTextView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ExperimentInstructionsActivity : PermissionCheckingAppCompatActivity() {

    @Inject
    lateinit var repository: ExperimentRepository

    private var experiment: ExperimentEntity? = null
    private var pendingOutcome: CheckinOutcome? = null
    private var dialogsShown = false

    override fun onResume() {
        super.onResume()

        val experimentId = intent.getIntExtra(EXPERIMENT_ID_EXTRA, -1)
        pendingOutcome = if (intent.getBooleanExtra(HAS_OUTCOME_EXTRA, false)) outcomeFromIntent(intent) else null

        lifecycleScope.launch {
            val current = if (experimentId != -1) {
                repository.getExperimentById(experimentId).first()
            } else {
                repository.getLatestExperiment().first()
            }
            experiment = current

            if (current == null) {
                startActivity(Intent(this@ExperimentInstructionsActivity, MainActivity::class.java))
                finish()
                overridePendingTransition(0, 0)
                return@launch
            }

            if (!current.isActive) {
                ExperimentCompleteActivity.startActivity(this@ExperimentInstructionsActivity, current.id)
                finish()
                overridePendingTransition(0, 0)
                return@launch
            }

            val checkins = repository.getCheckinsForExperiment(current.id).first()
            if (checkins.isEmpty()) {
                initFirstDayView()
                return@launch
            }

            val outcome = pendingOutcome ?: repository.refreshInstructions(current.id)
            initViews(current, outcome)
        }
    }

    private fun initFirstDayView() {
        setContentView(R.layout.activity_experiment_first_day)

        findViewById<View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun initViews(experiment: ExperimentEntity, outcome: CheckinOutcome) {
        val experimentType = ExperimentType.fromTypeKey(experiment.type)

        setContentView(R.layout.activity_experiment_instructions)

        if (!dialogsShown) {
            dialogsShown = true
            if (outcome.restartedStage) {
                FailedStageFragment.showDialog(this, outcome.restartReason)
            } else if (outcome.newStage || MainActivity.FORCE_NEW_STAGE_DIALOG) {
                NewStageFragment.showDialog(this)
            }
        }

        val questionView = findViewById<TextView>(R.id.question)
        questionView.text = experimentType.name

        if (outcome.currentStage == 0) {
            findViewById<View>(R.id.target_container).visibility = View.GONE
        }

        val stageView = findViewById<TextView>(R.id.stage)
        stageView.text = "Stage " + (outcome.currentStage + 1)

        val iconView = findViewById<ImageView>(R.id.icon)
        iconView.setImageDrawable(resources.getDrawable(experimentType.iconId))

        val instructionsView = findViewById<TextView>(R.id.stage_instructions)
        val instructions = resources.getStringArray(R.array.stage_instructions)
        instructionsView.text = instructions.getOrNull(outcome.currentStage) ?: ""

        val targetView = findViewById<TextView>(R.id.target)
        targetView.text = outcome.target?.let { experimentType.formatInstruction(it) } ?: ""

        findViewById<View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.refresh).setOnClickListener {
            refreshInstructions(experiment)
        }
        findViewById<View>(R.id.progress).setOnClickListener {
            onProgressClicked(experiment.id)
        }

        var inputGrid = findViewById<LinearLayout>(R.id.progressgrid)
        inputGrid.removeAllViews()

        val backgroundColor = (findViewById<View>(R.id.bg).background as ColorDrawable).color

        for (i in 0 until 7) {
            val input = outcome.stageInputs.getOrNull(i)
            val v = FontTextView(this, null)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
            v.layoutParams = params
            v.setPadding(0, 10, 0, 10)
            v.setTypeFaceName(FontTextView.RALEWAY)
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            v.setBackgroundColor(backgroundColor)
            v.gravity = Gravity.CENTER
            v.text = if (input != null) experimentType.formatTarget(input) else "-"
            inputGrid.addView(v)

            if (i == 3) {
                inputGrid = findViewById(R.id.progressgrid_secondrow)
                inputGrid.removeAllViews()
            }
        }
        val v = FontTextView(this, null)
        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
        v.layoutParams = params
        v.setBackgroundColor(backgroundColor)
        inputGrid.addView(v)
    }

    /**
     * Mid-experiment progress view is opt-in (paper §6.2: the original team withheld
     * this to avoid biasing behavior). First tap explains that trade-off once; the
     * choice is then remembered so it doesn't need re-confirming every day.
     */
    private fun onProgressClicked(experimentId: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(SHOW_PROGRESS_PREF, false)) {
            ExperimentProgressActivity.startActivity(this, experimentId)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("See Your Progress?")
            .setMessage(
                "You can see your check-in history for this experiment as it happens, " +
                    "instead of waiting until it's done. Keep in mind that knowing your " +
                    "past results might change how you behave going forward, which can " +
                    "affect the experiment's accuracy. Turn this on?"
            )
            .setPositiveButton("Yes, show me") { _, _ ->
                prefs.edit().putBoolean(SHOW_PROGRESS_PREF, true).apply()
                ExperimentProgressActivity.startActivity(this, experimentId)
            }
            .setNegativeButton("No thanks", null)
            .show()
    }

    private fun refreshInstructions(experiment: ExperimentEntity) {
        val rotateAnimation = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        rotateAnimation.interpolator = LinearInterpolator()
        rotateAnimation.duration = 500
        rotateAnimation.repeatCount = Animation.INFINITE

        findViewById<View>(R.id.refresh).startAnimation(rotateAnimation)

        lifecycleScope.launch {
            val outcome = repository.refreshInstructions(experiment.id)
            initViews(experiment, outcome)
        }
    }

    companion object {
        const val LOGTAG = "ExperimentInstructionsActivity"
        const val EXPERIMENT_ID_EXTRA = "experiment_id"
        private const val SHOW_PROGRESS_PREF = "show_progress_during_experiment"
        private const val HAS_OUTCOME_EXTRA = "has_outcome"
        private const val NEW_STAGE_EXTRA = "new_stage"
        private const val RESTARTED_STAGE_EXTRA = "restarted_stage"
        private const val RESTART_REASON_EXTRA = "restart_reason"
        private const val CURRENT_STAGE_EXTRA = "current_stage"
        private const val TARGET_EXTRA = "target"
        private const val DAY_EXTRA = "day"
        private const val STAGE_INPUTS_EXTRA = "stage_inputs"

        @JvmStatic
        fun startActivity(context: Context, experimentId: Int) {
            val intent = Intent(context, ExperimentInstructionsActivity::class.java)
            intent.putExtra(EXPERIMENT_ID_EXTRA, experimentId)
            context.startActivity(intent)
        }

        @JvmStatic
        fun startActivityAfterCheckin(context: Context, experimentId: Int, outcome: CheckinOutcome) {
            val intent = Intent(context, ExperimentInstructionsActivity::class.java)
            intent.putExtra(EXPERIMENT_ID_EXTRA, experimentId)
            intent.putExtra(HAS_OUTCOME_EXTRA, true)
            intent.putExtra(NEW_STAGE_EXTRA, outcome.newStage)
            intent.putExtra(RESTARTED_STAGE_EXTRA, outcome.restartedStage)
            intent.putExtra(RESTART_REASON_EXTRA, outcome.restartReason?.name)
            intent.putExtra(CURRENT_STAGE_EXTRA, outcome.currentStage)
            intent.putExtra(TARGET_EXTRA, outcome.target ?: Float.NaN)
            intent.putExtra(DAY_EXTRA, outcome.day)
            intent.putExtra(
                STAGE_INPUTS_EXTRA,
                outcome.stageInputs.map { it ?: Float.NaN }.toFloatArray()
            )
            context.startActivity(intent)
        }

        private fun outcomeFromIntent(intent: Intent): CheckinOutcome {
            val target = intent.getFloatExtra(TARGET_EXTRA, Float.NaN)
            val stageInputs = (intent.getFloatArrayExtra(STAGE_INPUTS_EXTRA) ?: FloatArray(0))
                .map { if (it.isNaN()) null else it }
            return CheckinOutcome(
                newStage = intent.getBooleanExtra(NEW_STAGE_EXTRA, false),
                restartedStage = intent.getBooleanExtra(RESTARTED_STAGE_EXTRA, false),
                restartReason = intent.getStringExtra(RESTART_REASON_EXTRA)
                    ?.let { ExperimentEngine.RestartReason.valueOf(it) },
                currentStage = intent.getIntExtra(CURRENT_STAGE_EXTRA, 0),
                target = if (target.isNaN()) null else target,
                day = intent.getIntExtra(DAY_EXTRA, 0),
                stageInputs = stageInputs
            )
        }
    }
}
