package edu.mit.media.mysnapshot.data

import edu.mit.media.mysnapshot.database.CheckinEntity
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.CustomSignalDef
import edu.mit.media.mysnapshot.engine.CustomValueKind
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.engine.ExperimentTypeRegistry
import edu.mit.media.mysnapshot.engine.FormatKind
import edu.mit.media.mysnapshot.engine.RangeTable
import edu.mit.media.mysnapshot.engine.SignalRef
import edu.mit.media.mysnapshot.engine.readBundledExperimentTypesJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Proof for the "Optional: local data export" feature (AGENT_PLANS/MODERNIZE.md): the
 * exported JSON carries the experiment's question/result/check-ins in human-readable form,
 * and the suggested filename is stable/identifying -- nothing here touches the filesystem
 * or Android (that glue lives in HistoryActivity, not plain-JUnit testable without
 * Robolectric).
 */
class ExperimentExporterTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadRegistry() {
            ExperimentTypeRegistry.loadForTest(readBundledExperimentTypesJson())
        }
    }

    private fun experiment(
        type: String = "leisurehappiness",
        startTime: OffsetDateTime = LocalDate.parse("2026-01-01").atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
        endTime: OffsetDateTime? = LocalDate.parse("2026-01-29").atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
        resultValue: Float? = 90f,
        resultConfidence: Float? = 0.8f
    ) = ExperimentEntity(
        id = 1,
        type = type,
        startTime = startTime,
        endTime = endTime,
        currentStage = 5,
        isActive = endTime == null,
        isCancelled = false,
        selfEfficacy = 3,
        appEfficacy = 3,
        experimentEfficacy = 3,
        resultValue = resultValue,
        resultConfidence = resultConfidence,
        stageDatesJson = "[]",
        stageTargetValuesJson = "[]",
        stageRestartCountJson = "[]"
    )

    @Test
    fun buildExportJson_includesQuestionResultAndCheckins() {
        val checkins = listOf(
            CheckinEntity(
                id = 1,
                experimentId = 1,
                checkinDate = LocalDate.parse("2026-01-02").atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
                didFollowInstructions = 1,
                happiness = 7,
                stress = 3,
                productivity = 5,
                leisureTime = 90
            )
        )

        val json = ExperimentExporter.buildExportJson(experiment(), checkins)

        assertTrue(json.contains("\"experimentType\": \"leisurehappiness\""))
        assertTrue(json.contains("How does my leisure time affect my happiness?"))
        assertTrue(json.contains("Try to get around 1.5 hours of leisure time each day"))
        assertTrue(json.contains("\"happiness\": 7"))
        assertTrue(json.contains("\"leisureTime\": 90"))
    }

    @Test
    fun buildExportJson_activeExperimentHasNoResult() {
        val json = ExperimentExporter.buildExportJson(
            experiment(endTime = null, resultValue = null, resultConfidence = null),
            emptyList()
        )

        assertTrue(json.contains("\"isActive\": true"))
        assertTrue(json.contains("\"resultValue\": null"))
        assertTrue(json.contains("\"resultDescription\": null"))
    }

    @Test
    fun suggestedFileName_includesTypeAndStartDate() {
        val name = ExperimentExporter.suggestedFileName(
            experiment(type = "stepssleepefficiency", startTime = LocalDate.parse("2026-03-05").atStartOfDay(ZoneOffset.UTC).toOffsetDateTime())
        )
        assertEquals("quantifyme_stepssleepefficiency_2026-03-05.json", name)
    }

    @After
    fun clearCustomTypes() {
        ExperimentTypeRegistry.refreshCustomTypes(emptyList())
    }

    @Test
    fun buildExportJson_includesCustomSignalQuestionsAndCheckinValues() {
        val customType = ExperimentType(
            typeKey = "custom_coffee_focus",
            name = "How does my coffee intake affect my focus?",
            ranges = RangeTable(under = 0f, n1 = 1f, n2 = 2f, n3 = 3f, over = 4f),
            rangeSize = 1f,
            stableRange = 1f,
            useVariability = false,
            shouldMinimizeResult = false,
            usesSleepData = false,
            inputSignal = SignalRef.Custom(
                CustomSignalDef(
                    label = "Coffee",
                    question = "How many cups of coffee did you drink today?",
                    kind = CustomValueKind.COUNT
                )
            ),
            outputSignal = SignalRef.Custom(
                CustomSignalDef(
                    label = "Focus",
                    question = "How focused did you feel today?",
                    kind = CustomValueKind.SCALE_1_7
                )
            ),
            targetFormatKind = FormatKind.RAW_NUMBER,
            resultFormatKind = FormatKind.RAW_NUMBER,
            instructionTemplate = "Try to drink around {value} cups of coffee",
            resultTemplate = "{value} cups of coffee produced the best focus",
            targetTemplate = "{value} cups"
        )
        ExperimentTypeRegistry.refreshCustomTypes(listOf(customType))

        val checkins = listOf(
            CheckinEntity(
                id = 1,
                experimentId = 1,
                checkinDate = LocalDate.parse("2026-01-02").atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
                didFollowInstructions = 1,
                happiness = 5,
                stress = 3,
                productivity = 5,
                leisureTime = 90,
                customInputValue = 2f,
                customOutputValue = 6f
            )
        )

        val json = ExperimentExporter.buildExportJson(experiment(type = "custom_coffee_focus"), checkins)

        assertTrue(json.contains("\"customInputQuestion\": \"How many cups of coffee did you drink today?\""))
        assertTrue(json.contains("\"customOutputQuestion\": \"How focused did you feel today?\""))
        assertTrue(json.contains("\"customInputValue\": 2.0"))
        assertTrue(json.contains("\"customOutputValue\": 6.0"))
    }

    @Test
    fun buildExportJson_builtinTypeHasNullCustomQuestionsAndValues() {
        val checkins = listOf(
            CheckinEntity(
                id = 1,
                experimentId = 1,
                checkinDate = LocalDate.parse("2026-01-02").atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
                didFollowInstructions = 1,
                happiness = 7,
                stress = 3,
                productivity = 5,
                leisureTime = 90
            )
        )

        val json = ExperimentExporter.buildExportJson(experiment(), checkins)

        assertTrue(json.contains("\"customInputQuestion\": null"))
        assertTrue(json.contains("\"customOutputQuestion\": null"))
        assertTrue(json.contains("\"customInputValue\": null"))
        assertTrue(json.contains("\"customOutputValue\": null"))
    }
}
