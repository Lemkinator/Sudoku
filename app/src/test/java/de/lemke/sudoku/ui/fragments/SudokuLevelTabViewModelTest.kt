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

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.InitSudokuLevelUseCase
import de.lemke.sudoku.domain.ObserveSudokuLevelUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private fun testSudoku(
    size: Int = 4,
    completed: Boolean = false,
): Sudoku =
    Sudoku.create(
        size = size,
        difficulty = Difficulty.EASY,
        modeLevel = 1,
        fields =
            MutableList(size * size) { index ->
                Field(Position.create(index, size), solution = 1, value = if (completed) 1 else null)
            },
    )

@OptIn(ExperimentalCoroutinesApi::class)
class SudokuLevelTabViewModelTest : ShouldSpec(
    {
        val initSudokuLevel = mockk<InitSudokuLevelUseCase>()
        val observeSudokuLevel = mockk<ObserveSudokuLevelUseCase>()
        val getMaxSudokuLevel = mockk<GetMaxSudokuLevelUseCase>()
        val generateSudokuLevel = mockk<GenerateSudokuLevelUseCase>()
        val saveSudoku = mockk<SaveSudokuUseCase>(relaxUnitFun = true)

        beforeEach {
            clearMocks(initSudokuLevel, observeSudokuLevel, getMaxSudokuLevel, generateSudokuLevel, saveSudoku)
            coEvery { initSudokuLevel(any()) } returns Unit
            every { observeSudokuLevel(any()) } returns flowOf(listOf(SudokuItem(testSudoku(completed = false), "1")))
        }

        fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
            SudokuLevelTabViewModel(
                initSudokuLevel,
                observeSudokuLevel,
                getMaxSudokuLevel,
                generateSudokuLevel,
                saveSudoku,
                savedStateHandle,
            )

        should("size defaults to 4 when the saved state handle has no size entry") {
            newViewModel(SavedStateHandle())
            coVerify(exactly = 1) { initSudokuLevel(4) }
        }

        should("size is read from the saved state handle when present") {
            newViewModel(SavedStateHandle(mapOf("size" to 9)))
            coVerify(exactly = 1) { initSudokuLevel(9) }
        }

        should("init sets isLoading false and emits ShowLoadError when initSudokuLevel throws") {
            coEvery { initSudokuLevel(4) } throws RuntimeException("init failed")

            val viewModel = newViewModel()

            viewModel.state.value.isLoading
                .shouldBeFalse()
            viewModel.events.test {
                awaitItem() shouldBe SudokuLevelTabEvent.ShowLoadError
            }
        }

        should("init does not treat initSudokuLevel's CancellationException as a load failure") {
            coEvery { initSudokuLevel(4) } throws CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.state.value shouldBe SudokuLevelTabUiState()
            viewModel.events.test { expectNoEvents() }
        }

        should("a non-empty, non-completed emission sets state directly without ScrollToTop") {
            val item = SudokuItem(testSudoku(completed = false), "1")
            every { observeSudokuLevel(4) } returns flowOf(listOf(item))

            val viewModel = newViewModel()

            viewModel.state.value shouldBe
                SudokuLevelTabUiState(
                    sudokuLevel = listOf(item),
                    isLoading = false,
                    isGeneratingNextLevel = false,
                    hasNextLevelToStart = false,
                )
            viewModel.events.test { expectNoEvents() }
        }

        should("an empty emission generates the next level and emits ScrollToTop after the delay") {
            runTest {
                Dispatchers.setMain(StandardTestDispatcher(testScheduler))
                every { observeSudokuLevel(4) } returns flowOf(emptyList())
                coEvery { getMaxSudokuLevel(4) } returns 5
                val nextLevelSudoku = testSudoku()
                coEvery { generateSudokuLevel(4, 6) } returns nextLevelSudoku

                val viewModel = newViewModel()

                viewModel.events.test {
                    advanceUntilIdle()
                    awaitItem() shouldBe SudokuLevelTabEvent.ScrollToTop
                    cancelAndIgnoreRemainingEvents()
                }
                viewModel.state.value shouldBe
                    SudokuLevelTabUiState(
                        sudokuLevel = listOf(SudokuItem(nextLevelSudoku, nextLevelSudoku.modeLevel.toString())),
                        isLoading = false,
                        isGeneratingNextLevel = false,
                        hasNextLevelToStart = true,
                    )
            }
        }

        should("a completed first item generates the next level and emits ScrollToTop after the delay") {
            runTest {
                Dispatchers.setMain(StandardTestDispatcher(testScheduler))
                val completedItem = SudokuItem(testSudoku(completed = true), "1")
                every { observeSudokuLevel(4) } returns flowOf(listOf(completedItem))
                coEvery { getMaxSudokuLevel(4) } returns 5
                val nextLevelSudoku = testSudoku()
                coEvery { generateSudokuLevel(4, 6) } returns nextLevelSudoku

                val viewModel = newViewModel()

                viewModel.events.test {
                    advanceUntilIdle()
                    awaitItem() shouldBe SudokuLevelTabEvent.ScrollToTop
                    cancelAndIgnoreRemainingEvents()
                }
                viewModel.state.value shouldBe
                    SudokuLevelTabUiState(
                        sudokuLevel = listOf(SudokuItem(nextLevelSudoku, nextLevelSudoku.modeLevel.toString()), completedItem),
                        isLoading = false,
                        isGeneratingNextLevel = false,
                        hasNextLevelToStart = true,
                    )
            }
        }

        should("inner failure sets isGeneratingNextLevel false and emits ShowLoadError when generateSudokuLevel throws") {
            every { observeSudokuLevel(4) } returns flowOf(emptyList())
            coEvery { getMaxSudokuLevel(4) } returns 5
            coEvery { generateSudokuLevel(4, 6) } throws RuntimeException("generate failed")

            val viewModel = newViewModel()

            viewModel.state.value.isGeneratingNextLevel
                .shouldBeFalse()
            viewModel.events.test {
                awaitItem() shouldBe SudokuLevelTabEvent.ShowLoadError
            }
        }

        should("inner CancellationException from generateSudokuLevel is not treated as a load failure") {
            every { observeSudokuLevel(4) } returns flowOf(emptyList())
            coEvery { getMaxSudokuLevel(4) } returns 5
            coEvery { generateSudokuLevel(4, 6) } throws CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.state.value.isGeneratingNextLevel
                .shouldBeTrue()
            viewModel.events.test { expectNoEvents() }
        }

        should("onNextLevelSudokuConfirmed delegates to saveSudoku") {
            val sudoku = testSudoku()
            val viewModel = newViewModel()

            viewModel.onNextLevelSudokuConfirmed(sudoku)

            coVerify(exactly = 1) { saveSudoku(sudoku) }
        }
    },
)
