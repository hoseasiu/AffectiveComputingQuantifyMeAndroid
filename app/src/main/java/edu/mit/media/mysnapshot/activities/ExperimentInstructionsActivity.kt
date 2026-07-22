package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.data.ExperimentRepository
import org.joda.time.format.DateTimeFormat
import edu.mit.media.mysnapshot.engine.CheckinOutcome
import edu.mit.media.mysnapshot.engine.ExperimentEngine
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.ui.theme.AccentRed
import edu.mit.media.mysnapshot.ui.theme.DarkPurple
import edu.mit.media.mysnapshot.ui.theme.FadeBlue
import edu.mit.media.mysnapshot.ui.theme.FadeGreen
import edu.mit.media.mysnapshot.ui.theme.FadeYellow
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeFonts
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme
import edu.mit.media.mysnapshot.ui.theme.Yellow
import edu.mit.media.mysnapshot.ui.theme.rememberQuantifyMeFonts
import edu.mit.media.mysnapshot.viewmodel.InstructionsEvent
import edu.mit.media.mysnapshot.viewmodel.InstructionsUiState
import edu.mit.media.mysnapshot.viewmodel.ExperimentInstructionsViewModel

/**
 * Daily instructions screen (AGENT_PLANS/IMPROVEMENTS.md 2.2). Now Compose +
 * [ExperimentInstructionsViewModel] instead of driving Room reads and dialog visibility
 * straight from Activity-scoped Compose state -- see the ViewModel's doc comments for why the
 * per-resume refresh and one-shot "dialogs shown" guard are shaped the way they are.
 */
@AndroidEntryPoint
class ExperimentInstructionsActivity : ComponentActivity() {

    private val viewModel: ExperimentInstructionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchIntent = intent
        val experimentId = launchIntent.getIntExtra(EXPERIMENT_ID_EXTRA, -1)
        val pendingOutcome = if (launchIntent.getBooleanExtra(HAS_OUTCOME_EXTRA, false)) {
            outcomeFromIntent(launchIntent)
        } else {
            null
        }
        viewModel.load(experimentId, pendingOutcome, MainActivity.FORCE_NEW_STAGE_DIALOG)

        setContent {
            QuantifyMeTheme {
                val fonts = rememberQuantifyMeFonts()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            InstructionsEvent.NavigateToMain -> {
                                startActivity(Intent(this@ExperimentInstructionsActivity, MainActivity::class.java))
                                finish()
                                overridePendingTransition(0, 0)
                            }
                            is InstructionsEvent.NavigateToComplete -> {
                                ExperimentCompleteActivity.startActivity(this@ExperimentInstructionsActivity, event.experimentId)
                                finish()
                                overridePendingTransition(0, 0)
                            }
                            is InstructionsEvent.NavigateToProgress -> {
                                ExperimentProgressActivity.startActivity(this@ExperimentInstructionsActivity, event.experimentId)
                            }
                        }
                    }
                }

                InstructionsScreen(
                    state = state,
                    fonts = fonts,
                    onSettingsClick = { startActivity(Intent(this@ExperimentInstructionsActivity, SettingsActivity::class.java)) },
                    onHistoryClick = { startActivity(Intent(this@ExperimentInstructionsActivity, HistoryActivity::class.java)) },
                    onRefreshClick = viewModel::onRefreshClick,
                    onProgressClick = viewModel::onProgressClick,
                    onPreviewTargetsClick = viewModel::onPreviewTargetsClick
                )

                if (state.newStageDialogVisible) {
                    NewStageDialog(fonts = fonts, onDismiss = viewModel::onNewStageDialogDismissed)
                }
                if (state.failedStageDialogVisible) {
                    FailedStageDialog(
                        reason = state.failedStageReason,
                        fonts = fonts,
                        onDismiss = viewModel::onFailedStageDialogDismissed
                    )
                }
                if (state.progressOptInDialogVisible) {
                    ProgressOptInDialog(
                        onDismiss = viewModel::onProgressOptInDismissed,
                        onConfirm = viewModel::onProgressOptInConfirmed
                    )
                }
                if (state.targetPreviewOptInDialogVisible) {
                    TargetPreviewOptInDialog(
                        onDismiss = viewModel::onTargetPreviewOptInDismissed,
                        onConfirm = viewModel::onTargetPreviewOptInConfirmed
                    )
                }
                if (state.targetPreviewDialogVisible) {
                    val experimentType = state.experimentType
                    if (experimentType != null) {
                        TargetPreviewDialog(
                            experimentType = experimentType,
                            upcomingTargets = state.upcomingTargets,
                            onDismiss = viewModel::onTargetPreviewDismissed
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    companion object {
        const val LOGTAG = "ExperimentInstructionsActivity"
        const val EXPERIMENT_ID_EXTRA = "experiment_id"
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

@Composable
private fun InstructionsScreen(
    state: InstructionsUiState,
    fonts: QuantifyMeFonts,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onProgressClick: () -> Unit,
    onPreviewTargetsClick: () -> Unit
) {
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.isFirstDay -> FirstDayScreen(
            onSettingsClick = onSettingsClick,
            onHistoryClick = onHistoryClick
        )
        else -> {
            val experimentType = state.experimentType
            val outcome = state.outcome
            if (experimentType != null && outcome != null) {
                InstructionsContent(
                    experimentType = experimentType,
                    outcome = outcome,
                    fonts = fonts,
                    isRefreshing = state.isRefreshing,
                    onSettingsClick = onSettingsClick,
                    onHistoryClick = onHistoryClick,
                    onRefreshClick = onRefreshClick,
                    onProgressClick = onProgressClick,
                    onPreviewTargetsClick = onPreviewTargetsClick
                )
            }
        }
    }
}

@Composable
private fun FirstDayScreen(onSettingsClick: () -> Unit, onHistoryClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(FadeGreen)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            HeaderIcon(R.drawable.button_profile, "Settings", onSettingsClick, tinted = false)
            HeaderIcon(R.drawable.button_home, "History", onHistoryClick, tinted = false)
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Your experiment is being created. Check back tomorrow morning " +
                    "(and every morning!) to check in and see what to do.",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 20.dp)
            )
        }
    }
}

@Composable
private fun InstructionsContent(
    experimentType: ExperimentType,
    outcome: CheckinOutcome,
    fonts: QuantifyMeFonts,
    isRefreshing: Boolean,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onProgressClick: () -> Unit,
    onPreviewTargetsClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(FadeYellow)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (outcome.currentStage != 0) {
                HeaderIcon(R.drawable.icon_settings_calendar, "Preview Upcoming Targets", onPreviewTargetsClick)
            }
            HeaderIcon(R.drawable.icon_settings_chart, "Progress", onProgressClick)
            RefreshHeaderIcon(isRefreshing = isRefreshing, onClick = onRefreshClick)
            HeaderIcon(R.drawable.button_profile, "Settings", onSettingsClick)
            HeaderIcon(R.drawable.button_home, "History", onHistoryClick)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.padding(top = 20.dp, bottom = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(experimentType.iconId),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp)
                )
                Text(
                    text = experimentType.name,
                    fontFamily = fonts.raleway,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 20.dp)
                )
            }

            if (outcome.currentStage != 0) {
                Text(
                    text = "Today's Target",
                    fontFamily = fonts.montserratBold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Text(
                    text = outcome.target?.let { experimentType.formatInstruction(it) } ?: "",
                    fontFamily = fonts.ralewaySemibold,
                    fontSize = 24.sp,
                    color = DarkPurple,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 30.dp, start = 15.dp, end = 15.dp)
                )
            }

            Text(
                text = "Stage ${outcome.currentStage + 1}",
                fontFamily = fonts.montserratBold,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            val stageInstructions = stringArrayResource(R.array.stage_instructions)
            Text(
                text = stageInstructions.getOrNull(outcome.currentStage) ?: "",
                fontFamily = fonts.raleway,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 30.dp)
            )

            Text(
                text = "Stage Progress",
                fontFamily = fonts.montserratBold,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            ProgressGridRow(
                cellTexts = (0..3).map { i ->
                    outcome.stageInputs.getOrNull(i)?.let { experimentType.formatTarget(it) } ?: "-"
                },
                fonts = fonts
            )
            ProgressGridRow(
                cellTexts = (4..6).map { i ->
                    outcome.stageInputs.getOrNull(i)?.let { experimentType.formatTarget(it) } ?: "-"
                } + "",
                fonts = fonts
            )
        }
    }
}

@Composable
private fun ProgressGridRow(cellTexts: List<String>, fonts: QuantifyMeFonts) {
    Row(modifier = Modifier.fillMaxWidth().background(Yellow)) {
        cellTexts.forEachIndexed { index, text ->
            if (index > 0) {
                Spacer(modifier = Modifier.width(1.dp).fillMaxHeight())
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = text, fontFamily = fonts.raleway, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun HeaderIcon(
    @DrawableRes iconId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    tinted: Boolean = true,
    iconPadding: Dp = 10.dp
) {
    Image(
        painter = painterResource(iconId),
        contentDescription = contentDescription,
        colorFilter = if (tinted) ColorFilter.tint(AccentRed) else null,
        modifier = Modifier
            .size(50.dp)
            .clickable(onClick = onClick)
            .padding(iconPadding)
    )
}

@Composable
private fun RefreshHeaderIcon(isRefreshing: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh-rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 500, easing = LinearEasing)),
        label = "refresh-rotation-value"
    )

    Image(
        painter = painterResource(R.drawable.button_refresh),
        contentDescription = "Refresh",
        colorFilter = ColorFilter.tint(AccentRed),
        modifier = Modifier
            .size(50.dp)
            .clickable(enabled = !isRefreshing, onClick = onClick)
            .padding(13.dp)
            .let { if (isRefreshing) it.rotate(rotation) else it }
    )
}

@Composable
private fun NewStageDialog(fonts: QuantifyMeFonts, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = FadeYellow) {
            Box(modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "New Stage\nUnlocked!",
                        fontFamily = fonts.montserratRegular,
                        fontSize = 40.sp,
                        textAlign = TextAlign.Center
                    )
                    Image(
                        painter = painterResource(R.drawable.icon_settings_birthday),
                        contentDescription = null,
                        modifier = Modifier.size(70.dp).padding(top = 30.dp)
                    )
                    Text(
                        text = "You did it! That stage is complete, and you're on to the next one. Congratulations!",
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 50.dp)
                    )
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 30.dp)
                ) {
                    Text("Yay!")
                }
            }
        }
    }
}

@Composable
private fun FailedStageDialog(
    reason: ExperimentEngine.RestartReason?,
    fonts: QuantifyMeFonts,
    onDismiss: () -> Unit
) {
    val reasonText = when (reason) {
        ExperimentEngine.RestartReason.TOO_MANY_MISSED_DAYS -> stringResource(R.string.restart_reason_missed_days)
        ExperimentEngine.RestartReason.TARGET_ZONE_UNREACHABLE -> stringResource(R.string.restart_reason_target_zone_unreachable)
        null -> stringResource(R.string.restart_reason_generic)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = FadeBlue) {
            Box(modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Stage Restarting!",
                        fontFamily = fonts.montserratRegular,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center
                    )
                    Image(
                        painter = painterResource(R.drawable.icon_settings_sad),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp).padding(top = 30.dp)
                    )
                    Text(
                        text = reasonText,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 50.dp)
                    )
                    Text(
                        text = "Please be sure to wear your tracker and checkin here every day, " +
                            "and to hit the target goals.",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 30.dp)
                    )
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 30.dp)
                ) {
                    Text("Okay. Got it.")
                }
            }
        }
    }
}

@Composable
private fun ProgressOptInDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("See Your Progress?") },
        text = {
            Text(
                "You can see your check-in history for this experiment as it happens, " +
                    "instead of waiting until it's done. Keep in mind that knowing your " +
                    "past results might change how you behave going forward, which can " +
                    "affect the experiment's accuracy. Turn this on?"
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Yes, show me") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("No thanks") }
        }
    )
}

@Composable
private fun TargetPreviewOptInDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preview Upcoming Targets?") },
        text = {
            Text(
                "You can see what your targets are likely to be for the rest of this " +
                    "stage, instead of finding out each morning. Keep in mind that knowing " +
                    "targets ahead of time might change how you behave today, which can " +
                    "affect the experiment's accuracy. Turn this on?"
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Yes, show me") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("No thanks") }
        }
    )
}

@Composable
private fun TargetPreviewDialog(
    experimentType: ExperimentType,
    upcomingTargets: List<ExperimentRepository.UpcomingTarget>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upcoming Targets") },
        text = {
            if (upcomingTargets.isEmpty()) {
                Text("No more days left to preview in this stage.")
            } else {
                Column {
                    upcomingTargets.forEach { upcoming ->
                        Text(
                            text = "${previewDayFormat.print(upcoming.date)}: " +
                                (upcoming.target?.let { experimentType.formatTarget(it) } ?: "-"),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = "Assumes this stage runs its normal course -- it can still " +
                            "end or restart early depending on your check-ins.",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Got it") }
        }
    )
}

private val previewDayFormat = DateTimeFormat.forPattern("EEE, MMM d")
