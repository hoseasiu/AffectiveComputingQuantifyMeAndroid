package edu.mit.media.mysnapshot.activities

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
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
import androidx.compose.ui.window.Dialog
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.health.HealthConnectManager
import edu.mit.media.mysnapshot.ui.theme.FadeBlue
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme
import edu.mit.media.mysnapshot.ui.theme.RadioGreen
import edu.mit.media.mysnapshot.ui.theme.RadioRed
import edu.mit.media.mysnapshot.ui.wizard.DropdownStep
import edu.mit.media.mysnapshot.ui.wizard.RadioScaleStep
import edu.mit.media.mysnapshot.ui.wizard.StepDotsIndicator
import edu.mit.media.mysnapshot.viewmodel.SettingsEvent
import edu.mit.media.mysnapshot.viewmodel.SettingsStep
import edu.mit.media.mysnapshot.viewmodel.SettingsUiState
import edu.mit.media.mysnapshot.viewmodel.SettingsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Onboarding + "edit existing settings" wizard (AGENT_PLANS/IMPROVEMENTS.md 3.2): a 10-step
 * progressive-reveal wizard in onboarding mode, or every step revealed at once with an
 * explicit Save button when revisiting Settings -- now Compose + [SettingsViewModel] instead
 * of the legacy `QuestionActivity`/`ViewPager` + the 8 live `Question*Fragment` classes. See
 * [SettingsViewModel]'s doc comments for how each legacy quirk (the two-mode
 * `isBuildingData` gate, the reveal-frontier auto-advance, the terms-checkbox lock) was
 * ported.
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    private val healthConnectPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.onHealthConnectPermissionResult(grantedPermissions.containsAll(HealthConnectManager.PERMISSIONS))
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op either way -- CheckinReminderWorker checks the permission again before notifying */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.load()

        setContent {
            QuantifyMeTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            SettingsEvent.RequestHealthConnectPermissions ->
                                healthConnectPermissionLauncher.launch(HealthConnectManager.PERMISSIONS)
                            SettingsEvent.RequestNotificationPermission ->
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            SettingsEvent.NavigateToIntroThanks -> {
                                startActivity(Intent(this@SettingsActivity, IntroThanksActivity::class.java))
                                finish()
                                overridePendingTransition(0, 0)
                            }
                            SettingsEvent.SavedAndNavigateToMain -> {
                                Toast.makeText(this@SettingsActivity, "Saved!", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this@SettingsActivity, MainActivity::class.java))
                                finish()
                            }
                        }
                    }
                }

                SettingsScreen(
                    state = state,
                    onBack = { finish() },
                    onSave = viewModel::onSave,
                    onShowCredits = viewModel::onShowCreditsDialog,
                    onDismissCredits = viewModel::onDismissCreditsDialog,
                    onTermsContinue = viewModel::onTermsContinue,
                    onRequestHealthConnect = viewModel::onRequestHealthConnectPermissions,
                    onBirthdateSelected = viewModel::onBirthdateSelected,
                    onRaceSelected = viewModel::onRaceSelected,
                    onGenderSelected = viewModel::onGenderSelected,
                    onNotificationContinue = viewModel::onNotificationContinue,
                    onHappySelected = viewModel::onHappySelected,
                    onStressSelected = viewModel::onStressSelected,
                    onActivitySelected = viewModel::onActivitySelected,
                    onSleepQualitySelected = viewModel::onSleepQualitySelected,
                    onDotClick = viewModel::goToStep
                )
            }
        }
    }

    companion object {
        const val LOGTAG = "SettingsActivity"
    }
}

private const val TOTAL_STEPS = 10
private val STORAGE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val DISPLAY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d\nyyyy")
private val STORAGE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DISPLAY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShowCredits: () -> Unit,
    onDismissCredits: () -> Unit,
    onTermsContinue: () -> Unit,
    onRequestHealthConnect: () -> Unit,
    onBirthdateSelected: (String) -> Unit,
    onRaceSelected: (String) -> Unit,
    onGenderSelected: (String) -> Unit,
    onNotificationContinue: (String, Boolean) -> Unit,
    onHappySelected: (Int) -> Unit,
    onStressSelected: (Int) -> Unit,
    onActivitySelected: (String) -> Unit,
    onSleepQualitySelected: (Int) -> Unit,
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
            // Only shown when revisiting Settings to edit existing answers -- onboarding hides
            // this whole row, matching the legacy `R.id.controls` visibility toggle.
            if (!state.isBuildingData) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("Back") }
                    TextButton(onClick = onShowCredits) { Text("Settings") }
                    TextButton(onClick = onSave) { Text("Save") }
                }
            }

            StepDotsIndicator(
                currentStep = state.currentStep,
                revealedSteps = state.revealedSteps,
                totalSteps = TOTAL_STEPS,
                onDotClick = onDotClick
            )

            Box(modifier = Modifier.weight(1f)) {
                when (SettingsStep.entries[state.currentStep]) {
                    SettingsStep.TERMS -> TermsStep(onContinue = onTermsContinue)
                    SettingsStep.HEALTH_CONNECT -> HealthConnectStep(
                        granted = state.healthConnectGranted,
                        onConnect = onRequestHealthConnect
                    )
                    SettingsStep.BIRTHDATE -> BirthdateStep(
                        dobString = state.dobString,
                        onSelected = onBirthdateSelected
                    )
                    SettingsStep.RACE -> DropdownStep(
                        icon = R.drawable.icon_settings_race,
                        question = "Which of these describes you the best?",
                        prompt = "Please Select an Option",
                        labels = stringArrayResource(R.array.races),
                        values = stringArrayResource(R.array.racevalues),
                        selected = state.race,
                        onSelect = onRaceSelected
                    )
                    SettingsStep.GENDER -> GenderStep(
                        selected = state.gender,
                        onSelect = onGenderSelected
                    )
                    SettingsStep.NOTIFICATION -> NotificationStep(
                        time = state.notificationTime,
                        enabled = state.notificationSet,
                        onContinue = onNotificationContinue
                    )
                    SettingsStep.HAPPY -> RadioScaleStep(
                        icon = R.drawable.icon_settings_happiness,
                        question = "What is your average happiness level?",
                        leftLabel = "Very Unhappy",
                        rightLabel = "Very Happy",
                        leftColor = RadioRed,
                        rightColor = RadioGreen,
                        selected = state.happy,
                        onSelect = onHappySelected
                    )
                    SettingsStep.STRESS -> RadioScaleStep(
                        icon = R.drawable.icon_settings_stress,
                        question = "What is your average stress level?",
                        leftLabel = "Very Low",
                        rightLabel = "Very High",
                        // Inverted vs. happy/sleep-quality: low stress reads as "good" (green).
                        leftColor = RadioGreen,
                        rightColor = RadioRed,
                        selected = state.stress,
                        onSelect = onStressSelected
                    )
                    SettingsStep.ACTIVITY -> DropdownStep(
                        icon = R.drawable.icon_settings_activity,
                        question = "On average, how many hours are you active each day?",
                        prompt = "Please Select an Option",
                        labels = stringArrayResource(R.array.activity),
                        values = stringArrayResource(R.array.activityvalues),
                        selected = state.activityLevel,
                        onSelect = onActivitySelected
                    )
                    SettingsStep.SLEEP_QUALITY -> RadioScaleStep(
                        icon = R.drawable.icon_settings_sleep,
                        question = "On average, how well do you sleep?",
                        leftLabel = "Terrible",
                        rightLabel = "Great!",
                        leftColor = RadioRed,
                        rightColor = RadioGreen,
                        selected = state.sleepQuality,
                        onSelect = onSleepQualitySelected
                    )
                }
            }
        }
    }

    if (state.showCreditsDialog) {
        CreditsDialog(onDismiss = onDismissCredits)
    }
}

@Composable
private fun TermsStep(onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Image(
            painter = painterResource(R.drawable.art_icon),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = "Welcome to Science!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() }
                .padding(bottom = 16.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = stringResource(R.string.terms_text), fontSize = 14.sp)
        }
        Button(
            onClick = onContinue,
            modifier = Modifier
                .padding(top = 12.dp)
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun HealthConnectStep(granted: Boolean?, onConnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.icon_settings_activity),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = "Please share your step and sleep data from Health Connect.",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 24.dp)
        )
        Button(
            onClick = onConnect,
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics { contentDescription = "Connect to Health Connect" }
        ) {
            Text("Connect to Health Connect")
        }
        when (granted) {
            true -> Text(modifier = Modifier.padding(top = 16.dp), text = "Connected")
            false -> Text(modifier = Modifier.padding(top = 16.dp), text = "Not connected")
            null -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdateStep(dobString: String?, onSelected: (String) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val parsedDate = remember(dobString) {
        dobString?.let { runCatching { LocalDate.parse(it, STORAGE_DATE_FORMAT) }.getOrNull() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.icon_settings_birthday),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = "When is your birthdate?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 24.dp)
        )
        Button(
            onClick = { showPicker = true },
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            Text("Select Date")
        }
        parsedDate?.let {
            Text(
                text = it.format(DISPLAY_DATE_FORMAT),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    if (showPicker) {
        val initialMillis = parsedDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showPicker = false
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onSelected(date.format(STORAGE_DATE_FORMAT))
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun GenderStep(selected: String?, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.icon_settings_gender),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = "What's your gender?",
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
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GenderIconChoice(
                label = "Male",
                icon = R.drawable.question_icon_male,
                color = colorResource(R.color.gender_male),
                selected = selected == "m",
                onClick = { onSelect("m") }
            )
            GenderIconChoice(
                label = "Female",
                icon = R.drawable.question_icon_female,
                color = colorResource(R.color.gender_female),
                selected = selected == "f",
                onClick = { onSelect("f") }
            )
        }
    }
}

@Composable
private fun GenderIconChoice(
    label: String,
    icon: Int,
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(12.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = label }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.size(72.dp)
        )
        Text(
            text = label,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationStep(
    time: String,
    enabled: Boolean,
    onContinue: (String, Boolean) -> Unit
) {
    var localTime by rememberSaveable(time) { mutableStateOf(time) }
    var localEnabled by rememberSaveable(enabled) { mutableStateOf(enabled) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.icon_settings_alarm),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = "Would you like daily notification reminders?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 24.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = localEnabled, onCheckedChange = { localEnabled = it })
            Text("Enable notifications")
        }
        Button(
            onClick = { showPicker = true },
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .padding(top = 12.dp)
        ) {
            Text(runCatching { LocalTime.parse(localTime, STORAGE_TIME_FORMAT).format(DISPLAY_TIME_FORMAT) }.getOrDefault(localTime))
        }
        Button(
            onClick = { onContinue(localTime, localEnabled) },
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .padding(top = 24.dp)
        ) {
            Text("Continue")
        }
    }

    if (showPicker) {
        val parsed = runCatching { LocalTime.parse(localTime, STORAGE_TIME_FORMAT) }.getOrDefault(LocalTime.of(9, 30))
        val pickerState = rememberTimePickerState(
            initialHour = parsed.hour,
            initialMinute = parsed.minute,
            is24Hour = false
        )
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = pickerState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            showPicker = false
                            localTime = LocalTime.of(pickerState.hour, pickerState.minute).format(STORAGE_TIME_FORMAT)
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditsDialog(onDismiss: () -> Unit) {
    val credits = stringArrayResource(R.array.iconcredits)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Credits") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                credits.forEach { credit -> Text(credit) }
            }
        }
    )
}
