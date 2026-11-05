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

package de.lemke.sudoku.ui.board

import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

private fun boardOf(
    size: SudokuSize,
    givens: Set<Int> = emptySet(),
): Board {
    val block = size.blockSize
    val solution =
        List(size.cellCount) { index ->
            val row = index / size.value
            val column = index % size.value
            (block * (row % block) + row / block + column) % size.value + 1
        }
    return Board.of(size, solution, givens)
}

private fun Board.filled(vararg indices: Int): Board =
    indices.fold(this) { board, index -> board.with(index, board[index].copy(value = board[index].solution)) }

class BoardRenderTest : ShouldSpec(
    {
        val board9 = boardOf(SudokuSize.NINE)

        should("show an error before the selection") {
            val wrong = board9.with(2, board9[2].copy(value = 4))
            BoardRender(wrong, selected = 2).toneOf(2) shouldBe CellTone.ERROR
        }

        should("show the selection before the same digit") {
            val board = board9.filled(40)
            BoardRender(board, selected = 40, highlightedDigit = 9).toneOf(40) shouldBe CellTone.SELECTED
        }

        should("show the same digit on a cell outside the peers") {
            val board = board9.filled(40, 8)
            BoardRender(board, selected = 40, highlightedDigit = 9, showPeers = true).toneOf(8) shouldBe CellTone.SAME_DIGIT
        }

        should("show the same digit before the peer tone") {
            val board = board9.filled(36)
            val render = BoardRender(board, selected = 40, highlightedDigit = 5, showPeers = true)
            listOf(render.toneOf(36), render.toneOf(37)) shouldBe listOf(CellTone.SAME_DIGIT, CellTone.PEER)
        }

        should("show no same digit without a highlighted digit") {
            BoardRender(board9.filled(8), selected = 40).toneOf(8) shouldBe CellTone.NONE
        }

        should("show peers only with showPeers") {
            val hidden = BoardRender(board9, selected = 40)
            val shown = hidden.copy(showPeers = true)
            listOf(hidden.toneOf(36), shown.toneOf(36), shown.toneOf(0)) shouldBe
                listOf(CellTone.BOX_TINT, CellTone.PEER, CellTone.NONE)
        }

        should("show the peer tone over the box tint") {
            BoardRender(board9, selected = 40, showPeers = true).toneOf(37) shouldBe CellTone.PEER
        }

        should("show no peers when the selected cell is a given") {
            val render = BoardRender(boardOf(SudokuSize.NINE, givens = setOf(40)), selected = 40, showPeers = true)
            listOf(render.toneOf(40), render.toneOf(41), render.toneOf(37)) shouldBe
                listOf(CellTone.SELECTED, CellTone.NONE, CellTone.BOX_TINT)
        }

        should("show no peers without a selection") {
            BoardRender(board9, showPeers = true).toneOf(41) shouldBe CellTone.NONE
        }

        should("tint the alternating boxes of a 9x9 board") {
            val render = BoardRender(board9)
            listOf(0, 3, 6, 27, 30, 40).map(render::toneOf) shouldBe
                listOf(CellTone.NONE, CellTone.BOX_TINT, CellTone.NONE, CellTone.BOX_TINT, CellTone.NONE, CellTone.NONE)
        }

        should("tint the alternating boxes of a 4x4 board") {
            val render = BoardRender(boardOf(SudokuSize.FOUR))
            listOf(0, 2, 8, 10).map(render::toneOf) shouldBe
                listOf(CellTone.NONE, CellTone.BOX_TINT, CellTone.BOX_TINT, CellTone.NONE)
        }

        should("tint the alternating boxes of a 16x16 board") {
            val render = BoardRender(boardOf(SudokuSize.SIXTEEN))
            listOf(0, 4, 64, 68).map(render::toneOf) shouldBe
                listOf(CellTone.NONE, CellTone.BOX_TINT, CellTone.BOX_TINT, CellTone.NONE)
        }

        should("emphasize the note of the highlighted digit only") {
            val render = BoardRender(board9, highlightedDigit = 5)
            listOf(render.isEmphasizedNote(5), render.isEmphasizedNote(4)) shouldBe listOf(true, false)
        }

        should("emphasize no note without a highlighted digit") {
            BoardRender(board9).isEmphasizedNote(5) shouldBe false
        }
    },
)
