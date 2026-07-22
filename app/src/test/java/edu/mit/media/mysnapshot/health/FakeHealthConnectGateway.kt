package edu.mit.media.mysnapshot.health

import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Instant

/**
 * Hand-written [HealthConnectGateway] test double -- see that interface's doc for why the fake
 * targets this narrower seam instead of the real [androidx.health.connect.client.HealthConnectClient]
 * (its `aggregate`/`readRecords` return types have library-`internal` constructors).
 */
internal class FakeHealthConnectGateway(
    private val stepsFn: (start: Instant, endExclusive: Instant) -> Float? = { _, _ -> null },
    private val exerciseMinutesFn: (start: Instant, endExclusive: Instant) -> Float? = { _, _ -> null },
    private val sessions: List<SleepSessionRecord> = emptyList(),
    private val granted: Set<String> = emptySet()
) : HealthConnectGateway {

    val totalStepsQueries = mutableListOf<Pair<Instant, Instant>>()
    val totalExerciseMinutesQueries = mutableListOf<Pair<Instant, Instant>>()
    val sleepSessionsQueries = mutableListOf<Pair<Instant, Instant>>()

    override suspend fun totalSteps(start: Instant, endExclusive: Instant): Float? {
        totalStepsQueries += start to endExclusive
        return stepsFn(start, endExclusive)
    }

    override suspend fun totalExerciseMinutes(start: Instant, endExclusive: Instant): Float? {
        totalExerciseMinutesQueries += start to endExclusive
        return exerciseMinutesFn(start, endExclusive)
    }

    override suspend fun sleepSessions(start: Instant, endExclusive: Instant): List<SleepSessionRecord> {
        sleepSessionsQueries += start to endExclusive
        return sessions
    }

    override suspend fun grantedPermissions(): Set<String> = granted
}
