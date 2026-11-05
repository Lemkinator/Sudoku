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

import de.lemke.sudoku.domain.model.BoardMove
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

private fun sudoku(): Sudoku = testSudoku(difficulty = Difficulty.VERY_EASY)

private fun Sudoku.withCell(
    index: Int,
    cell: Cell,
): Sudoku = copy(board = board.with(index, cell))

private fun selections(
    from: BoardInput,
    taps: List<InputTap>,
    sudoku: Sudoku = sudoku(),
): List<Selection?> = taps.map { reduceInput(from, it, sudoku).input.selection }

class BoardInputTest : ShouldSpec(
    {
        val none = BoardInput()

        should("select a cell, a digit, the eraser or the hint while nothing is selected") {
            selections(none, listOf(InputTap.Cell(1), InputTap.Digit(2), InputTap.Delete, InputTap.Hint)) shouldBe
                listOf(Selection.Cell(1), Selection.Digit(2), Selection.Delete, Selection.Hint)
        }

        should("select nothing when clearing while nothing is selected") {
            reduceInput(none, InputTap.Clear, sudoku()) shouldBe InputStep(none, move = null)
        }

        should("enter the digit and clear the selection when a digit follows a cell") {
            reduceInput(BoardInput(Selection.Cell(1)), InputTap.Digit(2), sudoku()) shouldBe InputStep(none, BoardMove.Enter(1, 2))
        }

        should("toggle a note when a digit follows a cell in notes mode") {
            val step = reduceInput(BoardInput(Selection.Cell(1), notesMode = true), InputTap.Digit(2), sudoku())

            step shouldBe InputStep(BoardInput(notesMode = true), BoardMove.ToggleNote(1, 2))
        }

        should("erase the cell when the eraser follows a cell, and clear its notes in notes mode") {
            reduceInput(BoardInput(Selection.Cell(1)), InputTap.Delete, sudoku()).move shouldBe BoardMove.Erase(1)
            reduceInput(BoardInput(Selection.Cell(1), notesMode = true), InputTap.Delete, sudoku()).move shouldBe BoardMove.ClearNotes(1)
        }

        should("reveal the cell when the hint follows a cell") {
            reduceInput(BoardInput(Selection.Cell(1)), InputTap.Hint, sudoku()) shouldBe InputStep(none, BoardMove.RevealHint(1))
        }

        should("switch the cell, or drop it when tapped again or cleared") {
            selections(BoardInput(Selection.Cell(1)), listOf(InputTap.Cell(2), InputTap.Cell(1), InputTap.Clear)) shouldBe
                listOf(Selection.Cell(2), null, null)
        }

        should("enter the digit and keep it selected when a cell follows a digit") {
            reduceInput(BoardInput(Selection.Digit(2)), InputTap.Cell(1), sudoku()) shouldBe
                InputStep(BoardInput(Selection.Digit(2)), BoardMove.Enter(1, 2))
        }

        should("toggle a note when a cell follows a digit in notes mode") {
            reduceInput(BoardInput(Selection.Digit(2), notesMode = true), InputTap.Cell(1), sudoku()).move shouldBe
                BoardMove.ToggleNote(1, 2)
        }

        should("jump to the next open digit when the entered digit completes") {
            val base = sudoku()
            val almost =
                base.board.cells.indices.fold(
                    base,
                ) { game, index -> game.withCell(index, game.board[index].copy(value = game.board[index].solution)) }
            val open = almost.withCell(5, Cell(4)).withCell(0, Cell(1))

            reduceInput(BoardInput(Selection.Digit(4)), InputTap.Cell(5), open).input.selection shouldBe Selection.Digit(1)
        }

        should("drop the digit when every digit is complete") {
            val solved = testSudoku(completed = true).withCell(5, Cell(4))

            reduceInput(BoardInput(Selection.Digit(4)), InputTap.Cell(5), solved).input.selection shouldBe null
        }

        should("keep the digit selected when its move is rejected") {
            reduceInput(BoardInput(Selection.Digit(2)), InputTap.Cell(0), sudoku()).input.selection shouldBe Selection.Digit(2)
        }

        should("switch to another control, or drop the digit when tapped again or cleared") {
            selections(
                BoardInput(Selection.Digit(2)),
                listOf(InputTap.Digit(3), InputTap.Delete, InputTap.Hint, InputTap.Digit(2), InputTap.Clear),
            ) shouldBe listOf(Selection.Digit(3), Selection.Delete, Selection.Hint, null, null)
        }

        should("erase the cell and keep the eraser selected when a cell follows the eraser") {
            reduceInput(BoardInput(Selection.Delete), InputTap.Cell(1), sudoku()) shouldBe
                InputStep(BoardInput(Selection.Delete), BoardMove.Erase(1))
            reduceInput(BoardInput(Selection.Delete, notesMode = true), InputTap.Cell(1), sudoku()).move shouldBe BoardMove.ClearNotes(1)
        }

        should("switch from the eraser to another control, or drop it when tapped again or cleared") {
            selections(BoardInput(Selection.Delete), listOf(InputTap.Digit(3), InputTap.Hint, InputTap.Delete, InputTap.Clear)) shouldBe
                listOf(Selection.Digit(3), Selection.Hint, null, null)
        }

        should("reveal the cell and keep the hint selected while a hint is left") {
            val hintsLeft = testSudoku(size = SudokuSize.NINE)

            reduceInput(BoardInput(Selection.Hint), InputTap.Cell(1), hintsLeft) shouldBe
                InputStep(BoardInput(Selection.Hint), BoardMove.RevealHint(1))
        }

        should("keep the hint selected when its cell is refused while a hint is left") {
            val hintsLeft = testSudoku(size = SudokuSize.NINE)

            reduceInput(BoardInput(Selection.Hint), InputTap.Cell(0), hintsLeft) shouldBe
                InputStep(BoardInput(Selection.Hint), BoardMove.RevealHint(0))
        }

        should("drop the hint selection when the last hint is used") {
            reduceInput(BoardInput(Selection.Hint), InputTap.Cell(1), sudoku()) shouldBe InputStep(none, BoardMove.RevealHint(1))
        }

        should("switch from the hint to another control, or drop it when tapped again or cleared") {
            selections(BoardInput(Selection.Hint), listOf(InputTap.Digit(3), InputTap.Delete, InputTap.Hint, InputTap.Clear)) shouldBe
                listOf(Selection.Digit(3), Selection.Delete, null, null)
        }

        should("highlight the digit of the selected cell and the neighbors") {
            val board = sudoku().board.with(1, Cell(2, 2))
            val render = boardRenderOf(board, BoardInput(Selection.Cell(1)), highlightRegional = true, highlightNumber = true)

            listOf(render.selected, render.highlightedDigit, render.showPeers) shouldBe listOf(1, 2, true)
        }

        should("highlight the selected digit and no cell") {
            val render = boardRenderOf(sudoku().board, BoardInput(Selection.Digit(3)), highlightRegional = true, highlightNumber = true)

            listOf(render.selected, render.highlightedDigit) shouldBe listOf(null, 3)
        }

        should("leave notes plain when number highlighting is off") {
            val render = boardRenderOf(sudoku().board, BoardInput(Selection.Digit(5)), highlightRegional = true, highlightNumber = false)

            render.highlightedDigit shouldBe null
        }

        should("highlight no digit for the eraser, the hint or no selection") {
            listOf(Selection.Delete, Selection.Hint, null).map {
                boardRenderOf(sudoku().board, BoardInput(it), highlightRegional = false, highlightNumber = true).highlightedDigit
            } shouldBe listOf(null, null, null)
        }

        should("hide the peers without regional highlighting") {
            val render = boardRenderOf(sudoku().board, BoardInput(Selection.Cell(1)), highlightRegional = false, highlightNumber = true)

            render.showPeers shouldBe false
        }
    },
)
