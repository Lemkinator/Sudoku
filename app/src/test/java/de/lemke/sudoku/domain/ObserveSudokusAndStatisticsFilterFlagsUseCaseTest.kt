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
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuFilterFlags
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSudokusAndStatisticsFilterFlagsUseCaseTest : ShouldSpec(
    {
        val sudokusRepository = mockk<SudokusRepository>()
        lateinit var userSettings: UserSettings
        lateinit var useCase: ObserveSudokusAndStatisticsFilterFlagsUseCase

        beforeEach {
            clearMocks(sudokusRepository)
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            useCase = ObserveSudokusAndStatisticsFilterFlagsUseCase(sudokusRepository, userSettings, UnconfinedTestDispatcher())
        }

        should("re-filters the repository's sudokus when userSettings.filterFlags changes") {
            val normal4x4 =
                Sudoku.create(size = 4, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_NORMAL, fields = mutableListOf())
            val normal9x9 =
                Sudoku.create(size = 9, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_NORMAL, fields = mutableListOf())
            every { sudokusRepository.observeAllSudokus() } returns flowOf(listOf(normal4x4, normal9x9))
            userSettings.filterFlags = SudokuFilterFlags.TYPE_ALL or SudokuFilterFlags.SIZE_4X4 or SudokuFilterFlags.DIFFICULTY_ALL

            useCase().test {
                awaitItem() shouldBe listOf(normal4x4)

                userSettings.filterFlags = SudokuFilterFlags.TYPE_ALL or SudokuFilterFlags.SIZE_9X9 or SudokuFilterFlags.DIFFICULTY_ALL

                awaitItem() shouldBe listOf(normal9x9)
                cancelAndIgnoreRemainingEvents()
            }
        }
    },
)
