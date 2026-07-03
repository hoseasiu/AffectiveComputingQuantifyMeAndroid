package edu.mit.media.mysnapshot.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.mit.media.mysnapshot.engine.ExperimentEngine
import org.joda.time.LocalDate

private val gson = Gson()

/**
 * Decoded view of `ExperimentEntity`'s per-stage JSON columns, mirroring the Django
 * `Experiment` model's `stage_dates`/`stage_target_values`/`stage_restart_count` fields
 * (simplejson-encoded lists of length NUM_STAGES + 1, index 0 = baseline stage).
 */
data class ExperimentStageState(
    val stageDates: List<Pair<LocalDate?, LocalDate?>>,
    val stageTargetValues: List<Float?>,
    val stageRestartCount: List<Int>
) {
    fun toJson(): Triple<String, String, String> {
        val datesJson = gson.toJson(stageDates.map { (start, end) ->
            listOf(start?.toString(), end?.toString())
        })
        val targetsJson = gson.toJson(stageTargetValues)
        val restartJson = gson.toJson(stageRestartCount)
        return Triple(datesJson, targetsJson, restartJson)
    }

    fun withStageDates(stage: Int, start: LocalDate, end: LocalDate): ExperimentStageState {
        val updated = stageDates.toMutableList()
        updated[stage] = start to end
        return copy(stageDates = updated)
    }

    fun withStageTargetValues(targets: List<Float>): ExperimentStageState {
        // setStageTargets returns NUM_STAGES+1 values (index 0 unused / current baseline range)
        return copy(stageTargetValues = targets)
    }

    fun withIncrementedRestartCount(stage: Int): ExperimentStageState {
        val updated = stageRestartCount.toMutableList()
        updated[stage] = updated[stage] + 1
        return copy(stageRestartCount = updated)
    }

    companion object {
        private val stringListType = object : TypeToken<List<List<String?>>>() {}.type
        private val floatListType = object : TypeToken<List<Float?>>() {}.type
        private val intListType = object : TypeToken<List<Int>>() {}.type

        fun initial(startDate: LocalDate): ExperimentStageState {
            val size = ExperimentEngine.NUM_STAGES + 1
            val dates = MutableList<Pair<LocalDate?, LocalDate?>>(size) { null to null }
            dates[0] = startDate to startDate.plusDays(7)
            return ExperimentStageState(
                stageDates = dates,
                stageTargetValues = List(size) { null },
                stageRestartCount = List(size) { 0 }
            )
        }

        fun fromJson(datesJson: String, targetsJson: String, restartJson: String): ExperimentStageState {
            val rawDates: List<List<String?>> = gson.fromJson(datesJson, stringListType)
            val dates = rawDates.map { pair ->
                val start = pair.getOrNull(0)?.let { LocalDate.parse(it) }
                val end = pair.getOrNull(1)?.let { LocalDate.parse(it) }
                start to end
            }
            val targets: List<Float?> = gson.fromJson(targetsJson, floatListType)
            val restarts: List<Int> = gson.fromJson(restartJson, intListType)
            return ExperimentStageState(dates, targets, restarts)
        }
    }
}
