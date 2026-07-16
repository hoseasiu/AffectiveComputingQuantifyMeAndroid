package edu.mit.media.mysnapshot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.engine.ExperimentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Screen state for [edu.mit.media.mysnapshot.activities.ExperimentProgressActivity].
 * `stages == null` is the loading state (mirrors the previous `mutableStateOf<List<...>?>(null)`).
 */
data class ExperimentProgressUiState(
    val experimentType: ExperimentType? = null,
    val stages: List<ExperimentRepository.StageProgress>? = null
)

@HiltViewModel
class ExperimentProgressViewModel @Inject constructor(
    private val repository: ExperimentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExperimentProgressUiState())
    val uiState: StateFlow<ExperimentProgressUiState> = _uiState.asStateFlow()

    // Guards against re-fetching on every recomposition/config change now that the
    // ViewModel (and its state) survives activity recreation -- the previous
    // LaunchedEffect(experimentId)-in-Compose version re-ran only when the composition
    // itself was discarded, which this preserves without an extra network/DB round trip.
    private var loadedExperimentId: Int? = null

    fun load(experimentId: Int) {
        if (loadedExperimentId == experimentId) return
        loadedExperimentId = experimentId
        viewModelScope.launch {
            val experiment = repository.getExperimentById(experimentId).first()
            val type = experiment?.let { ExperimentType.fromTypeKey(it.type) }
            val stages = repository.getProgressSummary(experimentId)
            _uiState.value = ExperimentProgressUiState(experimentType = type, stages = stages)
        }
    }
}
