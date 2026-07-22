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
    HEALTH_CONNECT_SLEEP_EFFICIENCY,
    HEALTH_CONNECT_EXERCISE_MINUTES
}

/** How a [CustomSignalDef]'s answer is collected and stored (issue #31/#32). */
enum class CustomValueKind {
    /** A 1-7 Likert-style scale, matching the built-in checkin signals' native range. */
    SCALE_1_7,
    /** A non-negative integer count (e.g. "cups of coffee"). */
    COUNT,
    /** A duration entered in minutes. */
    DURATION_MINUTES
}

/**
 * A user-authored signal that doesn't exist in [SignalSource]'s fixed vocabulary -- e.g. a
 * self-invented "cups of coffee" question. Answers are collected daily at checkin time (#32)
 * and stored in [edu.mit.media.mysnapshot.database.CheckinEntity]'s `customInputValue`/
 * `customOutputValue` columns, always in the type's native unit (same convention as every
 * built-in signal).
 */
data class CustomSignalDef(
    val label: String,
    val question: String,
    val kind: CustomValueKind,
    val lowLabel: String? = null,
    val highLabel: String? = null,
    val unitLabel: String? = null
)

/**
 * What feeds one of an [ExperimentType]'s two signal slots (input/output): either one of the
 * app's built-in [SignalSource]s, or a user-defined [CustomSignalDef] (issue #31). Every
 * experiment type has exactly two signal slots and never more, so this -- rather than a
 * generic key-value schema -- is enough to support fully custom, user-defined signals.
 */
sealed class SignalRef {
    data class Builtin(val source: SignalSource) : SignalRef()
    data class Custom(val definition: CustomSignalDef) : SignalRef()
}

/**
 * A short human-readable description of what this signal measures -- used by
 * `ExperimentIntroActivity`'s generic (no-dedicated-resources) intro screen to describe a
 * type's input/output at runtime instead of the baked-in copy a per-type XML would have.
 */
fun SignalRef.describe(): String = when (this) {
    is SignalRef.Custom -> definition.question
    is SignalRef.Builtin -> when (source) {
        SignalSource.CHECKIN_LEISURE_TIME -> "your leisure time"
        SignalSource.CHECKIN_HAPPINESS -> "your happiness"
        SignalSource.CHECKIN_STRESS -> "your stress level"
        SignalSource.CHECKIN_PRODUCTIVITY -> "your productivity"
        SignalSource.HEALTH_CONNECT_SLEEP_START_MINUTE -> "when you go to sleep"
        SignalSource.HEALTH_CONNECT_SLEEP_DURATION_MINUTES -> "how long you sleep"
        SignalSource.HEALTH_CONNECT_STEPS -> "your daily steps"
        SignalSource.HEALTH_CONNECT_SLEEP_EFFICIENCY -> "your sleep efficiency"
        SignalSource.HEALTH_CONNECT_EXERCISE_MINUTES -> "your exercise minutes"
    }
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
    val inputSignal: SignalRef,
    val outputSignal: SignalRef,
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
