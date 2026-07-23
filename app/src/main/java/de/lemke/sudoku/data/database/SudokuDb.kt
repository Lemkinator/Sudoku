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

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDateTime

@Entity(tableName = "sudoku")
data class SudokuDb(
    @PrimaryKey
    val id: String,
    val size: Int,
    val difficulty: Int,
    val modeLevel: Int,
    val regionalHighlightingUsed: Boolean,
    val numberHighlightingUsed: Boolean,
    val eraserUsed: Boolean,
    val isChecklist: Boolean,
    val isReverseChecklist: Boolean,
    val checklistNumber: Int,
    val hintsUsed: Int,
    val notesMade: Int,
    val errorsMade: Int,
    val created: LocalDateTime,
    val updated: LocalDateTime,
    val seconds: Int,
)

data class SudokuWithFields(
    @Embedded
    val sudoku: SudokuDb,
    @Relation(
        parentColumn = "id",
        entityColumn = "sudokuId",
    )
    val fields: List<FieldDb>,
)
