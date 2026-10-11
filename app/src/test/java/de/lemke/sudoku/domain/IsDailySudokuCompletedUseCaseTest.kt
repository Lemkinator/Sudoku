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
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class IsDailySudokuCompletedUseCaseTest : ShouldSpec(
    {
        lateinit var sudokusRepository: FakeSudokusRepository
        lateinit var useCase: IsDailySudokuCompletedUseCase
        val date = LocalDate.of(2026, 1, 1)

        beforeEach {
            val dispatcher = UnconfinedTestDispatcher()
            sudokusRepository = FakeSudokusRepository()
            useCase = IsDailySudokuCompletedUseCase(GetAllSudokusUseCase(sudokusRepository, dispatcher), dispatcher)
        }

        should("returns true when a completed daily sudoku exists for the date") {
            val completedSudoku =
                testSudoku(
                    size = SudokuSize.FOUR,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_DAILY,
                    created = date.atStartOfDay(),
                    completed = true,
                )
            sudokusRepository.saveSudoku(completedSudoku)

            useCase(date).shouldBeTrue()
        }

        should("returns false when no daily sudoku is completed on the date") {
            val incompleteSudoku =
                testSudoku(
                    size = SudokuSize.FOUR,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_DAILY,
                    created = date.atStartOfDay(),
                )
            sudokusRepository.saveSudoku(incompleteSudoku)

            useCase(date).shouldBeFalse()
        }

        should("returns false when a completed daily sudoku exists but on a different date") {
            val completedOnOtherDate =
                testSudoku(
                    size = SudokuSize.FOUR,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_DAILY,
                    created = date.plusDays(1).atStartOfDay(),
                    completed = true,
                )
            sudokusRepository.saveSudoku(completedOnOtherDate)

            useCase(date).shouldBeFalse()
        }
    },
)
