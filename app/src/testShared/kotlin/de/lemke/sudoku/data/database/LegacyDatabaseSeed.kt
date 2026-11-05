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
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import de.lemke.sudoku.domain.model.SudokuSize

private const val GIVEN_EVERY = 3

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
    given: (Int) -> Boolean = { it % GIVEN_EVERY == 0 },
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
                statement.bindAll(id, size, difficulty, modeLevel, created, updated)
                statement.step()
            }
        connection
            .prepare(
                "INSERT INTO field (sudokuId, gameSize, `index`, solution, value, given, hint, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                fields.forEach { field ->
                    statement.bindAll(
                        id,
                        field.gameSize ?: size,
                        field.index,
                        field.solution,
                        field.value,
                        field.given,
                        field.hint,
                        field.notes,
                    )
                    statement.step()
                    statement.reset()
                }
            }
    }
}

private fun SQLiteStatement.bindAll(vararg values: Any?) {
    values.forEachIndexed { position, value ->
        val index = position + 1
        when (value) {
            null -> bindNull(index)
            is String -> bindText(index, value)
            is Int -> bindLong(index, value.toLong())
            is Boolean -> bindLong(index, if (value) 1 else 0)
            else -> error("Unsupported value $value")
        }
    }
}
