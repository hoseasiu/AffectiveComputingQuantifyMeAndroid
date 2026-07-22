package edu.mit.media.mysnapshot.engine

import edu.mit.media.mysnapshot.health.HealthConnectManager
import org.joda.time.LocalDate

/**
 * Port of each `ExperimentType`'s `get_inputs`/`get_outputs` (analysis.py). Checkin-backed
 * signals (leisure time, happiness, stress, productivity) are fully wired against local
 * Room check-ins. Wearable-backed signals (sleep duration/efficiency/start time, step
 * count) are wired against Health Connect (Phase 3) via [HealthConnectManager] -- the
 * engine itself (should_end_stage/is_output_stable/calculate_results) is agnostic to where
 * the non-null values come from.
 *
 * Dispatches on [SignalRef] (a type's `inputSignal`/`outputSignal`) rather than on the type
 * itself (Phase 5.4): a new experiment type that reuses an existing signal for its input or
 * output needs no change here, only a config entry. A [SignalRef.Custom] signal (#31) always
 * reads straight from the checkin's `customInputValue`/`customOutputValue` column -- which of
 * the two depends only on whether this is the input or the output slot, since every
 * [ExperimentType] has exactly one of each.
 */
object ExperimentDataProvider {

    suspend fun getInputs(
        type: ExperimentType,
        checkins: List<CheckinRecord>,
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        healthConnect: HealthConnectManager
    ): List<Float?> =
        getSignal(type.inputSignal, checkins, startDate, endDateExclusive, healthConnect, isInputSlot = true)

    suspend fun getOutputs(
        type: ExperimentType,
        checkins: List<CheckinRecord>,
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        healthConnect: HealthConnectManager
    ): List<Float?> =
        getSignal(type.outputSignal, checkins, startDate, endDateExclusive, healthConnect, isInputSlot = false)

    private suspend fun getSignal(
        signal: SignalRef,
        checkins: List<CheckinRecord>,
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        healthConnect: HealthConnectManager,
        isInputSlot: Boolean
    ): List<Float?> {
        return when (signal) {
            is SignalRef.Custom ->
                getCheckinsValue(checkins, startDate, endDateExclusive) {
                    if (isInputSlot) it.customInputValue else it.customOutputValue
                }
            is SignalRef.Builtin -> when (signal.source) {
                SignalSource.CHECKIN_LEISURE_TIME ->
                    getCheckinsValue(checkins, startDate, endDateExclusive) { it.leisureTime }
                SignalSource.CHECKIN_HAPPINESS ->
                    getCheckinsValue(checkins, startDate, endDateExclusive) { it.happiness }
                SignalSource.CHECKIN_STRESS ->
                    getCheckinsValue(checkins, startDate, endDateExclusive) { it.stress }
                SignalSource.CHECKIN_PRODUCTIVITY ->
                    getCheckinsValue(checkins, startDate, endDateExclusive) { it.productivity }
                SignalSource.HEALTH_CONNECT_SLEEP_START_MINUTE ->
                    healthConnect.getSleepStartMinuteOfDay(startDate, endDateExclusive)
                SignalSource.HEALTH_CONNECT_SLEEP_DURATION_MINUTES ->
                    healthConnect.getSleepDurationMinutes(startDate, endDateExclusive)
                SignalSource.HEALTH_CONNECT_STEPS ->
                    healthConnect.getDailySteps(startDate, endDateExclusive)
                SignalSource.HEALTH_CONNECT_SLEEP_EFFICIENCY ->
                    healthConnect.getSleepEfficiency(startDate, endDateExclusive)
                SignalSource.HEALTH_CONNECT_EXERCISE_MINUTES ->
                    healthConnect.getExerciseMinutes(startDate, endDateExclusive)
            }
        }
    }
}
