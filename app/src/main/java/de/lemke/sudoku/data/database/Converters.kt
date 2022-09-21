package de.lemke.sudoku.data.database

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** Type converters to map between SQLite types and entity types. */
object Converters {

    /** Returns the string representation of the [localDate]. */
    @TypeConverter
    fun localDateToDb(localDate: LocalDate): String = localDate.toString()

    /** Returns the [LocalDate] represented by the [localDateString]. */
    @TypeConverter
    fun localDateFromDb(localDateString: String): LocalDate = LocalDate.parse(localDateString)

    /** Returns the string representation of the [localDateTime]. */
    @TypeConverter
    fun localDateTimeToDb(localDateTime: LocalDateTime): String = localDateTime.toString()

    /** Returns the [LocalDateTime] represented by the [localDateTimeString]. */
    @TypeConverter
    fun localDateTimeFromDb(localDateTimeString: String): LocalDateTime = LocalDateTime.parse(localDateTimeString)

    /** Returns the string representation of the [zonedDateTime]. */
    @TypeConverter
    fun zonedDateTimeToDb(zonedDateTime: ZonedDateTime): String = zonedDateTime.toString()

    /** Returns the [ZonedDateTime] represented by the [zonedDateTimeString]. */
    @TypeConverter
    fun zonedDateTimeFromDb(zonedDateTimeString: String): ZonedDateTime = ZonedDateTime.parse(zonedDateTimeString)

}
