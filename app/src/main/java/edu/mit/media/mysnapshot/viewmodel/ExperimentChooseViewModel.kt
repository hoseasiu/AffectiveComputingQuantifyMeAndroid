package edu.mit.media.mysnapshot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.mit.media.mysnapshot.data.ExperimentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
}

@HiltViewModel
class ExperimentChooseViewModel @Inject constructor(
    private val repository: ExperimentRepository
) : ViewModel() {

    private val eventChannel = Channel<ExperimentChooseEvent>(Channel.BUFFERED)
    val events: Flow<ExperimentChooseEvent> = eventChannel.receiveAsFlow()

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
}
