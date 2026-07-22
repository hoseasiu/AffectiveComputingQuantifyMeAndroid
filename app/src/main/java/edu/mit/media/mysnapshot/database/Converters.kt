package edu.mit.media.mysnapshot.database

import androidx.room.TypeConverter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object Converters {
    // Unlike Joda-Time's DateTime.parse(), OffsetDateTime.parse() always reconstructs the
    // offset embedded in the string rather than shifting to the JVM default zone, so this is
    // zone-faithful without any extra flag. Nanos are stripped before formatting (below) so
    // sub-second precision is never round-tripped, matching the previous dateTimeNoMillis().
    private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    @TypeConverter
    fun fromDateTime(value: OffsetDateTime?): String? {
        return value?.withNano(0)?.format(dateTimeFormatter)
    }

    @TypeConverter
    fun toDateTime(value: String?): OffsetDateTime? {
        return value?.let { OffsetDateTime.parse(it, dateTimeFormatter) }
    }
}
