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
 */
object ExperimentDataProvider {

    suspend fun getInputs(
        type: ExperimentType,
        checkins: List<CheckinRecord>,
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        healthConnect: HealthConnectManager
    ): List<Float?> {
        return when (type) {
            ExperimentType.LeisureHappiness ->
                getCheckinsValue(checkins, startDate, endDateExclusive) { it.leisureTime }
            ExperimentType.SleepVariabilityStress ->
                healthConnect.getSleepStartMinuteOfDay(startDate, endDateExclusive)
            ExperimentType.SleepDurationProductivity ->
                healthConnect.getSleepDurationMinutes(startDate, endDateExclusive)
            ExperimentType.StepsSleepEfficiency ->
                healthConnect.getDailySteps(startDate, endDateExclusive)
        }
    }

    suspend fun getOutputs(
        type: ExperimentType,
        checkins: List<CheckinRecord>,
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        healthConnect: HealthConnectManager
    ): List<Float?> {
        return when (type) {
            ExperimentType.LeisureHappiness ->
                getCheckinsValue(checkins, startDate, endDateExclusive) { it.happiness }
            ExperimentType.SleepVariabilityStress ->
                getCheckinsValue(checkins, startDate, endDateExclusive) { it.stress }
            ExperimentType.SleepDurationProductivity ->
                getCheckinsValue(checkins, startDate, endDateExclusive) { it.productivity }
            ExperimentType.StepsSleepEfficiency ->
                healthConnect.getSleepEfficiency(startDate, endDateExclusive)
        }
    }
}
