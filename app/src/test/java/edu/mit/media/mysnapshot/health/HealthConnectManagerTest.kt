package edu.mit.media.mysnapshot.health

import androidx.health.connect.client.records.SleepSessionRecord
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.joda.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Coverage for [HealthConnectManager]'s signal-extraction functions
 * (AGENT_PLANS/IMPROVEMENTS.md item 5, [issue #21](https://github.com/hoseasiu/AffectiveComputingQuantifyMeAndroid/issues/21)),
 * exercised against a hand-written [FakeHealthConnectGateway] rather than a real Health Connect
 * client -- see [HealthConnectGateway]'s doc for why the seam sits below the client rather than
 * faking it directly.
 *
 * `@Config(application = Application::class)` avoids booting `MyApplication`'s Hilt graph, same
 * as the other Robolectric tests in this suite. Robolectric is used only for a real `Context` to
 * satisfy [HealthConnectManager]'s constructor -- every test below sets [HealthConnectManager.testGatewayOverride]
 * before the first call, so the real Health Connect / `isAvailable()` codepath is never exercised
 * except in the two tests that deliberately leave it unset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class HealthConnectManagerTest {

    private lateinit var manager: HealthConnectManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        manager = HealthConnectManager(context)
    }

    private fun LocalDate.startOfDayInstant(): Instant =
        toDateTimeAtStartOfDay().toDate().toInstant()

    private fun session(
        startDate: LocalDate,
        startHour: Int,
        durationMinutes: Long,
        stages: List<SleepSessionRecord.Stage> = emptyList()
    ): SleepSessionRecord {
        val start = startDate.toDateTimeAtStartOfDay().plusHours(startHour).toDate().toInstant()
        val end = start.plusSeconds(durationMinutes * 60)
        return SleepSessionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            stages = stages
        )
    }

    // ---- getDailySteps ------------------------------------------------------------------

    @Test
    fun getDailySteps_returnsPerDayCountsAndNullForDaysWithNoData() = runBlocking {
        val today = LocalDate.now()
        manager.testGatewayOverride = FakeHealthConnectGateway(
            stepsFn = { start, _ ->
                when (LocalDate(start.toEpochMilli())) {
                    today -> 5000f
                    today.plusDays(1) -> 8000f
                    else -> null
                }
            }
        )

        val result = manager.getDailySteps(today, today.plusDays(3))

        assertEquals(listOf(5000f, 8000f, null), result)
    }

    @Test
    fun getDailySteps_queriesEachDayAsASeparateMidnightToMidnightWindow() = runBlocking {
        val today = LocalDate.now()
        val gateway = FakeHealthConnectGateway(stepsFn = { _, _ -> 1f })
        manager.testGatewayOverride = gateway

        manager.getDailySteps(today, today.plusDays(2))

        assertEquals(2, gateway.totalStepsQueries.size)
        assertEquals(today.startOfDayInstant(), gateway.totalStepsQueries[0].first)
        assertEquals(today.plusDays(1).startOfDayInstant(), gateway.totalStepsQueries[0].second)
        assertEquals(today.plusDays(1).startOfDayInstant(), gateway.totalStepsQueries[1].first)
        assertEquals(today.plusDays(2).startOfDayInstant(), gateway.totalStepsQueries[1].second)
    }

    @Test
    fun getDailySteps_emptyRange_returnsEmptyList() = runBlocking {
        val today = LocalDate.now()
        manager.testGatewayOverride = FakeHealthConnectGateway(stepsFn = { _, _ -> 42f })

        assertTrue(manager.getDailySteps(today, today).isEmpty())
    }

    @Test
    @Config(sdk = [33])
    fun getDailySteps_sdkUnavailableAndNoOverride_returnsNullForEveryDay() = runBlocking {
        // No testGatewayOverride set: falls through to the real isAvailable() check, which
        // reports unavailable under Robolectric (no Health Connect provider installed).
        val today = LocalDate.now()

        val result = manager.getDailySteps(today, today.plusDays(3))

        assertEquals(listOf(null, null, null), result)
    }

    // ---- getExerciseMinutes ---------------------------------------------------------------

    @Test
    fun getExerciseMinutes_returnsPerDayMinutesAndNullForDaysWithNoData() = runBlocking {
        val today = LocalDate.now()
        manager.testGatewayOverride = FakeHealthConnectGateway(
            exerciseMinutesFn = { start, _ ->
                when (LocalDate(start.toEpochMilli())) {
                    today -> 20f
                    today.plusDays(1) -> 45f
                    else -> null
                }
            }
        )

        val result = manager.getExerciseMinutes(today, today.plusDays(3))

        assertEquals(listOf(20f, 45f, null), result)
    }

    @Test
    fun getExerciseMinutes_queriesEachDayAsASeparateMidnightToMidnightWindow() = runBlocking {
        val today = LocalDate.now()
        val gateway = FakeHealthConnectGateway(exerciseMinutesFn = { _, _ -> 1f })
        manager.testGatewayOverride = gateway

        manager.getExerciseMinutes(today, today.plusDays(2))

        assertEquals(2, gateway.totalExerciseMinutesQueries.size)
        assertEquals(today.startOfDayInstant(), gateway.totalExerciseMinutesQueries[0].first)
        assertEquals(today.plusDays(1).startOfDayInstant(), gateway.totalExerciseMinutesQueries[0].second)
        assertEquals(today.plusDays(1).startOfDayInstant(), gateway.totalExerciseMinutesQueries[1].first)
        assertEquals(today.plusDays(2).startOfDayInstant(), gateway.totalExerciseMinutesQueries[1].second)
    }

    @Test
    fun getExerciseMinutes_emptyRange_returnsEmptyList() = runBlocking {
        val today = LocalDate.now()
        manager.testGatewayOverride = FakeHealthConnectGateway(exerciseMinutesFn = { _, _ -> 42f })

        assertTrue(manager.getExerciseMinutes(today, today).isEmpty())
    }

    @Test
    @Config(sdk = [33])
    fun getExerciseMinutes_sdkUnavailableAndNoOverride_returnsNullForEveryDay() = runBlocking {
        val today = LocalDate.now()

        val result = manager.getExerciseMinutes(today, today.plusDays(3))

        assertEquals(listOf(null, null, null), result)
    }

    // ---- getSleepDurationMinutes ---------------------------------------------------------

    @Test
    fun getSleepDurationMinutes_attributesSessionToTheDayItEndedOn() = runBlocking {
        val today = LocalDate.now()
        // Session starts the night before and ends today -- must be attributed to `today`,
        // not the start date, matching the endTime-based day lookup in perDaySleepSession.
        val overnightSession = session(startDate = today.minusDays(1), startHour = 23, durationMinutes = 480)
        manager.testGatewayOverride = FakeHealthConnectGateway(sessions = listOf(overnightSession))

        val result = manager.getSleepDurationMinutes(today, today.plusDays(1))

        assertEquals(listOf(480f), result)
    }

    @Test
    fun getSleepDurationMinutes_dayWithNoSession_isNull() = runBlocking {
        val today = LocalDate.now()
        manager.testGatewayOverride = FakeHealthConnectGateway(sessions = emptyList())

        val result = manager.getSleepDurationMinutes(today, today.plusDays(1))

        assertEquals(listOf<Float?>(null), result)
    }

    // ---- getSleepStartMinuteOfDay ----------------------------------------------------------

    @Test
    fun getSleepStartMinuteOfDay_usesTheSessionsOwnZoneOffsetNotSystemDefault() = runBlocking {
        val today = LocalDate.now()
        // Absolute instant fixed at UTC noon via joda's UTC zone (independent of the host
        // machine's default timezone), then paired with an explicit, deliberately-not-UTC
        // +05:00 record offset -- if getSleepStartMinuteOfDay used the system default zone
        // instead of the session's own offset, this assertion would fail on any machine whose
        // default zone isn't +05:00.
        val start = today.toDateTimeAtStartOfDay(org.joda.time.DateTimeZone.UTC).plusHours(12).toDate().toInstant()
        val daytimeSession = SleepSessionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.ofHours(5),
            endTime = start.plusSeconds(1800),
            endZoneOffset = ZoneOffset.ofHours(5)
        )
        manager.testGatewayOverride = FakeHealthConnectGateway(sessions = listOf(daytimeSession))

        val result = manager.getSleepStartMinuteOfDay(today, today.plusDays(1))

        // 12:00 UTC read at +05:00 = 17:00 local = 17*60 = 1020 minutes into the day.
        assertEquals(listOf(1020f), result)
    }

    @Test
    fun getSleepStartMinuteOfDay_missingZoneOffset_fallsBackToSystemDefaultZone() = runBlocking {
        val today = LocalDate.now()
        val start = today.toDateTimeAtStartOfDay().plusHours(6).plusMinutes(30).toDate().toInstant()
        val noOffsetSession = SleepSessionRecord(
            startTime = start,
            startZoneOffset = null,
            endTime = start.plusSeconds(3600),
            endZoneOffset = null
        )
        manager.testGatewayOverride = FakeHealthConnectGateway(sessions = listOf(noOffsetSession))

        val result = manager.getSleepStartMinuteOfDay(today, today.plusDays(1))

        val expectedLocalTime = start.atZone(ZoneId.systemDefault())
        val expectedMinuteOfDay = (expectedLocalTime.hour * 60 + expectedLocalTime.minute).toFloat()
        assertEquals(listOf(expectedMinuteOfDay), result)
    }

    // ---- getSleepEfficiency ---------------------------------------------------------------

    @Test
    fun getSleepEfficiency_subtractsAwakeStagesFromTotalDuration() = runBlocking {
        val today = LocalDate.now()
        val start = today.minusDays(1).toDateTimeAtStartOfDay().plusHours(23).toDate().toInstant()
        val end = start.plusSeconds(8 * 3600L) // 8h session
        val awakeStage = SleepSessionRecord.Stage(
            startTime = start.plusSeconds(3600),
            endTime = start.plusSeconds(3600 + 1800), // 30 min awake
            stage = SleepSessionRecord.STAGE_TYPE_AWAKE
        )
        val sleepingStage = SleepSessionRecord.Stage(
            startTime = start.plusSeconds(3600 + 1800),
            endTime = end,
            stage = SleepSessionRecord.STAGE_TYPE_SLEEPING
        )
        val sessionWithStages = SleepSessionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            stages = listOf(awakeStage, sleepingStage)
        )
        manager.testGatewayOverride = FakeHealthConnectGateway(sessions = listOf(sessionWithStages))

        val result = manager.getSleepEfficiency(today, today.plusDays(1))

        // 30 min awake out of 480 min total = 1 - (30/480) = 0.9375
        assertEquals(1, result.size)
        assertEquals(0.9375f, result[0]!!, 0.0001f)
    }

    @Test
    fun getSleepEfficiency_noAwakeStages_isFullEfficiency() = runBlocking {
        val today = LocalDate.now()
        val sessionNoStages = session(startDate = today.minusDays(1), startHour = 22, durationMinutes = 480)
        manager.testGatewayOverride = FakeHealthConnectGateway(sessions = listOf(sessionNoStages))

        val result = manager.getSleepEfficiency(today, today.plusDays(1))

        assertEquals(1f, result[0])
    }

    @Test
    fun getSleepEfficiency_dayWithNoSession_isNull() = runBlocking {
        val today = LocalDate.now()
        manager.testGatewayOverride = FakeHealthConnectGateway(sessions = emptyList())

        assertNull(manager.getSleepEfficiency(today, today.plusDays(1))[0])
    }

    // ---- getMostRecentSleepSession ---------------------------------------------------------

    @Test
    fun getMostRecentSleepSession_returnsTheSessionWithTheLatestEndTime() = runBlocking {
        val today = LocalDate.now()
        val older = session(startDate = today.minusDays(2), startHour = 22, durationMinutes = 60)
        val newer = session(startDate = today.minusDays(1), startHour = 22, durationMinutes = 60)
        manager.testGatewayOverride = FakeHealthConnectGateway(sessions = listOf(older, newer))

        val result = manager.getMostRecentSleepSession()

        assertEquals(newer.endTime, result!!.endTime)
        assertEquals(newer.startTime, result.startTime)
        assertEquals(LocalDate(newer.endTime.toEpochMilli()), result.attributedNight)
    }

    @Test
    fun getMostRecentSleepSession_noSessions_returnsNull() = runBlocking {
        manager.testGatewayOverride = FakeHealthConnectGateway(sessions = emptyList())

        assertNull(manager.getMostRecentSleepSession())
    }

    @Test
    @Config(sdk = [33])
    fun getMostRecentSleepSession_sdkUnavailableAndNoOverride_returnsNull() = runBlocking {
        assertNull(manager.getMostRecentSleepSession())
    }

    // ---- hasAllPermissions ------------------------------------------------------------------

    @Test
    fun hasAllPermissions_allGranted_isTrue() = runBlocking {
        manager.testGatewayOverride = FakeHealthConnectGateway(granted = HealthConnectManager.PERMISSIONS)

        assertTrue(manager.hasAllPermissions())
    }

    @Test
    fun hasAllPermissions_onlyPartiallyGranted_isFalse() = runBlocking {
        manager.testGatewayOverride = FakeHealthConnectGateway(granted = setOf(HealthConnectManager.PERMISSIONS.first()))

        assertFalse(manager.hasAllPermissions())
    }

    @Test
    @Config(sdk = [33])
    fun hasAllPermissions_sdkUnavailableAndNoOverride_isFalse() = runBlocking {
        assertFalse(manager.hasAllPermissions())
    }
}
