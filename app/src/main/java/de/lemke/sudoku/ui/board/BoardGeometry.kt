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

import de.lemke.sudoku.domain.model.SudokuSize
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val CELL_PADDING = 0.06f
private const val THIN_LINE_DP = 1f
private const val BOX_LINE_DP = 2f
private const val BORDER_DP = 2f
private val NO_INSET = Box(0f, 0f, 0f, 0f)
private val CORNER_CELL_FACTOR = 1f - 1f / sqrt(2f)

data class BoardMetrics(
    val side: Float,
    val cornerRadius: Float,
    val thinLine: Float,
    val boxLine: Float,
    val border: Float,
) {
    companion object {
        fun of(
            side: Float,
            cornerRadius: Float,
            density: Float,
        ) = BoardMetrics(
            side = side,
            cornerRadius = cornerRadius,
            thinLine = wholePixels(THIN_LINE_DP * density),
            boxLine = wholePixels(BOX_LINE_DP * density),
            border = wholePixels(BORDER_DP * density),
        )

        private fun wholePixels(value: Float): Float = value.roundToInt().coerceAtLeast(1).toFloat()
    }
}

class BoardGeometry(
    val size: SudokuSize,
    val metrics: BoardMetrics,
) {
    val cornerRadius = minOf(metrics.cornerRadius, metrics.side / 2)

    val cornerCells: List<Int> = (0 until size.cellCount).filter { cornerInsets(it) != NO_INSET }

    fun edge(i: Int): Float = (i * metrics.side / size.value).roundToInt().toFloat()

    fun cellBox(index: Int): Box {
        val row = index / size.value
        val col = index % size.value
        return Box(edge(col), edge(row), edge(col + 1), edge(row + 1))
    }

    fun cornerInsets(index: Int): Box {
        val box = cellBox(index)
        val last = size.value - 1
        val alongLeftRight = maxOf(arcInset(box.top), arcInset(metrics.side - box.bottom))
        val alongTopBottom = maxOf(arcInset(box.left), arcInset(metrics.side - box.right))
        return Box(
            left = if (index % size.value == 0) alongLeftRight else 0f,
            top = if (index / size.value == 0) alongTopBottom else 0f,
            right = if (index % size.value == last) alongLeftRight else 0f,
            bottom = if (index / size.value == last) alongTopBottom else 0f,
        )
    }

    fun contentBox(index: Int): Box {
        val box = cellBox(index)
        val padding = CELL_PADDING * box.width
        val insets = cornerInsets(index)
        return box.inset(padding + insets.left, padding + insets.top, padding + insets.right, padding + insets.bottom)
    }

    fun indexAt(
        x: Float,
        y: Float,
    ): Int? {
        if (!isOnBoard(x, y)) return null
        val col = (0 until size.value).first { x < edge(it + 1) }
        val row = (0 until size.value).first { y < edge(it + 1) }
        return row * size.value + col
    }

    private fun isOnBoard(
        x: Float,
        y: Float,
    ): Boolean {
        val side = metrics.side
        if (x < 0f || y < 0f || x >= side || y >= side) return false
        val dx = x - x.coerceIn(cornerRadius, side - cornerRadius)
        val dy = y - y.coerceIn(cornerRadius, side - cornerRadius)
        return dx * dx + dy * dy <= cornerRadius * cornerRadius
    }

    private fun arcInset(distanceFromCorner: Float): Float =
        when {
            distanceFromCorner <= 0f -> {
                cornerRadius * CORNER_CELL_FACTOR
            }

            distanceFromCorner < cornerRadius -> {
                val remaining = cornerRadius - distanceFromCorner
                cornerRadius - sqrt(cornerRadius * cornerRadius - remaining * remaining)
            }

            else -> {
                0f
            }
        }
}
