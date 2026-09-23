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

package de.lemke.sudoku.domain

import de.sfuhrm.sudoku.GameMatrix
import de.sfuhrm.sudoku.GameMatrixFactory
import de.sfuhrm.sudoku.GameSchema

/**
 * Returns the same valid solved board for a schema on every call. `Creator.createFull` seeds its diagonal
 * blocks from an unseeded `Random`, and some 16x16 seeds backtrack for minutes, so tests that only need a
 * valid board use this instead.
 */
class PatternSolvedBoardGenerator : SolvedBoardGenerator {
    override fun generate(schema: GameSchema): GameMatrix {
        val size = schema.width
        val blockSize = schema.blockWidth
        return GameMatrixFactory().newGameMatrix(schema).apply {
            setAll(
                Array(size) { row ->
                    ByteArray(size) { col -> patternValue(row, col, size, blockSize).toByte() }
                },
            )
        }
    }

    companion object {
        fun patternValue(
            row: Int,
            col: Int,
            size: Int,
            blockSize: Int,
        ): Int = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
    }
}
