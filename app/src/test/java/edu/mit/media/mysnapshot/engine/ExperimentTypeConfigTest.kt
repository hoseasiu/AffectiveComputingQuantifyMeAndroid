package edu.mit.media.mysnapshot.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 5.4 proof that the bundled `assets/experiment_types.json` config parses into the
 * same four experiment definitions/format strings the old hardcoded sealed-class
 * `ExperimentType` produced (see the deleted class's `formatInstruction`/`formatTarget`/
 * `formatResult` overrides for the values these assertions were derived from), plus the
 * `exercisestress` signal added in #29 and the `stepshappiness`/`leisureproductivity`
 * recombinations added in #28.
 */
class ExperimentTypeConfigTest {

    private val types: List<ExperimentType> by lazy {
        ExperimentTypeRegistry.parseConfig(readBundledExperimentTypesJson())
    }

    private fun type(key: String) = types.first { it.typeKey == key }

    @Test
    fun parsesAllBundledTypes() {
        assertEquals(
            setOf(
                "leisurehappiness",
                "sleepvariabilitystress",
                "sleepdurationproductivity",
                "stepssleepefficiency",
                "exercisestress",
                "stepshappiness",
                "leisureproductivity"
            ),
            types.map { it.typeKey }.toSet()
        )
    }

    @Test
    fun leisureHappiness_formatsMatchOriginal() {
        val t = type("leisurehappiness")
        assertEquals(false, t.useVariability)
        assertEquals(false, t.shouldMinimizeResult)
        assertEquals(SignalSource.CHECKIN_LEISURE_TIME, t.inputSignal)
        assertEquals(SignalSource.CHECKIN_HAPPINESS, t.outputSignal)
        assertEquals("Try to take 1.5 hours of leisure time today", t.formatInstruction(90f))
        assertEquals("Try to get around 1.5 hours of leisure time each day", t.formatResult(90f))
        assertEquals("1.5 Hours", t.formatTarget(90f))
    }

    @Test
    fun sleepVariabilityStress_targetUsesTimeOfDay_resultUsesRawMinutes() {
        val t = type("sleepvariabilitystress")
        assertEquals(true, t.useVariability)
        assertEquals(true, t.shouldMinimizeResult)
        assertEquals(SignalSource.HEALTH_CONNECT_SLEEP_START_MINUTE, t.inputSignal)
        assertEquals(SignalSource.CHECKIN_STRESS, t.outputSignal)
        // 22:30 -> minute-of-day 1350
        assertEquals("10:30PM", t.formatTarget(1350f))
        assertEquals("Try to go to sleep at 10:30PM today", t.formatInstruction(1350f))
        assertEquals("Try to go to sleep within 45 minutes each day", t.formatResult(45f))
    }

    @Test
    fun sleepDurationProductivity_formatsMatchOriginal() {
        val t = type("sleepdurationproductivity")
        assertEquals(SignalSource.HEALTH_CONNECT_SLEEP_DURATION_MINUTES, t.inputSignal)
        assertEquals(SignalSource.CHECKIN_PRODUCTIVITY, t.outputSignal)
        assertEquals("Try to sleep 7.5 hours tonight", t.formatInstruction(450f))
        assertEquals("Try to sleep 7.5 hours each night", t.formatResult(450f))
        assertEquals("7.5 Hours", t.formatTarget(450f))
    }

    @Test
    fun stepsSleepEfficiency_formatsMatchOriginal() {
        val t = type("stepssleepefficiency")
        assertEquals(SignalSource.HEALTH_CONNECT_STEPS, t.inputSignal)
        assertEquals(SignalSource.HEALTH_CONNECT_SLEEP_EFFICIENCY, t.outputSignal)
        assertEquals("Try to walk 8000 steps today", t.formatInstruction(8000f))
        assertEquals("Try to walk 8000 steps every day", t.formatResult(8000f))
        assertEquals("8000 Steps", t.formatTarget(8000f))
    }

    @Test
    fun exerciseStress_formatsMatchConfig() {
        val t = type("exercisestress")
        assertEquals(false, t.useVariability)
        assertEquals(true, t.shouldMinimizeResult)
        assertEquals(SignalSource.HEALTH_CONNECT_EXERCISE_MINUTES, t.inputSignal)
        assertEquals(SignalSource.CHECKIN_STRESS, t.outputSignal)
        assertEquals("Try to exercise 20 minutes today", t.formatInstruction(20f))
        assertEquals("Try to exercise 20 minutes each day", t.formatResult(20f))
        assertEquals("20 Minutes", t.formatTarget(20f))
    }

    @Test
    fun stepsHappiness_formatsMatchStepsSleepEfficiencyShape() {
        val t = type("stepshappiness")
        assertEquals(false, t.useVariability)
        assertEquals(false, t.shouldMinimizeResult)
        assertEquals(SignalSource.HEALTH_CONNECT_STEPS, t.inputSignal)
        assertEquals(SignalSource.CHECKIN_HAPPINESS, t.outputSignal)
        assertEquals("Try to walk 8000 steps today", t.formatInstruction(8000f))
        assertEquals("Try to walk 8000 steps every day", t.formatResult(8000f))
        assertEquals("8000 Steps", t.formatTarget(8000f))
    }

    @Test
    fun leisureProductivity_formatsMatchLeisureHappinessShape() {
        val t = type("leisureproductivity")
        assertEquals(false, t.useVariability)
        assertEquals(false, t.shouldMinimizeResult)
        assertEquals(SignalSource.CHECKIN_LEISURE_TIME, t.inputSignal)
        assertEquals(SignalSource.CHECKIN_PRODUCTIVITY, t.outputSignal)
        assertEquals("Try to take 1.5 hours of leisure time today", t.formatInstruction(90f))
        assertEquals("Try to get around 1.5 hours of leisure time each day", t.formatResult(90f))
        assertEquals("1.5 Hours", t.formatTarget(90f))
    }

    @Test
    fun timeOfDay_wrapsAroundMidnightInBothDirections() {
        assertEquals("12:00AM", FormatKind.TIME_OF_DAY.render(0f))
        assertEquals("12:00AM", FormatKind.TIME_OF_DAY.render(24 * 60f))
        assertEquals("11:59PM", FormatKind.TIME_OF_DAY.render(-1f))
    }
}
