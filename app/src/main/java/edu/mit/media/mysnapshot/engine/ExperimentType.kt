package edu.mit.media.mysnapshot.engine

import edu.mit.media.mysnapshot.R
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

sealed class ExperimentType(
    val typeKey: String,
    val name: String,
    val introLayout: Int,
    val iconId: Int,
    val ranges: RangeTable,
    val rangeSize: Float,
    val stableRange: Float,
    val useVariability: Boolean,
    val shouldMinimizeResult: Boolean,
    val usesSleepData: Boolean = false
) {
    abstract fun formatInstruction(value: Float): String
    abstract fun formatTarget(target: Float): String
    abstract fun formatResult(value: Float): String

    object LeisureHappiness : ExperimentType(
        typeKey = "leisurehappiness",
        name = "How does my leisure time affect my happiness?",
        introLayout = R.layout.experiment_intro_leisurehappy,
        iconId = R.drawable.icon_experiment_leisurehappiness,
        ranges = RangeTable(under = 15f, n1 = 30f, n2 = 60f, n3 = 90f, over = 105f),
        rangeSize = 15f,
        stableRange = 3f,
        useVariability = false,
        shouldMinimizeResult = false
    ) {
        override fun formatResult(value: Float): String {
            val df = DecimalFormat("#.##")
            return "Try to get around ${df.format(value / 60f)} hours of leisure time each day"
        }

        override fun formatInstruction(value: Float): String {
            val df = DecimalFormat("#.##")
            return "Try to take ${df.format(value / 60f)} hours of leisure time today"
        }

        override fun formatTarget(target: Float): String {
            val df = DecimalFormat("#.##")
            return "${df.format(target / 60f)} Hours"
        }
    }

    object SleepVariabilityStress : ExperimentType(
        typeKey = "sleepvariabilitystress",
        name = "How do inconsistent bedtimes affect my stress level?",
        introLayout = R.layout.experiment_intro_sleepvariabilitystress,
        iconId = R.drawable.icon_experiment_sleepdurationproductivity,
        ranges = RangeTable(under = 15f, n1 = 30f, n2 = 60f, n3 = 90f, over = 105f),
        rangeSize = 15f,
        stableRange = 3f,
        useVariability = true,
        shouldMinimizeResult = true,
        usesSleepData = true
    ) {
        override fun formatResult(value: Float): String {
            val df = DecimalFormat("#.##")
            return "Try to go to sleep within ${df.format(value)} minutes each day"
        }

        override fun formatInstruction(value: Float): String {
            return "Try to go to sleep at ${formatTarget(value)} today"
        }

        override fun formatTarget(target: Float): String {
            var time = target
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
    }

    object SleepDurationProductivity : ExperimentType(
        typeKey = "sleepdurationproductivity",
        name = "How does my nightly sleep affect my productivity?",
        introLayout = R.layout.experiment_intro_sleepdurationproductivity,
        iconId = R.drawable.icon_experiment_sleepvariabilitystress,
        ranges = RangeTable(under = 6 * 60f, n1 = 6.5f * 60f, n2 = 7.5f * 60f, n3 = 8.5f * 60f, over = 9 * 60f),
        rangeSize = 30f,
        stableRange = 3f,
        useVariability = false,
        shouldMinimizeResult = false,
        usesSleepData = true
    ) {
        override fun formatInstruction(value: Float): String {
            val df = DecimalFormat("#.##")
            return "Try to sleep ${df.format(value / 60)} hours tonight"
        }

        override fun formatResult(value: Float): String {
            val df = DecimalFormat("#.##")
            return "Try to sleep ${df.format(value / 60)} hours each night"
        }

        override fun formatTarget(target: Float): String {
            val df = DecimalFormat("#.##")
            return "${df.format(target / 60)} Hours"
        }
    }

    object StepsSleepEfficiency : ExperimentType(
        typeKey = "stepssleepefficiency",
        name = "How does my activity level affect my sleep efficiency?",
        introLayout = R.layout.experiment_intro_stepssleepefficiency,
        iconId = R.drawable.icon_experiment_stepssleepefficiency,
        ranges = RangeTable(under = 6500f, n1 = 8000f, n2 = 11000f, n3 = 14000f, over = 15500f),
        rangeSize = 1500f,
        stableRange = 0.1f,
        useVariability = false,
        shouldMinimizeResult = false,
        usesSleepData = true
    ) {
        override fun formatResult(value: Float): String {
            val df = DecimalFormat("#.##")
            return "Try to walk ${df.format(value)} steps every day"
        }

        override fun formatInstruction(value: Float): String {
            val df = DecimalFormat("#.##")
            return "Try to walk ${df.format(value)} steps today"
        }

        override fun formatTarget(target: Float): String {
            val df = DecimalFormat("#.##")
            return "${df.format(target)} Steps"
        }
    }

    companion object {
        fun fromTypeKey(typeKey: String): ExperimentType {
            return when (typeKey) {
                "leisurehappiness" -> LeisureHappiness
                "sleepvariabilitystress" -> SleepVariabilityStress
                "sleepdurationproductivity" -> SleepDurationProductivity
                "stepssleepefficiency" -> StepsSleepEfficiency
                else -> LeisureHappiness
            }
        }

        fun getAllTypes(): List<ExperimentType> {
            return listOf(
                LeisureHappiness,
                SleepVariabilityStress,
                SleepDurationProductivity,
                StepsSleepEfficiency
            )
        }
    }
}
