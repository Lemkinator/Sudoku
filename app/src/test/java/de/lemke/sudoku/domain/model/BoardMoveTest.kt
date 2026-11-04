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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private val SOLUTION_4 = listOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 3, 4, 1, 4, 1, 2, 3)
private val GIVENS_4 = setOf(0, 3, 6, 9, 12, 15)

private fun board4() = Board.of(SudokuSize.FOUR, SOLUTION_4, GIVENS_4)

private fun solvedExcept(index: Int): Board =
    Board(SudokuSize.FOUR, SOLUTION_4.mapIndexed { i, digit -> if (i == index) Cell(digit) else Cell(digit, digit) })

class BoardMoveTest : ShouldSpec(
    {
        should("leave peer notes without the entered digit alone") {
            val start = board4().with(1, Cell(2, notes = NoteMask.of(2, 3))).with(4, Cell(3, notes = NoteMask.of(1, 3)))
            val result = start.apply(BoardMove.Enter(5, 4)).shouldNotBeNull()
            result.board[1].notes shouldBe NoteMask.of(2, 3)
            result.changed shouldBe setOf(5)
        }

        should("remove the entered digit from peer notes and clear the cell notes") {
            val start = board4().with(1, Cell(2, notes = NoteMask.of(2, 3))).with(2, Cell(3, notes = NoteMask.of(1, 3)))
            val result = start.apply(BoardMove.Enter(2, 3)).shouldNotBeNull()
            result.board[1].notes shouldBe NoteMask.of(2)
            result.board[2] shouldBe Cell(3, 3)
            result.changed shouldBe setOf(2, 1)
        }

        should("mark an error and keep the cell notes for a wrong digit") {
            val start = board4().with(2, Cell(3, notes = NoteMask.of(1)))
            val result = start.apply(BoardMove.Enter(2, 4)).shouldNotBeNull()
            result.error shouldBe true
            result.board[2] shouldBe Cell(3, 4, notes = NoteMask.of(1))
            result.completed shouldBe CompletedRegions.NONE
        }

        should("reject a digit that is already in the cell") {
            board4().with(2, Cell(3, 4)).apply(BoardMove.Enter(2, 4)) shouldBe null
        }

        should("replace a wrong digit with the correct one") {
            val result = board4().with(2, Cell(3, 4)).apply(BoardMove.Enter(2, 3)).shouldNotBeNull()
            result.board[2] shouldBe Cell(3, 3)
            result.error shouldBe false
        }

        should("reject a digit in a correct cell") {
            board4().with(2, Cell(3, 3)).apply(BoardMove.Enter(2, 4)) shouldBe null
        }

        should("reject every move on a given cell") {
            listOf(
                BoardMove.Erase(0),
                BoardMove.Enter(0, 2),
                BoardMove.ToggleNote(0, 2),
                BoardMove.ClearNotes(0),
                BoardMove.RevealHint(0),
            ).map { board4().apply(it) } shouldBe List(5) { null }
        }

        should("reject every move on a hint cell") {
            board4().with(1, Cell(2, 2, CellOrigin.HINT)).apply(BoardMove.Erase(1)) shouldBe null
        }

        should("reject a note on a filled cell") {
            board4().with(2, Cell(3, 4)).apply(BoardMove.ToggleNote(2, 1)) shouldBe null
        }

        should("add a note and report it") {
            val result = board4().apply(BoardMove.ToggleNote(1, 3)).shouldNotBeNull()
            result.board[1].notes shouldBe NoteMask.of(3)
            result.noteAdded shouldBe true
            result.changed shouldBe setOf(1)
        }

        should("remove a note without reporting an added note") {
            val result = board4().with(1, Cell(2, notes = NoteMask.of(3))).apply(BoardMove.ToggleNote(1, 3)).shouldNotBeNull()
            result.board[1].notes shouldBe NoteMask.EMPTY
            result.noteAdded shouldBe false
        }

        should("clear the notes of an empty cell") {
            val result = board4().with(1, Cell(2, notes = NoteMask.of(1, 4))).apply(BoardMove.ClearNotes(1)).shouldNotBeNull()
            result.board[1].notes shouldBe NoteMask.EMPTY
            board4().apply(BoardMove.ClearNotes(1)).shouldNotBeNull().changed shouldBe setOf(1)
        }

        should("reject clearing the notes of a filled cell") {
            board4().with(2, Cell(3, 4)).apply(BoardMove.ClearNotes(2)) shouldBe null
        }

        should("reject erasing an empty cell without notes") { board4().apply(BoardMove.Erase(1)) shouldBe null }

        should("erase the value and keep the notes") {
            val start = board4().with(1, Cell(2, 3, notes = NoteMask.of(1)))
            start.apply(BoardMove.Erase(1)).shouldNotBeNull().board[1] shouldBe Cell(2, notes = NoteMask.of(1))
        }

        should("erase the notes of a cell without value") {
            val start = board4().with(1, Cell(2, notes = NoteMask.of(1)))
            start.apply(BoardMove.Erase(1)).shouldNotBeNull().board[1] shouldBe Cell(2)
        }

        should("complete row, column, block and board with the last digit") {
            val result = solvedExcept(1).apply(BoardMove.Enter(1, 2)).shouldNotBeNull()
            result.completed shouldBe CompletedRegions(row = true, column = true, block = true, board = true)
        }

        should("complete the row once its last cell is correct") {
            val start = board4().with(1, Cell(2)).with(2, Cell(3)).with(5, Cell(4))
            val result = start.apply(BoardMove.Enter(1, 2)).shouldNotBeNull()
            result.completed shouldBe CompletedRegions(row = false, column = false, block = false, board = false)
            val rowDone = start.with(2, Cell(3, 3)).apply(BoardMove.Enter(1, 2)).shouldNotBeNull()
            rowDone.completed shouldBe CompletedRegions(row = true, column = false, block = false, board = false)
        }

        should("reveal the solution as a hint") {
            val result = board4().apply(BoardMove.RevealHint(1)).shouldNotBeNull()
            result.board[1] shouldBe Cell(2, 2, CellOrigin.HINT)
            result.error shouldBe false
        }

        should("remove the hint digit from peer notes and clear its own notes") {
            val start = board4().with(1, Cell(2, notes = NoteMask.of(1, 2))).with(5, Cell(4, notes = NoteMask.of(2, 3)))
            val result = start.apply(BoardMove.RevealHint(1)).shouldNotBeNull()
            result.board[5].notes shouldBe NoteMask.of(3)
            result.board[1].notes shouldBe NoteMask.EMPTY
            result.changed shouldBe setOf(1, 5)
        }

        should("complete the board with the last hint") {
            solvedExcept(1)
                .apply(BoardMove.RevealHint(1))
                .shouldNotBeNull()
                .completed.board shouldBe true
        }
    },
)
