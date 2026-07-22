package edu.mit.media.mysnapshot.engine

import java.time.LocalDate

/** One entry per stage, index 0 = baseline stage, 1..NUM_STAGES = the real stages. */
data class StageDateRange(
    val start: LocalDate?,
    val end: LocalDate?
)

/**
 * Outcome of evaluating a day's check-in against the stage state machine, mirroring
 * what the old `BackendAPI.CheckinResponse.CheckinResult` reported from the server.
 */
data class CheckinOutcome(
    val newStage: Boolean = false,
    val endedEarly: Boolean = false,
    val restartedStage: Boolean = false,
    val restartReason: ExperimentEngine.RestartReason? = null,
    val currentStage: Int = 0,
    val target: Float? = null,
    val day: Int = 0,
    val isComplete: Boolean = false,
    val resultValue: Float = 0f,
    val resultConfidence: Float = 0f,
    // Each day's input value so far in the current stage (nulls for missed days),
    // mirroring the old `CheckinResponse.CheckinResult.stageInputs`.
    val stageInputs: List<Float?> = emptyList()
)
