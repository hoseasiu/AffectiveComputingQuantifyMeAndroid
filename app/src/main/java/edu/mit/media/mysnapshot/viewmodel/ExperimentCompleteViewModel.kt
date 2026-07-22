package edu.mit.media.mysnapshot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.ExperimentEntity
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
 * Screen state for [edu.mit.media.mysnapshot.activities.ExperimentCompleteActivity].
 * `isLoading` mirrors the legacy `isLoading` Activity field; `experiment == null` once loaded
 * means "no experiment at all" (as opposed to "still loading"), matching the legacy
 * `experiment: ExperimentEntity?` field semantics.
 */
data class ExperimentCompleteUiState(
    val isLoading: Boolean = true,
    val experiment: ExperimentEntity? = null,
    val experimentType: ExperimentType? = null
)

/** One-shot side effect: the loaded/current experiment isn't a valid completed one -- bounce to MainActivity. */
sealed interface ExperimentCompleteEvent {
    data object NavigateToMain : ExperimentCompleteEvent
}

@HiltViewModel
class ExperimentCompleteViewModel @Inject constructor(
    private val repository: ExperimentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExperimentCompleteUiState())
    val uiState: StateFlow<ExperimentCompleteUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ExperimentCompleteEvent>(Channel.BUFFERED)
    val events: Flow<ExperimentCompleteEvent> = eventChannel.receiveAsFlow()

    /** Guards against re-fetching on every config change, per the established convention. */
    private var loadedExperimentId: Int? = null

    fun load(experimentId: Int) {
        if (loadedExperimentId == experimentId) return
        loadedExperimentId = experimentId

        viewModelScope.launch {
            val experiment = if (experimentId != -1) {
                repository.getExperimentById(experimentId).first()
            } else {
                repository.getLatestExperiment().first()
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    experiment = experiment,
                    experimentType = experiment?.let { e -> ExperimentType.fromTypeKey(e.type) }
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
        val current = _uiState.value.experiment
        if (current == null || current.isActive || current.resultValue == null) {
            viewModelScope.launch { eventChannel.send(ExperimentCompleteEvent.NavigateToMain) }
        }
    }
}
