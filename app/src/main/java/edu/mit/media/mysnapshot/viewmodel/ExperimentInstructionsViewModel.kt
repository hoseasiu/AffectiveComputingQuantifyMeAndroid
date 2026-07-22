package edu.mit.media.mysnapshot.viewmodel

import android.content.Context
import android.preference.PreferenceManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.CheckinOutcome
import edu.mit.media.mysnapshot.engine.ExperimentEngine
import edu.mit.media.mysnapshot.engine.ExperimentType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Screen state for [edu.mit.media.mysnapshot.activities.ExperimentInstructionsActivity].
 * Flattened the same way as [CheckinUiState]/[HistoryUiState]: `isLoading`/`isFirstDay` pick the
 * screen mode, `experiment`/`experimentType`/`outcome` are only non-null once a real day's
 * instructions have been computed. The dialog-visibility fields (and [upcomingTargets]) used to
 * be `remember { mutableStateOf(...) }` in the Activity's Composable -- hoisting them here is
 * the whole point of this migration: that state now survives activity recreation instead of
 * resetting on every rotation.
 */
data class InstructionsUiState(
    val isLoading: Boolean = true,
    val isFirstDay: Boolean = false,
    val experiment: ExperimentEntity? = null,
    val experimentType: ExperimentType? = null,
    val outcome: CheckinOutcome? = null,
    val isRefreshing: Boolean = false,
    val newStageDialogVisible: Boolean = false,
    val failedStageDialogVisible: Boolean = false,
    val failedStageReason: ExperimentEngine.RestartReason? = null,
    val progressOptInDialogVisible: Boolean = false,
    val targetPreviewOptInDialogVisible: Boolean = false,
    val targetPreviewDialogVisible: Boolean = false,
    val upcomingTargets: List<ExperimentRepository.UpcomingTarget> = emptyList()
)

/** One-shot side effects the Activity must perform (Intents; not VM-testable). */
sealed interface InstructionsEvent {
    data object NavigateToMain : InstructionsEvent
    data class NavigateToComplete(val experimentId: Int) : InstructionsEvent
    data class NavigateToProgress(val experimentId: Int) : InstructionsEvent
}

private const val SHOW_PROGRESS_PREF = "show_progress_during_experiment"
private const val SHOW_TARGET_PREVIEW_PREF = "show_target_preview_during_experiment"

@HiltViewModel
class ExperimentInstructionsViewModel @Inject constructor(
    private val repository: ExperimentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructionsUiState())
    val uiState: StateFlow<InstructionsUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<InstructionsEvent>(Channel.BUFFERED)
    val events: Flow<InstructionsEvent> = eventChannel.receiveAsFlow()

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private var experimentId: Int = -1

    // Deliberately never cleared once set, matching the legacy Activity exactly: the intent
    // extras it was derived from don't change for the lifetime of the Activity instance either
    // (no onNewIntent override), so every resume that reuses this instance keeps reusing the
    // same pending outcome instead of re-computing one via refreshInstructions().
    private var pendingOutcome: CheckinOutcome? = null

    private var dialogsShown = false
    private var loaded = false
    private var forceNewStageDialog = false

    /**
     * Called once from `onCreate`. [forceNewStageDialog] is
     * [edu.mit.media.mysnapshot.activities.MainActivity.FORCE_NEW_STAGE_DIALOG], passed in
     * rather than referenced directly so this stays decoupled and test-controllable.
     */
    fun load(experimentId: Int, pendingOutcome: CheckinOutcome?, forceNewStageDialog: Boolean) {
        if (loaded) return
        loaded = true
        this.experimentId = experimentId
        this.pendingOutcome = pendingOutcome
        this.forceNewStageDialog = forceNewStageDialog
        refresh()
    }

    /** Mirrors the legacy `onResume()` incrementing `resumeTrigger`, re-running the same load body. */
    fun onResume() {
        if (loaded) refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val current = if (experimentId != -1) {
                repository.getExperimentById(experimentId).first()
            } else {
                repository.getLatestExperiment().first()
            }

            if (current == null) {
                eventChannel.send(InstructionsEvent.NavigateToMain)
                return@launch
            }

            if (!current.isActive) {
                eventChannel.send(InstructionsEvent.NavigateToComplete(current.id))
                return@launch
            }

            val checkins = repository.getCheckinsForExperiment(current.id).first()
            if (checkins.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, isFirstDay = true) }
                return@launch
            }

            val outcome = pendingOutcome ?: repository.refreshInstructions(current.id)

            // Once dialogsShown flips true, these three stay exactly as they were (whatever the
            // user last left them at, dismissed or not) on every subsequent refresh -- mirrors
            // the legacy Activity, where the guarded block below simply didn't run again, leaving
            // the composition-scoped `remember` dialog states untouched.
            val previous = _uiState.value
            var newStageDialogVisible = previous.newStageDialogVisible
            var failedStageDialogVisible = previous.failedStageDialogVisible
            var failedStageReason = previous.failedStageReason
            if (!dialogsShown) {
                dialogsShown = true
                if (outcome.restartedStage) {
                    failedStageReason = outcome.restartReason
                    failedStageDialogVisible = true
                } else if (outcome.newStage || forceNewStageDialog) {
                    newStageDialogVisible = true
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isFirstDay = false,
                    experiment = current,
                    experimentType = ExperimentType.fromTypeKey(current.type),
                    outcome = outcome,
                    newStageDialogVisible = newStageDialogVisible,
                    failedStageDialogVisible = failedStageDialogVisible,
                    failedStageReason = failedStageReason
                )
            }
        }
    }

    fun onRefreshClick() {
        val state = _uiState.value
        val current = state.experiment
        if (current == null || state.isRefreshing) return

        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val outcome = repository.refreshInstructions(current.id)
            _uiState.update { it.copy(outcome = outcome, isRefreshing = false) }
        }
    }

    fun onProgressClick() {
        val current = _uiState.value.experiment ?: return
        if (prefs.getBoolean(SHOW_PROGRESS_PREF, false)) {
            viewModelScope.launch { eventChannel.send(InstructionsEvent.NavigateToProgress(current.id)) }
        } else {
            _uiState.update { it.copy(progressOptInDialogVisible = true) }
        }
    }

    fun onProgressOptInDismissed() {
        _uiState.update { it.copy(progressOptInDialogVisible = false) }
    }

    fun onProgressOptInConfirmed() {
        val current = _uiState.value.experiment ?: return
        prefs.edit().putBoolean(SHOW_PROGRESS_PREF, true).apply()
        _uiState.update { it.copy(progressOptInDialogVisible = false) }
        viewModelScope.launch { eventChannel.send(InstructionsEvent.NavigateToProgress(current.id)) }
    }

    fun onPreviewTargetsClick() {
        val current = _uiState.value.experiment ?: return
        if (prefs.getBoolean(SHOW_TARGET_PREVIEW_PREF, false)) {
            loadUpcomingTargets(current.id)
        } else {
            _uiState.update { it.copy(targetPreviewOptInDialogVisible = true) }
        }
    }

    fun onTargetPreviewOptInDismissed() {
        _uiState.update { it.copy(targetPreviewOptInDialogVisible = false) }
    }

    fun onTargetPreviewOptInConfirmed() {
        val current = _uiState.value.experiment ?: return
        prefs.edit().putBoolean(SHOW_TARGET_PREVIEW_PREF, true).apply()
        _uiState.update { it.copy(targetPreviewOptInDialogVisible = false) }
        loadUpcomingTargets(current.id)
    }

    private fun loadUpcomingTargets(experimentId: Int) {
        viewModelScope.launch {
            val targets = repository.getUpcomingTargetPreview(experimentId)
            _uiState.update { it.copy(upcomingTargets = targets, targetPreviewDialogVisible = true) }
        }
    }

    fun onTargetPreviewDismissed() {
        _uiState.update { it.copy(targetPreviewDialogVisible = false) }
    }

    fun onNewStageDialogDismissed() {
        _uiState.update { it.copy(newStageDialogVisible = false) }
    }

    fun onFailedStageDialogDismissed() {
        _uiState.update { it.copy(failedStageDialogVisible = false) }
    }
}
