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
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private fun testSudoku(
    size: SudokuSize = SudokuSize.FOUR,
    completed: Boolean = false,
    modeLevel: Int = 1,
): Sudoku =
    Sudoku.create(
        size = size,
        difficulty = Difficulty.EASY,
        modeLevel = modeLevel,
        fields =
            MutableList(size.cellCount) { index ->
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

        fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(SudokuLevelTab.KEY_SIZE to 4))) =
            SudokuLevelTabViewModel(
                initSudokuLevel,
                observeSudokuLevel,
                getMaxSudokuLevel,
                generateSudokuLevel,
                saveSudoku,
                savedStateHandle,
            )

        should("fail fast when the saved state handle has no size entry") {
            val error = shouldThrow<IllegalStateException> { newViewModel(SavedStateHandle()) }
            error.message shouldBe "Missing size argument"
            coVerify(exactly = 0) { initSudokuLevel(any()) }
        }

        should("size is read from the saved state handle when present") {
            newViewModel(SavedStateHandle(mapOf(SudokuLevelTab.KEY_SIZE to 9)))
            coVerify(exactly = 1) { initSudokuLevel(SudokuSize.NINE) }
        }

        should("observe the level only while state is collected") {
            val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
            every { observeSudokuLevel(SudokuSize.FOUR) } returns levelFlow
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

        should("a completed top level emitted twice generates the next level once and reveals it once") {
            runTest {
                val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
                every { observeSudokuLevel(SudokuSize.FOUR) } returns levelFlow
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
                val nextLevelSudoku = testSudoku(modeLevel = 2)
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevelSudoku
                val completedItem = SudokuItem(testSudoku(completed = true), "1")
                val viewModel = newViewModel()

                viewModel.state.test {
                    levelFlow.emit(listOf(completedItem))
                    advanceUntilIdle()
                    viewModel.reveal.value shouldBe nextLevelSudoku.id
                    viewModel.onRevealHandled(nextLevelSudoku.id)
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
                coVerify(exactly = 1) { generateSudokuLevel(SudokuSize.FOUR, 2) }
                viewModel.reveal.value shouldBe null
            }
        }

        should("a re-emission right after the next level shows reveals it once") {
            runTest {
                val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
                every { observeSudokuLevel(SudokuSize.FOUR) } returns levelFlow
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
                val nextLevelSudoku = testSudoku(modeLevel = 2)
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevelSudoku
                val completedItem = SudokuItem(testSudoku(completed = true), "1")
                val viewModel = newViewModel()

                viewModel.state.test {
                    levelFlow.emit(listOf(completedItem))
                    runCurrent()
                    viewModel.reveal.value shouldBe nextLevelSudoku.id
                    viewModel.onRevealHandled(nextLevelSudoku.id)
                    levelFlow.emit(listOf(completedItem))
                    advanceUntilIdle()
                    cancelAndIgnoreRemainingEvents()
                }

                viewModel.reveal.value shouldBe null
            }
        }

        should("collecting state again after the stop timeout shows the same next level without generating another") {
            runTest {
                val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
                every { observeSudokuLevel(SudokuSize.FOUR) } returns levelFlow
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
                val nextLevelSudoku = testSudoku(modeLevel = 2)
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returnsMany listOf(nextLevelSudoku, testSudoku(modeLevel = 2))
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
                coVerify(exactly = 1) { generateSudokuLevel(SudokuSize.FOUR, 2) }
            }
        }

        should("returning after the stop timeout with the next level cached never shows the generating state") {
            runTest {
                val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
                every { observeSudokuLevel(SudokuSize.FOUR) } returns levelFlow
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
                val nextLevelSudoku = testSudoku(modeLevel = 2)
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevelSudoku
                val completedItem = SudokuItem(testSudoku(completed = true), "1")
                levelFlow.emit(listOf(completedItem))
                val viewModel = newViewModel()
                viewModel.state.test {
                    advanceUntilIdle()
                    cancelAndIgnoreRemainingEvents()
                }
                advanceTimeBy(5_001)
                runCurrent()
                val maxLevel = CompletableDeferred<Int>()
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } coAnswers { maxLevel.await() }
                val levelWithNext =
                    SudokuLevelTabUiState(
                        sudokuLevel = listOf(SudokuItem(nextLevelSudoku, "2"), completedItem),
                        isLoading = false,
                        isGeneratingNextLevel = false,
                        hasNextLevelToStart = true,
                    )

                viewModel.state.test {
                    runCurrent()
                    levelFlow.subscriptionCount.value shouldBe 1
                    viewModel.state.value shouldBe levelWithNext
                    maxLevel.complete(1)
                    advanceUntilIdle()
                    expectMostRecentItem() shouldBe levelWithNext
                    expectNoEvents()
                }
                coVerify(exactly = 1) { generateSudokuLevel(SudokuSize.FOUR, 2) }
            }
        }

        should("a missing next level shows the generating state only while it is generated") {
            runTest {
                every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(emptyList())
                val maxLevel = CompletableDeferred<Int>()
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } coAnswers { maxLevel.await() }
                val generatedSudoku = CompletableDeferred<Sudoku>()
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 6) } coAnswers { generatedSudoku.await() }
                val nextLevelSudoku = testSudoku(modeLevel = 6)
                val viewModel = newViewModel()

                viewModel.state.test {
                    runCurrent()
                    viewModel.state.value shouldBe SudokuLevelTabUiState()
                    maxLevel.complete(5)
                    runCurrent()
                    viewModel.state.value shouldBe SudokuLevelTabUiState(isGeneratingNextLevel = true)
                    generatedSudoku.complete(nextLevelSudoku)
                    advanceUntilIdle()
                    expectMostRecentItem() shouldBe
                        SudokuLevelTabUiState(
                            sudokuLevel = listOf(SudokuItem(nextLevelSudoku, "6")),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = true,
                        )
                }
            }
        }

        should("a top level completed while state is not collected shows a new next level once collected again") {
            runTest {
                val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
                every { observeSudokuLevel(SudokuSize.FOUR) } returns levelFlow
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
                val nextLevelSudoku = testSudoku(modeLevel = 2)
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevelSudoku
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
                viewModel.reveal.value shouldBe nextLevelSudoku.id
            }
        }

        should("init sets isLoading false and reports loadFailed when initSudokuLevel throws") {
            coEvery { initSudokuLevel(SudokuSize.FOUR) } throws RuntimeException("init failed")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false)
            }
            viewModel.loadFailed.value shouldBe true
        }

        should("an initSudokuLevel failure reports loadFailed once, and a collection after the stop timeout re-runs the upstream") {
            runTest {
                val initResult = CompletableDeferred<Unit>()
                coEvery { initSudokuLevel(SudokuSize.FOUR) } coAnswers { initResult.await() }
                val viewModel = newViewModel()

                viewModel.state.test { expectMostRecentItem() shouldBe SudokuLevelTabUiState() }
                advanceTimeBy(5_001)
                runCurrent()
                initResult.completeExceptionally(RuntimeException("init failed"))
                runCurrent()
                viewModel.state.value shouldBe SudokuLevelTabUiState()
                viewModel.loadFailed.value shouldBe true
                viewModel.onLoadFailureHandled()
                viewModel.state.test {
                    runCurrent()
                    expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false)
                }

                coVerify(exactly = 1) { initSudokuLevel(SudokuSize.FOUR) }
                viewModel.loadFailed.value shouldBe false
            }
        }

        should("onLoadFailureHandled clears a reported load failure") {
            coEvery { initSudokuLevel(SudokuSize.FOUR) } throws RuntimeException("init failed")
            val viewModel = newViewModel()
            viewModel.state.test { expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false) }
            viewModel.loadFailed.value shouldBe true

            viewModel.onLoadFailureHandled()

            viewModel.loadFailed.value shouldBe false
        }

        should("init does not treat initSudokuLevel's CancellationException as a load failure") {
            coEvery { initSudokuLevel(SudokuSize.FOUR) } throws CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState()
            }
            viewModel.loadFailed.value shouldBe false
        }

        should("a non-empty, non-completed emission sets state directly without a reveal") {
            val item = SudokuItem(testSudoku(completed = false), "1")
            every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(listOf(item))

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
            viewModel.reveal.value shouldBe null
        }

        should("an empty emission generates the next level and reveals it with the same dispatch") {
            runTest {
                every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(emptyList())
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 5
                val nextLevelSudoku = testSudoku()
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 6) } returns nextLevelSudoku

                val viewModel = newViewModel()

                viewModel.state.test {
                    viewModel.reveal.value shouldBe nextLevelSudoku.id
                    testScheduler.currentTime shouldBe 0
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

        should("a completed first item generates the next level and reveals it with the same dispatch") {
            runTest {
                val completedItem = SudokuItem(testSudoku(completed = true), "1")
                every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(listOf(completedItem))
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 5
                val nextLevelSudoku = testSudoku()
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 6) } returns nextLevelSudoku

                val viewModel = newViewModel()

                viewModel.state.test {
                    viewModel.reveal.value shouldBe nextLevelSudoku.id
                    testScheduler.currentTime shouldBe 0
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

        should("a generateSudokuLevel failure on the first emission stops loading and generating and reports loadFailed") {
            every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(emptyList())
            coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 5
            coEvery { generateSudokuLevel(SudokuSize.FOUR, 6) } throws RuntimeException("generate failed")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false, isGeneratingNextLevel = false)
            }
            viewModel.loadFailed.value shouldBe true
        }

        should("an observeSudokuLevel failure stops loading and reports loadFailed again for a collection after the stop timeout") {
            runTest {
                every { observeSudokuLevel(SudokuSize.FOUR) } returns flow { throw IllegalStateException("observe failed") }
                val viewModel = newViewModel()

                viewModel.state.test { expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false) }
                viewModel.loadFailed.value shouldBe true
                viewModel.onLoadFailureHandled()
                advanceTimeBy(5_001)
                runCurrent()
                viewModel.state.test { expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false) }

                verify(exactly = 2) { observeSudokuLevel(SudokuSize.FOUR) }
                viewModel.loadFailed.value shouldBe true
            }
        }

        should("inner CancellationException from generateSudokuLevel is not treated as a load failure") {
            every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(emptyList())
            coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 5
            coEvery { generateSudokuLevel(SudokuSize.FOUR, 6) } throws CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState(isGeneratingNextLevel = true)
            }
            viewModel.loadFailed.value shouldBe false
        }

        should("a newer next level replaces the pending reveal, a stale handled id keeps it and its own id clears it") {
            runTest {
                val levelFlow = MutableSharedFlow<List<SudokuItem>>(replay = 1)
                every { observeSudokuLevel(SudokuSize.FOUR) } returns levelFlow
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
                val levelTwo = testSudoku(modeLevel = 2)
                val levelThree = testSudoku(modeLevel = 3)
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns levelTwo
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 3) } returns levelThree
                val viewModel = newViewModel()

                viewModel.state.test {
                    levelFlow.emit(listOf(SudokuItem(testSudoku(completed = true, modeLevel = 1), "1")))
                    advanceUntilIdle()
                    viewModel.reveal.value shouldBe levelTwo.id
                    coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 2
                    levelFlow.emit(listOf(SudokuItem(testSudoku(completed = true, modeLevel = 2), "2")))
                    advanceUntilIdle()
                    viewModel.reveal.value shouldBe levelThree.id

                    viewModel.onRevealHandled(levelTwo.id)
                    viewModel.reveal.value shouldBe levelThree.id

                    viewModel.onRevealHandled(levelThree.id)
                    viewModel.reveal.value shouldBe null
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        should("confirming a row other than the top row opens it without saving") {
            val level = testSudoku(modeLevel = 1)
            val viewModel = newViewModel()

            viewModel.confirmSudokuStart(1, level)

            viewModel.levelStart.value shouldBe LevelStart.Open(level.id)
            coVerify(exactly = 0) { saveSudoku(any(), any()) }
        }

        should("confirming the top row without a next level to start opens it without saving") {
            val level = testSudoku(modeLevel = 1)
            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem().hasNextLevelToStart shouldBe false

                viewModel.confirmSudokuStart(0, level)
            }

            viewModel.levelStart.value shouldBe LevelStart.Open(level.id)
            coVerify(exactly = 0) { saveSudoku(any(), any()) }
        }

        should("confirming the next level saves it and then opens it when its level is above the saved max level") {
            val nextLevel = testSudoku(modeLevel = 2)
            every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(listOf(SudokuItem(testSudoku(completed = true), "1")))
            coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
            coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevel
            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem().hasNextLevelToStart shouldBe true

                viewModel.confirmSudokuStart(0, nextLevel)
            }

            viewModel.levelStart.value shouldBe LevelStart.Open(nextLevel.id)
            coVerify(exactly = 1) { saveSudoku(nextLevel) }
        }

        should("confirming the next level returns to idle without saving it when its level is already saved") {
            val completedItem = SudokuItem(testSudoku(completed = true), "1")
            val nextLevel = testSudoku(modeLevel = 2)
            every { observeSudokuLevel(SudokuSize.FOUR) } returns
                flowOf(listOf(completedItem)) andThen flowOf(listOf(SudokuItem(nextLevel, "2"), completedItem))
            coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
            coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevel
            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem().hasNextLevelToStart shouldBe true
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 2

                viewModel.confirmSudokuStart(0, nextLevel)

                expectMostRecentItem().hasNextLevelToStart shouldBe false
            }

            viewModel.levelStart.value shouldBe LevelStart.Idle
            coVerify(exactly = 0) { saveSudoku(any(), any()) }
            viewModel.reveal.value shouldBe nextLevel.id
            viewModel.loadFailed.value shouldBe false
            verify(exactly = 2) { observeSudokuLevel(SudokuSize.FOUR) }
        }

        should("a next-level start refused after the level list failed reloads the list, which then shows the saved level") {
            runTest {
                val completedItem = SudokuItem(testSudoku(completed = true), "1")
                val nextLevel = testSudoku(modeLevel = 2)
                val listFailure = CompletableDeferred<Unit>()
                every { observeSudokuLevel(SudokuSize.FOUR) } returns
                    flow {
                        emit(listOf(completedItem))
                        listFailure.await()
                        throw IllegalStateException("observe failed")
                    } andThen flowOf(listOf(SudokuItem(nextLevel, "2"), completedItem))
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevel
                val viewModel = newViewModel()

                viewModel.state.test {
                    advanceUntilIdle()
                    expectMostRecentItem().hasNextLevelToStart shouldBe true
                    coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 2
                    listFailure.complete(Unit)
                    advanceUntilIdle()
                    viewModel.loadFailed.value shouldBe true
                    viewModel.onLoadFailureHandled()

                    viewModel.confirmSudokuStart(0, nextLevel)
                    advanceUntilIdle()

                    expectMostRecentItem() shouldBe
                        SudokuLevelTabUiState(
                            sudokuLevel = listOf(SudokuItem(nextLevel, "2"), completedItem),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = false,
                        )
                }
                viewModel.levelStart.value shouldBe LevelStart.Idle
                viewModel.loadFailed.value shouldBe false
                coVerify(exactly = 0) { saveSudoku(any(), any()) }
            }
        }

        should("a next-level start refused after the level list failed reloads the list, which reports the load failure again") {
            runTest {
                val completedItem = SudokuItem(testSudoku(completed = true), "1")
                val nextLevel = testSudoku(modeLevel = 2)
                val listFailure = CompletableDeferred<Unit>()
                every { observeSudokuLevel(SudokuSize.FOUR) } returns
                    flow {
                        emit(listOf(completedItem))
                        listFailure.await()
                        throw IllegalStateException("observe failed")
                    } andThen flow { throw IllegalStateException("observe failed again") }
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevel
                val viewModel = newViewModel()

                viewModel.state.test {
                    advanceUntilIdle()
                    expectMostRecentItem().hasNextLevelToStart shouldBe true
                    coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 2
                    listFailure.complete(Unit)
                    advanceUntilIdle()
                    viewModel.loadFailed.value shouldBe true
                    viewModel.onLoadFailureHandled()

                    viewModel.confirmSudokuStart(0, nextLevel)
                    advanceUntilIdle()

                    viewModel.loadFailed.value shouldBe true
                    cancelAndIgnoreRemainingEvents()
                }
                viewModel.levelStart.value shouldBe LevelStart.Idle
                coVerify(exactly = 0) { saveSudoku(any(), any()) }
                verify(exactly = 2) { observeSudokuLevel(SudokuSize.FOUR) }
            }
        }

        should("a failed next-level save ends the start as failed and keeps the next level reveal pending") {
            val nextLevel = testSudoku(modeLevel = 2)
            every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(listOf(SudokuItem(testSudoku(completed = true), "1")))
            coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
            coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevel
            coEvery { saveSudoku(nextLevel) } throws IllegalStateException("insert failed")
            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem().hasNextLevelToStart shouldBe true

                viewModel.confirmSudokuStart(0, nextLevel)
            }

            viewModel.levelStart.value shouldBe LevelStart.Failed
            viewModel.reveal.value shouldBe nextLevel.id
        }

        should("a canceled next-level save is not reported as a failed start") {
            val nextLevel = testSudoku(modeLevel = 2)
            every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(listOf(SudokuItem(testSudoku(completed = true), "1")))
            coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
            coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevel
            coEvery { saveSudoku(nextLevel) } throws CancellationException("cancelled")
            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem().hasNextLevelToStart shouldBe true

                viewModel.confirmSudokuStart(0, nextLevel)
            }

            viewModel.levelStart.value shouldBe LevelStart.Running
            coVerify(exactly = 1) { saveSudoku(nextLevel) }
        }

        should("handling another id keeps the pending reveal and handling its own id clears it") {
            val nextLevel = testSudoku(modeLevel = 2)
            every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(listOf(SudokuItem(testSudoku(completed = true), "1")))
            coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
            coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevel
            val viewModel = newViewModel()
            viewModel.state.test { expectMostRecentItem().hasNextLevelToStart shouldBe true }
            viewModel.reveal.value shouldBe nextLevel.id

            viewModel.onRevealHandled(testSudoku(modeLevel = 1).id)
            viewModel.reveal.value shouldBe nextLevel.id

            viewModel.onRevealHandled(nextLevel.id)
            viewModel.reveal.value shouldBe null
        }

        should("a second confirm while the next level saves is refused and saves it once") {
            runTest {
                val nextLevel = testSudoku(modeLevel = 2)
                every { observeSudokuLevel(SudokuSize.FOUR) } returns flowOf(listOf(SudokuItem(testSudoku(completed = true), "1")))
                coEvery { getMaxSudokuLevel(SudokuSize.FOUR) } returns 1
                coEvery { generateSudokuLevel(SudokuSize.FOUR, 2) } returns nextLevel
                val saveGate = CompletableDeferred<Unit>()
                coEvery { saveSudoku(nextLevel) } coAnswers { saveGate.await() }
                val viewModel = newViewModel()

                viewModel.state.test {
                    expectMostRecentItem().hasNextLevelToStart shouldBe true

                    viewModel.confirmSudokuStart(0, nextLevel)
                    runCurrent()
                    viewModel.levelStart.value shouldBe LevelStart.Running
                    viewModel.confirmSudokuStart(0, nextLevel)
                    viewModel.confirmSudokuStart(1, testSudoku(completed = true))
                    viewModel.levelStart.value shouldBe LevelStart.Running
                    saveGate.complete(Unit)
                    runCurrent()
                }

                viewModel.levelStart.value shouldBe LevelStart.Open(nextLevel.id)
                coVerify(exactly = 1) { saveSudoku(nextLevel) }
            }
        }

        should("handling the current result returns to idle and a repeated start opens the level again") {
            val level = testSudoku(modeLevel = 1)
            val viewModel = newViewModel()
            viewModel.confirmSudokuStart(1, level)

            viewModel.onLevelStartHandled(LevelStart.Open(level.id))

            viewModel.levelStart.value shouldBe LevelStart.Idle
            viewModel.confirmSudokuStart(1, level)
            viewModel.levelStart.value shouldBe LevelStart.Open(level.id)
        }

        should("handling a stale result keeps the current result") {
            val level = testSudoku(modeLevel = 1)
            val otherLevel = testSudoku(modeLevel = 1)
            val viewModel = newViewModel()
            viewModel.confirmSudokuStart(1, level)

            viewModel.onLevelStartHandled(LevelStart.Open(otherLevel.id))
            viewModel.onLevelStartHandled(LevelStart.Failed)

            viewModel.levelStart.value shouldBe LevelStart.Open(level.id)
        }
    },
)
