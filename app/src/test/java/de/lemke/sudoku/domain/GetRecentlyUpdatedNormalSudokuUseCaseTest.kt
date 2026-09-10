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
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GetRecentlyUpdatedNormalSudokuUseCaseTest : ShouldSpec(
    {
        val sudokusRepository = mockk<SudokusRepository>()
        val useCase = GetRecentlyUpdatedNormalSudokuUseCase(sudokusRepository, UnconfinedTestDispatcher())

        beforeEach { clearMocks(sudokusRepository) }

        should("returns null when the repository has no recently updated normal sudoku") {
            coEvery { sudokusRepository.getRecentlyUpdatedNormalSudoku() } returns null

            useCase() shouldBe null
        }

        should("passes through the repository's recently updated normal sudoku") {
            val sudoku = mockk<Sudoku>()
            coEvery { sudokusRepository.getRecentlyUpdatedNormalSudoku() } returns sudoku

            useCase() shouldBe sudoku
        }
    },
)
