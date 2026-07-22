package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.engine.CustomValueKind
import edu.mit.media.mysnapshot.ui.theme.FadeBlue
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme
import edu.mit.media.mysnapshot.ui.theme.White
import edu.mit.media.mysnapshot.ui.wizard.DropdownStep
import edu.mit.media.mysnapshot.ui.wizard.StepDotsIndicator
import edu.mit.media.mysnapshot.ui.wizard.TextFieldStep
import edu.mit.media.mysnapshot.viewmodel.CreateExperimentEvent
import edu.mit.media.mysnapshot.viewmodel.CreateExperimentStep
import edu.mit.media.mysnapshot.viewmodel.CreateExperimentUiState
import edu.mit.media.mysnapshot.viewmodel.CreateExperimentViewModel
import edu.mit.media.mysnapshot.viewmodel.SIGNAL_SOURCE_LABELS
import edu.mit.media.mysnapshot.viewmodel.SIGNAL_SOURCE_VALUES
import edu.mit.media.mysnapshot.viewmodel.SignalSelection

/**
 * "Create your own experiment" wizard (issue #33): a progressive-reveal Compose flow, the same
 * shape as [ExperimentConfigActivity]/[ExperimentCheckinActivity], that lets a user author a
 * brand new [edu.mit.media.mysnapshot.engine.ExperimentType] entirely on-device -- name, pick
 * or define an input/output signal, tune (or accept a preset for) the stage-bucketing target
 * range, then save. See [CreateExperimentViewModel] for the step-sequencing and
 * [edu.mit.media.mysnapshot.engine.CustomRangePresets] for the range-preset derivation.
 */
@AndroidEntryPoint
class CreateExperimentActivity : ComponentActivity() {

    private val viewModel: CreateExperimentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QuantifyMeTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CreateExperimentEvent.NavigateToIntro -> {
                                ExperimentIntroActivity.startActivity(this@CreateExperimentActivity, event.type)
                                finish()
                                overridePendingTransition(0, 0)
                            }
                        }
                    }
                }

                CreateExperimentScreen(
                    state = state,
                    onNameChanged = viewModel::onNameChanged,
                    onNameContinue = viewModel::onNameContinue,
                    onInputSignalSelected = viewModel::onInputSignalSelected,
                    onInputCustomLabelChanged = viewModel::onInputCustomLabelChanged,
                    onInputCustomQuestionChanged = viewModel::onInputCustomQuestionChanged,
                    onInputCustomKindSelected = viewModel::onInputCustomKindSelected,
                    onInputCustomLowLabelChanged = viewModel::onInputCustomLowLabelChanged,
                    onInputCustomHighLabelChanged = viewModel::onInputCustomHighLabelChanged,
                    onInputCustomUnitLabelChanged = viewModel::onInputCustomUnitLabelChanged,
                    onInputCustomFormContinue = viewModel::onInputCustomFormContinue,
                    onOutputSignalSelected = viewModel::onOutputSignalSelected,
                    onOutputCustomLabelChanged = viewModel::onOutputCustomLabelChanged,
                    onOutputCustomQuestionChanged = viewModel::onOutputCustomQuestionChanged,
                    onOutputCustomKindSelected = viewModel::onOutputCustomKindSelected,
                    onOutputCustomLowLabelChanged = viewModel::onOutputCustomLowLabelChanged,
                    onOutputCustomHighLabelChanged = viewModel::onOutputCustomHighLabelChanged,
                    onOutputCustomUnitLabelChanged = viewModel::onOutputCustomUnitLabelChanged,
                    onOutputCustomFormContinue = viewModel::onOutputCustomFormContinue,
                    onToggleAdvanced = viewModel::onToggleAdvanced,
                    onRangeUnderChanged = viewModel::onRangeUnderChanged,
                    onRangeN1Changed = viewModel::onRangeN1Changed,
                    onRangeN2Changed = viewModel::onRangeN2Changed,
                    onRangeN3Changed = viewModel::onRangeN3Changed,
                    onRangeOverChanged = viewModel::onRangeOverChanged,
                    onRangeSizeChanged = viewModel::onRangeSizeChanged,
                    onStableRangeChanged = viewModel::onStableRangeChanged,
                    onTargetRangeContinue = viewModel::onTargetRangeContinue,
                    onCreateClicked = viewModel::onCreateClicked,
                    onDotClick = viewModel::goToStep
                )
            }
        }
    }

    companion object {
        const val LOGTAG = "CreateExperimentActivity"

        @JvmStatic
        fun startActivity(context: Context) {
            context.startActivity(Intent(context, CreateExperimentActivity::class.java))
        }
    }
}

@Composable
private fun CreateExperimentScreen(
    state: CreateExperimentUiState,
    onNameChanged: (String) -> Unit,
    onNameContinue: () -> Unit,
    onInputSignalSelected: (String) -> Unit,
    onInputCustomLabelChanged: (String) -> Unit,
    onInputCustomQuestionChanged: (String) -> Unit,
    onInputCustomKindSelected: (CustomValueKind) -> Unit,
    onInputCustomLowLabelChanged: (String) -> Unit,
    onInputCustomHighLabelChanged: (String) -> Unit,
    onInputCustomUnitLabelChanged: (String) -> Unit,
    onInputCustomFormContinue: () -> Unit,
    onOutputSignalSelected: (String) -> Unit,
    onOutputCustomLabelChanged: (String) -> Unit,
    onOutputCustomQuestionChanged: (String) -> Unit,
    onOutputCustomKindSelected: (CustomValueKind) -> Unit,
    onOutputCustomLowLabelChanged: (String) -> Unit,
    onOutputCustomHighLabelChanged: (String) -> Unit,
    onOutputCustomUnitLabelChanged: (String) -> Unit,
    onOutputCustomFormContinue: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onRangeUnderChanged: (Float) -> Unit,
    onRangeN1Changed: (Float) -> Unit,
    onRangeN2Changed: (Float) -> Unit,
    onRangeN3Changed: (Float) -> Unit,
    onRangeOverChanged: (Float) -> Unit,
    onRangeSizeChanged: (Float) -> Unit,
    onStableRangeChanged: (Float) -> Unit,
    onTargetRangeContinue: () -> Unit,
    onCreateClicked: () -> Unit,
    onDotClick: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FadeBlue)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StepDotsIndicator(
                currentStep = state.currentStep,
                revealedSteps = state.revealedSteps,
                totalSteps = state.totalSteps,
                onDotClick = onDotClick
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (state.currentStepKind) {
                    CreateExperimentStep.NAME -> TextFieldStep(
                        icon = R.drawable.icon_experiment_generic,
                        question = "What do you want to call your experiment?",
                        fieldLabel = "Experiment name",
                        value = state.name,
                        onValueChange = onNameChanged,
                        onContinue = onNameContinue,
                        continueEnabled = state.name.isNotBlank(),
                        helperText = state.typeKey.takeIf { it.isNotBlank() }?.let { "Internal ID: $it" }
                    )

                    CreateExperimentStep.INPUT_SIGNAL -> DropdownStep(
                        icon = R.drawable.icon_experiment_generic,
                        question = "What do you want to change (the input)?",
                        prompt = "Signal",
                        labels = SIGNAL_SOURCE_LABELS,
                        values = SIGNAL_SOURCE_VALUES,
                        selected = state.input.sourceKey,
                        onSelect = onInputSignalSelected
                    )

                    CreateExperimentStep.INPUT_CUSTOM_FORM -> CustomSignalFormStep(
                        selection = state.input,
                        onLabelChanged = onInputCustomLabelChanged,
                        onQuestionChanged = onInputCustomQuestionChanged,
                        onKindSelected = onInputCustomKindSelected,
                        onLowLabelChanged = onInputCustomLowLabelChanged,
                        onHighLabelChanged = onInputCustomHighLabelChanged,
                        onUnitLabelChanged = onInputCustomUnitLabelChanged,
                        onContinue = onInputCustomFormContinue
                    )

                    CreateExperimentStep.OUTPUT_SIGNAL -> DropdownStep(
                        icon = R.drawable.icon_experiment_generic,
                        question = "What do you want to measure (the output)?",
                        prompt = "Signal",
                        labels = SIGNAL_SOURCE_LABELS,
                        values = SIGNAL_SOURCE_VALUES,
                        selected = state.output.sourceKey,
                        onSelect = onOutputSignalSelected
                    )

                    CreateExperimentStep.OUTPUT_CUSTOM_FORM -> CustomSignalFormStep(
                        selection = state.output,
                        onLabelChanged = onOutputCustomLabelChanged,
                        onQuestionChanged = onOutputCustomQuestionChanged,
                        onKindSelected = onOutputCustomKindSelected,
                        onLowLabelChanged = onOutputCustomLowLabelChanged,
                        onHighLabelChanged = onOutputCustomHighLabelChanged,
                        onUnitLabelChanged = onOutputCustomUnitLabelChanged,
                        onContinue = onOutputCustomFormContinue
                    )

                    CreateExperimentStep.TARGET_RANGE -> TargetRangeStep(
                        state = state,
                        onToggleAdvanced = onToggleAdvanced,
                        onRangeUnderChanged = onRangeUnderChanged,
                        onRangeN1Changed = onRangeN1Changed,
                        onRangeN2Changed = onRangeN2Changed,
                        onRangeN3Changed = onRangeN3Changed,
                        onRangeOverChanged = onRangeOverChanged,
                        onRangeSizeChanged = onRangeSizeChanged,
                        onStableRangeChanged = onStableRangeChanged,
                        onContinue = onTargetRangeContinue
                    )

                    CreateExperimentStep.CONFIRM -> ConfirmStep(
                        state = state,
                        onCreateClicked = onCreateClicked
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
private fun CustomSignalFormStep(
    selection: SignalSelection,
    onLabelChanged: (String) -> Unit,
    onQuestionChanged: (String) -> Unit,
    onKindSelected: (CustomValueKind) -> Unit,
    onLowLabelChanged: (String) -> Unit,
    onHighLabelChanged: (String) -> Unit,
    onUnitLabelChanged: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.icon_experiment_generic),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = "Define your new signal",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = selection.customLabel,
            onValueChange = onLabelChanged,
            label = { Text("Short label (e.g. \"Coffee\")") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
        OutlinedTextField(
            value = selection.customQuestion,
            onValueChange = onQuestionChanged,
            label = { Text("Daily check-in question") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        Text(
            text = "How should this be answered?",
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CustomValueKind.entries.forEach { kind ->
                FilterChip(
                    selected = selection.customKind == kind,
                    onClick = { onKindSelected(kind) },
                    label = { Text(kindLabel(kind), fontSize = 12.sp) }
                )
            }
        }

        if (selection.customKind == CustomValueKind.SCALE_1_7) {
            OutlinedTextField(
                value = selection.customLowLabel,
                onValueChange = onLowLabelChanged,
                label = { Text("Low end label (optional)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = selection.customHighLabel,
                onValueChange = onHighLabelChanged,
                label = { Text("High end label (optional)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        } else {
            OutlinedTextField(
                value = selection.customUnitLabel,
                onValueChange = onUnitLabelChanged,
                label = { Text("Unit (optional, e.g. \"cups\")") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }

        Button(
            onClick = onContinue,
            enabled = selection.toSignalRef() != null,
            modifier = Modifier
                .padding(top = 12.dp)
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Text("Continue")
        }
    }
}

private fun kindLabel(kind: CustomValueKind): String = when (kind) {
    CustomValueKind.SCALE_1_7 -> "Scale (1-7)"
    CustomValueKind.COUNT -> "Count"
    CustomValueKind.DURATION_MINUTES -> "Duration (minutes)"
}

@Composable
private fun TargetRangeStep(
    state: CreateExperimentUiState,
    onToggleAdvanced: () -> Unit,
    onRangeUnderChanged: (Float) -> Unit,
    onRangeN1Changed: (Float) -> Unit,
    onRangeN2Changed: (Float) -> Unit,
    onRangeN3Changed: (Float) -> Unit,
    onRangeOverChanged: (Float) -> Unit,
    onRangeSizeChanged: (Float) -> Unit,
    onStableRangeChanged: (Float) -> Unit,
    onContinue: () -> Unit
) {
    val ranges = state.ranges

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.icon_experiment_generic),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = "Target range",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 16.dp)
        )
        Text(
            text = "We've picked a starting target range based on how you'll answer this " +
                "signal. You can fine-tune it below if you want.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        TextButton(onClick = onToggleAdvanced) {
            Text(if (state.showAdvanced) "Hide advanced options" else "Show advanced options")
        }

        if (state.showAdvanced && ranges != null) {
            NumberField("Under", ranges.under, onRangeUnderChanged)
            NumberField("Level 1", ranges.n1, onRangeN1Changed)
            NumberField("Level 2", ranges.n2, onRangeN2Changed)
            NumberField("Level 3", ranges.n3, onRangeN3Changed)
            NumberField("Over", ranges.over, onRangeOverChanged)
            NumberField("Range size", state.rangeSize ?: 0f, onRangeSizeChanged)
            NumberField("Stable range", state.stableRange ?: 0f, onStableRangeChanged)
        }

        Button(
            onClick = onContinue,
            enabled = ranges != null,
            modifier = Modifier
                .padding(top = 16.dp)
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Text("Continue")
        }
    }
}

/** Keeps its own text buffer so a partial edit (e.g. "15.") isn't clobbered by the Float
 *  round-tripping through the ViewModel on every keystroke -- only resets when [value] changes
 *  for a reason other than this field's own [onValueChange] (e.g. a fresh preset). */
@Composable
private fun NumberField(label: String, value: Float, onValueChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(formatFloat(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toFloatOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

private fun formatFloat(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()

@Composable
private fun ConfirmStep(state: CreateExperimentUiState, onCreateClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.icon_experiment_generic),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = state.name,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 16.dp)
        )
        Text(
            text = "Input: ${signalSummary(state.input)}\nOutput: ${signalSummary(state.output)}",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(
            onClick = onCreateClicked,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            Text("Create Experiment")
        }
    }
}

private fun signalSummary(selection: SignalSelection): String = when {
    selection.isCustom -> selection.customLabel.ifBlank { "(custom signal)" }
    selection.sourceKey != null -> {
        val index = SIGNAL_SOURCE_VALUES.indexOf(selection.sourceKey)
        SIGNAL_SOURCE_LABELS.getOrElse(index) { selection.sourceKey.orEmpty() }
    }
    else -> "(not selected)"
}

@Composable
private fun SubmittingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .semantics { contentDescription = "Creating experiment" },
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = White) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Creating Experiment...")
            }
        }
    }
}
