package edu.mit.media.mysnapshot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.mit.media.mysnapshot.data.ExperimentRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.joda.time.LocalDate
import javax.inject.Inject

/**
 * One-shot navigation decision for [edu.mit.media.mysnapshot.activities.MainActivity]. This
 * screen never renders anything itself -- it only figures out which real screen to route to
 * (mirrors the legacy `lifecycleScope.launch { ... startActivity(...); finish() }` body) --
 * so the Activity performs every destination unconditionally on receipt of an event.
 */
sealed interface MainEvent {
    data object NavigateToChoose : MainEvent
    data class NavigateToComplete(val experimentId: Int) : MainEvent
    data class NavigateToCheckin(val experimentId: Int) : MainEvent
    data class NavigateToInstructions(val experimentId: Int) : MainEvent
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ExperimentRepository
) : ViewModel() {

    private val eventChannel = Channel<MainEvent>(Channel.BUFFERED)
    val events: Flow<MainEvent> = eventChannel.receiveAsFlow()

    // Guards against re-running the routing decision on every config change now that it lives
    // in viewModelScope (which survives recreation) rather than the Activity's lifecycleScope
    // (which didn't) -- a second run would be idempotent but would re-hit Room for no reason.
    private var hasRouted = false

    /**
     * Direct port of the legacy `onCreate` body: no active/uncancelled experiment -> let the
     * user choose one; a completed experiment -> show its result; otherwise -> checkin (if the
     * user hasn't already checked in today, or [forceCheckin] is set) or straight to
     * instructions. [forceCheckin] is [edu.mit.media.mysnapshot.activities.MainActivity.FORCE_CHECKIN],
     * passed in rather than referenced directly so this stays decoupled and test-controllable.
     */
    fun route(forceCheckin: Boolean) {
        if (hasRouted) return
        hasRouted = true

        viewModelScope.launch {
            val experiment = repository.getLatestExperiment().first()

            if (experiment == null || experiment.isCancelled) {
                eventChannel.send(MainEvent.NavigateToChoose)
                return@launch
            }

            if (!experiment.isActive) {
                eventChannel.send(MainEvent.NavigateToComplete(experiment.id))
                return@launch
            }

            val checkins = repository.getCheckinsForExperiment(experiment.id).first()
            val today = LocalDate.now()
            val startedToday = LocalDate(experiment.startTime) == today
            val checkedInToday = checkins.any { LocalDate(it.checkinDate) == today }
            val hadCheckinToday = startedToday || checkedInToday

            if (forceCheckin || !hadCheckinToday) {
                eventChannel.send(MainEvent.NavigateToCheckin(experiment.id))
            } else {
                eventChannel.send(MainEvent.NavigateToInstructions(experiment.id))
            }
        }
    }
}
