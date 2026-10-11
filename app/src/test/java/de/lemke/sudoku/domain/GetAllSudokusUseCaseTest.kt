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

import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuFilterFlags
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GetAllSudokusUseCaseTest : ShouldSpec(
    {
        lateinit var sudokusRepository: FakeSudokusRepository
        lateinit var useCase: GetAllSudokusUseCase

        beforeEach {
            sudokusRepository = FakeSudokusRepository()
            useCase = GetAllSudokusUseCase(sudokusRepository, UnconfinedTestDispatcher())
        }

        should("returns every sudoku from the repository when using the default all-inclusive flags") {
            val sudokus =
                listOf(
                    testSudoku(
                        size = SudokuSize.FOUR,
                        difficulty = Difficulty.EASY,
                        modeLevel = Sudoku.MODE_NORMAL,
                        created = LocalDateTime.of(2026, 1, 2, 10, 0),
                    ),
                    testSudoku(
                        size = SudokuSize.NINE,
                        difficulty = Difficulty.HARD,
                        modeLevel = Sudoku.MODE_DAILY,
                        created = LocalDateTime.of(2026, 1, 1, 10, 0),
                    ),
                )
            sudokus.forEach { sudokusRepository.saveSudoku(it) }

            useCase() shouldBe sudokus
        }

        should("filters out sudokus that don't match the given flags") {
            val normal9x9 = testSudoku(size = SudokuSize.NINE, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_NORMAL)
            val daily9x9 = testSudoku(size = SudokuSize.NINE, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_DAILY)
            val normal4x4 = testSudoku(size = SudokuSize.FOUR, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_NORMAL)
            listOf(normal9x9, daily9x9, normal4x4).forEach { sudokusRepository.saveSudoku(it) }
            val flags = SudokuFilterFlags.TYPE_NORMAL or SudokuSize.NINE.filterFlag or SudokuFilterFlags.DIFFICULTY_ALL

            useCase(flags) shouldBe listOf(normal9x9)
        }
    },
)
