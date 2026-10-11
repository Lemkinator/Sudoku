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
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GetSudokuUseCaseTest : ShouldSpec(
    {
        lateinit var sudokusRepository: FakeSudokusRepository
        lateinit var useCase: GetSudokuUseCase
        val sudokuId = SudokuId.generate()

        beforeEach {
            sudokusRepository = FakeSudokusRepository()
            useCase = GetSudokuUseCase(sudokusRepository, UnconfinedTestDispatcher())
        }

        should("returns null when the repository has no sudoku with that id") {
            sudokusRepository.saveSudoku(testSudoku())

            useCase(sudokuId) shouldBe null
        }

        should("passes through the repository's sudoku for that id") {
            val sudoku = testSudoku(sudokuId = sudokuId)
            sudokusRepository.saveSudoku(sudoku)

            useCase(sudokuId) shouldBe sudoku
        }
    },
)
