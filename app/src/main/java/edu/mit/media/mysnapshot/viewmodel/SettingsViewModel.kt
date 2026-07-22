package edu.mit.media.mysnapshot.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import edu.mit.media.mysnapshot.data.NotificationData
import edu.mit.media.mysnapshot.data.UserData
import edu.mit.media.mysnapshot.data.loadUserData
import edu.mit.media.mysnapshot.data.saveUserData
import edu.mit.media.mysnapshot.notifications.AdherenceNudgeScheduler
import edu.mit.media.mysnapshot.notifications.CheckinReminderScheduler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

/**
 * The ten wizard pages of onboarding/settings, in the exact order the legacy
 * `SettingsActivity.initFragments()` built its fragment list. Ordinal == step index, used as
 * the progressive-reveal frontier exactly like [CheckinStep] -- see that enum's doc comment.
 */
enum class SettingsStep {
    TERMS, HEALTH_CONNECT, BIRTHDATE, RACE, GENDER, NOTIFICATION, HAPPY, STRESS, ACTIVITY, SLEEP_QUALITY
}

/**
 * Screen state for [edu.mit.media.mysnapshot.activities.SettingsActivity]. Mirrors
 * [CheckinUiState]'s shape, with one addition: [isBuildingData] distinguishes the two legacy
 * `QuestionActivity` modes -- first-run onboarding (progressive reveal, `revealedSteps` starts
 * at 1) vs. revisiting Settings to edit existing answers (every step revealed up front, no
 * auto-advance on selection -- see [SettingsViewModel.advance]).
 *
 * The happy/stress/sleepQuality scale answers are nullable here (unanswered) even though the
 * persisted [UserData] fields are non-null `Int` -- the legacy fragments had the same split:
 * `QuestionRadioGroupFragment.value` (nullable `Integer`) vs. `UserData.happy` (primitive
 * `int`, default 0), reconciled only at save time.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val isBuildingData: Boolean = true,
    val currentStep: Int = 0,
    val revealedSteps: Int = 1,
    val acceptedTerms: Boolean = false,
    val healthConnectGranted: Boolean? = null,
    val dobString: String? = null,
    val race: String? = null,
    val gender: String? = null,
    val notificationTime: String = "09:30",
    val notificationSet: Boolean = true,
    val happy: Int? = null,
    val stress: Int? = null,
    val activityLevel: String? = null,
    val sleepQuality: Int? = null,
    val showCreditsDialog: Boolean = false
)

/** One-shot side effects the Activity must perform (permission launchers, navigation). */
sealed interface SettingsEvent {
    data object RequestHealthConnectPermissions : SettingsEvent
    data object RequestNotificationPermission : SettingsEvent
    data object NavigateToIntroThanks : SettingsEvent
    data object SavedAndNavigateToMain : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    // Carries the fields load() couldn't put in SettingsUiState as-is (only the persisted
    // UserData.timezone, kept solely so submit() doesn't need to re-read prefs).
    private var userData: UserData = UserData()
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true

        val result = loadUserData(context)
        userData = result.userData
        val isBuilding = !result.existed
        val notification = userData.notificationData ?: NotificationData()

        _uiState.update {
            it.copy(
                isLoading = false,
                isBuildingData = isBuilding,
                revealedSteps = if (isBuilding) 1 else SettingsStep.entries.size,
                acceptedTerms = userData.acceptedTerms,
                healthConnectGranted = userData.healthConnectGranted,
                dobString = userData.dobString,
                race = userData.race,
                gender = userData.gender,
                notificationTime = notification.notificationTime ?: "09:30",
                notificationSet = notification.notificationSet,
                // Onboarding never pre-fills a scale answer (matches the legacy fragments'
                // `setValue()` only being called `if (!isBuildingData())`); editing does.
                happy = if (isBuilding) null else userData.happy,
                stress = if (isBuilding) null else userData.stress,
                activityLevel = userData.activity,
                sleepQuality = if (isBuilding) null else userData.sleepQuality
            )
        }
    }

    fun onTermsCheckedChange(checked: Boolean) {
        // Mirrors the legacy checkbox: once accepted it locks (isEnabled = false), and
        // unchecking before that point is a local UI toggle only -- never persisted or
        // advanced, matching `QuestionCheckboxFragment`'s listener only acting `if (value)`.
        if (_uiState.value.acceptedTerms) return
        if (checked) {
            _uiState.update { it.copy(acceptedTerms = true) }
            advance(SettingsStep.TERMS.ordinal)
        }
    }

    fun onRequestHealthConnectPermissions() {
        viewModelScope.launch { eventChannel.send(SettingsEvent.RequestHealthConnectPermissions) }
    }

    fun onHealthConnectPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(healthConnectGranted = granted) }
        // Mirrors `onDataSave` (not `onSelected`): the legacy fragment calls `onPageComplete()`
        // *and* an unconditional `waitThenSlidePage()`, i.e. forceSlide = true.
        advance(SettingsStep.HEALTH_CONNECT.ordinal, forceSlide = true)
    }

    fun onBirthdateSelected(dateString: String) {
        _uiState.update { it.copy(dobString = dateString) }
        advance(SettingsStep.BIRTHDATE.ordinal)
    }

    fun onRaceSelected(value: String) {
        _uiState.update { it.copy(race = value) }
        advance(SettingsStep.RACE.ordinal)
    }

    fun onGenderSelected(value: String) {
        _uiState.update { it.copy(gender = value) }
        advance(SettingsStep.GENDER.ordinal)
    }

    fun onNotificationContinue(time: String, enabled: Boolean) {
        _uiState.update { it.copy(notificationTime = time, notificationSet = enabled) }
        // Same onDataSave/forceSlide reasoning as health-connect above.
        advance(SettingsStep.NOTIFICATION.ordinal, forceSlide = true)
    }

    fun onHappySelected(index: Int) {
        _uiState.update { it.copy(happy = index) }
        advance(SettingsStep.HAPPY.ordinal)
    }

    fun onStressSelected(index: Int) {
        _uiState.update { it.copy(stress = index) }
        advance(SettingsStep.STRESS.ordinal)
    }

    fun onActivitySelected(value: String) {
        _uiState.update { it.copy(activityLevel = value) }
        advance(SettingsStep.ACTIVITY.ordinal)
    }

    fun onSleepQualitySelected(index: Int) {
        _uiState.update { it.copy(sleepQuality = index) }
        // Last step -- advance() submits instead of revealing an eleventh page.
        advance(SettingsStep.SLEEP_QUALITY.ordinal)
    }

    fun onShowCreditsDialog() {
        _uiState.update { it.copy(showCreditsDialog = true) }
    }

    fun onDismissCreditsDialog() {
        _uiState.update { it.copy(showCreditsDialog = false) }
    }

    /** Lets the dots indicator jump to any already-revealed page (edit mode reveals all of them up front). */
    fun goToStep(step: Int) {
        val state = _uiState.value
        if (step in 0 until state.revealedSteps) {
            _uiState.update { it.copy(currentStep = step) }
        }
    }

    /** The explicit "Save" control shown only when editing existing settings (not onboarding). */
    fun onSave() {
        submit()
    }

    /**
     * Direct port of `QuestionActivity.onPageComplete(forceSlide)`, folded together with the
     * legacy `isBuildingData` gate at its call site: in onboarding mode this is exactly the
     * check-in wizard's reveal-frontier logic; in edit mode `onPageComplete()` itself was
     * always a no-op, but two fragments (health-connect, notification) *also* called the
     * legacy `waitThenSlidePage()` completely unconditionally as a manual "next page"
     * convenience -- `forceSlide` still does that here even when [SettingsUiState.isBuildingData]
     * is false.
     */
    private fun advance(step: Int, forceSlide: Boolean = false) {
        val state = _uiState.value
        if (!state.isBuildingData) {
            if (forceSlide) goToStep(step + 1)
            return
        }

        val lastStep = SettingsStep.entries.size - 1
        if (step == lastStep) {
            submit()
            return
        }

        val isFrontier = state.revealedSteps == step + 1
        if (isFrontier) {
            _uiState.update { it.copy(revealedSteps = it.revealedSteps + 1) }
        }

        if (isFrontier || forceSlide) {
            viewModelScope.launch {
                delay(150)
                _uiState.update { it.copy(currentStep = step + 1) }
            }
        }
    }

    /** Equivalent of the legacy `SettingsActivity.onFinish()`. */
    private fun submit() {
        val state = _uiState.value

        userData.acceptedTerms = state.acceptedTerms
        userData.healthConnectGranted = state.healthConnectGranted
        userData.dobString = state.dobString
        userData.race = state.race
        userData.gender = state.gender
        userData.happy = state.happy ?: 0
        userData.stress = state.stress ?: 0
        userData.activity = state.activityLevel
        userData.sleepQuality = state.sleepQuality ?: 0
        userData.notificationData = NotificationData().apply {
            notificationTime = state.notificationTime
            notificationSet = state.notificationSet
        }
        userData.timezone = TimeZone.getDefault().id

        saveUserData(context, userData)

        CheckinReminderScheduler.schedule(context)
        AdherenceNudgeScheduler.schedule(context)

        viewModelScope.launch {
            if (state.notificationSet && needsNotificationPermission()) {
                eventChannel.send(SettingsEvent.RequestNotificationPermission)
            }
            if (state.isBuildingData) {
                eventChannel.send(SettingsEvent.NavigateToIntroThanks)
            } else {
                eventChannel.send(SettingsEvent.SavedAndNavigateToMain)
            }
        }
    }

    private fun needsNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    }
}
