package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import edu.mit.media.mysnapshot.ui.theme.FadeBlue
import edu.mit.media.mysnapshot.ui.theme.PageIndicatorDisabled
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme
import edu.mit.media.mysnapshot.ui.theme.RadioGreen
import edu.mit.media.mysnapshot.ui.theme.RadioRed
import edu.mit.media.mysnapshot.ui.theme.White
import edu.mit.media.mysnapshot.viewmodel.CheckinEvent
import edu.mit.media.mysnapshot.viewmodel.CheckinStep
import edu.mit.media.mysnapshot.viewmodel.CheckinUiState
import edu.mit.media.mysnapshot.viewmodel.CheckinViewModel

/**
 * The daily check-in (AGENT_PLANS/IMPROVEMENTS.md 3.1): a 6-step progressive-reveal wizard,
 * now Compose + [CheckinViewModel] instead of the legacy `QuestionActivity`/`ViewPager` +
 * `QuestionRadioGroupFragment`/`QuestionSpinnerFragment`. Behavior and data semantics are
 * preserved exactly -- see the ViewModel's doc comments for how each legacy quirk (0-based
 * scale indices, the reveal-frontier auto-advance, the stress-scale color inversion) was
 * ported. All Room/Health Connect/repository I/O happens in `viewModelScope`; this Activity
 * only renders state and performs the one-shot Intent side effects the ViewModel can't
 * (opening Health Connect, navigating away).
 */
@AndroidEntryPoint
class ExperimentCheckinActivity : ComponentActivity() {

    private val viewModel: CheckinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val experimentId = intent.getIntExtra(EXPERIMENT_ID_EXTRA, -1)
        viewModel.load(experimentId)

        setContent {
            QuantifyMeTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            CheckinEvent.OpenHealthConnect -> openHealthConnect()
                            CheckinEvent.NavigateToMain ->
                                navigateAndFinish(Intent(this@ExperimentCheckinActivity, MainActivity::class.java))
                            is CheckinEvent.NavigateToComplete -> {
                                ExperimentCompleteActivity.startActivity(this@ExperimentCheckinActivity, event.experimentId)
                                finish()
                                overridePendingTransition(0, 0)
                            }
                            is CheckinEvent.NavigateToInstructions -> {
                                ExperimentInstructionsActivity.startActivityAfterCheckin(
                                    this@ExperimentCheckinActivity, event.experimentId, event.outcome
                                )
                                finish()
                                overridePendingTransition(0, 0)
                            }
                        }
                    }
                }

                CheckinScreen(
                    state = state,
                    onOpenHealthConnect = viewModel::onOpenHealthConnect,
                    onIntroContinue = viewModel::onIntroContinue,
                    onDidFollowDirectionsSelected = viewModel::onDidFollowDirectionsSelected,
                    onLeisureSelected = viewModel::onLeisureSelected,
                    onHappySelected = viewModel::onHappySelected,
                    onStressSelected = viewModel::onStressSelected,
                    onProductivitySelected = viewModel::onProductivitySelected,
                    onDotClick = viewModel::goToStep
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Mirrors the legacy per-resume validity re-check (redirect away if the experiment
        // was completed/cancelled elsewhere while this screen was backgrounded).
        viewModel.onResume()
    }

    private fun openHealthConnect() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
    }

    private fun navigateAndFinish(intent: Intent) {
        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
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

private const val TOTAL_STEPS = 6

@Composable
private fun CheckinScreen(
    state: CheckinUiState,
    onOpenHealthConnect: () -> Unit,
    onIntroContinue: () -> Unit,
    onDidFollowDirectionsSelected: (Int) -> Unit,
    onLeisureSelected: (String) -> Unit,
    onHappySelected: (Int) -> Unit,
    onStressSelected: (Int) -> Unit,
    onProductivitySelected: (Int) -> Unit,
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
                when (CheckinStep.entries[state.currentStep]) {
                    CheckinStep.INTRO -> IntroStep(
                        icon = state.experimentType.iconId,
                        body = state.introText,
                        onOpenHealthConnect = onOpenHealthConnect,
                        onContinue = onIntroContinue
                    )
                    CheckinStep.DID_FOLLOW_DIRECTIONS -> RadioScaleStep(
                        icon = R.drawable.icon_settings_self_effectiveness,
                        question = stringResource(R.string.checkin_question_did_follow_directions),
                        leftLabel = stringResource(R.string.checkin_scale_label_poor),
                        rightLabel = stringResource(R.string.checkin_scale_label_great),
                        leftColor = RadioRed,
                        rightColor = RadioGreen,
                        selected = state.didFollowDirections,
                        onSelect = onDidFollowDirectionsSelected
                    )
                    CheckinStep.LEISURE -> LeisureStep(
                        icon = R.drawable.icon_settings_leisure,
                        question = stringResource(R.string.checkin_question_leisure),
                        selected = state.leisureValue,
                        onSelect = onLeisureSelected
                    )
                    CheckinStep.HAPPY -> RadioScaleStep(
                        icon = R.drawable.icon_settings_happiness,
                        question = stringResource(R.string.checkin_question_happy),
                        leftLabel = stringResource(R.string.checkin_scale_label_not_at_all),
                        rightLabel = stringResource(R.string.checkin_scale_label_extremely),
                        leftColor = RadioRed,
                        rightColor = RadioGreen,
                        selected = state.happiness,
                        onSelect = onHappySelected
                    )
                    CheckinStep.STRESS -> RadioScaleStep(
                        icon = R.drawable.icon_settings_stress,
                        question = stringResource(R.string.checkin_question_stress),
                        leftLabel = stringResource(R.string.checkin_scale_label_not_at_all),
                        rightLabel = stringResource(R.string.checkin_scale_label_extremely),
                        // Inverted vs. the other three scales: low stress reads as "good" (green),
                        // matching the legacy `stress.init(..., radio_green, radio_red, 7)` call.
                        leftColor = RadioGreen,
                        rightColor = RadioRed,
                        selected = state.stress,
                        onSelect = onStressSelected
                    )
                    CheckinStep.PRODUCTIVITY -> RadioScaleStep(
                        icon = R.drawable.icon_settings_productivity,
                        question = stringResource(R.string.checkin_question_productivity),
                        leftLabel = stringResource(R.string.checkin_scale_label_not_at_all),
                        rightLabel = stringResource(R.string.checkin_scale_label_extremely),
                        leftColor = RadioRed,
                        rightColor = RadioGreen,
                        selected = state.productivity,
                        onSelect = onProductivitySelected
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
private fun SubmittingOverlay() {
    val description = stringResource(R.string.checkin_generating_instructions_description)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = White) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.checkin_generating_instructions))
            }
        }
    }
}

@Composable
private fun StepDotsIndicator(
    currentStep: Int,
    revealedSteps: Int,
    totalSteps: Int,
    onDotClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val currentSuffix = stringResource(R.string.checkin_step_current_suffix)
        val unavailableSuffix = stringResource(R.string.checkin_step_unavailable_suffix)
        for (i in 0 until totalSteps) {
            val revealed = i < revealedSteps
            val isCurrent = i == currentStep
            val description = stringResource(R.string.checkin_step_of_total_description, i + 1, totalSteps) + when {
                isCurrent -> currentSuffix
                !revealed -> unavailableSuffix
                else -> ""
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(
                        if (revealed) {
                            Modifier.clickable(onClickLabel = description) { onDotClick(i) }
                        } else {
                            Modifier
                        }
                    )
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 14.dp else 10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                revealed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> PageIndicatorDisabled
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun IntroStep(
    icon: Int,
    body: String,
    onOpenHealthConnect: () -> Unit,
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
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.checkin_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 16.dp)
        )
        Text(
            text = body,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        val openHealthConnectDescription = stringResource(R.string.checkin_open_health_connect_description)
        Button(
            onClick = onOpenHealthConnect,
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics { contentDescription = openHealthConnectDescription }
        ) {
            Text(stringResource(R.string.checkin_open_health_connect_button))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            Text(stringResource(R.string.checkin_continue))
        }
    }
}

@Composable
private fun RadioScaleStep(
    icon: Int,
    question: String,
    leftLabel: String,
    rightLabel: String,
    leftColor: Color,
    rightColor: Color,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    val scaleSize = 7

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
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = question,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 24.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until scaleSize) {
                ScaleButton(
                    color = scaleButtonColor(i, scaleSize, leftColor, rightColor),
                    selected = selected == i,
                    description = stringResource(R.string.checkin_scale_option_description, question, i + 1, scaleSize) +
                        when (i) {
                            0 -> ", $leftLabel"
                            scaleSize - 1 -> ", $rightLabel"
                            else -> ""
                        },
                    onClick = { onSelect(i) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = leftLabel, fontSize = 14.sp)
            Text(text = rightLabel, fontSize = 14.sp)
        }
    }
}

/**
 * A single scale button. `size(48.dp)` satisfies the 7.1 accessibility touch-target minimum;
 * `selectable(role = Role.RadioButton)` gives TalkBack the standard "radio button, selected/not
 * selected" announcement, layered with an explicit position-in-set [description] (the scale
 * has no visible numeric labels otherwise).
 */
@Composable
private fun ScaleButton(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape)
                } else {
                    Modifier
                }
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(text = "✓", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

/**
 * Direct port of `ColoredRadioGroup.getColor()`'s linear interpolation (`lerp(i, total, ...)`,
 * i.e. `start + (finish - start) * i / total` -- note this is `i / total`, not `i / (total-1)`,
 * so the rightmost button never quite reaches the pure [rightColor], exactly matching the
 * legacy widget's math).
 */
private fun scaleButtonColor(index: Int, total: Int, leftColor: Color, rightColor: Color): Color {
    val fraction = index / total.toFloat()
    return Color(
        red = leftColor.red + (rightColor.red - leftColor.red) * fraction,
        green = leftColor.green + (rightColor.green - leftColor.green) * fraction,
        blue = leftColor.blue + (rightColor.blue - leftColor.blue) * fraction,
        alpha = 1f
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeisureStep(
    icon: Int,
    question: String,
    selected: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val labels = stringArrayResource(R.array.leisurelabels)
    val values = stringArrayResource(R.array.leisurevalues)
    val selectedLabel = selected
        ?.let { value -> values.indexOf(value).takeIf { it >= 0 } }
        ?.let { labels[it] }
        ?: ""
    val leisureSelectDescription = stringResource(R.string.checkin_leisure_select_description, question, selectedLabel)

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
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = question,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 24.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.checkin_leisure_select_option_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .semantics { contentDescription = leisureSelectDescription }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                labels.forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onSelect(values[i])
                        }
                    )
                }
            }
        }
    }
}
