package edu.mit.media.mysnapshot.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.CheckinOutcome
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.health.HealthConnectManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * The six wizard pages of the daily check-in, in the exact order the legacy
 * `ExperimentCheckinActivity.initFragments()` built its fragment list (and thus the exact
 * order the current ViewPager/`ScrollPageIndicator` shows them in). Ordinal == step index,
 * used throughout [CheckinViewModel] as the progressive-reveal frontier.
 */
enum class CheckinStep {
    INTRO, DID_FOLLOW_DIRECTIONS, LEISURE, HAPPY, STRESS, PRODUCTIVITY
}

/**
 * Screen state for [edu.mit.media.mysnapshot.activities.ExperimentCheckinActivity].
 *
 * `revealedSteps` is the Compose analogue of the legacy `ScreenSlidePagerAdapter.activeCount`:
 * the number of wizard pages unlocked so far (starts at 1 -- only the intro is visible).
 * `currentStep` is the page currently shown, always `< revealedSteps`. The four scale answers
 * and the leisure value are nullable until answered; each holds exactly the value that will be
 * sent to [ExperimentRepository.submitCheckin] (0-based index for the scales, minutes string
 * for leisure), so there's no separate "display value" vs "submitted value" to keep in sync.
 */
data class CheckinUiState(
    val isLoading: Boolean = true,
    val experimentType: ExperimentType = ExperimentType.fromTypeKey("leisurehappiness"),
    val introText: String = "",
    val currentStep: Int = CheckinStep.INTRO.ordinal,
    val revealedSteps: Int = 1,
    val didFollowDirections: Int? = null,
    val happiness: Int? = null,
    val stress: Int? = null,
    val productivity: Int? = null,
    val leisureValue: String? = null,
    val isSubmitting: Boolean = false
)

/** One-shot side effects the Activity must perform (Intents; not VM-testable). */
sealed interface CheckinEvent {
    /** Mirrors `packageManager.getLaunchIntentForPackage(...)`, which needs a Context. */
    data object OpenHealthConnect : CheckinEvent

    /** No current experiment at all -- redirect to [edu.mit.media.mysnapshot.activities.MainActivity]. */
    data object NavigateToMain : CheckinEvent

    /** Either the loaded experiment is no longer active (validity guard), or a check-in just completed it. */
    data class NavigateToComplete(val experimentId: Int) : CheckinEvent

    data class NavigateToInstructions(val experimentId: Int, val outcome: CheckinOutcome) : CheckinEvent
}

@HiltViewModel
class CheckinViewModel @Inject constructor(
    private val repository: ExperimentRepository,
    private val healthConnect: HealthConnectManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<CheckinEvent>(Channel.BUFFERED)
    val events: Flow<CheckinEvent> = eventChannel.receiveAsFlow()

    // Cached for the validity re-check on every onResume(), mirroring the legacy Activity's
    // `experiment` field -- avoids a redundant Room read on every resume.
    private var experiment: ExperimentEntity? = null
    private var loadedExperimentId: Int? = null

    /** Guards against re-fetching on every recomposition/config change, per the established convention. */
    fun load(experimentId: Int) {
        if (loadedExperimentId == experimentId) return
        loadedExperimentId = experimentId
        viewModelScope.launch {
            val loaded = if (experimentId != -1) {
                repository.getExperimentById(experimentId).first()
            } else {
                repository.getLatestExperiment().first()
            }
            experiment = loaded

            val type = loaded?.let { ExperimentType.fromTypeKey(it.type) }
                ?: ExperimentType.fromTypeKey("leisurehappiness")
            val sleepExplanation = if (type.usesSleepData) buildSleepNightExplanation() else null

            _uiState.update {
                it.copy(
                    isLoading = false,
                    experimentType = type,
                    introText = buildIntroText(sleepExplanation)
                )
            }

            checkValidity()
        }
    }

    /** Re-checks validity on every resume, matching the legacy `onResume()` behavior exactly. */
    fun onResume() {
        if (_uiState.value.isLoading) {
            // Validity is checked once the load (above) completes; nothing loaded yet.
            return
        }
        checkValidity()
    }

    private fun checkValidity() {
        val current = experiment
        if (current == null) {
            viewModelScope.launch { eventChannel.send(CheckinEvent.NavigateToMain) }
            return
        }
        if (!current.isActive) {
            viewModelScope.launch { eventChannel.send(CheckinEvent.NavigateToComplete(current.id)) }
        }
    }

    /**
     * Paper §6.3: participants didn't understand what counted as "a night" when they slept
     * past midnight. Health Connect gives explicit session start/end, so surface it directly
     * instead of leaving the day-boundary silently inferred. Ported verbatim from the legacy
     * Activity's `buildSleepNightExplanation()`.
     */
    private suspend fun buildSleepNightExplanation(): String? {
        val session = healthConnect.getMostRecentSleepSession() ?: return null
        val zone = ZoneId.systemDefault()
        val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
        val start = session.startTime.atZone(zone)
        val end = session.endTime.atZone(zone)
        val night = session.attributedNight.toString("EEEE, MMM d")
        return context.getString(
            R.string.checkin_sleep_night_explanation,
            start.format(timeFormat),
            end.format(timeFormat),
            night
        )
    }

    private fun buildIntroText(sleepExplanation: String?): String {
        var introText = context.getString(R.string.checkin_intro_text)
        sleepExplanation?.let { introText += "\n\n$it" }
        return introText
    }

    fun onOpenHealthConnect() {
        viewModelScope.launch { eventChannel.send(CheckinEvent.OpenHealthConnect) }
    }

    fun onIntroContinue() {
        // Mirrors the legacy text fragment: its `onSelected` always calls `onPageComplete(true)`,
        // i.e. always force-slides forward even if the intro isn't the current reveal frontier
        // (e.g. the user swiped back to it after answering later questions).
        advance(CheckinStep.INTRO.ordinal, forceSlide = true)
    }

    fun onDidFollowDirectionsSelected(index: Int) {
        _uiState.update { it.copy(didFollowDirections = index) }
        advance(CheckinStep.DID_FOLLOW_DIRECTIONS.ordinal)
    }

    fun onLeisureSelected(value: String) {
        _uiState.update { it.copy(leisureValue = value) }
        advance(CheckinStep.LEISURE.ordinal)
    }

    fun onHappySelected(index: Int) {
        _uiState.update { it.copy(happiness = index) }
        advance(CheckinStep.HAPPY.ordinal)
    }

    fun onStressSelected(index: Int) {
        _uiState.update { it.copy(stress = index) }
        advance(CheckinStep.STRESS.ordinal)
    }

    fun onProductivitySelected(index: Int) {
        _uiState.update { it.copy(productivity = index) }
        // Productivity is the last step -- mirrors the legacy `onPageComplete()` finding
        // `adapter.getTotalCount() == viewPager.getCurrentItem() + 1` and calling `onFinish()`.
        advance(CheckinStep.PRODUCTIVITY.ordinal)
    }

    /** Lets the dots indicator jump back to any already-revealed page (a plain swipe/tap in the legacy ViewPager). */
    fun goToStep(step: Int) {
        val state = _uiState.value
        if (step in 0 until state.revealedSteps) {
            _uiState.update { it.copy(currentStep = step) }
        }
    }

    /**
     * Direct port of `QuestionActivity.onPageComplete(forceSlide)`: if the answered page was
     * the current reveal frontier, unlock the next page immediately and slide to it after the
     * same 150ms `waitThenSlidePage()` delay; otherwise only slide if [forceSlide] was requested
     * (only the intro's continue button does this). The final step (productivity) submits
     * instead of revealing a seventh page, matching the legacy "no more pages -> onFinish()" path.
     */
    private fun advance(step: Int, forceSlide: Boolean = false) {
        val lastStep = CheckinStep.entries.size - 1
        if (step == lastStep) {
            submit()
            return
        }

        val state = _uiState.value
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

    /** Equivalent of the legacy `onFinish()`: submit the check-in, then navigate per the outcome. */
    private fun submit() {
        val current = experiment ?: return
        val state = _uiState.value
        val didFollow = state.didFollowDirections ?: return
        val happiness = state.happiness ?: return
        val stress = state.stress ?: return
        val productivity = state.productivity ?: return
        val leisure = state.leisureValue?.toIntOrNull() ?: return

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            val outcome = repository.submitCheckin(
                experimentId = current.id,
                didFollowInstructions = didFollow,
                happiness = happiness,
                stress = stress,
                productivity = productivity,
                leisureTime = leisure
            )

            _uiState.update { it.copy(isSubmitting = false) }

            if (outcome.isComplete) {
                eventChannel.send(CheckinEvent.NavigateToComplete(current.id))
            } else {
                eventChannel.send(CheckinEvent.NavigateToInstructions(current.id, outcome))
            }
        }
    }
}
