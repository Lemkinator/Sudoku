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
import de.lemke.sudoku.domain.model.Sudoku
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteSudokusUseCaseTest : ShouldSpec(
    {
        val sudokusRepository = mockk<SudokusRepository>(relaxUnitFun = true)
        val useCase = DeleteSudokusUseCase(sudokusRepository, UnconfinedTestDispatcher())

        beforeEach { clearMocks(sudokusRepository) }

        should("deletes nothing when the list is empty") {
            useCase(emptyList())

            coVerify(exactly = 0) { sudokusRepository.deleteSudoku(any()) }
        }

        should("deletes every sudoku in the list") {
            val sudokus = listOf(mockk<Sudoku>(), mockk<Sudoku>(), mockk<Sudoku>())

            useCase(sudokus)

            coVerify(exactly = 1) { sudokusRepository.deleteSudoku(sudokus[0]) }
            coVerify(exactly = 1) { sudokusRepository.deleteSudoku(sudokus[1]) }
            coVerify(exactly = 1) { sudokusRepository.deleteSudoku(sudokus[2]) }
            coVerify(exactly = 3) { sudokusRepository.deleteSudoku(any()) }
        }
    },
)
