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

private fun testSudoku(
    size: Int = 9,
    difficulty: Difficulty = Difficulty.MEDIUM,
    modeLevel: Int = Sudoku.MODE_NORMAL,
): Sudoku =
    Sudoku.create(
        size = size,
        difficulty = difficulty,
        modeLevel = modeLevel,
        fields = mutableListOf(Field(position = Position.create(0, size), solution = 1)),
    )

private const val TYPE_LEVEL_MODE = 1

class SudokuFilterFlagsTest : ShouldSpec(
    {
        val typesByFlag =
            mapOf(
                SudokuFilterFlags.TYPE_NORMAL to Sudoku.MODE_NORMAL,
                SudokuFilterFlags.TYPE_DAILY to Sudoku.MODE_DAILY,
                SudokuFilterFlags.TYPE_LEVEL to TYPE_LEVEL_MODE,
            )
        val sizesByFlag =
            mapOf(
                SudokuFilterFlags.SIZE_4X4 to 4,
                SudokuFilterFlags.SIZE_9X9 to 9,
                SudokuFilterFlags.SIZE_16X16 to 16,
            )
        val difficultiesByFlag =
            mapOf(
                SudokuFilterFlags.DIFFICULTY_VERY_EASY to Difficulty.VERY_EASY,
                SudokuFilterFlags.DIFFICULTY_EASY to Difficulty.EASY,
                SudokuFilterFlags.DIFFICULTY_MEDIUM to Difficulty.MEDIUM,
                SudokuFilterFlags.DIFFICULTY_HARD to Difficulty.HARD,
                SudokuFilterFlags.DIFFICULTY_EXPERT to Difficulty.EXPERT,
            )

        should("match TYPE_ALL for every type of sudoku") {
            val flags = SudokuFilterFlags.TYPE_ALL or SudokuFilterFlags.SIZE_ALL or SudokuFilterFlags.DIFFICULTY_ALL

            typesByFlag.values.forEach { modeLevel ->
                flags.matchesSudokuFilterFlags(testSudoku(modeLevel = modeLevel)) shouldBe true
            }
        }

        should("match a single type flag only for its own type") {
            typesByFlag.forEach { (flag, modeLevel) ->
                val flags = flag or SudokuFilterFlags.SIZE_ALL or SudokuFilterFlags.DIFFICULTY_ALL

                flags.matchesSudokuFilterFlags(testSudoku(modeLevel = modeLevel)) shouldBe true
                typesByFlag.values.filter { it != modeLevel }.forEach { otherModeLevel ->
                    flags.matchesSudokuFilterFlags(testSudoku(modeLevel = otherModeLevel)) shouldBe false
                }
            }
        }

        should("match a combination of type flags for either matching type") {
            val flags =
                (SudokuFilterFlags.TYPE_NORMAL or SudokuFilterFlags.TYPE_DAILY) or
                    SudokuFilterFlags.SIZE_ALL or
                    SudokuFilterFlags.DIFFICULTY_ALL

            flags.matchesSudokuFilterFlags(testSudoku(modeLevel = Sudoku.MODE_NORMAL)) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(modeLevel = Sudoku.MODE_DAILY)) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(modeLevel = TYPE_LEVEL_MODE)) shouldBe false
        }

        should("match SIZE_ALL for every board size") {
            val flags = SudokuFilterFlags.TYPE_ALL or SudokuFilterFlags.SIZE_ALL or SudokuFilterFlags.DIFFICULTY_ALL

            sizesByFlag.values.forEach { size ->
                flags.matchesSudokuFilterFlags(testSudoku(size = size)) shouldBe true
            }
        }

        should("match a single size flag only for its own size") {
            sizesByFlag.forEach { (flag, size) ->
                val flags = SudokuFilterFlags.TYPE_ALL or flag or SudokuFilterFlags.DIFFICULTY_ALL

                flags.matchesSudokuFilterFlags(testSudoku(size = size)) shouldBe true
                sizesByFlag.values.filter { it != size }.forEach { otherSize ->
                    flags.matchesSudokuFilterFlags(testSudoku(size = otherSize)) shouldBe false
                }
            }
        }

        should("match a combination of size flags for either matching size") {
            val flags =
                SudokuFilterFlags.TYPE_ALL or
                    (SudokuFilterFlags.SIZE_4X4 or SudokuFilterFlags.SIZE_9X9) or
                    SudokuFilterFlags.DIFFICULTY_ALL

            flags.matchesSudokuFilterFlags(testSudoku(size = 4)) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(size = 9)) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(size = 16)) shouldBe false
        }

        should("match DIFFICULTY_ALL for every difficulty") {
            val flags = SudokuFilterFlags.TYPE_ALL or SudokuFilterFlags.SIZE_ALL or SudokuFilterFlags.DIFFICULTY_ALL

            difficultiesByFlag.values.forEach { difficulty ->
                flags.matchesSudokuFilterFlags(testSudoku(difficulty = difficulty)) shouldBe true
            }
        }

        should("match a single difficulty flag only for its own difficulty") {
            difficultiesByFlag.forEach { (flag, difficulty) ->
                val flags = SudokuFilterFlags.TYPE_ALL or SudokuFilterFlags.SIZE_ALL or flag

                flags.matchesSudokuFilterFlags(testSudoku(difficulty = difficulty)) shouldBe true
                difficultiesByFlag.values.filter { it != difficulty }.forEach { otherDifficulty ->
                    flags.matchesSudokuFilterFlags(testSudoku(difficulty = otherDifficulty)) shouldBe false
                }
            }
        }

        should("match a combination of difficulty flags for either matching difficulty") {
            val flags =
                SudokuFilterFlags.TYPE_ALL or
                    SudokuFilterFlags.SIZE_ALL or
                    (SudokuFilterFlags.DIFFICULTY_EASY or SudokuFilterFlags.DIFFICULTY_HARD)

            flags.matchesSudokuFilterFlags(testSudoku(difficulty = Difficulty.EASY)) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(difficulty = Difficulty.HARD)) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(difficulty = Difficulty.MEDIUM)) shouldBe false
        }

        should("require type, size and difficulty to all match") {
            val flags = SudokuFilterFlags.TYPE_NORMAL or SudokuFilterFlags.SIZE_9X9 or SudokuFilterFlags.DIFFICULTY_HARD
            val matching = testSudoku(size = 9, difficulty = Difficulty.HARD, modeLevel = Sudoku.MODE_NORMAL)

            flags.matchesSudokuFilterFlags(matching) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(size = 4, difficulty = Difficulty.HARD)) shouldBe false
            flags.matchesSudokuFilterFlags(testSudoku(size = 9, difficulty = Difficulty.EASY)) shouldBe false
            flags.matchesSudokuFilterFlags(testSudoku(size = 9, difficulty = Difficulty.HARD, modeLevel = Sudoku.MODE_DAILY)) shouldBe false
        }
    },
)
