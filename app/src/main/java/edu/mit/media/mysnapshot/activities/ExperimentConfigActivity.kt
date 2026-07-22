package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.ui.theme.FadeBlue
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme
import edu.mit.media.mysnapshot.ui.theme.RadioGreen
import edu.mit.media.mysnapshot.ui.theme.RadioRed
import edu.mit.media.mysnapshot.ui.theme.White
import edu.mit.media.mysnapshot.ui.wizard.RadioScaleStep
import edu.mit.media.mysnapshot.ui.wizard.StepDotsIndicator
import edu.mit.media.mysnapshot.viewmodel.ExperimentConfigEvent
import edu.mit.media.mysnapshot.viewmodel.ExperimentConfigStep
import edu.mit.media.mysnapshot.viewmodel.ExperimentConfigUiState
import edu.mit.media.mysnapshot.viewmodel.ExperimentConfigViewModel

/**
 * The per-experiment efficacy questionnaire (AGENT_PLANS/IMPROVEMENTS.md 3.2): a 4-step
 * progressive-reveal wizard, now Compose + [ExperimentConfigViewModel] instead of the legacy
 * `QuestionActivity`/`ViewPager` + `QuestionTextFragment`/`QuestionRadioGroupFragment`. See
 * [ExperimentConfigViewModel]'s doc comments for how the legacy behavior was ported.
 */
@AndroidEntryPoint
class ExperimentConfigActivity : ComponentActivity() {

    private val viewModel: ExperimentConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val typeKey = intent.extras?.getString(EXPERIMENT_TYPE_EXTRA) ?: ""
        viewModel.load(typeKey)

        setContent {
            QuantifyMeTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            ExperimentConfigEvent.NavigateToCreated -> {
                                ExperimentCreatedActivity.startActivity(this@ExperimentConfigActivity)
                                finish()
                                overridePendingTransition(0, 0)
                            }
                        }
                    }
                }

                ExperimentConfigScreen(
                    state = state,
                    onIntroContinue = viewModel::onIntroContinue,
                    onAppEfficacySelected = viewModel::onAppEfficacySelected,
                    onExperimentEfficacySelected = viewModel::onExperimentEfficacySelected,
                    onSelfEfficacySelected = viewModel::onSelfEfficacySelected,
                    onDotClick = viewModel::goToStep
                )
            }
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

private const val TOTAL_STEPS = 4

@Composable
private fun ExperimentConfigScreen(
    state: ExperimentConfigUiState,
    onIntroContinue: () -> Unit,
    onAppEfficacySelected: (Int) -> Unit,
    onExperimentEfficacySelected: (Int) -> Unit,
    onSelfEfficacySelected: (Int) -> Unit,
    onDotClick: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FadeBlue)
    ) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            StepDotsIndicator(
                currentStep = state.currentStep,
                revealedSteps = state.revealedSteps,
                totalSteps = TOTAL_STEPS,
                onDotClick = onDotClick
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (ExperimentConfigStep.entries[state.currentStep]) {
                    ExperimentConfigStep.INTRO -> IntroStep(
                        icon = state.experimentType.iconId,
                        onContinue = onIntroContinue
                    )
                    ExperimentConfigStep.APP_EFFICACY -> RadioScaleStep(
                        icon = R.drawable.icon_settings_app_effectiveness,
                        question = "How effective do you think this app will be in helping you run this experiment?",
                        leftLabel = "Poor",
                        rightLabel = "Great",
                        leftColor = RadioRed,
                        rightColor = RadioGreen,
                        selected = state.appEfficacy,
                        onSelect = onAppEfficacySelected
                    )
                    ExperimentConfigStep.EXPERIMENT_EFFICACY -> RadioScaleStep(
                        icon = R.drawable.icon_settings_chart,
                        question = "How effective do you think this experiment will be in getting concrete results?",
                        leftLabel = "Poor",
                        rightLabel = "Great",
                        leftColor = RadioRed,
                        rightColor = RadioGreen,
                        selected = state.experimentEfficacy,
                        onSelect = onExperimentEfficacySelected
                    )
                    ExperimentConfigStep.SELF_EFFICACY -> RadioScaleStep(
                        icon = R.drawable.icon_settings_self_effectiveness,
                        question = "How effective do you think you will be in carrying out the experiment?",
                        leftLabel = "Poor",
                        rightLabel = "Great",
                        leftColor = RadioRed,
                        rightColor = RadioGreen,
                        selected = state.selfEfficacy,
                        onSelect = onSelfEfficacySelected
                    )
                }
            }
        }

        if (state.isSubmitting) {
            SubmittingOverlay()
        }
    }
}

@Composable
private fun IntroStep(icon: Int, onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = "Configuration",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 16.dp)
        )
        Text(
            text = "First, we need to ask you some questions to help us make your experiment.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(
            onClick = onContinue,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun SubmittingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .semantics { contentDescription = "Starting experiment" },
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = White) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Starting Experiment...")
            }
        }
    }
}
