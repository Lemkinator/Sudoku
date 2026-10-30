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

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable

/** One game of a backup. [difficulty] is the name of a `Difficulty`. */
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
    val board: BoardBackup,
)

/**
 * The cells of a game in cell order. [solution] and [values] hold one digit char of a sudoku (`1`..`9`, `A`..`G`) per cell,
 * and [values] marks an empty cell with `.`. [givens] and [hints] list the indices of those cells, and [notes] maps a
 * cell index to the digit chars of its notes.
 */
@Serializable
data class BoardBackup(
    val solution: String,
    val values: String,
    val givens: List<Int> = emptyList(),
    val hints: List<Int> = emptyList(),
    val notes: Map<Int, String> = emptyMap(),
)
