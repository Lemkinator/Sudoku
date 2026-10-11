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

import app.cash.turbine.test
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testLevelSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSudokuLevelUseCaseTest : ShouldSpec(
    {
        val sudokusRepository = FakeSudokusRepository()
        val useCase = ObserveSudokuLevelUseCase(sudokusRepository, UnconfinedTestDispatcher())

        should("maps each sudoku to a SudokuItem labeled with its modeLevel") {
            val sudoku = testLevelSudoku(SudokuSize.NINE, level = 5)
            sudokusRepository.saveSudoku(sudoku)

            useCase(SudokuSize.NINE).test {
                awaitItem().map { item -> (item as SudokuListItem.SudokuItem).sudoku to item.label } shouldBe listOf(sudoku to "5")
                cancelAndIgnoreRemainingEvents()
            }
        }
    },
)
