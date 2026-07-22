package edu.mit.media.mysnapshot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [CustomRangePresets.presetFor] (issue #33): the DURATION_MINUTES/COUNT presets
 * must match the bundled `leisurehappiness`/`stepssleepefficiency` configs exactly (see
 * `ExperimentTypeConfigTest`'s equivalent assertions), and every preset's bucket boundaries
 * must be strictly ascending, since [ExperimentEngine.setStageTargets] relies on that ordering.
 */
class CustomRangePresetsTest {

    @Test
    fun durationMinutes_matchesLeisureHappinessBundledConfig() {
        val preset = CustomRangePresets.presetFor(CustomValueKind.DURATION_MINUTES)

        assertEquals(RangeTable(under = 15f, n1 = 30f, n2 = 60f, n3 = 90f, over = 105f), preset.ranges)
        assertEquals(15f, preset.rangeSize)
        assertEquals(3f, preset.stableRange)
    }

    @Test
    fun count_matchesStepsSleepEfficiencyBundledConfig() {
        val preset = CustomRangePresets.presetFor(CustomValueKind.COUNT)

        assertEquals(RangeTable(under = 6500f, n1 = 8000f, n2 = 11000f, n3 = 14000f, over = 15500f), preset.ranges)
        assertEquals(1500f, preset.rangeSize)
        assertEquals(0.1f, preset.stableRange)
    }

    @Test
    fun scale1To7_boundariesFitWithinNativeZeroToSixIndexRange() {
        val preset = CustomRangePresets.presetFor(CustomValueKind.SCALE_1_7)

        assertTrue(preset.ranges.under >= 0f)
        assertTrue(preset.ranges.over <= 6f)
        assertEquals(1f, preset.rangeSize)
        assertEquals(1f, preset.stableRange)
    }

    @Test
    fun everyPreset_hasStrictlyAscendingBucketBoundaries() {
        for (kind in CustomValueKind.entries) {
            val ranges = CustomRangePresets.presetFor(kind).ranges
            assertTrue(
                "$kind's ranges must be strictly ascending for setStageTargets' bucketing to make sense",
                ranges.under < ranges.n1 && ranges.n1 < ranges.n2 && ranges.n2 < ranges.n3 && ranges.n3 < ranges.over
            )
        }
    }
}
