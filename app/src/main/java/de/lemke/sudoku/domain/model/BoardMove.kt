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

package de.lemke.sudoku.domain.model

sealed interface BoardMove {
    val index: Int

    data class Enter(override val index: Int, val digit: Int) : BoardMove

    data class ToggleNote(override val index: Int, val digit: Int) : BoardMove

    data class ClearNotes(override val index: Int) : BoardMove

    data class Erase(override val index: Int) : BoardMove

    data class RevealHint(override val index: Int) : BoardMove
}

data class CompletedRegions(
    val row: Boolean,
    val column: Boolean,
    val block: Boolean,
    val board: Boolean,
) {
    companion object {
        val NONE = CompletedRegions(row = false, column = false, block = false, board = false)
    }
}

data class MoveResult(
    val board: Board,
    val move: BoardMove,
    val changed: Set<Int>,
    val noteAdded: Boolean,
    val error: Boolean,
    val completed: CompletedRegions,
)

/** Returns null when the move is rejected; the board stays unchanged then. */
fun Board.apply(move: BoardMove): MoveResult? {
    val cell = this[move.index]
    if (cell.locked) return null
    return when (move) {
        is BoardMove.Enter -> enter(move, cell)
        is BoardMove.ToggleNote -> toggleNote(move, cell)
        is BoardMove.ClearNotes -> clearNotes(move, cell)
        is BoardMove.Erase -> erase(move, cell)
        is BoardMove.RevealHint -> placeCorrect(move, cell.copy(value = cell.solution, origin = CellOrigin.HINT))
    }
}

private fun Board.enter(
    move: BoardMove.Enter,
    cell: Cell,
): MoveResult? =
    when {
        cell.correct || cell.value == move.digit -> {
            null
        }

        move.digit == cell.solution -> {
            placeCorrect(move, cell.copy(value = move.digit))
        }

        else -> {
            val entered = cell.copy(value = move.digit)
            MoveResult(with(move.index, entered), move, setOf(move.index), noteAdded = false, error = true, CompletedRegions.NONE)
        }
    }

private fun Board.toggleNote(
    move: BoardMove.ToggleNote,
    cell: Cell,
): MoveResult? {
    if (cell.value != null) return null
    val toggled = cell.copy(notes = cell.notes.toggled(move.digit))
    val added = move.digit in toggled.notes
    return MoveResult(with(move.index, toggled), move, setOf(move.index), added, error = false, CompletedRegions.NONE)
}

private fun Board.clearNotes(
    move: BoardMove.ClearNotes,
    cell: Cell,
): MoveResult? {
    if (cell.value != null) return null
    return MoveResult(
        with(move.index, cell.copy(notes = NoteMask.EMPTY)),
        move,
        setOf(move.index),
        noteAdded = false,
        error = false,
        CompletedRegions.NONE,
    )
}

private fun Board.erase(
    move: BoardMove.Erase,
    cell: Cell,
): MoveResult? {
    if (cell.value == null && cell.notes.isEmpty()) return null
    val erased = if (cell.value == null) cell.copy(notes = NoteMask.EMPTY) else cell.copy(value = null)
    return MoveResult(with(move.index, erased), move, setOf(move.index), noteAdded = false, error = false, CompletedRegions.NONE)
}

private fun Board.placeCorrect(
    move: BoardMove,
    placed: Cell,
): MoveResult {
    val digit = checkNotNull(placed.value)
    val changed = mutableSetOf(move.index)
    var next = with(move.index, placed.copy(notes = NoteMask.EMPTY))
    peers(move.index).forEach { peer ->
        val peerCell = next[peer]
        if (digit in peerCell.notes) {
            next = next.with(peer, peerCell.copy(notes = peerCell.notes - digit))
            changed += peer
        }
    }
    val position = next.position(move.index)
    val completed =
        CompletedRegions(
            row = next.isRowCompleted(position.row),
            column = next.isColumnCompleted(position.column),
            block = next.isBlockCompleted(position.block),
            board = next.completed,
        )
    return MoveResult(next, move, changed, noteAdded = false, error = false, completed)
}
