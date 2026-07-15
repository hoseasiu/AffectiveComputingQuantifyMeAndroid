package edu.mit.media.mysnapshot.activities

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.activities.questions.QuestionActivity
import edu.mit.media.mysnapshot.activities.questions.QuestionListener
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionRadioGroupFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionSpinnerFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionTextActionButtonFragment
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.health.HealthConnectManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class ExperimentCheckinActivity : QuestionActivity() {

    @Inject
    lateinit var repository: ExperimentRepository

    @Inject
    lateinit var healthConnect: HealthConnectManager

    private var experiment: ExperimentEntity? = null

    // Placeholder until the async load (below) resolves the real experiment/type. initFragments()
    // (called synchronously from QuestionActivity's onCreate) needs *some* value to build the intro
    // fragment with, so we start with a harmless default and patch the rendered view in place once
    // the real data arrives -- see applyLoadedExperimentData().
    private var experimentType: ExperimentType = ExperimentType.LeisureHappiness
    private var sleepNightExplanation: String? = null
    private var isLoadingExperiment = true

    private lateinit var textFragment: QuestionTextActionButtonFragment
    private lateinit var didFollowDirections: QuestionRadioGroupFragment
    private lateinit var happy: QuestionRadioGroupFragment
    private lateinit var stress: QuestionRadioGroupFragment
    private lateinit var productivity: QuestionRadioGroupFragment
    private lateinit var leisure: QuestionSpinnerFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        // QuestionActivity.onCreate() synchronously builds the ViewPager/fragments (initFragments()
        // is called from within it), so it must run before we know the real experiment data. The
        // fragments are built with placeholder content below and patched once loading completes.
        super.onCreate(savedInstanceState)

        val experimentId = intent.getIntExtra(EXPERIMENT_ID_EXTRA, -1)
        lifecycleScope.launch {
            val loadedExperiment = if (experimentId != -1) repository.getExperimentById(experimentId).first()
                else repository.getLatestExperiment().first()

            experiment = loadedExperiment
            experimentType = loadedExperiment?.let { ExperimentType.fromTypeKey(it.type) } ?: ExperimentType.LeisureHappiness

            if (experimentType.usesSleepData) {
                sleepNightExplanation = buildSleepNightExplanation()
            }

            isLoadingExperiment = false

            // Mirrors the validity checks onResume() performs once data is available; if we
            // navigate away there's no point patching the (about to be destroyed) fragment view.
            if (checkExperimentValidity()) {
                applyLoadedExperimentData()
            }
        }
    }

    /**
     * Paper §6.3: participants didn't understand what counted as "a night" when they
     * slept past midnight. Health Connect gives explicit session start/end, so surface
     * it directly instead of leaving the day-boundary silently inferred.
     */
    private suspend fun buildSleepNightExplanation(): String? {
        val session = healthConnect.getMostRecentSleepSession() ?: return null
        val zone = ZoneId.systemDefault()
        val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
        val start = session.startTime.atZone(zone)
        val end = session.endTime.atZone(zone)
        val night = session.attributedNight.toString("EEEE, MMM d")
        return "We counted your sleep from ${start.format(timeFormat)} to ${end.format(timeFormat)} " +
            "as the night of $night (a night belongs to the day you woke up, not the day you fell asleep)."
    }

    override fun getLayoutId(): Int = R.layout.activity_experiment_config

    override fun initFragments(fragments: MutableList<Fragment>, icons: MutableList<Drawable>) {
        // Built eagerly (with placeholder experiment data where needed) so the ViewPager has its
        // full page set from the start; only the intro fragment's content depends on the real
        // experiment/type, which is patched in once the async load completes.
        initText(fragments)
        initDidFollowDirections(fragments)
        initLeisure(fragments)
        initHappy(fragments)
        initStress(fragments)
        initProductivity(fragments)
    }

    override fun onResume() {
        super.onResume()

        if (isLoadingExperiment) {
            // Validity is checked in the load callback (onCreate) once data is available.
            return
        }

        checkExperimentValidity()
    }

    /**
     * Redirects away if there's no current experiment, or the current one is no longer active.
     * Returns true if the check-in screen should keep showing.
     */
    private fun checkExperimentValidity(): Boolean {
        val current = experiment
        if (current == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(0, 0)
            return false
        }

        if (!current.isActive) {
            ExperimentCompleteActivity.startActivity(this, current.id)
            finish()
            overridePendingTransition(0, 0)
            return false
        }

        return true
    }

    private fun buildIntroText(): String {
        var introText = "We're going to ask you a couple quick questions about your day, then we'll let you know what you should do for your experiment.\n\nYou only need to check in once a day!"
        sleepNightExplanation?.let { introText += "\n\n$it" }
        return introText
    }

    /**
     * Patches the already-inflated intro fragment view with the real experiment icon/text once
     * the async load (in onCreate) resolves. QuestionActivity doesn't expose its ViewPager/adapter
     * fields for a full fragment rebuild, so we update the rendered views directly instead.
     */
    private fun applyLoadedExperimentData() {
        textFragment.root?.let { root ->
            root.findViewById<ImageView>(R.id.icon)?.setImageResource(experimentType.iconId)
            root.findViewById<TextView>(R.id.text)?.text = buildIntroText()
        }
    }

    private fun initText(fragments: MutableList<Fragment>) {
        textFragment = QuestionTextActionButtonFragment()
        textFragment.setLayout(QuestionFragment.Layout(experimentType.iconId, "Daily Check In"))
        textFragment.init(
            buildIntroText(),
            android.view.View.OnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
        )
        textFragment.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete(true)
            }
        })
        fragments.add(textFragment)
    }

    private fun initDidFollowDirections(fragments: MutableList<Fragment>) {
        didFollowDirections = QuestionRadioGroupFragment()
        didFollowDirections.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_self_effectiveness, "How did you do with following the experiment's instructions?"))
        didFollowDirections.init("Poor", "Great")
        didFollowDirections.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        fragments.add(didFollowDirections)
    }

    private fun initProductivity(fragments: MutableList<Fragment>) {
        productivity = QuestionRadioGroupFragment()
        productivity.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_productivity, "How productive were you in the past 24 hours?"))
        productivity.init("Not at all", "Extremely")
        productivity.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        fragments.add(productivity)
    }

    private fun initStress(fragments: MutableList<Fragment>) {
        stress = QuestionRadioGroupFragment()
        stress.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_stress, "How stressed were you in the past 24 hours?"))
        stress.init("Not at all", "Extremely", resources.getColor(R.color.radio_green), resources.getColor(R.color.radio_red), 7)
        stress.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        fragments.add(stress)
    }

    private fun initHappy(fragments: MutableList<Fragment>) {
        happy = QuestionRadioGroupFragment()
        happy.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_happiness, "How happy were you in the past 24 hours?"))
        happy.init("Not at all", "Extremely")
        happy.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        fragments.add(happy)
    }

    private fun initLeisure(fragments: MutableList<Fragment>) {
        leisure = QuestionSpinnerFragment()
        leisure.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_leisure, "How much leisure time did you have in the past 24 hours?"))
        leisure.init(R.array.leisurevalues, R.array.leisurelabels, "Please Select an Option")
        leisure.setListener(object : QuestionListener<String>() {
            override fun onSelected(value: String) {
                onPageComplete()
            }
        })
        fragments.add(leisure)
    }

    override fun loadInitialData(): Boolean = false

    override fun onFinish() {
        val current = experiment ?: return

        val dialog = ProgressDialog(this)
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        dialog.setTitle("Generating daily instructions...")
        dialog.setCancelable(false)
        dialog.setIcon(android.R.drawable.ic_menu_upload)
        dialog.show()

        lifecycleScope.launch {
            val outcome = repository.submitCheckin(
                experimentId = current.id,
                didFollowInstructions = didFollowDirections.value,
                happiness = happy.value,
                stress = stress.value,
                productivity = productivity.value,
                leisureTime = leisure.value.toInt()
            )

            dialog.dismiss()

            if (outcome.isComplete) {
                ExperimentCompleteActivity.startActivity(this@ExperimentCheckinActivity, current.id)
            } else {
                ExperimentInstructionsActivity.startActivityAfterCheckin(
                    this@ExperimentCheckinActivity, current.id, outcome
                )
            }

            finish()
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val LOGTAG = "ExperimentCheckinActivity"
        const val EXPERIMENT_ID_EXTRA = "experiment_id"

        @JvmStatic
        fun startActivity(context: Context, experimentId: Int) {
            val intent = Intent(context, ExperimentCheckinActivity::class.java)
            intent.putExtra(EXPERIMENT_ID_EXTRA, experimentId)
            context.startActivity(intent)
        }
    }
}
