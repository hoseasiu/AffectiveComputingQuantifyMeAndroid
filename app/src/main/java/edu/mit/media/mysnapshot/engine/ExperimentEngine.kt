package edu.mit.media.mysnapshot.engine

/**
 * Faithful port of the adaptive stage/target/confidence algorithm that used to live
 * server-side in AffectiveComputingQuantifyMeDjango/src/app/models.py (class `Experiment`)
 * and analysis.py. Ported via app/src/test/.../backend/ExperimentEngineReference.java,
 * the Phase 0 oracle validated against the Django source directly.
 *
 * These are pure functions over plain data; Room-backed state (stage dates, target
 * values, restart counts) lives in [edu.mit.media.mysnapshot.data.ExperimentRepository].
 */
object ExperimentEngine {

    const val NUM_STAGES = 3

    data class StageTargets(
        // index 0 = baseline stage (unused directly), 1..NUM_STAGES = per-stage targets
        val stageTargetValues: List<Float>,
        val initialStageAverage: Float
    )

    /** Port of `Experiment.set_stage_targets`. */
    fun setStageTargets(
        initialStageInputsRaw: List<Float?>,
        useVariability: Boolean,
        ranges: RangeTable,
        rangeSize: Float
    ): StageTargets {
        val initialStageInputs = initialStageInputsRaw.filterNotNull()
        val average = mean(initialStageInputs)
        val min = initialStageInputs.min()
        val max = initialStageInputs.max()
        val variability = max - min

        val targetValue = if (useVariability) variability else average

        val targetKeys: List<Float> = when {
            targetValue <= ranges.under -> listOf(ranges.under, ranges.n1, ranges.n3, ranges.n2)
            targetValue <= ranges.n1 + rangeSize -> listOf(ranges.n1, ranges.n3, ranges.n1, ranges.n2)
            targetValue <= ranges.n2 + rangeSize -> listOf(ranges.n2, ranges.n3, ranges.n1, ranges.n2)
            targetValue <= ranges.n3 + rangeSize -> listOf(ranges.n3, ranges.n1, ranges.n3, ranges.n2)
            else -> listOf(ranges.over, ranges.n3, ranges.n1, ranges.n2)
        }

        return StageTargets(targetKeys, average)
    }

    private fun mean(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        return values.sum() / values.size
    }

    /**
     * Port of `Experiment.get_daily_target`. [stageTarget] may be null (no target set
     * for this stage yet).
     */
    fun getDailyTarget(
        useVariability: Boolean,
        stageTarget: Float?,
        initialStageAverage: Float,
        dayInStage: Int
    ): Float? {
        if (!useVariability || stageTarget == null) {
            return stageTarget
        }
        // Python's `%` is floor-based (e.g. -1 % 2 == 1); Kotlin's `%` is truncating
        // (-1 % 2 == -1). dayInStage can go negative right when a new stage starts the
        // same day an old one ends (see ExperimentRepository), so match Python exactly.
        return if (Math.floorMod(dayInStage, 2) != 0) {
            initialStageAverage + stageTarget
        } else {
            initialStageAverage - stageTarget
        }
    }

    /**
     * Port of `Experiment.is_output_stable`. [outputs] may contain nulls (missed
     * check-ins); only the last 5 non-null values are considered.
     */
    fun isOutputStable(currentStage: Int, outputs: List<Float?>, stableRange: Float): Boolean {
        if (currentStage == 0) {
            return false
        }
        val relevant = outputs.filterNotNull().takeLast(5)
        if (relevant.isEmpty()) {
            return false
        }
        return (relevant.max() - relevant.min()) <= stableRange
    }

    data class DayPair(val input: Float, val output: Float)

    /** Port of `Experiment.get_valid_days`. */
    fun getValidDays(
        inputs: List<Float?>,
        outputs: List<Float?>,
        target: Float?,
        rangeSize: Float
    ): List<DayPair> {
        val n = minOf(inputs.size, outputs.size)
        val result = mutableListOf<DayPair>()
        for (i in 0 until n) {
            val input = inputs[i]
            val output = outputs[i]
            if (input == null || output == null) continue
            if (target == null || (target - rangeSize <= input && input <= target + rangeSize)) {
                result.add(DayPair(input, output))
            }
        }
        return result
    }

    /** Port of `Experiment.get_num_missed_days`. */
    fun getNumMissedDays(inputs: List<Float?>, outputs: List<Float?>): Int {
        val n = minOf(inputs.size, outputs.size)
        var missed = 0
        for (i in 0 until n) {
            if (inputs[i] == null || outputs[i] == null) missed++
        }
        return missed
    }

    data class StageEndDecision(
        val shouldEnd: Boolean,
        val endedEarly: Boolean,
        val restartedStage: Boolean
    )

    /**
     * Port of `Experiment.should_end_stage`. [stageDay] is the pre-computed
     * `(today - stage_start).days` value from the Python version.
     */
    fun shouldEndStage(
        currentStage: Int,
        stageDay: Int,
        missedDays: Int,
        validDaysCount: Int,
        isOutputStable: Boolean
    ): StageEndDecision {
        if ((currentStage > 0 && missedDays >= 2) || (currentStage == 0 && missedDays > 2)) {
            return StageEndDecision(shouldEnd = false, endedEarly = true, restartedStage = true)
        }

        if (currentStage > 0) {
            if (validDaysCount >= 5 && isOutputStable) {
                return StageEndDecision(shouldEnd = true, endedEarly = true, restartedStage = false)
            }

            if (stageDay >= 4) {
                val daysLeft = 7 - stageDay
                val possibleValidDays = validDaysCount + daysLeft
                if (possibleValidDays < 4) {
                    return StageEndDecision(shouldEnd = false, endedEarly = true, restartedStage = true)
                }
            }
        }

        if (stageDay == 7) {
            return StageEndDecision(shouldEnd = true, endedEarly = false, restartedStage = false)
        }

        return StageEndDecision(shouldEnd = false, endedEarly = false, restartedStage = false)
    }

    data class StageResult(
        val stage: Int,
        val target: Float,
        val meanOutput: Float,
        val minOutput: Float,
        val maxOutput: Float,
        val outputs: List<Float>
    )

    data class Results(
        val resultValue: Float,
        val resultConfidence: Float,
        val bestStage: Int,
        val stageResults: Map<Int, StageResult>
    )

    /**
     * Port of `Experiment.calculate_results`. [targets] and [validDaysByStage] are
     * keyed by stage number (1..NUM_STAGES); stage 0 (baseline) is excluded, matching
     * the Python version.
     */
    fun calculateResults(
        wantMinimizedResults: Boolean,
        targets: Map<Int, Float>,
        validDaysByStage: Map<Int, List<DayPair>>
    ): Results {
        val stageResults = mutableMapOf<Int, StageResult>()
        var bestStage = 0
        var bestOutput = if (wantMinimizedResults) 500000f else -500000f

        for (stage in 1..NUM_STAGES) {
            val validDays = validDaysByStage.getValue(stage)
            val outputs = validDays.map { it.output }
            val target = targets.getValue(stage)
            val meanOutput = outputs.sum() / outputs.size
            val minOutput = outputs.min()
            val maxOutput = outputs.max()

            if ((meanOutput > bestOutput && !wantMinimizedResults) || (wantMinimizedResults && meanOutput < bestOutput)) {
                bestOutput = meanOutput
                bestStage = stage
            }

            stageResults[stage] = StageResult(stage, target, meanOutput, minOutput, maxOutput, outputs)
        }

        var maxOverlap = if (wantMinimizedResults) 50000f else -50000f
        for (stage in 1..NUM_STAGES) {
            if (stage == bestStage) continue
            val sr = stageResults.getValue(stage)
            val best = stageResults.getValue(bestStage)
            if (wantMinimizedResults) {
                val count = sr.outputs.count { it <= best.maxOutput }
                val overlap = count.toFloat() / sr.outputs.size
                maxOverlap = minOf(maxOverlap, overlap)
            } else {
                val count = sr.outputs.count { it >= best.minOutput }
                val overlap = count.toFloat() / sr.outputs.size
                maxOverlap = maxOf(maxOverlap, overlap)
            }
        }

        var confidence = 1.0f - maxOverlap
        confidence = Math.round(confidence * 100f) / 100f

        val resultValue = stageResults.getValue(bestStage).target
        val resultConfidence = minOf(confidence, 0.9f)

        return Results(resultValue, resultConfidence, bestStage, stageResults)
    }
}
