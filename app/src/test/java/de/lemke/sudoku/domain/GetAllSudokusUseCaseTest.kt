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
import de.lemke.sudoku.domain.model.SudokuFilterFlags
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GetAllSudokusUseCaseTest : ShouldSpec(
    {
        val sudokusRepository = mockk<SudokusRepository>()
        val useCase = GetAllSudokusUseCase(sudokusRepository, UnconfinedTestDispatcher())

        beforeEach { clearMocks(sudokusRepository) }

        should("returns every sudoku from the repository when using the default all-inclusive flags") {
            val sudokus =
                listOf(
                    Sudoku.create(size = 4, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_NORMAL, fields = mutableListOf()),
                    Sudoku.create(size = 9, difficulty = Difficulty.HARD, modeLevel = Sudoku.MODE_DAILY, fields = mutableListOf()),
                )
            coEvery { sudokusRepository.getAllSudokus() } returns sudokus

            useCase() shouldBe sudokus
        }

        should("filters out sudokus that don't match the given flags") {
            val normal9x9 =
                Sudoku.create(size = 9, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_NORMAL, fields = mutableListOf())
            val daily9x9 =
                Sudoku.create(size = 9, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_DAILY, fields = mutableListOf())
            val normal4x4 =
                Sudoku.create(size = 4, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_NORMAL, fields = mutableListOf())
            coEvery { sudokusRepository.getAllSudokus() } returns listOf(normal9x9, daily9x9, normal4x4)
            val flags = SudokuFilterFlags.TYPE_NORMAL or SudokuFilterFlags.SIZE_9X9 or SudokuFilterFlags.DIFFICULTY_ALL

            useCase(flags) shouldBe listOf(normal9x9)
        }
    },
)
