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

package de.lemke.sudoku.ui.game

import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.BoardMove
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.apply
import java.io.Serializable

sealed interface Selection : Serializable {
    data class Cell(val index: Int) : Selection

    data class Digit(val digit: Int) : Selection

    data object Delete : Selection

    data object Hint : Selection
}

data class BoardInput(
    val selection: Selection? = null,
    val notesMode: Boolean = false,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1
    }
}

sealed interface InputTap {
    data class Cell(val index: Int) : InputTap

    data class Digit(val digit: Int) : InputTap

    data object Delete : InputTap

    data object Hint : InputTap

    data object Clear : InputTap
}

data class InputStep(
    val input: BoardInput,
    val move: BoardMove?,
)

fun reduceInput(
    input: BoardInput,
    tap: InputTap,
    sudoku: Sudoku,
): InputStep =
    when (val selection = input.selection) {
        null -> InputStep(input.copy(selection = tap.asSelection()), move = null)
        is Selection.Cell -> fromCell(input, selection, tap)
        is Selection.Digit -> fromDigit(input, selection, tap, sudoku)
        Selection.Delete -> fromDelete(input, tap)
        Selection.Hint -> fromHint(input, tap, sudoku)
    }

private fun InputTap.asSelection(): Selection? =
    when (this) {
        is InputTap.Cell -> Selection.Cell(index)
        is InputTap.Digit -> Selection.Digit(digit)
        InputTap.Delete -> Selection.Delete
        InputTap.Hint -> Selection.Hint
        InputTap.Clear -> null
    }

private fun InputTap.sameAs(selection: Selection): Boolean = asSelection() == selection

private fun fromCell(
    input: BoardInput,
    cell: Selection.Cell,
    tap: InputTap,
): InputStep =
    when {
        tap == InputTap.Clear || tap.sameAs(cell) -> InputStep(input.copy(selection = null), move = null)
        tap is InputTap.Cell -> InputStep(input.copy(selection = Selection.Cell(tap.index)), move = null)
        tap is InputTap.Digit -> InputStep(input.copy(selection = null), input.enter(cell.index, tap.digit))
        tap == InputTap.Delete -> InputStep(input.copy(selection = null), input.erase(cell.index))
        else -> InputStep(input.copy(selection = null), BoardMove.RevealHint(cell.index))
    }

private fun fromDigit(
    input: BoardInput,
    digit: Selection.Digit,
    tap: InputTap,
    sudoku: Sudoku,
): InputStep =
    when {
        tap == InputTap.Clear || tap.sameAs(digit) -> {
            InputStep(input.copy(selection = null), move = null)
        }

        tap is InputTap.Cell -> {
            val move = input.enter(tap.index, digit.digit)
            val completed = sudoku.board.afterwards(move).completedDigits
            InputStep(input.copy(selection = nextOpenDigit(digit.digit, completed, sudoku.board.size.value)), move)
        }

        else -> {
            InputStep(input.copy(selection = tap.asSelection()), move = null)
        }
    }

private fun fromDelete(
    input: BoardInput,
    tap: InputTap,
): InputStep =
    when {
        tap == InputTap.Clear || tap == InputTap.Delete -> InputStep(input.copy(selection = null), move = null)
        tap is InputTap.Cell -> InputStep(input, input.erase(tap.index))
        else -> InputStep(input.copy(selection = tap.asSelection()), move = null)
    }

private fun fromHint(
    input: BoardInput,
    tap: InputTap,
    sudoku: Sudoku,
): InputStep =
    when {
        tap == InputTap.Clear || tap == InputTap.Hint -> {
            InputStep(input.copy(selection = null), move = null)
        }

        tap is InputTap.Cell -> {
            val move = BoardMove.RevealHint(tap.index)
            val hintLeft = sudoku.afterwards(move).info.isHintAvailable
            InputStep(input.copy(selection = Selection.Hint.takeIf { hintLeft }), move)
        }

        else -> {
            InputStep(input.copy(selection = tap.asSelection()), move = null)
        }
    }

private fun BoardInput.enter(
    index: Int,
    digit: Int,
): BoardMove = if (notesMode) BoardMove.ToggleNote(index, digit) else BoardMove.Enter(index, digit)

private fun BoardInput.erase(index: Int): BoardMove = if (notesMode) BoardMove.ClearNotes(index) else BoardMove.Erase(index)

private fun nextOpenDigit(
    current: Int,
    completed: Set<Int>,
    digitCount: Int,
): Selection.Digit? =
    generateSequence(current) { it % digitCount + 1 }
        .take(digitCount)
        .firstOrNull { it !in completed }
        ?.let(Selection::Digit)

private fun Board.afterwards(move: BoardMove): Board {
    val result = apply(move)
    return if (result == null) this else result.board
}

private fun Sudoku.afterwards(move: BoardMove): Sudoku {
    val played = play(move)
    return if (played == null) this else played.sudoku
}
