package edu.mit.media.mysnapshot.engine

import org.joda.time.LocalDate

/**
 * Port of each `ExperimentType`'s `get_inputs`/`get_outputs` (analysis.py). Checkin-backed
 * signals (leisure time, happiness, stress, productivity) are fully wired against local
 * Room check-ins. Wearable-backed signals (sleep duration/efficiency/start time, step
 * count) return all-null placeholders until Phase 3 wires Health Connect — the engine
 * itself (should_end_stage/is_output_stable/calculate_results) is agnostic to where the
 * non-null values come from, so nothing here needs to change once Phase 3 lands.
 */
object ExperimentDataProvider {

    fun getInputs(
        type: ExperimentType,
        checkins: List<CheckinRecord>,
        startDate: LocalDate,
        endDateExclusive: LocalDate
    ): List<Float?> {
        return when (type) {
            ExperimentType.LeisureHappiness ->
                getCheckinsValue(checkins, startDate, endDateExclusive) { it.leisureTime }
            // Sleep start time (Health Connect SleepSessionRecord) -- Phase 3.
            ExperimentType.SleepVariabilityStress -> nullDays(startDate, endDateExclusive)
            // Sleep duration (Health Connect SleepSessionRecord) -- Phase 3.
            ExperimentType.SleepDurationProductivity -> nullDays(startDate, endDateExclusive)
            // Daily step count (Health Connect StepsRecord) -- Phase 3.
            ExperimentType.StepsSleepEfficiency -> nullDays(startDate, endDateExclusive)
        }
    }

    fun getOutputs(
        type: ExperimentType,
        checkins: List<CheckinRecord>,
        startDate: LocalDate,
        endDateExclusive: LocalDate
    ): List<Float?> {
        return when (type) {
            ExperimentType.LeisureHappiness ->
                getCheckinsValue(checkins, startDate, endDateExclusive) { it.happiness }
            ExperimentType.SleepVariabilityStress ->
                getCheckinsValue(checkins, startDate, endDateExclusive) { it.stress }
            ExperimentType.SleepDurationProductivity ->
                getCheckinsValue(checkins, startDate, endDateExclusive) { it.productivity }
            // Sleep efficiency (Health Connect SleepSessionRecord stages) -- Phase 3.
            ExperimentType.StepsSleepEfficiency -> nullDays(startDate, endDateExclusive)
        }
    }

    private fun nullDays(startDate: LocalDate, endDateExclusive: LocalDate): List<Float?> {
        val days = mutableListOf<Float?>()
        var date = startDate
        while (date.isBefore(endDateExclusive)) {
            days.add(null)
            date = date.plusDays(1)
        }
        return days
    }
}
