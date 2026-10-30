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

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.domain.model.toSudokuChar
import de.lemke.sudoku.domain.model.toSudokuInt
import java.time.ZoneId
import java.time.temporal.ChronoUnit.SECONDS

private const val EMPTY_CELL = '.'

/**
 * The game of [entry], or null if the entry is broken. A broken entry fails a shape check; a given or hint cell needs no value that
 * matches its solution, because `sudokuFromDb` reads such a game too. [zone] places the updated stamp on the device clock.
 */
fun sudokuFromBackup(
    entry: SudokuBackupEntry,
    zone: ZoneId,
): Sudoku? {
    val size = SudokuSize.fromValueOrNull(entry.size)
    val difficulty = Difficulty.fromNameOrNull(entry.difficulty)
    val fields = size?.let { entry.board.toFieldsOrNull(it) }
    val countersValid = entry.modeLevel >= Sudoku.MODE_DAILY && entry.seconds >= 0
    return if (size != null && difficulty != null && fields != null && countersValid) {
        Sudoku.create(
            sudokuId = SudokuId(entry.id),
            size = size,
            difficulty = difficulty,
            modeLevel = entry.modeLevel,
            regionalHighlightingUsed = entry.regionalHighlightingUsed,
            numberHighlightingUsed = entry.numberHighlightingUsed,
            eraserUsed = entry.eraserUsed,
            isChecklist = entry.isChecklist,
            isReverseChecklist = entry.isReverseChecklist,
            checklistNumber = entry.checklistNumber,
            hintsUsed = entry.hintsUsed,
            notesMade = entry.notesMade,
            errorsMade = entry.errorsMade,
            created = entry.created.toLocalDateTime(),
            updated = entry.updated.atZoneSameInstant(zone).toLocalDateTime(),
            seconds = entry.seconds,
            fields = fields,
        )
    } else {
        null
    }
}

/** The entry of [sudoku], or null if a solution or value has no digit char. [zone] gives the offset of both stamps. */
fun sudokuToBackup(
    sudoku: Sudoku,
    zone: ZoneId,
): SudokuBackupEntry? =
    sudoku.fields.toBoardBackupOrNull()?.let { board ->
        SudokuBackupEntry(
            id = sudoku.id.value,
            size = sudoku.size.value,
            difficulty = sudoku.difficulty.name,
            modeLevel = sudoku.modeLevel,
            created =
                sudoku.created
                    .atZone(zone)
                    .toOffsetDateTime()
                    .truncatedTo(SECONDS),
            updated =
                sudoku.updated
                    .atZone(zone)
                    .toOffsetDateTime()
                    .truncatedTo(SECONDS),
            seconds = sudoku.seconds,
            regionalHighlightingUsed = sudoku.regionalHighlightingUsed,
            numberHighlightingUsed = sudoku.numberHighlightingUsed,
            eraserUsed = sudoku.eraserUsed,
            isChecklist = sudoku.isChecklist,
            isReverseChecklist = sudoku.isReverseChecklist,
            checklistNumber = sudoku.checklistNumber,
            hintsUsed = sudoku.hintsUsed,
            notesMade = sudoku.notesMade,
            errorsMade = sudoku.errorsMade,
            board = board,
        )
    }

/** The progress of the game of [entry]; a broken entry has none. */
fun progressOf(
    entry: SudokuBackupEntry,
    zone: ZoneId,
): Int = sudokuFromBackup(entry, zone)?.progress ?: 0

private fun List<Field>.toBoardBackupOrNull(): BoardBackup? {
    val solution = map { it.solution.toSudokuChar() }.noNullsOrNull()
    val values = map { it.value.toCellChar() }.noNullsOrNull()
    return if (solution == null || values == null) {
        null
    } else {
        BoardBackup(
            solution = solution.joinToString(""),
            values = values.joinToString(""),
            givens = indices.filter { this[it].given },
            hints = indices.filter { this[it].hint },
            notes = indices.filter { this[it].notes.isNotEmpty() }.associateWith { this[it].notes.joinToString("") },
        )
    }
}

private fun Int?.toCellChar(): Char? = if (this == null) EMPTY_CELL else toSudokuChar()

private fun <T : Any> List<T?>.noNullsOrNull(): List<T>? = if (null in this) null else requireNoNulls()

private fun BoardBackup.toFieldsOrNull(size: SudokuSize): MutableList<Field>? {
    val cells = 0 until size.cellCount
    val marked = givens + hints
    val shapeValid =
        solution.length == size.cellCount &&
            values.length == size.cellCount &&
            marked.toSet().size == marked.size &&
            marked.all { it in cells } &&
            notes.keys.all { it in cells }
    if (!shapeValid) return null
    val fields = cells.map { fieldOrNull(it, size) }
    return if (null in fields) null else fields.requireNoNulls().toMutableList()
}

private fun BoardBackup.fieldOrNull(
    index: Int,
    size: SudokuSize,
): Field? {
    val answer = solution[index].toDigitOrNull(size)
    val cell = values[index]
    val value = cell.toDigitOrNull(size)
    val noteDigits = notes[index].orEmpty()
    val given = index in givens
    val hint = index in hints
    val validValue = cell == EMPTY_CELL || value != null
    val validNotes = noteDigits.all { it.toDigitOrNull(size) != null }
    return if (answer != null && validValue && validNotes) {
        Field(
            position = Position.create(index, size),
            solution = answer,
            value = value,
            given = given,
            hint = hint,
            notes = noteDigits.toSortedSet().toMutableList(),
        )
    } else {
        null
    }
}

private fun Char.toDigitOrNull(size: SudokuSize): Int? = toSudokuInt()?.takeIf { it <= size.value }
