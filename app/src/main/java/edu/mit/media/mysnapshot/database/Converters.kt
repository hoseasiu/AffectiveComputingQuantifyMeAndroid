package edu.mit.media.mysnapshot.database

import androidx.room.TypeConverter
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

object Converters {
    // `.withOffsetParsed()` makes the DB round-trip zone-faithful: without it, Joda renders
    // the parsed result in the formatter's zone (the JVM default), discarding the offset
    // embedded in the stored string, so reading back a value that was written with an
    // explicit non-default zone would yield the correct instant but shifted date/time
    // *components* (year/dayOfMonth/hourOfDay). Today every write goes through
    // DateTime.now() (default zone) and every read derives its components via LocalDate(...)
    // in that same default zone, so the components already match in practice; this flag just
    // removes the latent trap for any future call site that persists a non-default zone.
    private val dateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis().withOffsetParsed()

    @TypeConverter
    fun fromDateTime(value: DateTime?): String? {
        return value?.toString(dateTimeFormatter)
    }

    @TypeConverter
    fun toDateTime(value: String?): DateTime? {
        return value?.let { DateTime.parse(it, dateTimeFormatter) }
    }
}
