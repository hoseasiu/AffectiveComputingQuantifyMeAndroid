package edu.mit.media.mysnapshot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.engine.ExperimentType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The four wizard pages of `ExperimentConfigActivity`'s efficacy questionnaire, in the exact
 * order the legacy `initFragments()` built its fragment list. Unlike [SettingsStep], this
 * wizard has no "edit mode" -- `loadInitialData()` always returned `false`
 * (`isBuildingData` was always `true`), so [ExperimentConfigViewModel.advance] is a direct,
 * unconditional port of [CheckinViewModel]'s reveal-frontier logic.
 */
enum class ExperimentConfigStep {
    INTRO, APP_EFFICACY, EXPERIMENT_EFFICACY, SELF_EFFICACY
}

data class ExperimentConfigUiState(
    val isLoading: Boolean = true,
    val experimentType: ExperimentType = ExperimentType.fromTypeKey("leisurehappiness"),
    val currentStep: Int = ExperimentConfigStep.INTRO.ordinal,
    val revealedSteps: Int = 1,
    val appEfficacy: Int? = null,
    val experimentEfficacy: Int? = null,
    val selfEfficacy: Int? = null,
    val isSubmitting: Boolean = false
)

sealed interface ExperimentConfigEvent {
    data object NavigateToCreated : ExperimentConfigEvent
}

@HiltViewModel
class ExperimentConfigViewModel @Inject constructor(
    private val repository: ExperimentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExperimentConfigUiState())
    val uiState: StateFlow<ExperimentConfigUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ExperimentConfigEvent>(Channel.BUFFERED)
    val events: Flow<ExperimentConfigEvent> = eventChannel.receiveAsFlow()

    private var loadedTypeKey: String? = null

    fun load(typeKey: String) {
        if (loadedTypeKey == typeKey) return
        loadedTypeKey = typeKey
        val type = ExperimentType.fromTypeKey(typeKey)
        _uiState.update { it.copy(isLoading = false, experimentType = type) }
    }

    fun onIntroContinue() {
        advance(ExperimentConfigStep.INTRO.ordinal)
    }

    fun onAppEfficacySelected(index: Int) {
        _uiState.update { it.copy(appEfficacy = index) }
        advance(ExperimentConfigStep.APP_EFFICACY.ordinal)
    }

    fun onExperimentEfficacySelected(index: Int) {
        _uiState.update { it.copy(experimentEfficacy = index) }
        advance(ExperimentConfigStep.EXPERIMENT_EFFICACY.ordinal)
    }

    fun onSelfEfficacySelected(index: Int) {
        _uiState.update { it.copy(selfEfficacy = index) }
        // Last step -- advance() submits instead of revealing a fifth page.
        advance(ExperimentConfigStep.SELF_EFFICACY.ordinal)
    }

    fun goToStep(step: Int) {
        val state = _uiState.value
        if (step in 0 until state.revealedSteps) {
            _uiState.update { it.copy(currentStep = step) }
        }
    }

    /** Direct port of `QuestionActivity.onPageComplete(forceSlide)` -- see [CheckinViewModel.advance]. */
    private fun advance(step: Int, forceSlide: Boolean = false) {
        val lastStep = ExperimentConfigStep.entries.size - 1
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

    /** Equivalent of the legacy `ExperimentConfigActivity.onFinish()`. */
    private fun submit() {
        val state = _uiState.value
        val appEfficacy = state.appEfficacy ?: return
        val experimentEfficacy = state.experimentEfficacy ?: return
        val selfEfficacy = state.selfEfficacy ?: return

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            repository.createExperiment(
                state.experimentType,
                selfEfficacy,
                appEfficacy,
                experimentEfficacy
            )
            eventChannel.send(ExperimentConfigEvent.NavigateToCreated)
        }
    }
}
