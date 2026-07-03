package edu.mit.media.mysnapshot.activities

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.activities.questions.QuestionActivity
import edu.mit.media.mysnapshot.activities.questions.QuestionListener
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionRadioGroupFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionTextFragment
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.engine.ExperimentType
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ExperimentConfigActivity : QuestionActivity() {

    @Inject
    lateinit var repository: ExperimentRepository

    private lateinit var textFragment: QuestionTextFragment
    private lateinit var appEfficacy: QuestionRadioGroupFragment
    private lateinit var experimentEfficacy: QuestionRadioGroupFragment
    private lateinit var selfEfficacy: QuestionRadioGroupFragment

    private lateinit var experimentType: ExperimentType

    override fun getLayoutId(): Int = R.layout.activity_experiment_config

    override fun initFragments(fragments: MutableList<Fragment>, icons: MutableList<Drawable>) {
        val typeName = intent.extras?.getString(EXPERIMENT_TYPE_EXTRA)
        experimentType = ExperimentType.fromTypeKey(typeName ?: "")

        initText(fragments)
        initAppEfficacy(fragments)
        initExperimentEfficacy(fragments)
        initSelfEfficacy(fragments)
    }

    private fun initText(fragments: MutableList<Fragment>) {
        textFragment = QuestionTextFragment()
        textFragment.setLayout(QuestionFragment.Layout(experimentType.iconId, "Configuration"))
        textFragment.init("First, we need to ask you some questions to help us make your experiment.")
        textFragment.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        fragments.add(textFragment)
    }

    private fun initAppEfficacy(fragments: MutableList<Fragment>) {
        appEfficacy = QuestionRadioGroupFragment()
        appEfficacy.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_app_effectiveness, "How effective do you think this app will be in helping you run this experiment?"))
        appEfficacy.init("Poor", "Great")
        appEfficacy.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        fragments.add(appEfficacy)
    }

    private fun initExperimentEfficacy(fragments: MutableList<Fragment>) {
        experimentEfficacy = QuestionRadioGroupFragment()
        experimentEfficacy.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_chart, "How effective do you think this experiment will be in getting concrete results?"))
        experimentEfficacy.init("Poor", "Great")
        experimentEfficacy.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        fragments.add(experimentEfficacy)
    }

    private fun initSelfEfficacy(fragments: MutableList<Fragment>) {
        selfEfficacy = QuestionRadioGroupFragment()
        selfEfficacy.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_self_effectiveness, "How effective do you think you will be in carrying out the experiment?"))
        selfEfficacy.init("Poor", "Great")
        selfEfficacy.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        fragments.add(selfEfficacy)
    }

    override fun loadInitialData(): Boolean = false

    override fun onFinish() {
        val dialog = ProgressDialog(this)
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        dialog.setTitle("Starting Experiment...")
        dialog.setIcon(android.R.drawable.ic_menu_upload)
        dialog.show()

        lifecycleScope.launch {
            repository.createExperiment(
                experimentType,
                selfEfficacy.value,
                appEfficacy.value,
                experimentEfficacy.value
            )
            dialog.dismiss()
            ExperimentCreatedActivity.startActivity(this@ExperimentConfigActivity)
            finish()
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val LOGTAG = "ExperimentConfigActivity"
        const val EXPERIMENT_TYPE_EXTRA = "ADGHIOADGOUADGOUADG"

        @JvmStatic
        fun startActivity(context: Context, experimentType: ExperimentType) {
            val intent = Intent(context, ExperimentConfigActivity::class.java)
            intent.putExtra(EXPERIMENT_TYPE_EXTRA, experimentType.typeKey)
            context.startActivity(intent)
        }
    }
}
