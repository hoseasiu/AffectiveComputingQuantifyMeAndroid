package edu.mit.media.mysnapshot.database

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure round-trip tests for the Room [Converters] used on every `DateTime` column in
 * [ExperimentEntity]/[CheckinEntity]. No Android/Robolectric dependency needed -- Joda-Time
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
        val dt = DateTime(2024, 1, 15, 10, 30, 45, DateTimeZone.UTC)
        val encoded = Converters.fromDateTime(dt)
        assertEquals("2024-01-15T10:30:45Z", encoded)
    }

    @Test
    fun toDateTime_parsesIsoString() {
        val decoded = Converters.toDateTime("2024-01-15T10:30:45Z")
        // DateTime.parse(str, formatter) without withOffsetParsed() renders the result in
        // the JVM's default time zone (documented Joda-Time behavior), not necessarily the
        // "Z"/UTC offset embedded in the string -- so compare the represented instant
        // (millis), not full DateTime equality, which also compares chronology/zone.
        assertEquals(DateTime(2024, 1, 15, 10, 30, 45, DateTimeZone.UTC).millis, decoded!!.millis)
    }

    @Test
    fun roundTrip_preservesInstant() {
        val original = DateTime(2023, 6, 1, 23, 59, 59, DateTimeZone.UTC)
        val roundTripped = Converters.toDateTime(Converters.fromDateTime(original))
        assertEquals(original.millis, roundTripped!!.millis)
    }

    @Test
    fun roundTrip_dropsSubSecondPrecision() {
        // dateTimeNoMillis() -- milliseconds are intentionally not preserved across a
        // round trip; this documents that behavior rather than assuming full fidelity.
        val original = DateTime(2023, 6, 1, 12, 0, 0, 123, DateTimeZone.UTC)
        val roundTripped = Converters.toDateTime(Converters.fromDateTime(original))
        assertEquals(0, roundTripped!!.millisOfSecond)
        assertEquals(original.secondOfMinute, roundTripped.secondOfMinute)
    }

    @Test
    fun roundTrip_preservesInstantAcrossNonUtcZone() {
        // The formatter renders in the DateTime's own zone (with offset), and DateTime.parse
        // with the same formatter reconstructs an equal instant regardless of the zone the
        // value was originally created in.
        val original = DateTime(2024, 3, 10, 2, 15, 0, DateTimeZone.forOffsetHours(-5))
        val roundTripped = Converters.toDateTime(Converters.fromDateTime(original))
        assertEquals(original.millis, roundTripped!!.millis)
    }
}
