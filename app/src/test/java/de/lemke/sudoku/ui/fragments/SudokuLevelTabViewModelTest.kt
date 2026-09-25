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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private fun testSudoku(
    size: Int = 4,
    completed: Boolean = false,
    modeLevel: Int = 1,
): Sudoku =
    Sudoku.create(
        size = size,
        difficulty = Difficulty.EASY,
        modeLevel = modeLevel,
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

        should("observe the level only while state is collected") {
            val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
            every { observeSudokuLevel(4) } returns levelFlow
            val item = SudokuItem(testSudoku(completed = false), "1")

            val viewModel = newViewModel()
            levelFlow.emit(listOf(item))

            levelFlow.subscriptionCount.value shouldBe 0
            viewModel.state.value shouldBe SudokuLevelTabUiState()
            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState(sudokuLevel = listOf(item), isLoading = false)
                levelFlow.subscriptionCount.value shouldBe 1
            }
        }

        should("a completed top level emitted twice generates the next level once and scrolls to it once") {
            runTest {
                val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
                every { observeSudokuLevel(4) } returns levelFlow
                coEvery { getMaxSudokuLevel(4) } returns 1
                val nextLevelSudoku = testSudoku(modeLevel = 2)
                coEvery { generateSudokuLevel(4, 2) } returns nextLevelSudoku
                val completedItem = SudokuItem(testSudoku(completed = true), "1")
                val viewModel = newViewModel()

                viewModel.state.test {
                    levelFlow.emit(listOf(completedItem))
                    advanceUntilIdle()
                    levelFlow.emit(listOf(completedItem))
                    advanceUntilIdle()

                    expectMostRecentItem() shouldBe
                        SudokuLevelTabUiState(
                            sudokuLevel = listOf(SudokuItem(nextLevelSudoku, "2"), completedItem),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = true,
                        )
                }
                coVerify(exactly = 1) { generateSudokuLevel(4, 2) }
                viewModel.events.test {
                    awaitItem() shouldBe SudokuLevelTabEvent.ScrollToTop
                    expectNoEvents()
                }
            }
        }

        should("a re-emission during the scroll delay still scrolls to the new next level once") {
            runTest {
                val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
                every { observeSudokuLevel(4) } returns levelFlow
                coEvery { getMaxSudokuLevel(4) } returns 1
                coEvery { generateSudokuLevel(4, 2) } returns testSudoku(modeLevel = 2)
                val completedItem = SudokuItem(testSudoku(completed = true), "1")
                val viewModel = newViewModel()

                viewModel.state.test {
                    levelFlow.emit(listOf(completedItem))
                    runCurrent()
                    levelFlow.emit(listOf(completedItem))
                    advanceUntilIdle()
                    cancelAndIgnoreRemainingEvents()
                }

                viewModel.events.test {
                    awaitItem() shouldBe SudokuLevelTabEvent.ScrollToTop
                    expectNoEvents()
                }
            }
        }

        should("collecting state again after the stop timeout shows the same next level without generating another") {
            runTest {
                val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
                every { observeSudokuLevel(4) } returns levelFlow
                coEvery { getMaxSudokuLevel(4) } returns 1
                val nextLevelSudoku = testSudoku(modeLevel = 2)
                coEvery { generateSudokuLevel(4, 2) } returnsMany listOf(nextLevelSudoku, testSudoku(modeLevel = 2))
                levelFlow.emit(listOf(SudokuItem(testSudoku(completed = true), "1")))
                val viewModel = newViewModel()
                viewModel.state.test {
                    advanceUntilIdle()
                    cancelAndIgnoreRemainingEvents()
                }
                advanceTimeBy(5_001)
                runCurrent()
                levelFlow.subscriptionCount.value shouldBe 0

                viewModel.state.test {
                    advanceUntilIdle()
                    levelFlow.subscriptionCount.value shouldBe 1
                    val shownNextLevel = expectMostRecentItem().sudokuLevel.first() as SudokuItem
                    shownNextLevel.sudoku shouldBeSameInstanceAs nextLevelSudoku
                }
                coVerify(exactly = 1) { generateSudokuLevel(4, 2) }
            }
        }

        should("a top level completed while state is not collected shows a new next level once collected again") {
            runTest {
                val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
                every { observeSudokuLevel(4) } returns levelFlow
                coEvery { getMaxSudokuLevel(4) } returns 1
                val nextLevelSudoku = testSudoku(modeLevel = 2)
                coEvery { generateSudokuLevel(4, 2) } returns nextLevelSudoku
                levelFlow.emit(listOf(SudokuItem(testSudoku(completed = false), "1")))
                val viewModel = newViewModel()
                viewModel.state.test {
                    advanceUntilIdle()
                    cancelAndIgnoreRemainingEvents()
                }
                advanceTimeBy(5_001)
                runCurrent()

                val completedItem = SudokuItem(testSudoku(completed = true), "1")
                levelFlow.emit(listOf(completedItem))
                coVerify(exactly = 0) { generateSudokuLevel(any(), any()) }

                viewModel.state.test {
                    advanceUntilIdle()
                    expectMostRecentItem() shouldBe
                        SudokuLevelTabUiState(
                            sudokuLevel = listOf(SudokuItem(nextLevelSudoku, "2"), completedItem),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = true,
                        )
                }
                viewModel.events.test {
                    awaitItem() shouldBe SudokuLevelTabEvent.ScrollToTop
                }
            }
        }

        should("init sets isLoading false and emits ShowLoadError when initSudokuLevel throws") {
            coEvery { initSudokuLevel(4) } throws RuntimeException("init failed")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false)
            }
            viewModel.events.test {
                awaitItem() shouldBe SudokuLevelTabEvent.ShowLoadError
            }
        }

        should("an initSudokuLevel failure emits ShowLoadError once, and a collection after the stop timeout re-runs the upstream") {
            runTest {
                val initResult = CompletableDeferred<Unit>()
                coEvery { initSudokuLevel(4) } coAnswers { initResult.await() }
                val viewModel = newViewModel()

                viewModel.state.test { expectMostRecentItem() shouldBe SudokuLevelTabUiState() }
                advanceTimeBy(5_001)
                runCurrent()
                initResult.completeExceptionally(RuntimeException("init failed"))
                runCurrent()
                viewModel.state.value shouldBe SudokuLevelTabUiState()
                viewModel.state.test {
                    runCurrent()
                    expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false)
                }

                coVerify(exactly = 1) { initSudokuLevel(4) }
                viewModel.events.test {
                    awaitItem() shouldBe SudokuLevelTabEvent.ShowLoadError
                    expectNoEvents()
                }
            }
        }

        should("init does not treat initSudokuLevel's CancellationException as a load failure") {
            coEvery { initSudokuLevel(4) } throws CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState()
            }
            viewModel.events.test { expectNoEvents() }
        }

        should("a non-empty, non-completed emission sets state directly without ScrollToTop") {
            val item = SudokuItem(testSudoku(completed = false), "1")
            every { observeSudokuLevel(4) } returns flowOf(listOf(item))

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe
                    SudokuLevelTabUiState(
                        sudokuLevel = listOf(item),
                        isLoading = false,
                        isGeneratingNextLevel = false,
                        hasNextLevelToStart = false,
                    )
            }
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

                viewModel.state.test {
                    viewModel.events.test {
                        advanceUntilIdle()
                        awaitItem() shouldBe SudokuLevelTabEvent.ScrollToTop
                        cancelAndIgnoreRemainingEvents()
                    }
                    expectMostRecentItem() shouldBe
                        SudokuLevelTabUiState(
                            sudokuLevel = listOf(SudokuItem(nextLevelSudoku, nextLevelSudoku.modeLevel.toString())),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = true,
                        )
                }
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

                viewModel.state.test {
                    viewModel.events.test {
                        advanceUntilIdle()
                        awaitItem() shouldBe SudokuLevelTabEvent.ScrollToTop
                        cancelAndIgnoreRemainingEvents()
                    }
                    expectMostRecentItem() shouldBe
                        SudokuLevelTabUiState(
                            sudokuLevel = listOf(SudokuItem(nextLevelSudoku, nextLevelSudoku.modeLevel.toString()), completedItem),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = true,
                        )
                }
            }
        }

        should("inner failure sets isGeneratingNextLevel false and emits ShowLoadError when generateSudokuLevel throws") {
            every { observeSudokuLevel(4) } returns flowOf(emptyList())
            coEvery { getMaxSudokuLevel(4) } returns 5
            coEvery { generateSudokuLevel(4, 6) } throws RuntimeException("generate failed")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState(isGeneratingNextLevel = false)
            }
            viewModel.events.test {
                awaitItem() shouldBe SudokuLevelTabEvent.ShowLoadError
            }
        }

        should("inner CancellationException from generateSudokuLevel is not treated as a load failure") {
            every { observeSudokuLevel(4) } returns flowOf(emptyList())
            coEvery { getMaxSudokuLevel(4) } returns 5
            coEvery { generateSudokuLevel(4, 6) } throws CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState(isGeneratingNextLevel = true)
            }
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
