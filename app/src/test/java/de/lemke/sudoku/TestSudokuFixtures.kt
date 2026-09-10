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

/**
 * A deterministic, hand-formulaic (not solver-generated) board for screenshot tests — game logic never validates
 * solvability at construction, so the exact digits don't matter, only that every run produces the same ones.
 */
fun testLevelSudoku(
    size: Int,
    level: Int = 1,
    completed: Boolean = false,
): Sudoku =
    Sudoku.create(
        size = size,
        difficulty = Difficulty.VERY_EASY,
        modeLevel = level,
        fields =
            MutableList(size * size) { index ->
                val solution = index % size + 1
                val given = index % 3 == 0
                Field(
                    position = Position.create(index, size),
                    solution = solution,
                    value = if (given || completed) solution else null,
                    given = given,
                )
            },
    )
