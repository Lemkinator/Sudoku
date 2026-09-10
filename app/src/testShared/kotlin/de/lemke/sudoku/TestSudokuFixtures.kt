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

package de.lemke.sudoku

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import kotlin.math.sqrt

/**
 * A real, valid solved grid via the standard band-shifted base pattern
 * `(blockSize * (row % blockSize) + row / blockSize + col) % size + 1` — deterministic (no solver, no RNG) but
 * satisfies every row/column/box constraint, unlike a naive formula. Every third cell (in reading order) is given;
 * the rest start blank. Shared between `src/test` (Robolectric) and `src/androidTest` (instrumented) — this is a
 * plain data builder with no Hilt/Robolectric coupling, so unlike `TestSettingsModule` it doesn't need per-source-set
 * twins.
 */
fun testLevelSudoku(
    size: Int,
    level: Int = 1,
    completed: Boolean = false,
    sudokuId: SudokuId = SudokuId.generate(),
): Sudoku {
    val blockSize = sqrt(size.toDouble()).toInt()
    return Sudoku.create(
        sudokuId = sudokuId,
        size = size,
        difficulty = Difficulty.VERY_EASY,
        modeLevel = level,
        fields =
            MutableList(size * size) { index ->
                val row = index / size
                val col = index % size
                val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
                val given = index % 3 == 0
                Field(
                    position = Position.create(index, size),
                    solution = solution,
                    value = if (given || completed) solution else null,
                    given = given,
                )
            },
    )
}
