package edu.mit.media.mysnapshot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 proof that the Kotlin [ExperimentEngine] port matches the Phase 0 oracle
 * (app/src/test/.../backend/ExperimentEngineCharacterizationTest.java +
 * ExperimentEngineReference.java, hand-derived from the Django source). Same inputs,
 * same expected outputs, against the real Kotlin engine instead of the Java reference.
 */
class ExperimentEngineTest {

    private val sleepDurationRanges = RangeTable(under = 360f, n1 = 390f, n2 = 450f, n3 = 510f, over = 540f)
    private val sleepDurationRangeSize = 30f

    private val sleepVariabilityRanges = RangeTable(under = 15f, n1 = 30f, n2 = 60f, n3 = 90f, over = 105f)
    private val sleepVariabilityRangeSize = 15f

    private fun constant(v: Float, n: Int): List<Float?> = List(n) { v }

    // ---- setStageTargets ----------------------------------------------------------

    @Test
    fun setStageTargets_underRange_targetsUnderN1N3N2() {
        val r = ExperimentEngine.setStageTargets(constant(360f, 7), false, sleepDurationRanges, sleepDurationRangeSize)
        assertEquals(360f, r.initialStageAverage, 1e-6f)
        assertEquals(listOf(360f, 390f, 510f, 450f), r.stageTargetValues)
    }

    @Test
    fun setStageTargets_n1Range_targetsN1N3N1N2() {
        val r = ExperimentEngine.setStageTargets(constant(420f, 7), false, sleepDurationRanges, sleepDurationRangeSize)
        assertEquals(listOf(390f, 510f, 390f, 450f), r.stageTargetValues)
    }

    @Test
    fun setStageTargets_n2Range_targetsN2N3N1N2() {
        val r = ExperimentEngine.setStageTargets(constant(460f, 7), false, sleepDurationRanges, sleepDurationRangeSize)
        assertEquals(listOf(450f, 510f, 390f, 450f), r.stageTargetValues)
    }

    @Test
    fun setStageTargets_n3Range_targetsN3N1N3N2() {
        val r = ExperimentEngine.setStageTargets(constant(525f, 7), false, sleepDurationRanges, sleepDurationRangeSize)
        assertEquals(listOf(510f, 390f, 510f, 450f), r.stageTargetValues)
    }

    @Test
    fun setStageTargets_overRange_targetsOverN3N1N2() {
        val r = ExperimentEngine.setStageTargets(constant(600f, 7), false, sleepDurationRanges, sleepDurationRangeSize)
        assertEquals(listOf(540f, 510f, 390f, 450f), r.stageTargetValues)
    }

    @Test
    fun setStageTargets_ignoresNullInputs() {
        val inputs = listOf(360f, null, 360f, 360f, null, 360f, 360f)
        val r = ExperimentEngine.setStageTargets(inputs, false, sleepDurationRanges, sleepDurationRangeSize)
        assertEquals(360f, r.initialStageAverage, 1e-6f)
        assertEquals(listOf(360f, 390f, 510f, 450f), r.stageTargetValues)
    }

    @Test
    fun setStageTargets_variabilityStudy_usesMaxMinusMinNotMean() {
        val inputs = listOf(0f, 10f, 20f, 30f, 40f, 50f, 60f)
        val r = ExperimentEngine.setStageTargets(inputs, true, sleepVariabilityRanges, sleepVariabilityRangeSize)
        assertEquals(30f, r.initialStageAverage, 1e-6f) // mean(inputs), NOT the variability
        // variability = 60 - 0 = 60 <= N2(60)+15 -> N2 branch
        assertEquals(listOf(60f, 90f, 30f, 60f), r.stageTargetValues)
    }

    // ---- getDailyTarget -------------------------------------------------------------

    @Test
    fun getDailyTarget_nonVariability_returnsStageTargetRegardlessOfParity() {
        assertEquals(390f, ExperimentEngine.getDailyTarget(false, 390f, 999f, 0))
        assertEquals(390f, ExperimentEngine.getDailyTarget(false, 390f, 999f, 1))
    }

    @Test
    fun getDailyTarget_nullStageTarget_returnsNull() {
        assertNull(ExperimentEngine.getDailyTarget(true, null, 30f, 0))
        assertNull(ExperimentEngine.getDailyTarget(false, null, 30f, 1))
    }

    @Test
    fun getDailyTarget_variability_alternatesAroundBaseline() {
        assertEquals(-30f, ExperimentEngine.getDailyTarget(true, 60f, 30f, 0))
        assertEquals(90f, ExperimentEngine.getDailyTarget(true, 60f, 30f, 1))
    }

    // ---- isOutputStable ---------------------------------------------------------------

    @Test
    fun isOutputStable_stageZero_alwaysFalse() {
        assertFalse(ExperimentEngine.isOutputStable(0, constant(3f, 7), 3f))
    }

    @Test
    fun isOutputStable_noNonNullOutputs_false() {
        assertFalse(ExperimentEngine.isOutputStable(1, listOf(null, null, null), 3f))
    }

    @Test
    fun isOutputStable_last5WithinRange_true() {
        assertTrue(ExperimentEngine.isOutputStable(1, constant(3f, 7), 3f))
    }

    @Test
    fun isOutputStable_last5OutsideRange_false() {
        assertFalse(ExperimentEngine.isOutputStable(1, listOf(1f, 10f, 3f, 3f, 3f), 3f))
    }

    // ---- getValidDays -------------------------------------------------------------------

    @Test
    fun getValidDays_withTarget_filtersByRangeAndNulls() {
        val inputs = listOf(300f, 360f, 390f, 420f, null)
        val outputs = listOf(5f, 6f, 7f, null, 8f)
        val valid = ExperimentEngine.getValidDays(inputs, outputs, 390f, 30f)
        assertEquals(2, valid.size)
        assertEquals(360f, valid[0].input, 1e-6f)
        assertEquals(6f, valid[0].output, 1e-6f)
        assertEquals(390f, valid[1].input, 1e-6f)
        assertEquals(7f, valid[1].output, 1e-6f)
    }

    @Test
    fun getValidDays_nullTarget_onlyRequiresBothPresent() {
        val inputs = listOf(300f, 360f, 390f, 420f, null)
        val outputs = listOf(5f, 6f, 7f, null, 8f)
        val valid = ExperimentEngine.getValidDays(inputs, outputs, null, 30f)
        assertEquals(3, valid.size)
    }

    // ---- getNumMissedDays -----------------------------------------------------------------

    @Test
    fun getNumMissedDays_countsEitherSideNull() {
        val inputs = listOf(300f, 360f, null, 420f, null)
        val outputs = listOf(5f, null, 7f, 8f, 9f)
        assertEquals(3, ExperimentEngine.getNumMissedDays(inputs, outputs))
    }

    // ---- shouldEndStage -----------------------------------------------------------------

    @Test
    fun shouldEndStage_baselineStage_tooManyMissedDays_restarts() {
        val d = ExperimentEngine.shouldEndStage(0, 5, 3, 4, false)
        assertFalse(d.shouldEnd)
        assertTrue(d.endedEarly)
        assertTrue(d.restartedStage)
    }

    @Test
    fun shouldEndStage_baselineStage_exactlyTwoMissed_notRestarted_endsOnDay7() {
        val d = ExperimentEngine.shouldEndStage(0, 7, 2, 5, false)
        assertTrue(d.shouldEnd)
        assertFalse(d.endedEarly)
        assertFalse(d.restartedStage)
    }

    @Test
    fun shouldEndStage_laterStage_twoMissedDays_restarts() {
        val d = ExperimentEngine.shouldEndStage(1, 3, 2, 1, false)
        assertFalse(d.shouldEnd)
        assertTrue(d.endedEarly)
        assertTrue(d.restartedStage)
    }

    @Test
    fun shouldEndStage_laterStage_fiveValidDaysAndStable_endsEarly() {
        val d = ExperimentEngine.shouldEndStage(1, 3, 0, 5, true)
        assertTrue(d.shouldEnd)
        assertTrue(d.endedEarly)
        assertFalse(d.restartedStage)
    }

    @Test
    fun shouldEndStage_laterStage_stillPossibleToReachFourValidDays_continues() {
        val d = ExperimentEngine.shouldEndStage(1, 4, 0, 3, false)
        assertFalse(d.shouldEnd)
        assertFalse(d.endedEarly)
        assertFalse(d.restartedStage)
    }

    @Test
    fun shouldEndStage_laterStage_cannotReachFourValidDays_restarts() {
        val d = ExperimentEngine.shouldEndStage(1, 5, 1, 1, false)
        assertFalse(d.shouldEnd)
        assertTrue(d.endedEarly)
        assertTrue(d.restartedStage)
    }

    @Test
    fun shouldEndStage_laterStage_day7NotStable_endsOnTime() {
        val d = ExperimentEngine.shouldEndStage(1, 7, 0, 4, false)
        assertTrue(d.shouldEnd)
        assertFalse(d.endedEarly)
        assertFalse(d.restartedStage)
    }

    // ---- calculateResults -----------------------------------------------------------------

    @Test
    fun calculateResults_maximize_picksHighestMeanStage_andComputesConfidence() {
        val targets = mapOf(1 to 30f, 2 to 60f, 3 to 90f)
        val validDays = mapOf(
            1 to pairs(floatArrayOf(1f, 1f, 1f), floatArrayOf(5f, 5f, 5f)),
            2 to pairs(floatArrayOf(1f, 1f, 1f), floatArrayOf(8f, 8f, 8f)),
            3 to pairs(floatArrayOf(1f, 1f, 1f), floatArrayOf(6f, 7f, 8f))
        )
        val results = ExperimentEngine.calculateResults(false, targets, validDays)
        assertEquals(2, results.bestStage)
        assertEquals(60f, results.resultValue, 1e-6f)
        // stage1 overlap (v>=best.min=8): 0/3=0; stage3 overlap (v>=8): 1/3=0.333 -> max=0.333
        // confidence = 1 - 0.333 = 0.67 (rounded), under the 0.9 cap
        assertEquals(0.67f, results.resultConfidence, 1e-6f)
    }

    @Test
    fun calculateResults_minimize_picksLowestMeanStage_andCapsConfidenceAt0point9() {
        val targets = mapOf(1 to 15f, 2 to 30f, 3 to 60f)
        val validDays = mapOf(
            1 to pairs(floatArrayOf(1f, 1f, 1f), floatArrayOf(9f, 9f, 9f)),
            2 to pairs(floatArrayOf(1f, 1f, 1f), floatArrayOf(3f, 3f, 3f)),
            3 to pairs(floatArrayOf(1f, 1f, 1f), floatArrayOf(5f, 6f, 7f))
        )
        val results = ExperimentEngine.calculateResults(true, targets, validDays)
        assertEquals(2, results.bestStage)
        assertEquals(30f, results.resultValue, 1e-6f)
        // both other stages have zero overlap with the winning stage's max(=3) -> confidence
        // would be 1.0, but the engine caps confidence at 0.9.
        assertEquals(0.9f, results.resultConfidence, 1e-6f)
    }

    private fun pairs(inputs: FloatArray, outputs: FloatArray): List<ExperimentEngine.DayPair> {
        return inputs.indices.map { ExperimentEngine.DayPair(inputs[it], outputs[it]) }
    }
}
