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
import de.lemke.sudoku.data.database.toLegacyModeLevel
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.domain.model.toSudokuDigitChar
import de.lemke.sudoku.domain.model.toSudokuInt
import java.time.ZoneId
import java.time.temporal.ChronoUnit.SECONDS

private const val EMPTY_CELL = '.'

/**
 * The game of [entry], or null if the entry is broken. A broken entry fails a shape check, has a mode level that names no game mode,
 * negative seconds, or a note digit beyond its board size. A given or hint cell needs no value that matches its solution. [zone] places
 * the updated stamp on the device clock.
 */
fun sudokuFromBackup(
    entry: SudokuBackupEntry,
    zone: ZoneId,
): Sudoku? {
    if (entry.seconds < 0) return null
    val size = SudokuSize.fromValueOrNull(entry.size)
    val difficulty = Difficulty.fromNameOrNull(entry.difficulty)
    val mode = legacyGameModeOrNull(entry.modeLevel)
    val board = size?.let { entry.board.toBoardOrNull(it) }
    return if (size != null && difficulty != null && mode != null && board != null) {
        Sudoku(
            info =
                GameInfo(
                    id = SudokuId(entry.id),
                    size = size,
                    difficulty = difficulty,
                    mode = mode,
                    created = entry.created.toLocalDateTime(),
                    updated = entry.updated.atZoneSameInstant(zone).toLocalDateTime(),
                    seconds = entry.seconds,
                    hintsUsed = entry.hintsUsed,
                    notesMade = entry.notesMade,
                    errorsMade = entry.errorsMade,
                    regionalHighlightingUsed = entry.regionalHighlightingUsed,
                    numberHighlightingUsed = entry.numberHighlightingUsed,
                    eraserUsed = entry.eraserUsed,
                    isChecklist = entry.isChecklist,
                    isReverseChecklist = entry.isReverseChecklist,
                    checklistNumber = entry.checklistNumber,
                ),
            board = board,
        )
    } else {
        null
    }
}

/**
 * The entry of [sudoku], or null if [sudokuFromBackup] cannot read the entry back, so every written entry restores. [zone] gives the
 * offset of both stamps.
 */
fun sudokuToBackup(
    sudoku: Sudoku,
    zone: ZoneId,
): SudokuBackupEntry? = entryOf(sudoku, zone).takeIf { sudokuFromBackup(it, zone) != null }

private fun entryOf(
    sudoku: Sudoku,
    zone: ZoneId,
): SudokuBackupEntry =
    with(sudoku.info) {
        SudokuBackupEntry(
            id = id.value,
            size = size.value,
            difficulty = difficulty.name,
            modeLevel = mode.toLegacyModeLevel(),
            created = created.atZone(zone).toOffsetDateTime().truncatedTo(SECONDS),
            updated = updated.atZone(zone).toOffsetDateTime().truncatedTo(SECONDS),
            seconds = seconds,
            regionalHighlightingUsed = regionalHighlightingUsed,
            numberHighlightingUsed = numberHighlightingUsed,
            eraserUsed = eraserUsed,
            isChecklist = isChecklist,
            isReverseChecklist = isReverseChecklist,
            checklistNumber = checklistNumber,
            hintsUsed = hintsUsed,
            notesMade = notesMade,
            errorsMade = errorsMade,
            board = sudoku.board.toBackup(),
        )
    }

/** The progress of the game of [entry]; a broken entry has none. */
fun progressOf(
    entry: SudokuBackupEntry,
    zone: ZoneId,
): Int = sudokuFromBackup(entry, zone)?.progress ?: 0

private fun Board.toBackup(): BoardBackup =
    BoardBackup(
        solution = cells.map { digitChar(it.solution) }.joinToString(""),
        values = cells.map { cell -> cell.value?.let(::digitChar) ?: EMPTY_CELL }.joinToString(""),
        givens = cells.indices.filter { cells[it].origin == CellOrigin.GIVEN },
        hints = cells.indices.filter { cells[it].origin == CellOrigin.HINT },
        notes = cells.indices.filterNot { cells[it].notes.isEmpty() }.associateWith { cells[it].notes.toSudokuChars() },
    )

private fun digitChar(digit: Int): Char = digit.toSudokuDigitChar()

private fun NoteMask.toSudokuChars(): String = digits.map(::digitChar).joinToString("")

private fun BoardBackup.toBoardOrNull(size: SudokuSize): Board? {
    val cells = 0 until size.cellCount
    val marked = givens + hints
    val shapeValid =
        solution.length == size.cellCount &&
            values.length == size.cellCount &&
            marked.toSet().size == marked.size &&
            marked.all { it in cells } &&
            notes.keys.all { it in cells }
    if (!shapeValid) return null
    val decoded = cells.map { cellOrNull(it, size) }
    return if (null in decoded) null else Board(size, decoded.requireNoNulls())
}

private fun BoardBackup.cellOrNull(
    index: Int,
    size: SudokuSize,
): Cell? {
    val answer = solution[index].toDigitOrNull(size)
    val cell = values[index]
    val value = cell.toDigitOrNull(size)
    val noteDigits = notes[index].orEmpty().map { it.toDigitOrNull(size) }
    val validValue = cell == EMPTY_CELL || value != null
    val origin =
        when (index) {
            in givens -> CellOrigin.GIVEN
            in hints -> CellOrigin.HINT
            else -> CellOrigin.PLAYER
        }
    return if (answer != null && validValue && null !in noteDigits) {
        Cell(solution = answer, value = value, origin = origin, notes = NoteMask.of(*noteDigits.requireNoNulls().toIntArray()))
    } else {
        null
    }
}

private fun Char.toDigitOrNull(size: SudokuSize): Int? = toSudokuInt()?.takeIf { it <= size.value }
