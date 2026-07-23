/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
