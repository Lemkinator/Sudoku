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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private val SOLUTION_4 = listOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 3, 4, 1, 4, 1, 2, 3)
private val GIVENS_4 = setOf(0, 3, 6, 9, 12, 15)

private fun fresh4() = Board.of(SudokuSize.FOUR, SOLUTION_4, GIVENS_4)

private fun solved4() = Board(SudokuSize.FOUR, SOLUTION_4.map { Cell(it, it) })

private fun fresh9() = Board.of(SudokuSize.NINE, List(81) { (it % 9 + it / 9 * 3 + it / 27) % 9 + 1 }, emptySet())

class BoardTest : ShouldSpec(
    {
        should("give a 9x9 cell 20 peers") { fresh9().peers(40).size shouldBe 20 }

        should("list the peers of 4x4 index 5") { fresh4().peers(5) shouldBe setOf(1, 4, 6, 7, 9, 13, 0) }

        should("list row, column and block indices of 4x4 index 5") {
            val board = fresh4()
            board.rowOf(5) shouldBe listOf(4, 5, 6, 7)
            board.columnOf(5) shouldBe listOf(1, 5, 9, 13)
            board.blockOf(5) shouldBe listOf(0, 1, 4, 5)
        }

        should("list the block indices of the last 4x4 block") { fresh4().blockOf(15) shouldBe listOf(10, 11, 14, 15) }

        should("resolve the position of an index") {
            val position = fresh4().position(6)
            listOf(position.row, position.column, position.block) shouldBe listOf(1, 2, 1)
        }

        should("keep the preserved fields through a changed cell and through the initial board") {
            val preserved = PreservedFields(byteArrayOf(0x30, 0x07))
            val board = Board(SudokuSize.FOUR, fresh4().cells, preserved)

            board.with(1, Cell(2, value = 2)).preserved shouldBe preserved
            board.initial().preserved shouldBe preserved
        }

        should("keep no reference to the list it was built from") {
            val source = fresh4().cells.toMutableList()
            val board = Board(SudokuSize.FOUR, source)

            source[1] = Cell(2, value = 2)

            board[1] shouldBe Cell(2)
            board.cells shouldBe fresh4().cells
        }

        should("expose cells that cannot be changed") {
            val cells = fresh4().cells as MutableList<Cell>

            shouldThrow<UnsupportedOperationException> { cells[1] = Cell(2, value = 2) }
        }

        should("equal a board with the same size and cells, with the same hash code") {
            fresh4() shouldBe fresh4()
            fresh4().hashCode() shouldBe fresh4().hashCode()
            fresh4() shouldNotBe fresh4().with(1, Cell(2, value = 2))
            fresh4().equals("board") shouldBe false
            fresh4() shouldNotBe fresh9()
            fresh4() shouldNotBe Board(SudokuSize.FOUR, fresh4().cells, PreservedFields(byteArrayOf(0x30, 0x07)))
        }

        should("describe itself by size and cells") {
            fresh4().toString() shouldBe
                "Board(size=FOUR, cells=${fresh4().cells}, preserved=PreservedFields(0 bytes))"
        }

        should("reject a board with 15 of 16 cells") {
            shouldThrow<IllegalArgumentException> { Board(SudokuSize.FOUR, fresh4().cells.take(15)) }
        }

        should("reject a note digit beyond the size") {
            shouldThrow<IllegalArgumentException> { fresh4().with(1, Cell(2, notes = NoteMask.of(5))) }
        }

        should("reject a value beyond the size") {
            shouldThrow<IllegalArgumentException> { fresh4().with(1, Cell(2, value = 5)) }
        }

        should("reject a solution beyond the size") {
            shouldThrow<IllegalArgumentException> { fresh4().with(1, Cell(0)) }
            shouldThrow<IllegalArgumentException> { fresh4().with(1, Cell(5)) }
        }

        should("reject a value below 1") {
            shouldThrow<IllegalArgumentException> { fresh4().with(1, Cell(2, value = 0)) }
        }

        should("replace one cell and keep the original board") {
            val board = fresh4()
            val changed = board.with(1, Cell(2, value = 2))
            changed[1] shouldBe Cell(2, 2)
            board[1] shouldBe Cell(2)
        }

        should("count correct player cells as progress") {
            var board = fresh4()
            listOf(1, 2, 4, 5, 7).forEach { board = board.with(it, Cell(SOLUTION_4[it], SOLUTION_4[it])) }
            board.progress shouldBe 50
        }

        should("ignore wrong values in progress") {
            fresh4().with(1, Cell(2, 3)).progress shouldBe 0
        }

        should("count a revealed hint as progress") {
            fresh4().with(1, Cell(2, 2, CellOrigin.HINT)).progress shouldBe 10
        }

        should("report full progress when every cell is given") {
            Board.of(SudokuSize.FOUR, SOLUTION_4, SOLUTION_4.indices.toSet()).progress shouldBe 100
        }

        should("complete only when every cell is correct") {
            solved4().completed shouldBe true
            solved4().with(0, Cell(1, 2)).completed shouldBe false
            fresh4().completed shouldBe false
        }

        should("keep givens and drop values, hints and notes in initial") {
            val fresh = fresh4()
            val played =
                fresh
                    .with(1, Cell(2, 2))
                    .with(2, Cell(3, notes = NoteMask.of(1)))
                    .with(4, Cell(3, 3, CellOrigin.HINT))
                    .with(0, fresh[0].copy(notes = NoteMask.of(2)))
            played.initial() shouldBe fresh
        }

        should("list a digit as completed once it is correct in all cells") {
            solved4().completedDigits shouldBe setOf(1, 2, 3, 4)
            solved4().with(0, Cell(1)).completedDigits shouldBe setOf(2, 3, 4)
            fresh4().completedDigits shouldBe emptySet()
        }

        should("complete a row, column and block when all its cells are correct") {
            val board = solved4().with(5, Cell(4))
            listOf(board.isRowCompleted(0), board.isRowCompleted(1)) shouldBe listOf(true, false)
            listOf(board.isColumnCompleted(0), board.isColumnCompleted(1)) shouldBe listOf(true, false)
            listOf(board.isBlockCompleted(1), board.isBlockCompleted(0)) shouldBe listOf(true, false)
        }

        should("expose cell flags") {
            Cell(2, 3).error shouldBe true
            Cell(2, 3).correct shouldBe false
            Cell(2, 2).correct shouldBe true
            Cell(2, 2).error shouldBe false
            Cell(2).error shouldBe false
            Cell(2).correct shouldBe false
            Cell(2, 2, CellOrigin.HINT).locked shouldBe true
            Cell(2).locked shouldBe false
        }
    },
)
