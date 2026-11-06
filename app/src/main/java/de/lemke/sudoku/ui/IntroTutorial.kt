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

package de.lemke.sudoku.ui

import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.BoardMove
import de.lemke.sudoku.domain.model.apply
import de.lemke.sudoku.ui.game.BoardInput
import de.lemke.sudoku.ui.game.InputTap
import de.lemke.sudoku.ui.game.Selection
import java.io.Serializable

enum class IntroStep {
    BLOCKS,
    SELECTION,
    SELECT_CELL,
    ENTER_NUMBER,
    SELECT_NUMBER,
    ENTER_ROW_COLUMN,
    ENTER_BLOCK,
    NUMBER_ENTRY,
    NUMBER_ENTRY_DEMO,
    TOOLS,
    NOTES,
}

data class IntroState(
    val step: IntroStep = IntroStep.BLOCKS,
    val input: BoardInput = BoardInput(),
    val entries: Map<Int, Int> = emptyMap(),
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1
    }
}

internal const val DEMO_CELL_INDEX_4 = 4
internal const val DEMO_CELL_INDEX_24 = 24
internal const val DEMO_CELL_INDEX_49 = 49
internal const val DEMO_ENTER_DIGIT = 5
internal const val DEMO_SELECT_DIGIT = 2

fun IntroState.next(): IntroState = if (step == IntroStep.entries.last()) this else copy(step = IntroStep.entries[step.ordinal + 1])

fun IntroState.board(base: Board): Board =
    entries.entries.fold(base) { board, (index, digit) ->
        when (val result = board.apply(BoardMove.Enter(index, digit))) {
            null -> board
            else -> result.board
        }
    }

fun IntroState.tap(tap: InputTap): IntroState =
    when {
        tap == InputTap.Clear -> {
            copy(input = input.copy(selection = null))
        }

        step == IntroStep.SELECT_CELL && input.selection == null && tap == InputTap.Cell(DEMO_CELL_INDEX_4) -> {
            next().copy(input = input.copy(selection = Selection.Cell(DEMO_CELL_INDEX_4)))
        }

        step == IntroStep.ENTER_NUMBER && input.selection ==
            Selection.Cell(
                DEMO_CELL_INDEX_4,
            ) && tap == InputTap.Digit(DEMO_ENTER_DIGIT) -> {
            next().copy(input = input.copy(selection = null), entries = entries + (DEMO_CELL_INDEX_4 to DEMO_ENTER_DIGIT))
        }

        step == IntroStep.SELECT_NUMBER && input.selection == null && tap == InputTap.Digit(DEMO_SELECT_DIGIT) -> {
            next().copy(input = input.copy(selection = Selection.Digit(DEMO_SELECT_DIGIT)))
        }

        step == IntroStep.ENTER_ROW_COLUMN -> {
            enterSelectedDigit(tap, DEMO_CELL_INDEX_49)
        }

        step == IntroStep.ENTER_BLOCK -> {
            enterSelectedDigit(tap, DEMO_CELL_INDEX_24)
        }

        else -> {
            this
        }
    }

private fun IntroState.enterSelectedDigit(
    tap: InputTap,
    index: Int,
): IntroState {
    val digit = (input.selection as? Selection.Digit)?.digit ?: return this
    return if (tap == InputTap.Cell(index)) next().copy(entries = entries + (index to digit)) else this
}

fun IntroState.toggleNotes(): IntroState = if (step == IntroStep.NOTES) copy(input = input.copy(notesMode = !input.notesMode)) else this

fun IntroState.erase(index: Int): IntroState = copy(entries = entries - index)
