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

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class InitSudokuLevelUseCaseTest : ShouldSpec(
    {
        val sudokusRepository = mockk<SudokusRepository>(relaxUnitFun = true)
        val generateSudokuLevel = mockk<GenerateSudokuLevelUseCase>()
        val getMaxSudokuLevel = mockk<GetMaxSudokuLevelUseCase>()
        val useCase = InitSudokuLevelUseCase(sudokusRepository, generateSudokuLevel, getMaxSudokuLevel, UnconfinedTestDispatcher())

        beforeEach { clearMocks(sudokusRepository, generateSudokuLevel, getMaxSudokuLevel) }

        should("generates nothing when every level up to max already exists") {
            val existing =
                (1..3).map { level ->
                    Sudoku.create(size = 4, difficulty = Difficulty.EASY, modeLevel = level, fields = mutableListOf())
                }
            coEvery { sudokusRepository.getSudokuLevel(4) } returns existing
            coEvery { getMaxSudokuLevel(4) } returns 3

            useCase(4)

            coVerify(exactly = 0) { generateSudokuLevel(any(), any()) }
            coVerify(exactly = 0) { sudokusRepository.saveSudoku(any(), any()) }
        }

        should("generates and saves exactly the missing levels") {
            val existing =
                listOf(
                    Sudoku.create(size = 4, difficulty = Difficulty.EASY, modeLevel = 1, fields = mutableListOf()),
                    Sudoku.create(size = 4, difficulty = Difficulty.EASY, modeLevel = 3, fields = mutableListOf()),
                )
            coEvery { sudokusRepository.getSudokuLevel(4) } returns existing
            coEvery { getMaxSudokuLevel(4) } returns 4
            val level2 = mockk<Sudoku>()
            val level4 = mockk<Sudoku>()
            coEvery { generateSudokuLevel(4, 2) } returns level2
            coEvery { generateSudokuLevel(4, 4) } returns level4

            useCase(4)

            coVerify(exactly = 1) { generateSudokuLevel(4, 2) }
            coVerify(exactly = 1) { generateSudokuLevel(4, 4) }
            coVerify(exactly = 1) { sudokusRepository.saveSudoku(level2, false) }
            coVerify(exactly = 1) { sudokusRepository.saveSudoku(level4, false) }
            coVerify(exactly = 2) { generateSudokuLevel(any(), any()) }
        }
    },
)
