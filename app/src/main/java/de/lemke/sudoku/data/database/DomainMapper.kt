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

import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.domain.model.toSudokuDigitChar
import de.lemke.sudoku.domain.model.toSudokuInt

private const val LEGACY_MODE_NORMAL = 0
private const val LEGACY_MODE_DAILY = -1

/** A stored field of the cell at [index]. */
data class IndexedCell(
    val index: Int,
    val cell: Cell,
)

/** The mode level the stored tables and the export format keep for this mode. */
fun GameMode.toLegacyModeLevel(): Int =
    when (this) {
        GameMode.Normal -> LEGACY_MODE_NORMAL
        GameMode.Daily -> LEGACY_MODE_DAILY
        is GameMode.Level -> number
    }

/** The mode of a stored mode level, or null if no mode has that level. */
fun legacyGameModeOrNull(modeLevel: Int): GameMode? =
    when {
        modeLevel == LEGACY_MODE_NORMAL -> GameMode.Normal
        modeLevel == LEGACY_MODE_DAILY -> GameMode.Daily
        modeLevel > LEGACY_MODE_NORMAL -> GameMode.Level(modeLevel)
        else -> null
    }

fun sudokuFromDb(sudokuWithFields: SudokuWithFields?): Sudoku? {
    val row = sudokuWithFields?.sudoku
    val size = row?.let { SudokuSize.fromValueOrNull(it.size) }
    val mode = row?.let { legacyGameModeOrNull(it.modeLevel) }
    val fields = sudokuWithFields?.fields.orEmpty()
    val board = size?.takeIf { fields.all { field -> field.gameSize == it.value } }?.let { boardOrNull(it, fields.map(::fieldFromDb)) }
    return if (row != null && size != null && mode != null && board != null) {
        Sudoku(
            info =
                GameInfo(
                    id = SudokuId(row.id),
                    size = size,
                    difficulty = difficultyOf(row.difficulty),
                    mode = mode,
                    created = row.created,
                    updated = row.updated,
                    seconds = row.seconds,
                    hintsUsed = row.hintsUsed,
                    notesMade = row.notesMade,
                    errorsMade = row.errorsMade,
                    regionalHighlightingUsed = row.regionalHighlightingUsed,
                    numberHighlightingUsed = row.numberHighlightingUsed,
                    eraserUsed = row.eraserUsed,
                    isChecklist = row.isChecklist,
                    isReverseChecklist = row.isReverseChecklist,
                    checklistNumber = row.checklistNumber,
                ),
            board = board,
        )
    } else {
        null
    }
}

private fun difficultyOf(code: Int): Difficulty = Difficulty.fromCodeOrNull(code) ?: Difficulty.MEDIUM

/** The board of [cells] in cell order, or null unless they are exactly one decodable cell per index of [size]. */
private fun boardOrNull(
    size: SudokuSize,
    cells: List<IndexedCell?>,
): Board? {
    val decoded = cells.filterNotNull()
    if (decoded.size != cells.size) return null
    val sorted = decoded.sortedBy { it.index }
    return if (sorted.map { it.index } == (0 until size.cellCount).toList()) Board(size, sorted.map { it.cell }) else null
}

/**
 * The cell of the stored values, or null if the solution or value is no digit of [size]. A given cell without value takes its solution,
 * and a note that is no digit of [size] is dropped.
 */
private fun cellOrNull(
    size: SudokuSize,
    solution: Int?,
    value: Int?,
    given: Boolean,
    hint: Boolean,
    notes: String,
): Cell? {
    val digits = 1..size.value
    if (solution == null || solution !in digits || (value != null && value !in digits)) return null
    val origin =
        when {
            given -> CellOrigin.GIVEN
            hint -> CellOrigin.HINT
            else -> CellOrigin.PLAYER
        }
    val noteDigits = notes.mapNotNull { it.toSudokuInt() }.filter { it <= size.value }
    return Cell(
        solution = solution,
        value = value ?: solution.takeIf { origin == CellOrigin.GIVEN },
        origin = origin,
        notes = NoteMask.of(*noteDigits.toIntArray()),
    )
}

/** The digits of these notes as sudoku chars in ascending order. */
internal fun NoteMask.toSudokuChars(): String = digits.map { it.toSudokuDigitChar() }.joinToString("")

fun sudokuToDb(sudoku: Sudoku): SudokuDb =
    with(sudoku.info) {
        SudokuDb(
            id = id.value,
            size = size.value,
            difficulty = difficulty.code,
            modeLevel = mode.toLegacyModeLevel(),
            created = created,
            updated = updated,
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
        )
    }

fun sudokuWithFieldsToDb(sudoku: Sudoku): SudokuWithFields =
    SudokuWithFields(
        sudoku = sudokuToDb(sudoku),
        fields = sudoku.board.cells.mapIndexed { index, cell -> fieldToDb(index, cell, sudoku.info.id, sudoku.board.size) },
    )

fun fieldFromDb(fieldDb: FieldDb?): IndexedCell? {
    val size = fieldDb?.let { SudokuSize.fromValueOrNull(it.gameSize) }
    return if (fieldDb != null && size != null && fieldDb.index in 0 until size.cellCount) {
        cellOrNull(
            size,
            fieldDb.solution,
            fieldDb.value,
            fieldDb.given,
            fieldDb.hint,
            fieldDb.notes,
        )?.let { IndexedCell(fieldDb.index, it) }
    } else {
        null
    }
}

fun fieldToDb(
    index: Int,
    cell: Cell,
    sudokuId: SudokuId,
    size: SudokuSize,
): FieldDb =
    FieldDb(
        sudokuId = sudokuId.value,
        index = index,
        gameSize = size.value,
        solution = cell.solution,
        value = cell.value,
        given = cell.origin == CellOrigin.GIVEN,
        hint = cell.origin == CellOrigin.HINT,
        notes = cell.notes.toSudokuChars(),
    )

fun sudokuFromExport(sudokuExport: SudokuExport): Sudoku? {
    val size = SudokuSize.fromValueOrNull(sudokuExport.size)
    val mode = legacyGameModeOrNull(sudokuExport.modeLevel)
    val board = size?.let { boardOrNull(it, sudokuExport.fields.map { field -> fieldFromExport(field, it) }) }
    return if (size != null && mode != null && board != null) {
        Sudoku(
            info =
                GameInfo(
                    id = SudokuId(sudokuExport.id),
                    size = size,
                    difficulty = difficultyOf(sudokuExport.difficulty),
                    mode = mode,
                    created = sudokuExport.created,
                    updated = sudokuExport.updated,
                    seconds = sudokuExport.seconds,
                    hintsUsed = sudokuExport.hintsUsed ?: 0,
                    notesMade = sudokuExport.notesMade ?: 0,
                    errorsMade = sudokuExport.errorsMade ?: 0,
                    regionalHighlightingUsed = sudokuExport.regionalHighlightingUsed == true,
                    numberHighlightingUsed = sudokuExport.numberHighlightingUsed == true,
                    eraserUsed = sudokuExport.eraserUsed == true,
                    isChecklist = sudokuExport.isChecklist == true,
                    isReverseChecklist = sudokuExport.isReverseChecklist == true,
                    checklistNumber = sudokuExport.checklistNumber ?: 0,
                ),
            board = board,
        )
    } else {
        null
    }
}

fun sudokuToExport(sudoku: Sudoku): SudokuExport =
    with(sudoku.info) {
        SudokuExport(
            id = id.value,
            size = size.value,
            difficulty = difficulty.code,
            modeLevel = mode.toLegacyModeLevel(),
            created = created,
            updated = updated,
            seconds = seconds,
            regionalHighlightingUsed = regionalHighlightingUsed.takeIf { it },
            numberHighlightingUsed = numberHighlightingUsed.takeIf { it },
            eraserUsed = eraserUsed.takeIf { it },
            isChecklist = isChecklist.takeIf { it },
            isReverseChecklist = isReverseChecklist.takeIf { it },
            checklistNumber = checklistNumber.takeIf { it > 0 },
            hintsUsed = hintsUsed.takeIf { it > 0 },
            notesMade = notesMade.takeIf { it > 0 },
            errorsMade = errorsMade.takeIf { it > 0 },
            fields = sudoku.board.cells.mapIndexed { index, cell -> fieldToExport(index, cell) },
        )
    }

fun fieldFromExport(
    fieldExport: FieldExport,
    size: SudokuSize,
): IndexedCell? {
    if (fieldExport.index !in 0 until size.cellCount) return null
    return cellOrNull(
        size,
        fieldExport.solution,
        fieldExport.value,
        fieldExport.given == true,
        fieldExport.hint == true,
        fieldExport.notes.orEmpty(),
    )?.let { IndexedCell(fieldExport.index, it) }
}

fun fieldToExport(
    index: Int,
    cell: Cell,
): FieldExport =
    FieldExport(
        index = index,
        value = cell.value,
        solution = cell.solution,
        given = (cell.origin == CellOrigin.GIVEN).takeIf { it },
        hint = (cell.origin == CellOrigin.HINT).takeIf { it },
        notes = cell.notes.toSudokuChars().ifBlank { null },
    )
