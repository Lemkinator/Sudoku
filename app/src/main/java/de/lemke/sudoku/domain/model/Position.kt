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

import kotlin.math.sqrt

class Position(
    val size: Int,
    val index: Int,
    val row: Int,
    val column: Int,
    val block: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Position
        if (size != other.size) return false
        return index == other.index
    }

    override fun hashCode(): Int = 31 * size + index

    fun next(): Position? = if (index < size * size - 1) create(index + 1, size) else null

    companion object {
        fun create(
            index: Int,
            size: Int,
        ): Position {
            val blockSize = sqrt(size.toDouble()).toInt()
            val row = index / size
            val column = index % size
            val block = row / blockSize * blockSize + column / blockSize
            return Position(size, index, row, column, block)
        }

        fun create(
            size: Int,
            row: Int,
            column: Int,
        ): Position {
            val blockSize = sqrt(size.toDouble()).toInt()
            return Position(
                size = size,
                index = row * size + column,
                row = row,
                column = column,
                block = row / blockSize * blockSize + column / blockSize,
            )
        }
    }
}
