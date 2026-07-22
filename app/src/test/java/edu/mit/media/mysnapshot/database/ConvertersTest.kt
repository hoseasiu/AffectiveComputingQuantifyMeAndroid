package edu.mit.media.mysnapshot.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Pure round-trip tests for the Room [Converters] used on every `DateTime` column in
 * [ExperimentEntity]/[CheckinEntity]. No Android/Robolectric dependency needed -- `OffsetDateTime`
 * and Room's `@TypeConverter`-annotated functions are plain JVM code, so these run as fast
 * plain JUnit tests. See AGENT_PLANS/IMPROVEMENTS.md item 5 (test coverage backlog) --
 * priority 1 target ("pure round-trip tests, no Android needed").
 */
class ConvertersTest {

    @Test
    fun fromDateTime_null_returnsNull() {
        assertNull(Converters.fromDateTime(null))
    }

    @Test
    fun toDateTime_null_returnsNull() {
        assertNull(Converters.toDateTime(null))
    }

    @Test
    fun fromDateTime_formatsAsIsoDateTimeNoMillis() {
        val dt = OffsetDateTime.of(2024, 1, 15, 10, 30, 45, 0, ZoneOffset.UTC)
        val encoded = Converters.fromDateTime(dt)
        assertEquals("2024-01-15T10:30:45Z", encoded)
    }

    @Test
    fun toDateTime_parsesIsoString() {
        val decoded = Converters.toDateTime("2024-01-15T10:30:45Z")
        assertEquals(
            OffsetDateTime.of(2024, 1, 15, 10, 30, 45, 0, ZoneOffset.UTC).toInstant(),
            decoded!!.toInstant()
        )
    }

    @Test
    fun roundTrip_preservesInstant() {
        val original = OffsetDateTime.of(2023, 6, 1, 23, 59, 59, 0, ZoneOffset.UTC)
        val roundTripped = Converters.toDateTime(Converters.fromDateTime(original))
        assertEquals(original.toInstant(), roundTripped!!.toInstant())
    }

    @Test
    fun roundTrip_dropsSubSecondPrecision() {
        // Nanos are stripped before formatting (see Converters) -- this documents that
        // behavior rather than assuming full fidelity.
        val original = OffsetDateTime.of(2023, 6, 1, 12, 0, 0, 123_000_000, ZoneOffset.UTC)
        val roundTripped = Converters.toDateTime(Converters.fromDateTime(original))
        assertEquals(0, roundTripped!!.nano)
        assertEquals(original.second, roundTripped.second)
    }

    @Test
    fun roundTrip_preservesInstantAcrossNonUtcZone() {
        // The formatter renders in the OffsetDateTime's own offset, and OffsetDateTime.parse
        // with the same formatter reconstructs an equal instant regardless of the offset the
        // value was originally created in.
        val original = OffsetDateTime.of(2024, 3, 10, 2, 15, 0, 0, ZoneOffset.ofHours(-5))
        val roundTripped = Converters.toDateTime(Converters.fromDateTime(original))
        assertEquals(original.toInstant(), roundTripped!!.toInstant())
    }
}
