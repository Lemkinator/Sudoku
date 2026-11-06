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

import de.lemke.sudoku.ui.board.Box
import kotlin.math.max
import kotlin.math.min

data class GameArrangement(
    val board: Box,
    val controls: Box,
)

fun arrangeGame(
    width: Int,
    height: Int,
    controlsExtent: Int,
    controlsAtEnd: Boolean,
): GameArrangement {
    val main = if (controlsAtEnd) width else height
    val cross = if (controlsAtEnd) height else width
    val side = max(0, min(cross, main - controlsExtent))
    val boardStart = max(0, main - controlsExtent - side) / 2f
    val boardEnd = boardStart + side
    val controlsStart = max(0, main - controlsExtent).toFloat()
    val controlsEnd = controlsStart + controlsExtent
    val boardCross = (cross - side) / 2f
    return if (controlsAtEnd) {
        GameArrangement(
            board = Box(boardStart, boardCross, boardEnd, boardCross + side),
            controls = Box(controlsStart, 0f, controlsEnd, cross.toFloat()),
        )
    } else {
        GameArrangement(
            board = Box(boardCross, boardStart, boardCross + side, boardEnd),
            controls = Box(0f, controlsStart, cross.toFloat(), controlsEnd),
        )
    }
}
