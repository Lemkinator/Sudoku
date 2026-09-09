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

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuFilterFlags
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class IsDailySudokuCompletedUseCaseTest : ShouldSpec(
    {
        val getAllSudokus = mockk<GetAllSudokusUseCase>()
        val useCase = IsDailySudokuCompletedUseCase(getAllSudokus, UnconfinedTestDispatcher())
        val date = LocalDate.of(2026, 1, 1)
        val flags = SudokuFilterFlags.TYPE_DAILY or SudokuFilterFlags.DIFFICULTY_ALL or SudokuFilterFlags.SIZE_ALL

        beforeEach { clearMocks(getAllSudokus) }

        should("returns true when a completed daily sudoku exists for the date") {
            val completedSudoku =
                Sudoku.create(
                    size = 4,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_DAILY,
                    created = date.atStartOfDay(),
                    fields = mutableListOf(),
                )
            coEvery { getAllSudokus(flags) } returns listOf(completedSudoku)

            useCase(date).shouldBeTrue()
        }

        should("returns false when no daily sudoku is completed on the date") {
            val incompleteField = Field(Position.create(0, 4), solution = 1, value = null)
            val incompleteSudoku =
                Sudoku.create(
                    size = 4,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_DAILY,
                    created = date.atStartOfDay(),
                    fields = mutableListOf(incompleteField),
                )
            coEvery { getAllSudokus(flags) } returns listOf(incompleteSudoku)

            useCase(date).shouldBeFalse()
        }
    },
)
