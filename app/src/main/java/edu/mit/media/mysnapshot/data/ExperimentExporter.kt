package edu.mit.media.mysnapshot.data

import com.google.gson.GsonBuilder
import edu.mit.media.mysnapshot.database.CheckinEntity
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.engine.SignalRef
import java.time.format.DateTimeFormatter

/**
 * Builds the "export my data" JSON payload (AGENT_PLANS/MODERNIZE.md, "Optional: local
 * data export") -- a user-initiated, one-shot dump of a single experiment's check-ins and
 * outcome, so someone can hand their own data to a researcher or keep a personal record.
 * Pure string-building only; no network, no Android dependency (the file write + share
 * sheet launch live with the UI that triggers it -- see `HistoryActivity`).
 */
object ExperimentExporter {

    data class CheckinExport(
        val date: String,
        val didFollowInstructions: Int,
        val happiness: Int,
        val stress: Int,
        val productivity: Int,
        val leisureTime: Int,
        val customInputValue: Float?,
        val customOutputValue: Float?
    )

    data class ExperimentExport(
        val experimentType: String,
        val question: String,
        val startTime: String,
        val endTime: String?,
        val currentStage: Int,
        val isActive: Boolean,
        val isCancelled: Boolean,
        val resultValue: Float?,
        val resultDescription: String?,
        val resultConfidence: Float?,
        val customInputQuestion: String?,
        val customOutputQuestion: String?,
        val checkins: List<CheckinExport>
    )

    // serializeNulls: an active experiment has no result yet -- an explicit "resultValue":
    // null is clearer to a researcher reading the export than a silently missing key.
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

    fun buildExportJson(experiment: ExperimentEntity, checkins: List<CheckinEntity>): String {
        val type = ExperimentType.fromTypeKey(experiment.type)
        val export = ExperimentExport(
            experimentType = experiment.type,
            question = type.name,
            startTime = experiment.startTime.toString(),
            endTime = experiment.endTime?.toString(),
            currentStage = experiment.currentStage,
            isActive = experiment.isActive,
            isCancelled = experiment.isCancelled,
            resultValue = experiment.resultValue,
            resultDescription = experiment.resultValue?.let { type.formatResult(it) },
            resultConfidence = experiment.resultConfidence,
            customInputQuestion = (type.inputSignal as? SignalRef.Custom)?.definition?.question,
            customOutputQuestion = (type.outputSignal as? SignalRef.Custom)?.definition?.question,
            checkins = checkins.sortedBy { it.checkinDate }.map {
                CheckinExport(
                    date = it.checkinDate.toString(),
                    didFollowInstructions = it.didFollowInstructions,
                    happiness = it.happiness,
                    stress = it.stress,
                    productivity = it.productivity,
                    leisureTime = it.leisureTime,
                    customInputValue = it.customInputValue,
                    customOutputValue = it.customOutputValue
                )
            }
        )
        return gson.toJson(export)
    }

    fun suggestedFileName(experiment: ExperimentEntity): String {
        val datePart = experiment.startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return "quantifyme_${experiment.type}_$datePart.json"
    }
}
