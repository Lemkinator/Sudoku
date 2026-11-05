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

package de.lemke.sudoku.data.backup

import de.lemke.sudoku.data.database.legacyGameModeOrNull
import de.lemke.sudoku.domain.model.GameMode
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable

/**
 * One game of a backup. [difficulty] is the name of a `Difficulty`, [modeLevel] is 0 for a normal game, -1 for a daily one and the
 * level for a level one, [dailyDate] is the day a daily game was made for, and [board] is the Base64 of the stored board blob. An
 * entry without [dailyDate] takes the day of [created].
 */
@Serializable
data class SudokuBackupEntry(
    val id: String,
    val size: Int,
    val difficulty: String,
    val modeLevel: Int,
    @Serializable(with = OffsetDateTimeSerializer::class) val created: OffsetDateTime,
    @Serializable(with = OffsetDateTimeSerializer::class) val updated: OffsetDateTime,
    val seconds: Int,
    val regionalHighlightingUsed: Boolean = false,
    val numberHighlightingUsed: Boolean = false,
    val eraserUsed: Boolean = false,
    val isChecklist: Boolean = false,
    val isReverseChecklist: Boolean = false,
    val checklistNumber: Int = 0,
    val hintsUsed: Int = 0,
    val notesMade: Int = 0,
    val errorsMade: Int = 0,
    val imported: Boolean = false,
    @Serializable(with = LocalDateSerializer::class) val dailyDate: LocalDate? = null,
    val board: String,
) {
    /** The mode of the entry, or null if [modeLevel] names none. */
    fun modeOrNull(): GameMode? = legacyGameModeOrNull(modeLevel, dailyDate ?: created.toLocalDate())
}
