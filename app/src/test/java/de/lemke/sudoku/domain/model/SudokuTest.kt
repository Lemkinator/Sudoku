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
import io.kotest.matchers.shouldBe

class SudokuTest : ShouldSpec(
    {
        should("report 100% progress instead of throwing when every field is given") {
            val sudoku =
                Sudoku.create(
                    size = 4,
                    difficulty = Difficulty.VERY_EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    fields =
                        mutableListOf(
                            Field(position = Position.create(0, 4), solution = 1, value = 1, given = true),
                            Field(position = Position.create(1, 4), solution = 2, value = 2, given = true),
                        ),
                )

            sudoku.progress shouldBe 100
        }

        should("compute progress as the percentage of non-given fields solved correctly") {
            val sudoku =
                Sudoku.create(
                    size = 4,
                    difficulty = Difficulty.VERY_EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    fields =
                        mutableListOf(
                            Field(position = Position.create(0, 4), solution = 1, value = 1, given = true),
                            Field(position = Position.create(1, 4), solution = 2, value = 2, given = false),
                            Field(position = Position.create(2, 4), solution = 3, value = null, given = false),
                        ),
                )

            sudoku.progress shouldBe 50
        }
    },
)
