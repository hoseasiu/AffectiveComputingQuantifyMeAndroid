package edu.mit.media.mysnapshot.database

import androidx.room.TypeConverter
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

object Converters {
    private val dateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis()

    @TypeConverter
    fun fromDateTime(value: DateTime?): String? {
        return value?.toString(dateTimeFormatter)
    }

    @TypeConverter
    fun toDateTime(value: String?): DateTime? {
        return value?.let { DateTime.parse(it, dateTimeFormatter) }
    }
}
