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
import de.lemke.sudoku.domain.CalculateStatisticsUseCase
import de.lemke.sudoku.domain.ObserveSudokusAndStatisticsFilterFlagsUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuStatistics
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf

class TabStatisticsViewModelTest : ShouldSpec(
    {
        val observeSudokusAndStatisticsFilterFlags = mockk<ObserveSudokusAndStatisticsFilterFlagsUseCase>()
        val calculateStatistics = mockk<CalculateStatisticsUseCase>()

        beforeEach {
            clearMocks(observeSudokusAndStatisticsFilterFlags, calculateStatistics)
        }

        fun newViewModel() = TabStatisticsViewModel(observeSudokusAndStatisticsFilterFlags, calculateStatistics)

        should("stays loading while computing, then settles with the computed statistics per emission") {
            val sudokusFlow = MutableSharedFlow<List<Sudoku>>(extraBufferCapacity = 1)
            every { observeSudokusAndStatisticsFilterFlags() } returns sudokusFlow
            val firstDeferred = CompletableDeferred<SudokuStatistics>()
            coEvery { calculateStatistics(emptyList()) } coAnswers { firstDeferred.await() }

            val viewModel = newViewModel()

            viewModel.state.test {
                awaitItem() shouldBe TabStatisticsUiState()

                sudokusFlow.emit(emptyList())
                val firstStats = mockk<SudokuStatistics>()
                firstDeferred.complete(firstStats)
                awaitItem() shouldBe TabStatisticsUiState(statistics = firstStats, isLoading = false)

                val sudokuList = listOf(mockk<Sudoku>())
                val secondDeferred = CompletableDeferred<SudokuStatistics>()
                coEvery { calculateStatistics(sudokuList) } coAnswers { secondDeferred.await() }
                sudokusFlow.emit(sudokuList)
                awaitItem() shouldBe TabStatisticsUiState(statistics = firstStats, isLoading = true)

                val secondStats = mockk<SudokuStatistics>()
                secondDeferred.complete(secondStats)
                awaitItem() shouldBe TabStatisticsUiState(statistics = secondStats, isLoading = false)

                cancelAndIgnoreRemainingEvents()
            }
        }

        should("init sets isLoading false and emits ShowLoadError when observeSudokusAndStatisticsFilterFlags throws") {
            every { observeSudokusAndStatisticsFilterFlags() } throws RuntimeException("observe failed")

            val viewModel = newViewModel()

            viewModel.state.value shouldBe TabStatisticsUiState(isLoading = false)
            viewModel.events.test {
                awaitItem() shouldBe TabStatisticsEvent.ShowLoadError
            }
        }

        should("init sets isLoading false and emits ShowLoadError when calculateStatistics throws") {
            every { observeSudokusAndStatisticsFilterFlags() } returns flowOf(emptyList())
            coEvery { calculateStatistics(emptyList()) } throws RuntimeException("calculate failed")

            val viewModel = newViewModel()

            viewModel.state.value shouldBe TabStatisticsUiState(isLoading = false)
            viewModel.events.test {
                awaitItem() shouldBe TabStatisticsEvent.ShowLoadError
            }
        }

        should("init does not treat CancellationException as a load failure") {
            every { observeSudokusAndStatisticsFilterFlags() } throws CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.state.value shouldBe TabStatisticsUiState()
            viewModel.events.test { expectNoEvents() }
        }
    },
)
