package edu.mit.media.mysnapshot.engine

import java.text.DecimalFormat

/**
 * Range table for the stage-target bucketing algorithm, ported from each experiment
 * type's `get_ranges()` in AffectiveComputingQuantifyMeDjango/src/app/analysis.py.
 */
data class RangeTable(
    val under: Float,
    val n1: Float,
    val n2: Float,
    val n3: Float,
    val over: Float
)

/**
 * The fixed vocabulary of per-day signals the app knows how to fetch, from either local
 * check-ins or Health Connect (see [ExperimentDataProvider]). A new experiment type that
 * reuses one of these as its input/output needs no new code -- only a JSON config entry
 * (Phase 5.4). A genuinely new signal source unavoidably needs a new fetch implementation
 * (and a new enum constant here) -- that isn't something a config file can express.
 */
enum class SignalSource {
    CHECKIN_LEISURE_TIME,
    CHECKIN_HAPPINESS,
    CHECKIN_STRESS,
    CHECKIN_PRODUCTIVITY,
    HEALTH_CONNECT_SLEEP_START_MINUTE,
    HEALTH_CONNECT_SLEEP_DURATION_MINUTES,
    HEALTH_CONNECT_STEPS,
    HEALTH_CONNECT_SLEEP_EFFICIENCY
}

/**
 * How a raw engine value (always stored/compared in the type's native unit -- minutes,
 * steps, etc.) is rendered into display text. Ported verbatim from what were previously
 * four hand-written `formatInstruction`/`formatTarget`/`formatResult` overrides on the old
 * sealed-class `ExperimentType` -- the actual rendering logic is genuinely code (unit
 * conversion, clock-time wraparound), but which kind a given type/template uses is now a
 * JSON config choice instead of a new Kotlin subclass.
 */
enum class FormatKind {
    /** Value stored in minutes, displayed in hours (e.g. leisure time, sleep duration). */
    HOURS_FROM_MINUTES {
        override fun render(value: Float): String = DecimalFormat("#.##").format(value / 60f)
    },

    /** Value is a minute-of-day (with wraparound), displayed as a 12-hour clock time. */
    TIME_OF_DAY {
        override fun render(value: Float): String {
            var time = value
            while (time < 0) {
                time += 24 * 60
            }
            time %= 24 * 60
            val hours = (time / 60).toInt()
            val minutes = (time % 60).toInt()
            val ampm = if (hours >= 12) "PM" else "AM"
            val displayHours = if (hours == 0) 12 else if (hours > 12) hours - 12 else hours
            return String.format("%d:%02d%s", displayHours, minutes, ampm)
        }
    },

    /** Value displayed as-is (e.g. steps, or a raw minutes figure). */
    RAW_NUMBER {
        override fun render(value: Float): String = DecimalFormat("#.##").format(value)
    };

    abstract fun render(value: Float): String
}

/**
 * One experiment definition: target zones, the stage-machine tuning knobs, which signals
 * feed the engine, and how values are displayed. Ported from `analysis.py`'s four
 * `ExperimentType` subclasses (Phase 2) and, per Phase 5.4 of the modernization plan,
 * loaded from a bundled JSON config ([ExperimentTypeRegistry]) instead of being one
 * hardcoded Kotlin subclass per type -- adding a type that reuses existing signals/format
 * kinds is now a config + resource-asset change, not a new class. `introLayout`/`iconId`/
 * `chooseBannerIconId` stay resolved from a small compile-time resource map
 * ([ExperimentTypeRegistry]) rather than the JSON itself, since Android resource IDs are
 * aapt-generated compile-time constants -- per the plan, this can't be made truly
 * OTA/dynamic anyway, so there's no benefit to reflecting resource names at runtime.
 */
data class ExperimentType(
    val typeKey: String,
    val name: String,
    val ranges: RangeTable,
    val rangeSize: Float,
    val stableRange: Float,
    val useVariability: Boolean,
    val shouldMinimizeResult: Boolean,
    val usesSleepData: Boolean,
    val inputSignal: SignalSource,
    val outputSignal: SignalSource,
    val targetFormatKind: FormatKind,
    val resultFormatKind: FormatKind,
    val instructionTemplate: String,
    val resultTemplate: String,
    val targetTemplate: String
) {
    val introLayout: Int get() = ExperimentTypeRegistry.introLayoutId(typeKey)
    val iconId: Int get() = ExperimentTypeRegistry.iconId(typeKey)
    val chooseBannerIconId: Int get() = ExperimentTypeRegistry.chooseBannerIconId(typeKey)

    fun formatInstruction(value: Float): String =
        instructionTemplate.replace("{value}", targetFormatKind.render(value))

    fun formatTarget(target: Float): String =
        targetTemplate.replace("{value}", targetFormatKind.render(target))

    fun formatResult(value: Float): String =
        resultTemplate.replace("{value}", resultFormatKind.render(value))

    companion object {
        fun fromTypeKey(typeKey: String): ExperimentType =
            ExperimentTypeRegistry.all.find { it.typeKey == typeKey } ?: ExperimentTypeRegistry.all.first()

        fun getAllTypes(): List<ExperimentType> = ExperimentTypeRegistry.all
    }
}
