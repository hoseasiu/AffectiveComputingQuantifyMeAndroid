package edu.mit.media.mysnapshot.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import edu.mit.media.mysnapshot.database.CheckinEntity
import edu.mit.media.mysnapshot.database.QuantifyMeDatabase
import edu.mit.media.mysnapshot.engine.ExperimentEngine
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.health.HealthConnectManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Happy-path orchestration coverage for [ExperimentRepository] -- the on-device
 * re-implementation of the old Django `views.py` (`should_end_stage` -> restart/end_stage
 * side effects -> recompute today's target -> `calculate_results` once complete). Per
 * AGENT_PLANS/IMPROVEMENTS.md item 5, this is called out as "the riskiest glue in the whole
 * port" with zero prior tests.
 *
 * IMPORTANT / merge-risk note: this file is intentionally scoped to
 * [ExperimentType.LeisureHappiness] only, whose `getInputs`/`getOutputs` are 100%
 * checkin-backed (see `ExperimentDataProvider.kt`) and never call [HealthConnectManager].
 * That sidesteps two problems at once:
 *   1. `HealthConnectManager` is a concrete (non-open) Hilt-injected class wrapping a real
 *      Health Connect client with no seam for a hand-written fake without an invasive
 *      production refactor (extracting an interface) that's out of scope here. Under
 *      Robolectric, `HealthConnectClient.getSdkStatus()` reports unavailable, so a real
 *      `HealthConnectManager(context)` harmlessly returns "no signal" for every call --
 *      which is *why* it's safe to construct one below, but it means the three
 *      Health-Connect-backed experiment types (SleepVariabilityStress,
 *      SleepDurationProductivity, StepsSleepEfficiency) are NOT exercised by these tests.
 *   2. `ExperimentRepository.kt` and the `engine` package are both being concurrently modified by
 *      other agents (data export work and an experiment-type-to-JSON generalization,
 *      respectively -- see AGENT_PLANS notes at the time this file was written). These
 *      tests assert against the *current* stage-machine behavior by cross-checking against
 *      the same pure `ExperimentEngine`/`ExperimentStageState` functions the repository
 *      itself calls (rather than hardcoding independently-derived magic numbers), but they
 *      still call the repository's concrete methods and decode its concrete JSON columns,
 *      so **re-verify this file after those merges land** -- it is the most likely test
 *      file in this pass to need updating, not because the coverage is wrong today, but
 *      because its scaffolding (backdating stage dates via `ExperimentStageState`) is
 *      coupled to the exact current shape of that class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ExperimentRepositoryTest {

    private lateinit var db: QuantifyMeDatabase
    private lateinit var repository: ExperimentRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuantifyMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val healthConnect = HealthConnectManager(context)
        repository = ExperimentRepository(context, db, healthConnect)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun checkinAt(experimentId: Int, date: LocalDate, leisureTime: Int = 60, happiness: Int = 5) =
        CheckinEntity(
            experimentId = experimentId,
            checkinDate = date.toDateTimeAtStartOfDay().plusHours(8),
            didFollowInstructions = 1,
            happiness = happiness,
            stress = 2,
            productivity = 4,
            leisureTime = leisureTime
        )

    private suspend fun readState(experimentId: Int): ExperimentStageState {
        val entity = db.experimentDao().getById(experimentId).first()!!
        return ExperimentStageState.fromJson(
            entity.stageDatesJson, entity.stageTargetValuesJson, entity.stageRestartCountJson
        )
    }

    // ---- createExperiment ----------------------------------------------------------

    @Test
    fun createExperiment_insertsActiveExperimentInBaselineStage() = runBlocking {
        val id = repository.createExperiment(ExperimentType.LeisureHappiness, 3, 4, 5).toInt()

        val entity = db.experimentDao().getById(id).first()!!
        assertEquals("leisurehappiness", entity.type)
        assertEquals(0, entity.currentStage)
        assertTrue(entity.isActive)
        assertEquals(3, entity.selfEfficacy)
        assertEquals(4, entity.appEfficacy)
        assertEquals(5, entity.experimentEfficacy)

        val state = readState(id)
        assertEquals(LocalDate.now(), state.stageDates[0].first)
        assertEquals(LocalDate.now().plusDays(7), state.stageDates[0].second)
    }

    // ---- submitCheckin: normal day, no stage transition ------------------------------

    @Test
    fun submitCheckin_onCreationDay_recordsCheckinAndStaysInBaseline() = runBlocking {
        val id = repository.createExperiment(ExperimentType.LeisureHappiness, 3, 3, 3).toInt()

        val outcome = repository.submitCheckin(
            id, didFollowInstructions = 1, happiness = 5, stress = 2, productivity = 4, leisureTime = 60
        )

        assertTrue("day 0 of a fresh 7-day baseline stage must not end it", !outcome.newStage)
        assertEquals(0, outcome.currentStage)
        assertEquals(1, db.checkinDao().getCheckinsForExperiment(id).first().size)

        val entity = db.experimentDao().getById(id).first()!!
        assertEquals(0, entity.currentStage)
        assertTrue(entity.isActive)
    }

    // ---- submitCheckin: baseline stage completes -> stage 1 starts -------------------

    @Test
    fun submitCheckin_baselineComplete_advancesToStage1WithComputedTargets() = runBlocking {
        val today = LocalDate.now()
        val id = repository.createExperiment(ExperimentType.LeisureHappiness, 3, 3, 3).toInt()

        // Backdate the baseline stage to have started 7 days ago, so today's check-in is the
        // 7th day and should end it (Experiment.get_stage_dates / should_end_stage: stageDay
        // == 7). This is what "waiting a week" would do in real use; the repository itself
        // has no clock injection seam, so date manipulation happens directly on the stored
        // stage-state JSON (see class doc).
        val original = readState(id)
        val backdated = original.withStageDates(0, today.minusDays(7), today)
        val (datesJson, targetsJson, restartJson) = backdated.toJson()
        val entity = db.experimentDao().getById(id).first()!!
        db.experimentDao().update(
            entity.copy(stageDatesJson = datesJson, stageTargetValuesJson = targetsJson, stageRestartCountJson = restartJson)
        )

        // getCheckinsValue attributes a day's value to the check-in recorded the *next*
        // calendar day (users check in each morning about the day just finished), so the
        // 7-day input window [today-7, today) needs check-ins dated today-6..today. The last
        // of those (today) is recorded by submitCheckin() itself below.
        for (offset in 6 downTo 1) {
            db.checkinDao().insert(checkinAt(id, today.minusDays(offset), leisureTime = 60, happiness = 5))
        }

        val outcome = repository.submitCheckin(
            id, didFollowInstructions = 1, happiness = 5, stress = 2, productivity = 4, leisureTime = 60
        )

        // Cross-check against the same pure engine function the repository calls, rather
        // than hardcoding the expected target values independently.
        val expectedTargets = ExperimentEngine.setStageTargets(
            List(7) { 60f }, useVariability = false, ExperimentType.LeisureHappiness.ranges, ExperimentType.LeisureHappiness.rangeSize
        )

        assertTrue("a full 7-day baseline must end the stage", outcome.newStage)
        assertEquals(1, outcome.currentStage)
        assertEquals(expectedTargets.stageTargetValues[1], outcome.target)

        val updatedEntity = db.experimentDao().getById(id).first()!!
        assertEquals(1, updatedEntity.currentStage)
        assertTrue(updatedEntity.isActive)
        assertEquals(expectedTargets.initialStageAverage, updatedEntity.initialStageAverage)

        val updatedState = readState(id)
        assertEquals(expectedTargets.stageTargetValues, updatedState.stageTargetValues)
        assertEquals(today, updatedState.stageDates[1].first)
        assertEquals(today.plusDays(7), updatedState.stageDates[1].second)
    }

    // ---- submitCheckin: mid-stage restart on too many missed days --------------------

    @Test
    fun submitCheckin_tooManyMissedDaysInStage_restartsSameStageWithoutAdvancing() = runBlocking {
        val today = LocalDate.now()
        val id = repository.createExperiment(ExperimentType.LeisureHappiness, 3, 3, 3).toInt()

        // Fast-forward straight into stage 1, started 3 days ago, with pre-set targets (as
        // if a baseline had already completed) but *no* check-ins recorded for the two days
        // that would need them (today-3 -> needs today-2's check-in, today-2 -> needs
        // today-1's) -- only today's check-in (submitted below) will exist, so
        // getNumMissedDays should be 2, tripping the ">= 2 missed days while currentStage >
        // 0" restart branch.
        val original = readState(id)
        val fastForwarded = original
            .withStageDates(1, today.minusDays(3), today.plusDays(4))
            .withStageTargetValues(listOf(60f, 90f, 30f, 60f))
        val (datesJson, targetsJson, restartJson) = fastForwarded.toJson()
        val entity = db.experimentDao().getById(id).first()!!
        db.experimentDao().update(
            entity.copy(
                currentStage = 1,
                stageDatesJson = datesJson,
                stageTargetValuesJson = targetsJson,
                stageRestartCountJson = restartJson
            )
        )

        val outcome = repository.submitCheckin(
            id, didFollowInstructions = 1, happiness = 5, stress = 2, productivity = 4, leisureTime = 60
        )

        assertTrue(outcome.restartedStage)
        assertEquals(ExperimentEngine.RestartReason.TOO_MANY_MISSED_DAYS, outcome.restartReason)
        assertTrue("a restart must not also report shouldEnd/newStage", !outcome.newStage)
        assertEquals(1, outcome.currentStage) // still stage 1, just restarted

        val updatedState = readState(id)
        assertEquals(1, updatedState.stageRestartCount[1]) // incremented from 0
        assertEquals(today, updatedState.stageDates[1].first) // stage window reset to today..+7
        assertEquals(today.plusDays(7), updatedState.stageDates[1].second)
    }

    // ---- refreshInstructions: read-only ------------------------------------------------

    @Test
    fun refreshInstructions_doesNotRecordACheckinOrMutateExperimentState() = runBlocking {
        val today = LocalDate.now()
        val id = repository.createExperiment(ExperimentType.LeisureHappiness, 3, 3, 3).toInt()
        val original = readState(id)
        val advanced = original
            .withStageDates(1, today, today.plusDays(7))
            .withStageTargetValues(listOf(60f, 90f, 30f, 60f))
        val (datesJson, targetsJson, restartJson) = advanced.toJson()
        val entityBefore = db.experimentDao().getById(id).first()!!
        db.experimentDao().update(
            entityBefore.copy(currentStage = 1, stageDatesJson = datesJson, stageTargetValuesJson = targetsJson, stageRestartCountJson = restartJson)
        )
        val entitySnapshot = db.experimentDao().getById(id).first()!!

        val outcome = repository.refreshInstructions(id)

        assertEquals(1, outcome.currentStage)
        assertEquals(90f, outcome.target) // stage 1's target, index 1 of [60,90,30,60]
        assertTrue("refreshInstructions must not write a check-in", db.checkinDao().getCheckinsForExperiment(id).first().isEmpty())

        val entityAfter = db.experimentDao().getById(id).first()!!
        assertEquals(entitySnapshot, entityAfter)
    }

    // ---- cancelExperiment -------------------------------------------------------------

    @Test
    fun cancelExperiment_marksInactiveAndCancelled() = runBlocking {
        val id = repository.createExperiment(ExperimentType.LeisureHappiness, 3, 3, 3).toInt()

        repository.cancelExperiment(id)

        val entity = db.experimentDao().getById(id).first()!!
        assertTrue(entity.isCancelled)
        assertTrue(!entity.isActive)
    }

    @Test
    fun cancelExperiment_unknownId_doesNotThrow() = runBlocking {
        // No experiment with this id exists; cancelExperiment should be a silent no-op
        // (matches its firstOrNull-guarded implementation), not a crash.
        repository.cancelExperiment(999)
    }

    // ---- getProgressSummary ------------------------------------------------------------

    @Test
    fun getProgressSummary_returnsPerStageDateRangesRestartCountsAndCheckins() = runBlocking {
        val today = LocalDate.now()
        val id = repository.createExperiment(ExperimentType.LeisureHappiness, 3, 3, 3).toInt()

        val stage1Start = today.minusDays(14)
        val stage1End = today.minusDays(7)
        val stage2Start = today.minusDays(7)
        val stage2End = today

        val original = readState(id)
        val withStages = original
            .withStageDates(1, stage1Start, stage1End)
            .withStageDates(2, stage2Start, stage2End)
            .withIncrementedRestartCount(2)
        val (datesJson, targetsJson, restartJson) = withStages.toJson()
        val entity = db.experimentDao().getById(id).first()!!
        db.experimentDao().update(
            entity.copy(currentStage = 2, stageDatesJson = datesJson, stageTargetValuesJson = targetsJson, stageRestartCountJson = restartJson)
        )

        db.checkinDao().insert(checkinAt(id, today.minusDays(10))) // inside stage 1
        db.checkinDao().insert(checkinAt(id, today.minusDays(3))) // inside stage 2

        val progress = repository.getProgressSummary(id)

        assertEquals(2, progress.size)
        assertEquals(1, progress[0].stage)
        assertEquals(stage1Start, progress[0].start)
        assertEquals(stage1End, progress[0].end)
        assertEquals(0, progress[0].restartCount)
        assertEquals(1, progress[0].checkins.size)

        assertEquals(2, progress[1].stage)
        assertEquals(stage2Start, progress[1].start)
        assertEquals(1, progress[1].restartCount)
        assertEquals(1, progress[1].checkins.size)
    }

    @Test
    fun getProgressSummary_unknownExperimentId_throws() = runBlocking {
        var threw = false
        try {
            repository.getProgressSummary(999)
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
    }
}
