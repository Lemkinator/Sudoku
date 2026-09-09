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

package de.lemke.sudoku.ui.fragments

import app.cash.turbine.test
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.DeleteSudokusUseCase
import de.lemke.sudoku.domain.ObserveSudokuHistoryUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.SudokuListItem.SeparatorItem
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class TabHistoryViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        val observeSudokuHistory = mockk<ObserveSudokuHistoryUseCase>()
        val deleteSudoku = mockk<DeleteSudokusUseCase>(relaxUnitFun = true)

        beforeEach {
            clearMocks(observeSudokuHistory, deleteSudoku)
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            every { observeSudokuHistory() } returns flowOf(mutableListOf())
        }

        fun newViewModel() = TabHistoryViewModel(userSettings, observeSudokuHistory, deleteSudoku)

        should("errorLimit reflects the real UserSettings.errorLimitFlow") {
            val viewModel = newViewModel()
            userSettings.errorLimit = 7
            viewModel.errorLimit.value shouldBe 7
        }

        should("the first emission sets sudokuHistory without emitting ScrollToTop") {
            val firstHistory = mutableListOf<SudokuListItem>(SeparatorItem("Jan 2026"))
            every { observeSudokuHistory() } returns flowOf(firstHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.value shouldBe firstHistory
            viewModel.events.test { expectNoEvents() }
        }

        should("a second emission with a larger list emits ScrollToTop") {
            val firstHistory = mutableListOf<SudokuListItem>(SeparatorItem("Jan 2026"))
            val secondHistory = mutableListOf<SudokuListItem>(SeparatorItem("Jan 2026"), SeparatorItem("Feb 2026"))
            every { observeSudokuHistory() } returns flowOf(firstHistory, secondHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.value shouldBe secondHistory
            viewModel.events.test {
                awaitItem() shouldBe TabHistoryEvent.ScrollToTop
            }
        }

        should("a second emission with an equal-or-smaller list does not emit ScrollToTop") {
            val firstHistory = mutableListOf<SudokuListItem>(SeparatorItem("Jan 2026"), SeparatorItem("Feb 2026"))
            val secondHistory = mutableListOf<SudokuListItem>(SeparatorItem("Mar 2026"))
            every { observeSudokuHistory() } returns flowOf(firstHistory, secondHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.value shouldBe secondHistory
            viewModel.events.test { expectNoEvents() }
        }

        should("init emits ShowLoadError when observeSudokuHistory throws") {
            every { observeSudokuHistory() } throws RuntimeException("observe failed")

            val viewModel = newViewModel()

            viewModel.events.test {
                awaitItem() shouldBe TabHistoryEvent.ShowLoadError
            }
        }

        should("init does not treat CancellationException as a load failure") {
            every { observeSudokuHistory() } throws CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.sudokuHistory.value shouldBe emptyList()
            viewModel.events.test { expectNoEvents() }
        }

        should("deleteSelectedSudokus delegates to deleteSudoku") {
            val sudokus = listOf(mockk<Sudoku>(), mockk<Sudoku>())
            val viewModel = newViewModel()

            viewModel.deleteSelectedSudokus(sudokus)

            coVerify(exactly = 1) { deleteSudoku(sudokus) }
        }
    },
)
