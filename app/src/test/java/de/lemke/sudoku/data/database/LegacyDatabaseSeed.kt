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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.lemke.sudoku.domain.model.SudokuSize

/** One row of the version 2 `field` table. */
data class SeedField(
    val index: Int,
    val solution: Int?,
    val value: Int? = null,
    val given: Boolean = false,
    val hint: Boolean = false,
    val notes: String = "",
    val gameSize: Int? = null,
)

/** The solution digit of cell [index] on a valid pattern board of [size]. */
fun patternSolution(
    size: SudokuSize,
    index: Int,
): Int {
    val row = index / size.value
    val column = index % size.value
    return (size.blockSize * (row % size.blockSize) + row / size.blockSize + column) % size.value + 1
}

/** The fields of a pattern board of [size] where every cell is empty except the cells in [given] and the cells [filled] reports. */
fun patternFields(
    size: SudokuSize,
    given: (Int) -> Boolean = { it % 3 == 0 },
    filled: (Int) -> Boolean = { false },
): List<SeedField> =
    List(size.cellCount) { index ->
        val solution = patternSolution(size, index)
        val isGiven = given(index)
        SeedField(index, solution, value = solution.takeIf { isGiven || filled(index) }, given = isGiven)
    }

/** Writes games into a database created at version 2. */
class LegacyDatabaseSeed(private val connection: SQLiteConnection) {
    fun begin() = connection.execSQL("BEGIN")

    fun commit() = connection.execSQL("COMMIT")

    fun game(
        id: String,
        size: Int,
        modeLevel: Int,
        created: String,
        updated: String,
        fields: List<SeedField>,
        difficulty: Int = 1,
    ) {
        connection
            .prepare(
                "INSERT INTO sudoku (id, size, difficulty, modeLevel, regionalHighlightingUsed, numberHighlightingUsed, eraserUsed, " +
                    "isChecklist, isReverseChecklist, checklistNumber, hintsUsed, notesMade, errorsMade, created, updated, seconds) " +
                    "VALUES (?, ?, ?, ?, 1, 0, 1, 0, 0, 0, 2, 3, 4, ?, ?, 95)",
            ).use { statement ->
                statement.bindText(1, id)
                statement.bindLong(2, size.toLong())
                statement.bindLong(3, difficulty.toLong())
                statement.bindLong(4, modeLevel.toLong())
                statement.bindText(5, created)
                statement.bindText(6, updated)
                statement.step()
            }
        connection
            .prepare(
                "INSERT INTO field (sudokuId, gameSize, `index`, solution, value, given, hint, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                fields.forEach { field ->
                    statement.bindText(1, id)
                    statement.bindLong(2, (field.gameSize ?: size).toLong())
                    statement.bindLong(3, field.index.toLong())
                    field.solution?.let { statement.bindLong(4, it.toLong()) } ?: statement.bindNull(4)
                    field.value?.let { statement.bindLong(5, it.toLong()) } ?: statement.bindNull(5)
                    statement.bindLong(6, if (field.given) 1 else 0)
                    statement.bindLong(7, if (field.hint) 1 else 0)
                    statement.bindText(8, field.notes)
                    statement.step()
                    statement.reset()
                }
            }
    }
}
