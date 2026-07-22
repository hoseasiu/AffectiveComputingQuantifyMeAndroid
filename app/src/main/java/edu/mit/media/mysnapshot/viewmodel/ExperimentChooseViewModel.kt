package edu.mit.media.mysnapshot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.engine.ExperimentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One-shot navigation event for [edu.mit.media.mysnapshot.activities.ExperimentChooseActivity].
 * Redirecting to [edu.mit.media.mysnapshot.activities.MainActivity] is an Activity-level
 * side effect (startActivity/finish/overridePendingTransition), so the ViewModel only signals
 * *that* it should happen; the Activity performs it.
 */
sealed interface ExperimentChooseEvent {
    data object NavigateToMain : ExperimentChooseEvent

    /** Issue #34's deletion guard refused the delete -- some experiment still uses this type. */
    data class DeleteCustomTypeFailed(val typeName: String) : ExperimentChooseEvent
}

data class ExperimentChooseUiState(
    val builtinTypes: List<ExperimentType> = emptyList(),
    val customTypes: List<ExperimentType> = emptyList()
)

@HiltViewModel
class ExperimentChooseViewModel @Inject constructor(
    private val repository: ExperimentRepository
) : ViewModel() {

    private val eventChannel = Channel<ExperimentChooseEvent>(Channel.BUFFERED)
    val events: Flow<ExperimentChooseEvent> = eventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(buildUiState())
    val uiState: StateFlow<ExperimentChooseUiState> = _uiState.asStateFlow()

    /**
     * Mirrors the previous `onResume` guard exactly: this must be invoked every time the
     * screen resumes (not just once on first creation), since navigating *back* into this
     * screen after starting an experiment elsewhere is exactly the case it needs to catch.
     * Returns the launched [Job] purely so tests can deterministically await completion
     * (`join()`) before asserting whether an event was -- or wasn't -- emitted; the Activity
     * call site ignores the return value.
     */
    fun checkForExistingExperiment(): Job = viewModelScope.launch {
        val experiment = repository.getLatestExperiment().first()
        if (experiment != null && !experiment.isCancelled) {
            eventChannel.send(ExperimentChooseEvent.NavigateToMain)
        }
    }

    /**
     * Re-reads [ExperimentType.getAllTypes] into [uiState]. Called on every `onResume` (like
     * [checkForExistingExperiment]) since a custom type may have been created or deleted
     * elsewhere -- e.g. `CreateExperimentActivity` -- since this screen was last shown.
     * `ExperimentType.getAllTypes()` reads an in-memory registry, so this is a cheap
     * synchronous re-read, not a DB query.
     */
    fun refreshTypes() {
        _uiState.value = buildUiState()
    }

    /**
     * Deletes a custom type (issue #34). On success, refreshes [uiState] so the card
     * disappears immediately; on refusal (still in use), emits an event so the Activity can
     * surface the reason -- see [ExperimentRepository.deleteCustomType].
     */
    fun deleteCustomType(type: ExperimentType) = viewModelScope.launch {
        val deleted = repository.deleteCustomType(type.typeKey)
        if (deleted) {
            refreshTypes()
        } else {
            eventChannel.send(ExperimentChooseEvent.DeleteCustomTypeFailed(type.name))
        }
    }

    private fun buildUiState() = ExperimentChooseUiState(
        builtinTypes = CHOOSE_ORDER.map { ExperimentType.fromTypeKey(it) },
        customTypes = ExperimentType.getAllTypes().filter { it.typeKey !in CHOOSE_ORDER }
    )

    companion object {
        // Display order on the choose screen -- deliberately independent of
        // ExperimentType.getAllTypes()'s config order (this is a UI-presentation concern).
        val CHOOSE_ORDER = listOf(
            "leisurehappiness",
            "stepssleepefficiency",
            "sleepdurationproductivity",
            "sleepvariabilitystress",
            "exercisestress",
            "stepshappiness",
            "leisureproductivity"
        )
    }
}
