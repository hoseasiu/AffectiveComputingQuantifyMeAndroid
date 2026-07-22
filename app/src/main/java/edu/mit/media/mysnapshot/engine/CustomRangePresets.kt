package edu.mit.media.mysnapshot.engine

/**
 * The [RangeTable] plus its two tuning knobs ([rangeSize], [stableRange]) that together drive
 * [ExperimentEngine.setStageTargets]/[ExperimentEngine.isOutputStable] for one experiment type
 * -- bundled so [CustomRangePresets.presetFor] can hand a "create your own experiment" wizard
 * (issue #33) one coherent default instead of three independently-guessed numbers.
 */
data class CustomRangePreset(
    val ranges: RangeTable,
    val rangeSize: Float,
    val stableRange: Float
)

/**
 * Derives a reasonable default [CustomRangePreset] from a [CustomValueKind], so a user
 * authoring a brand new experiment type (issue #33) doesn't have to hand-tune the
 * stage-bucketing algorithm's raw numbers just to get started -- the wizard's "Advanced"
 * section still lets them override any of these.
 *
 * [CustomValueKind.DURATION_MINUTES] and [CustomValueKind.COUNT] reuse the exact bundled
 * values of `leisurehappiness` and `stepssleepefficiency` respectively (see
 * `assets/experiment_types.json`) -- both are already-tuned, shipped configs for signals of
 * that same shape (a duration in minutes; a daily count). [CustomValueKind.SCALE_1_7] has no
 * existing bundled analogue (every builtin scale signal is used only as an *output*, never as
 * the input the RangeTable buckets), so its preset is new: bucket boundaries 1..5 leave a
 * one-point buffer at each end of the native 0-6 index range RadioScaleStep answers use,
 * with a rangeSize/stableRange of 1 (one scale point) -- the smallest step that still
 * distinguishes adjacent targets on a 7-point scale.
 */
object CustomRangePresets {
    fun presetFor(kind: CustomValueKind): CustomRangePreset = when (kind) {
        CustomValueKind.DURATION_MINUTES -> CustomRangePreset(
            ranges = RangeTable(under = 15f, n1 = 30f, n2 = 60f, n3 = 90f, over = 105f),
            rangeSize = 15f,
            stableRange = 3f
        )

        CustomValueKind.COUNT -> CustomRangePreset(
            ranges = RangeTable(under = 6500f, n1 = 8000f, n2 = 11000f, n3 = 14000f, over = 15500f),
            rangeSize = 1500f,
            stableRange = 0.1f
        )

        CustomValueKind.SCALE_1_7 -> CustomRangePreset(
            ranges = RangeTable(under = 1f, n1 = 2f, n2 = 3f, n3 = 4f, over = 5f),
            rangeSize = 1f,
            stableRange = 1f
        )
    }
}
