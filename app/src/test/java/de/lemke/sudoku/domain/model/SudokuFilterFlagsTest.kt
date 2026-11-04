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

import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class SudokuFilterFlagsTest : ShouldSpec(
    {
        val typesByFlag =
            mapOf(
                SudokuFilterFlags.TYPE_NORMAL to GameMode.Normal,
                SudokuFilterFlags.TYPE_DAILY to GameMode.Daily,
                SudokuFilterFlags.TYPE_LEVEL to GameMode.Level(1),
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

            typesByFlag.values.forEach { mode ->
                flags.matchesSudokuFilterFlags(testSudoku(mode = mode)) shouldBe true
            }
        }

        should("match a single type flag only for its own type") {
            typesByFlag.forEach { (flag, mode) ->
                val flags = flag or SudokuFilterFlags.SIZE_ALL or SudokuFilterFlags.DIFFICULTY_ALL

                flags.matchesSudokuFilterFlags(testSudoku(mode = mode)) shouldBe true
                typesByFlag.values.filter { it != mode }.forEach { otherModeLevel ->
                    flags.matchesSudokuFilterFlags(testSudoku(mode = otherModeLevel)) shouldBe false
                }
            }
        }

        should("match a combination of type flags for either matching type") {
            val flags =
                (SudokuFilterFlags.TYPE_NORMAL or SudokuFilterFlags.TYPE_DAILY) or
                    SudokuFilterFlags.SIZE_ALL or
                    SudokuFilterFlags.DIFFICULTY_ALL

            flags.matchesSudokuFilterFlags(testSudoku(mode = GameMode.Normal)) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(mode = GameMode.Daily)) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(mode = GameMode.Level(1))) shouldBe false
        }

        should("match SIZE_ALL for every board size") {
            val flags = SudokuFilterFlags.TYPE_ALL or SudokuFilterFlags.SIZE_ALL or SudokuFilterFlags.DIFFICULTY_ALL

            SudokuSize.entries.forEach { size ->
                flags.matchesSudokuFilterFlags(testSudoku(size = size)) shouldBe true
            }
        }

        should("match a sudoku of each size by its own size flag and by no other size flag") {
            SudokuSize.entries.forEach { size ->
                SudokuSize.entries.forEach { flagSize ->
                    val flags = SudokuFilterFlags.TYPE_ALL or flagSize.filterFlag or SudokuFilterFlags.DIFFICULTY_ALL

                    flags.matchesSudokuFilterFlags(testSudoku(size = size)) shouldBe (flagSize == size)
                }
            }
        }

        should("match a combination of size flags for either matching size") {
            val flags =
                SudokuFilterFlags.TYPE_ALL or
                    (SudokuSize.FOUR.filterFlag or SudokuSize.NINE.filterFlag) or
                    SudokuFilterFlags.DIFFICULTY_ALL

            flags.matchesSudokuFilterFlags(testSudoku(size = SudokuSize.FOUR)) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(size = SudokuSize.NINE)) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(size = SudokuSize.SIXTEEN)) shouldBe false
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
            val flags = SudokuFilterFlags.TYPE_NORMAL or SudokuSize.NINE.filterFlag or SudokuFilterFlags.DIFFICULTY_HARD
            val matching = testSudoku(size = SudokuSize.NINE, difficulty = Difficulty.HARD, mode = GameMode.Normal)

            flags.matchesSudokuFilterFlags(matching) shouldBe true
            flags.matchesSudokuFilterFlags(testSudoku(size = SudokuSize.FOUR, difficulty = Difficulty.HARD)) shouldBe false
            flags.matchesSudokuFilterFlags(testSudoku(size = SudokuSize.NINE, difficulty = Difficulty.EASY)) shouldBe false
            val daily = testSudoku(size = SudokuSize.NINE, difficulty = Difficulty.HARD, mode = GameMode.Daily)
            flags.matchesSudokuFilterFlags(daily) shouldBe false
        }
    },
)
