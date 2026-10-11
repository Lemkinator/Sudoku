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

import de.lemke.sudoku.domain.model.Difficulty.EASY
import de.lemke.sudoku.domain.model.Difficulty.EXPERT
import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Difficulty.MEDIUM
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateSudokuLevelUseCaseTest : ShouldSpec(
    {
        val dispatcher = UnconfinedTestDispatcher()
        val useCase = GenerateSudokuLevelUseCase(GenerateFieldsUseCase(dispatcher, PatternSolvedBoardGenerator()), dispatcher)

        should("map every level-threshold boundary to its difficulty") {
            val cases =
                mapOf(
                    0 to EXPERT,
                    1 to VERY_EASY,
                    30 to VERY_EASY,
                    31 to EASY,
                    100 to EASY,
                    101 to MEDIUM,
                    200 to MEDIUM,
                    201 to HARD,
                    500 to HARD,
                    501 to EXPERT,
                )

            cases.forEach { (level, expectedDifficulty) ->
                val sudoku = useCase(SudokuSize.NINE, level)

                sudoku.difficulty shouldBe expectedDifficulty
                sudoku.modeLevel shouldBe level
            }
        }
    },
)
