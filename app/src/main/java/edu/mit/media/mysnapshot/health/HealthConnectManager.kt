package edu.mit.media.mysnapshot.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import org.joda.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything [HealthConnectManager] needs from a real [HealthConnectClient], narrowed down to
 * plain Kotlin return types. This is the test seam called for in AGENT_PLANS/IMPROVEMENTS.md
 * item 5: [HealthConnectClient.aggregate] and [HealthConnectClient.readRecords] return
 * [androidx.health.connect.client.aggregate.AggregationResult] /
 * [androidx.health.connect.client.response.ReadRecordsResponse], both of which have a
 * library-`internal` constructor, so a fake can't produce them directly -- but it can implement
 * this interface and hand back the plain values those responses would have wrapped.
 */
internal interface HealthConnectGateway {
    suspend fun totalSteps(start: Instant, endExclusive: Instant): Float?
    suspend fun sleepSessions(start: Instant, endExclusive: Instant): List<SleepSessionRecord>
    suspend fun grantedPermissions(): Set<String>
}

private class RealHealthConnectGateway(private val client: HealthConnectClient) : HealthConnectGateway {
    override suspend fun totalSteps(start: Instant, endExclusive: Instant): Float? {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, endExclusive)
            )
        )
        return response[StepsRecord.COUNT_TOTAL]?.toFloat()
    }

    override suspend fun sleepSessions(start: Instant, endExclusive: Instant): List<SleepSessionRecord> =
        client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, endExclusive)
            )
        ).records

    override suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()
}

/**
 * Thin wrapper around the Health Connect client -- Phase 3 replacement for the dead Jawbone
 * UP SDK (AGENT_PLANS/MODERNIZE.md). Exposes only the four signals the experiment engine
 * needs (daily step count, nightly sleep duration, sleep efficiency %, sleep start time),
 * each aligned to the *local calendar day the sleep/activity happened on* -- unlike
 * check-in-derived values (see `getCheckinsValue`), Health Connect records carry their own
 * timestamps so no day-shift is needed.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
    }

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun permissionRequestContract() = PermissionController.createRequestPermissionResultContract()

    /**
     * Test seam: set before any suspend function below is called (the real lookup is cached in
     * the `by lazy` below), a fake [HealthConnectGateway] bypasses both `isAvailable()` and the
     * real [HealthConnectClient] entirely -- see [HealthConnectGateway]'s doc for why the fake
     * targets this interface rather than [HealthConnectClient] itself.
     */
    internal var testGatewayOverride: HealthConnectGateway? = null

    private val gateway: HealthConnectGateway? by lazy {
        testGatewayOverride ?: if (isAvailable()) RealHealthConnectGateway(HealthConnectClient.getOrCreate(context)) else null
    }

    suspend fun hasAllPermissions(): Boolean {
        val gateway = gateway ?: return false
        return gateway.grantedPermissions().containsAll(PERMISSIONS)
    }

    private fun LocalDate.startOfDayInstant(): Instant =
        toDateTimeAtStartOfDay().toDate().toInstant()

    /** Daily step count for each day in [startDate, endDateExclusive). */
    suspend fun getDailySteps(startDate: LocalDate, endDateExclusive: LocalDate): List<Float?> {
        val gateway = gateway ?: return nullDays(startDate, endDateExclusive)
        return perDay(startDate, endDateExclusive) { date ->
            gateway.totalSteps(date.startOfDayInstant(), date.plusDays(1).startOfDayInstant())
        }
    }

    /** Nightly sleep duration (minutes) for the session that *ended* on each day. */
    suspend fun getSleepDurationMinutes(startDate: LocalDate, endDateExclusive: LocalDate): List<Float?> {
        return perDaySleepSession(startDate, endDateExclusive) { session ->
            (session.endTime.epochSecond - session.startTime.epochSecond) / 60f
        }
    }

    /** Sleep start time as minute-of-day (0-1439) for the session that ended on each day. */
    suspend fun getSleepStartMinuteOfDay(startDate: LocalDate, endDateExclusive: LocalDate): List<Float?> {
        val zone = ZoneId.systemDefault()
        return perDaySleepSession(startDate, endDateExclusive) { session ->
            val localTime = session.startTime.atZone(session.startZoneOffset ?: zone.rules.getOffset(session.startTime))
            (localTime.hour * 60 + localTime.minute).toFloat()
        }
    }

    /** Sleep efficiency (0-1): fraction of the session not spent in an awake stage. */
    suspend fun getSleepEfficiency(startDate: LocalDate, endDateExclusive: LocalDate): List<Float?> {
        return perDaySleepSession(startDate, endDateExclusive) { session ->
            val totalSeconds = session.endTime.epochSecond - session.startTime.epochSecond
            if (totalSeconds <= 0) {
                null
            } else {
                val awakeSeconds = session.stages
                    .filter { it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE }
                    .sumOf { it.endTime.epochSecond - it.startTime.epochSecond }
                1f - (awakeSeconds.toFloat() / totalSeconds.toFloat())
            }
        }
    }

    /**
     * The most recently completed sleep session, for explaining to the user which
     * calendar night their data belongs to (paper §6.3: users didn't understand what
     * counted as "a night" when they slept past midnight). A session is attributed to
     * the calendar day its [SleepSessionRecord.endTime] falls on, matching
     * [perDaySleepSession] above.
     */
    data class RecentSleepSession(
        val startTime: Instant,
        val endTime: Instant,
        val attributedNight: LocalDate
    )

    suspend fun getMostRecentSleepSession(): RecentSleepSession? {
        val gateway = gateway ?: return null
        val end = LocalDate.now().plusDays(1)
        val start = end.minusDays(3)
        val session = gateway.sleepSessions(start.startOfDayInstant(), end.startOfDayInstant())
            .maxByOrNull { it.endTime }
        return session?.let {
            RecentSleepSession(it.startTime, it.endTime, LocalDate(it.endTime.toEpochMilli()))
        }
    }

    private suspend fun perDaySleepSession(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        extract: (SleepSessionRecord) -> Float?
    ): List<Float?> {
        val gateway = gateway ?: return nullDays(startDate, endDateExclusive)
        val sessions = gateway.sleepSessions(
            startDate.startOfDayInstant(), endDateExclusive.plusDays(1).startOfDayInstant()
        )
        return perDay(startDate, endDateExclusive) { date ->
            val session = sessions.firstOrNull { LocalDate(it.endTime.toEpochMilli()) == date }
            session?.let(extract)
        }
    }

    private inline fun perDay(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        value: (LocalDate) -> Float?
    ): List<Float?> {
        val days = mutableListOf<Float?>()
        var date = startDate
        while (date.isBefore(endDateExclusive)) {
            days.add(value(date))
            date = date.plusDays(1)
        }
        return days
    }

    private fun nullDays(startDate: LocalDate, endDateExclusive: LocalDate): List<Float?> =
        perDay(startDate, endDateExclusive) { null }
}
