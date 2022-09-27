package de.lemke.sudoku.data.database

import androidx.room.TypeConverter
import java.time.LocalDateTime

/** Type converters to map between SQLite types and entity types. */
object Converters {

    /** Returns the string representation of the [localDateTime]. */
    @TypeConverter
    fun localDateTimeToDb(localDateTime: LocalDateTime): String = localDateTime.toString()

    /** Returns the [LocalDateTime] represented by the [localDateTimeString]. */
    @TypeConverter
    fun localDateTimeFromDb(localDateTimeString: String): LocalDateTime = LocalDateTime.parse(localDateTimeString)

}
