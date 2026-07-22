package edu.mit.media.mysnapshot.engine

import java.time.LocalDate
import java.time.OffsetDateTime

/** Sensor/checkin-agnostic view of a single day's check-in, for feeding the engine. */
data class CheckinRecord(
    val time: OffsetDateTime,
    val leisureTime: Float,
    val happiness: Float,
    val stress: Float,
    val productivity: Float,
    // Answers to a custom-signal experiment type's input/output question (#31), null for
    // every built-in-only checkin and for days the custom question was skipped/missed.
    val customInputValue: Float? = null,
    val customOutputValue: Float? = null
)

/**
 * Port of `ExperimentType._get_checkins_value` (analysis.py): builds one value per day
 * in [startDate, endDateExclusive) by finding the check-in recorded the *next* calendar
 * day (users check in each morning about the day just finished), or null if missed.
 */
fun getCheckinsValue(
    checkins: List<CheckinRecord>,
    startDate: LocalDate,
    endDateExclusive: LocalDate,
    selector: (CheckinRecord) -> Float?
): List<Float?> {
    val sorted = checkins.sortedBy { it.time }
    val results = mutableListOf<Float?>()
    var date = startDate
    while (date.isBefore(endDateExclusive)) {
        val targetDate = date.plusDays(1)
        val found = sorted.firstOrNull { it.time.toLocalDate() == targetDate }
        results.add(found?.let(selector))
        date = date.plusDays(1)
    }
    return results
}
