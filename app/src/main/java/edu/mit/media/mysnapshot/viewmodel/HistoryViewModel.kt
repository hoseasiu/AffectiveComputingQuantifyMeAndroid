package edu.mit.media.mysnapshot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.mit.media.mysnapshot.data.ExperimentExporter
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.ExperimentEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Screen state for [edu.mit.media.mysnapshot.activities.HistoryActivity]. `experiments == null`
 * is the loading state (mirrors the previous `collectAsStateWithLifecycle(initialValue = null)`
 * on the raw repository flow); `isCancelling` disables the "Quit" button on every card while a
 * cancel is in flight, matching the prior single shared `isCancelling` Compose state.
 */
data class HistoryUiState(
    val experiments: List<ExperimentEntity>? = null,
    val isCancelling: Boolean = false
)

/** One-shot side effects the Activity must perform (file IO + share-sheet Intent; not VM-testable). */
sealed interface HistoryEvent {
    data class ExportReady(val experiment: ExperimentEntity, val json: String) : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ExperimentRepository
) : ViewModel() {

    private val isCancelling = MutableStateFlow(false)

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.getAllExperiments(),
        isCancelling
    ) { experiments, cancelling ->
        HistoryUiState(experiments = experiments, isCancelling = cancelling)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    private val eventChannel = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = eventChannel.receiveAsFlow()

    fun cancelExperiment(experiment: ExperimentEntity) {
        viewModelScope.launch {
            isCancelling.value = true
            repository.cancelExperiment(experiment.id)
            isCancelling.value = false
        }
    }

    fun exportExperiment(experiment: ExperimentEntity) {
        viewModelScope.launch {
            val checkins = repository.getCheckinsForExperiment(experiment.id).first()
            val json = ExperimentExporter.buildExportJson(experiment, checkins)
            eventChannel.send(HistoryEvent.ExportReady(experiment, json))
        }
    }
}
