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
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class InitDailySudokusUseCaseTest : ShouldSpec(
    {
        val sudokusRepository = mockk<SudokusRepository>(relaxUnitFun = true)
        val saveSudoku = mockk<SaveSudokuUseCase>(relaxUnitFun = true)
        val generateDailySudoku = mockk<GenerateDailySudokuUseCase>()
        val useCase = InitDailySudokusUseCase(sudokusRepository, saveSudoku, generateDailySudoku, UnconfinedTestDispatcher())
        val date = LocalDate.of(2026, 1, 1)

        beforeEach { clearMocks(sudokusRepository, saveSudoku, generateDailySudoku) }

        should("generates and saves a new daily sudoku when none exists for the date") {
            coEvery { sudokusRepository.getDailySudokus() } returns emptyList()
            val generated = mockk<Sudoku>()
            coEvery { generateDailySudoku() } returns generated

            useCase(date)

            coVerify(exactly = 1) { generateDailySudoku() }
            coVerify(exactly = 1) { saveSudoku(generated) }
        }

        should("does nothing when a daily sudoku already exists for the date") {
            val existing =
                Sudoku.create(
                    size = 9,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_DAILY,
                    created = date.atStartOfDay(),
                    fields = mutableListOf(),
                )
            coEvery { sudokusRepository.getDailySudokus() } returns listOf(existing)

            useCase(date)

            coVerify(exactly = 0) { generateDailySudoku() }
            coVerify(exactly = 0) { saveSudoku(any()) }
        }
    },
)
