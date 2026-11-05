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

enum class CellTone {
    NONE,
    BOX_TINT,
    PEER,
    SAME_DIGIT,
    SELECTED,
    ERROR,
}

data class BoardRender(
    val board: Board,
    val selected: Int? = null,
    val highlightedDigit: Int? = null,
    val showPeers: Boolean = false,
    val highlighted: Set<Int> = emptySet(),
) {
    private val peerSource: Int? = selected?.takeIf { showPeers && !board[it].locked }
    private val peers: Set<Int> = peerSource?.let(board::peers).orEmpty() + highlighted

    val showsPeers: Boolean get() = peerSource != null

    fun toneOf(index: Int): CellTone {
        val cell = board[index]
        return when {
            cell.error -> CellTone.ERROR
            index == selected -> CellTone.SELECTED
            highlightedDigit != null && cell.value == highlightedDigit -> CellTone.SAME_DIGIT
            index in peers -> CellTone.PEER
            isTinted(index) -> CellTone.BOX_TINT
            else -> CellTone.NONE
        }
    }

    fun isEmphasizedNote(digit: Int): Boolean = digit == highlightedDigit

    private fun isTinted(index: Int): Boolean {
        val position = board.position(index)
        val blockSize = board.size.blockSize
        val period = blockSize * 2
        return (position.row % period >= blockSize) != (position.column % period >= blockSize)
    }
}
