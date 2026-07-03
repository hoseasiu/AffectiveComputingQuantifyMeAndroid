package edu.mit.media.mysnapshot.data

import dagger.hilt.android.qualifiers.ApplicationContext
import edu.mit.media.mysnapshot.database.CheckinEntity
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.database.QuantifyMeDatabase
import edu.mit.media.mysnapshot.database.UserProfileEntity
import edu.mit.media.mysnapshot.engine.CheckinOutcome
import edu.mit.media.mysnapshot.engine.CheckinRecord
import edu.mit.media.mysnapshot.engine.ExperimentDataProvider
import edu.mit.media.mysnapshot.engine.ExperimentEngine
import edu.mit.media.mysnapshot.engine.ExperimentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local, on-device replacement for the Django `/start_experiment/`, `/experiment_checkin/`,
 * `/refresh_instructions/`, `/get_experiments/`, `/cancel_experiment/` endpoints. The
 * orchestration below mirrors `views.py`'s `experiment_checkin`/`refresh_instructions`
 * exactly (should_end_stage -> [restart|end_stage] -> recompute stage_inputs/target ->
 * calculate_results if complete); [ExperimentEngine] holds the pure algorithm.
 */
@Singleton
class ExperimentRepository @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val database: QuantifyMeDatabase
) {
    private val experimentDao = database.experimentDao()
    private val checkinDao = database.checkinDao()
    private val userProfileDao = database.userProfileDao()

    suspend fun createExperiment(
        type: ExperimentType,
        selfEfficacy: Int,
        appEfficacy: Int,
        experimentEfficacy: Int
    ): Long {
        val today = LocalDate.now()
        val state = ExperimentStageState.initial(today)
        val (datesJson, targetsJson, restartJson) = state.toJson()
        val experiment = ExperimentEntity(
            type = type.typeKey,
            startTime = DateTime.now(),
            selfEfficacy = selfEfficacy,
            appEfficacy = appEfficacy,
            experimentEfficacy = experimentEfficacy,
            stageDatesJson = datesJson,
            stageTargetValuesJson = targetsJson,
            stageRestartCountJson = restartJson
        )
        return experimentDao.insert(experiment)
    }

    fun getCurrentExperiment(): Flow<ExperimentEntity?> = experimentDao.getCurrentExperiment()

    fun getAllExperiments(): Flow<List<ExperimentEntity>> = experimentDao.getAllExperiments()

    fun getCheckinsForExperiment(experimentId: Int): Flow<List<CheckinEntity>> =
        checkinDao.getCheckinsForExperiment(experimentId)

    fun getUserProfile(): Flow<UserProfileEntity?> = userProfileDao.getUserProfile()

    suspend fun updateUserProfile(profile: UserProfileEntity) {
        val existing = userProfileDao.getUserProfile().firstOrNull()
        if (existing != null) {
            userProfileDao.update(profile.copy(id = existing.id))
        } else {
            userProfileDao.insert(profile)
        }
    }

    suspend fun cancelExperiment(experimentId: Int) {
        val experiment = experimentDao.getById(experimentId).firstOrNull()
        if (experiment != null) {
            experimentDao.update(experiment.copy(isActive = false, isCancelled = true))
        }
    }

    /** Equivalent of `/refresh_instructions/`: recompute today's instruction, no new check-in. */
    suspend fun refreshInstructions(experimentId: Int): CheckinOutcome {
        val experiment = experimentDao.getById(experimentId).first()
            ?: error("No experiment with id $experimentId")
        val type = ExperimentType.fromTypeKey(experiment.type)
        val state = ExperimentStageState.fromJson(
            experiment.stageDatesJson, experiment.stageTargetValuesJson, experiment.stageRestartCountJson
        )
        val checkins = checkinDao.getCheckinsForExperiment(experimentId).first().map { it.toRecord() }
        val today = LocalDate.now()

        val (start, end) = stageDateRange(state, experiment.currentStage, today, experiment.endTime, clipToToday = true)
        val stageInputs = if (start != null) ExperimentDataProvider.getInputs(type, checkins, start, end!!) else emptyList()
        val dayInStage = stageInputs.size - 1
        val stageTarget = state.stageTargetValues.getOrNull(experiment.currentStage)
        val dailyTarget = ExperimentEngine.getDailyTarget(
            type.useVariability, stageTarget, experiment.initialStageAverage ?: 0f, dayInStage
        )

        return CheckinOutcome(
            currentStage = experiment.currentStage,
            target = dailyTarget,
            day = dayInStage
        )
    }

    /** Equivalent of `/experiment_checkin/`: record a check-in, then advance the stage machine. */
    suspend fun submitCheckin(
        experimentId: Int,
        didFollowInstructions: Int,
        happiness: Int,
        stress: Int,
        productivity: Int,
        leisureTime: Int
    ): CheckinOutcome {
        checkinDao.insert(
            CheckinEntity(
                experimentId = experimentId,
                checkinDate = DateTime.now(),
                didFollowInstructions = didFollowInstructions,
                happiness = happiness,
                stress = stress,
                productivity = productivity,
                leisureTime = leisureTime
            )
        )

        val experiment = experimentDao.getById(experimentId).first()
            ?: error("No experiment with id $experimentId")
        val type = ExperimentType.fromTypeKey(experiment.type)
        var state = ExperimentStageState.fromJson(
            experiment.stageDatesJson, experiment.stageTargetValuesJson, experiment.stageRestartCountJson
        )
        val checkins = checkinDao.getCheckinsForExperiment(experimentId).first().map { it.toRecord() }
        val today = LocalDate.now()

        // should_end_stage(): evaluate the *current* (pre-advance) stage.
        val (curStart, curEndClipped) = stageDateRange(state, experiment.currentStage, today, experiment.endTime, clipToToday = true)
        val stageDay = if (curStart != null) Days.daysBetween(curStart, today).days else 0
        val curInputs = if (curStart != null) ExperimentDataProvider.getInputs(type, checkins, curStart, curEndClipped!!) else emptyList()
        val curOutputs = if (curStart != null) ExperimentDataProvider.getOutputs(type, checkins, curStart, curEndClipped!!) else emptyList()
        val missedDays = ExperimentEngine.getNumMissedDays(curInputs, curOutputs)
        val curStageTarget = state.stageTargetValues.getOrNull(experiment.currentStage)
        val validDays = ExperimentEngine.getValidDays(curInputs, curOutputs, curStageTarget, type.rangeSize)
        val stable = ExperimentEngine.isOutputStable(experiment.currentStage, curOutputs, type.stableRange)

        val decision = ExperimentEngine.shouldEndStage(experiment.currentStage, stageDay, missedDays, validDays.size, stable)

        var currentStageNum = experiment.currentStage
        var initialStageAverage = experiment.initialStageAverage

        if (decision.restartedStage) {
            state = state.withIncrementedRestartCount(currentStageNum)
            state = state.withStageDates(currentStageNum, today, today.plusDays(7))
        }

        if (decision.shouldEnd) {
            if (currentStageNum == 0) {
                // end_stage(): stage 0 -> compute baseline average/targets with always_get_median (no variability).
                val (baseStart, baseEnd) = stageDateRange(state, 0, today, experiment.endTime, clipToToday = true)
                val baselineInputs = ExperimentDataProvider.getInputs(type, checkins, baseStart!!, baseEnd!!)
                val stageTargets = ExperimentEngine.setStageTargets(baselineInputs, useVariability = false, type.ranges, type.rangeSize)
                state = state.withStageTargetValues(stageTargets.stageTargetValues)
                initialStageAverage = stageTargets.initialStageAverage
            }
            currentStageNum += 1
            if (currentStageNum <= ExperimentEngine.NUM_STAGES) {
                state = state.withStageDates(currentStageNum, today, today.plusDays(7))
            }
        }

        // Recompute stage_inputs/target for the (possibly just-advanced) current stage.
        val (newStart, newEndRaw) = stageDateRange(state, currentStageNum, today, experiment.endTime ?: if (currentStageNum > ExperimentEngine.NUM_STAGES) DateTime.now() else null, clipToToday = true)
        val newStageInputs = if (newStart != null) ExperimentDataProvider.getInputs(type, checkins, newStart, newEndRaw!!) else emptyList()
        val dayInStage = newStageInputs.size - 1
        val newStageTarget = state.stageTargetValues.getOrNull(currentStageNum)
        val dailyTarget = ExperimentEngine.getDailyTarget(type.useVariability, newStageTarget, initialStageAverage ?: 0f, dayInStage)

        var isComplete = false
        var resultValue = experiment.resultValue ?: 0f
        var resultConfidence = experiment.resultConfidence ?: 0f
        var endTime = experiment.endTime

        if (currentStageNum > ExperimentEngine.NUM_STAGES) {
            endTime = DateTime.now()
            val validDaysByStage = (1..ExperimentEngine.NUM_STAGES).associateWith { stage ->
                val (s, e) = stageDateRange(state, stage, today, endTime, clipToToday = true)
                val ins = ExperimentDataProvider.getInputs(type, checkins, s!!, e!!)
                val outs = ExperimentDataProvider.getOutputs(type, checkins, s, e)
                ExperimentEngine.getValidDays(ins, outs, state.stageTargetValues[stage], type.rangeSize)
            }
            val targetsMap = (1..ExperimentEngine.NUM_STAGES).associateWith { state.stageTargetValues[it]!! }
            val results = ExperimentEngine.calculateResults(type.shouldMinimizeResult, targetsMap, validDaysByStage)
            isComplete = true
            resultValue = results.resultValue
            resultConfidence = results.resultConfidence
        }

        val (datesJson, targetsJson, restartJson) = state.toJson()
        experimentDao.update(
            experiment.copy(
                currentStage = currentStageNum,
                isActive = currentStageNum <= ExperimentEngine.NUM_STAGES,
                endTime = endTime,
                resultValue = resultValue,
                resultConfidence = resultConfidence,
                initialStageAverage = initialStageAverage,
                stageDatesJson = datesJson,
                stageTargetValuesJson = targetsJson,
                stageRestartCountJson = restartJson
            )
        )

        return CheckinOutcome(
            newStage = decision.shouldEnd,
            endedEarly = decision.endedEarly,
            restartedStage = decision.restartedStage,
            currentStage = currentStageNum,
            target = dailyTarget,
            day = dayInStage,
            isComplete = isComplete,
            resultValue = resultValue,
            resultConfidence = resultConfidence
        )
    }

    /** Port of `Experiment.get_stage_dates(stage, today=...)`, including the past-the-end fallback. */
    private fun stageDateRange(
        state: ExperimentStageState,
        stage: Int,
        today: LocalDate,
        endTime: DateTime?,
        clipToToday: Boolean
    ): Pair<LocalDate?, LocalDate?> {
        if (stage >= state.stageDates.size) {
            return if (endTime != null) {
                val now = LocalDate(endTime)
                now to now
            } else {
                null to null
            }
        }
        val (start, endRaw) = state.stageDates[stage]
        if (start == null) return null to null
        val end = if (clipToToday) (if (endRaw != null && endRaw.isBefore(today)) endRaw else today) else endRaw
        return start to end
    }

    private fun CheckinEntity.toRecord() = CheckinRecord(
        time = checkinDate,
        leisureTime = leisureTime.toFloat(),
        happiness = happiness.toFloat(),
        stress = stress.toFloat(),
        productivity = productivity.toFloat()
    )
}
