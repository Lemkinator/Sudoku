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
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import de.lemke.sudoku.domain.GenerateFieldsUseCase
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.InitSudokuLevelUseCase
import de.lemke.sudoku.domain.ObserveSudokuLevelUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SolvedBoardGenerator
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testLevelSudoku
import de.lemke.sudoku.testSudoku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private data class ShownLevels(
    val levels: List<Pair<SudokuId, String>>,
    val isLoading: Boolean,
    val isGeneratingNextLevel: Boolean,
    val hasNextLevelToStart: Boolean,
)

private fun SudokuLevelTabUiState.shown() =
    ShownLevels(
        levels = sudokuLevel.map { item -> (item as SudokuItem).sudoku.id to item.label },
        isLoading = isLoading,
        isGeneratingNextLevel = isGeneratingNextLevel,
        hasNextLevelToStart = hasNextLevelToStart,
    )

private val SudokuLevelTabUiState.topLevel: Sudoku get() = (sudokuLevel.first() as SudokuItem).sudoku

@OptIn(ExperimentalCoroutinesApi::class)
class SudokuLevelTabViewModelTest : ShouldSpec(
    {
        lateinit var repository: FakeSudokusRepository

        beforeEach {
            repository = FakeSudokusRepository()
        }

        fun newViewModel(
            savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(SudokuLevelTab.KEY_SIZE to 4)),
            solvedBoardGenerator: SolvedBoardGenerator = PatternSolvedBoardGenerator(),
            generateDispatcher: CoroutineDispatcher? = null,
        ): SudokuLevelTabViewModel {
            val dispatcher = UnconfinedTestDispatcher()
            val generateSudokuLevel =
                GenerateSudokuLevelUseCase(GenerateFieldsUseCase(dispatcher, solvedBoardGenerator), generateDispatcher ?: dispatcher)
            val getMaxSudokuLevel = GetMaxSudokuLevelUseCase(repository, dispatcher)
            return SudokuLevelTabViewModel(
                InitSudokuLevelUseCase(repository, generateSudokuLevel, getMaxSudokuLevel, dispatcher),
                ObserveSudokuLevelUseCase(repository, dispatcher),
                getMaxSudokuLevel,
                generateSudokuLevel,
                SaveSudokuUseCase(repository, dispatcher),
                savedStateHandle,
            )
        }

        should("fail fast when the saved state handle has no size entry") {
            repository.store(testSudoku(modeLevel = 2))

            val error = shouldThrow<IllegalStateException> { newViewModel(SavedStateHandle()) }
            error.message shouldBe "Missing size argument"
            repository.saves shouldBe emptyList()
        }

        should("size is read from the saved state handle when present") {
            repository.store(testLevelSudoku(SudokuSize.NINE, level = 2))

            newViewModel(SavedStateHandle(mapOf(SudokuLevelTab.KEY_SIZE to 9)))

            repository.saves.map { it.rows.sudoku.size to it.rows.sudoku.modeLevel } shouldBe listOf(9 to 1)
        }

        should("observe the level only while state is collected") {
            val level = testSudoku(modeLevel = 1)

            val viewModel = newViewModel()
            repository.store(level)

            repository.activeObservers shouldBe 0
            viewModel.state.value shouldBe SudokuLevelTabUiState()
            viewModel.state.test {
                expectMostRecentItem().shown() shouldBe
                    ShownLevels(
                        levels = listOf(level.id to "1"),
                        isLoading = false,
                        isGeneratingNextLevel = false,
                        hasNextLevelToStart = false,
                    )
                repository.activeObservers shouldBe 1
            }
        }

        should("a completed top level emitted twice generates the next level once and reveals it once") {
            runTest {
                val completedLevel = testSudoku(modeLevel = 1, completed = true)
                repository.store(completedLevel)
                val viewModel = newViewModel()

                viewModel.state.test {
                    advanceUntilIdle()
                    val nextLevel = viewModel.state.value.topLevel
                    nextLevel.modeLevel shouldBe 2
                    viewModel.reveal.value shouldBe nextLevel.id
                    viewModel.onRevealHandled(nextLevel.id)
                    repository.store(completedLevel.also { it.seconds = 30 })
                    advanceUntilIdle()

                    val shown = expectMostRecentItem()
                    shown.shown() shouldBe
                        ShownLevels(
                            levels = listOf(nextLevel.id to "2", completedLevel.id to "1"),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = true,
                        )
                    (shown.sudokuLevel[1] as SudokuItem).sudoku.seconds shouldBe 30
                }
                viewModel.reveal.value shouldBe null
            }
        }

        should("a re-emission right after the next level shows reveals it once") {
            runTest {
                val completedLevel = testSudoku(modeLevel = 1, completed = true)
                repository.store(completedLevel)
                val viewModel = newViewModel()

                viewModel.state.test {
                    runCurrent()
                    val nextLevel = viewModel.state.value.topLevel
                    viewModel.reveal.value shouldBe nextLevel.id
                    viewModel.onRevealHandled(nextLevel.id)
                    repository.store(completedLevel.also { it.seconds = 30 })
                    advanceUntilIdle()
                    cancelAndIgnoreRemainingEvents()
                }

                viewModel.reveal.value shouldBe null
            }
        }

        should("collecting state again after the stop timeout shows the same next level without generating another") {
            runTest {
                repository.store(testSudoku(modeLevel = 1, completed = true))
                val viewModel = newViewModel()
                lateinit var nextLevel: Sudoku
                viewModel.state.test {
                    advanceUntilIdle()
                    nextLevel = expectMostRecentItem().topLevel
                }
                advanceTimeBy(5_001)
                runCurrent()
                repository.activeObservers shouldBe 0

                viewModel.state.test {
                    advanceUntilIdle()
                    repository.activeObservers shouldBe 1
                    expectMostRecentItem().topLevel shouldBeSameInstanceAs nextLevel
                }
                nextLevel.modeLevel shouldBe 2
            }
        }

        should("returning after the stop timeout with the next level cached never shows the generating state") {
            runTest {
                val completedLevel = testSudoku(modeLevel = 1, completed = true)
                repository.store(completedLevel)
                val viewModel = newViewModel()
                lateinit var nextLevel: Sudoku
                viewModel.state.test {
                    advanceUntilIdle()
                    nextLevel = expectMostRecentItem().topLevel
                }
                advanceTimeBy(5_001)
                runCurrent()
                val maxLevel = CompletableDeferred<Unit>()
                repository.beforeGetMaxSudokuLevel = { maxLevel.await() }
                val levelWithNext =
                    ShownLevels(
                        levels = listOf(nextLevel.id to "2", completedLevel.id to "1"),
                        isLoading = false,
                        isGeneratingNextLevel = false,
                        hasNextLevelToStart = true,
                    )

                viewModel.state.test {
                    runCurrent()
                    repository.activeObservers shouldBe 1
                    viewModel.state.value.shown() shouldBe levelWithNext
                    maxLevel.complete(Unit)
                    advanceUntilIdle()
                    expectMostRecentItem().shown() shouldBe levelWithNext
                    expectNoEvents()
                }
            }
        }

        should("a missing next level shows the generating state only while it is generated") {
            runTest {
                val maxLevel = CompletableDeferred<Unit>()
                repository.beforeGetMaxSudokuLevel = { maxLevel.await() }
                val viewModel = newViewModel(generateDispatcher = StandardTestDispatcher(testScheduler))

                viewModel.state.test {
                    runCurrent()
                    viewModel.state.value shouldBe SudokuLevelTabUiState()
                    maxLevel.complete(Unit)
                    viewModel.state.value shouldBe SudokuLevelTabUiState(isGeneratingNextLevel = true)
                    advanceUntilIdle()
                    val shown = expectMostRecentItem()
                    shown.shown() shouldBe
                        ShownLevels(
                            levels = listOf(shown.topLevel.id to "1"),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = true,
                        )
                }
            }
        }

        should("a top level completed while state is not collected shows a new next level once collected again") {
            runTest {
                val level = testSudoku(modeLevel = 1)
                repository.store(level)
                val viewModel = newViewModel()
                viewModel.state.test {
                    advanceUntilIdle()
                    cancelAndIgnoreRemainingEvents()
                }
                advanceTimeBy(5_001)
                runCurrent()

                repository.store(testSudoku(modeLevel = 1, completed = true, sudokuId = level.id))
                viewModel.state.value.hasNextLevelToStart shouldBe false
                viewModel.reveal.value shouldBe null

                lateinit var nextLevel: Sudoku
                viewModel.state.test {
                    advanceUntilIdle()
                    val shown = expectMostRecentItem()
                    nextLevel = shown.topLevel
                    shown.shown() shouldBe
                        ShownLevels(
                            levels = listOf(nextLevel.id to "2", level.id to "1"),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = true,
                        )
                }
                viewModel.reveal.value shouldBe nextLevel.id
            }
        }

        should("init sets isLoading false and reports loadFailed when initSudokuLevel throws") {
            repository.store(testSudoku(modeLevel = 2))
            repository.saveFailure = RuntimeException("init failed")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false)
            }
            viewModel.loadFailed.value shouldBe true
        }

        should("an initSudokuLevel failure reports loadFailed once, and a collection after the stop timeout re-runs the upstream") {
            runTest {
                repository.store(testSudoku(modeLevel = 2))
                val initResult = CompletableDeferred<Unit>()
                var saveAttempts = 0
                repository.beforeSave = {
                    saveAttempts++
                    initResult.await()
                }
                repository.saveFailure = RuntimeException("init failed")
                val viewModel = newViewModel()

                viewModel.state.test { expectMostRecentItem() shouldBe SudokuLevelTabUiState() }
                advanceTimeBy(5_001)
                runCurrent()
                initResult.complete(Unit)
                runCurrent()
                viewModel.state.value shouldBe SudokuLevelTabUiState()
                viewModel.loadFailed.value shouldBe true
                viewModel.onLoadFailureHandled()
                viewModel.state.test {
                    runCurrent()
                    expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false)
                }

                saveAttempts shouldBe 1
                viewModel.loadFailed.value shouldBe false
            }
        }

        should("onLoadFailureHandled clears a reported load failure") {
            repository.store(testSudoku(modeLevel = 2))
            repository.saveFailure = RuntimeException("init failed")
            val viewModel = newViewModel()
            viewModel.state.test { expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false) }
            viewModel.loadFailed.value shouldBe true

            viewModel.onLoadFailureHandled()

            viewModel.loadFailed.value shouldBe false
        }

        should("init does not treat initSudokuLevel's CancellationException as a load failure") {
            repository.store(testSudoku(modeLevel = 2))
            repository.saveFailure = CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState()
            }
            viewModel.loadFailed.value shouldBe false
        }

        should("a non-empty, non-completed emission sets state directly without a reveal") {
            val level = testSudoku(modeLevel = 1)
            repository.store(level)

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem().shown() shouldBe
                    ShownLevels(
                        levels = listOf(level.id to "1"),
                        isLoading = false,
                        isGeneratingNextLevel = false,
                        hasNextLevelToStart = false,
                    )
            }
            viewModel.reveal.value shouldBe null
        }

        should("an empty emission generates the next level and reveals it with the same dispatch") {
            runTest {
                val viewModel = newViewModel()

                viewModel.state.test {
                    val nextLevel = viewModel.state.value.topLevel
                    viewModel.reveal.value shouldBe nextLevel.id
                    testScheduler.currentTime shouldBe 0
                    expectMostRecentItem().shown() shouldBe
                        ShownLevels(
                            levels = listOf(nextLevel.id to "1"),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = true,
                        )
                }
            }
        }

        should("a completed first item generates the next level and reveals it with the same dispatch") {
            runTest {
                val completedLevel = testSudoku(modeLevel = 1, completed = true)
                repository.store(completedLevel)

                val viewModel = newViewModel()

                viewModel.state.test {
                    val nextLevel = viewModel.state.value.topLevel
                    viewModel.reveal.value shouldBe nextLevel.id
                    testScheduler.currentTime shouldBe 0
                    expectMostRecentItem().shown() shouldBe
                        ShownLevels(
                            levels = listOf(nextLevel.id to "2", completedLevel.id to "1"),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = true,
                        )
                }
            }
        }

        should("a generateSudokuLevel failure on the first emission stops loading and generating and reports loadFailed") {
            val viewModel = newViewModel(solvedBoardGenerator = SolvedBoardGenerator { throw IllegalStateException("generate failed") })

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false, isGeneratingNextLevel = false)
            }
            viewModel.loadFailed.value shouldBe true
        }

        should("an observeSudokuLevel failure stops loading and reports loadFailed again for a collection after the stop timeout") {
            runTest {
                repository.observed = { flow { throw IllegalStateException("observe failed") } }
                val viewModel = newViewModel()

                viewModel.state.test { expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false) }
                viewModel.loadFailed.value shouldBe true
                viewModel.onLoadFailureHandled()
                advanceTimeBy(5_001)
                runCurrent()
                viewModel.state.test { expectMostRecentItem() shouldBe SudokuLevelTabUiState(isLoading = false) }

                repository.observeCalls shouldBe 2
                viewModel.loadFailed.value shouldBe true
            }
        }

        should("inner CancellationException from generateSudokuLevel is not treated as a load failure") {
            val viewModel = newViewModel(solvedBoardGenerator = SolvedBoardGenerator { throw CancellationException("cancelled") })

            viewModel.state.test {
                expectMostRecentItem() shouldBe SudokuLevelTabUiState(isGeneratingNextLevel = true)
            }
            viewModel.loadFailed.value shouldBe false
        }

        should("a newer next level replaces the pending reveal, a stale handled id keeps it and its own id clears it") {
            runTest {
                repository.store(testSudoku(modeLevel = 1, completed = true))
                val viewModel = newViewModel()

                viewModel.state.test {
                    advanceUntilIdle()
                    val levelTwo = viewModel.state.value.topLevel
                    levelTwo.modeLevel shouldBe 2
                    viewModel.reveal.value shouldBe levelTwo.id
                    repository.store(testSudoku(modeLevel = 2, completed = true))
                    advanceUntilIdle()
                    val levelThree = viewModel.state.value.topLevel
                    levelThree.modeLevel shouldBe 3
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
            repository.saves shouldBe emptyList()
        }

        should("confirming the top row without a next level to start opens it without saving") {
            val level = testSudoku(modeLevel = 1)
            repository.store(level)
            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem().hasNextLevelToStart shouldBe false

                viewModel.confirmSudokuStart(0, level)
            }

            viewModel.levelStart.value shouldBe LevelStart.Open(level.id)
            repository.saves shouldBe emptyList()
        }

        should("confirming the next level saves it and then opens it when its level is above the saved max level") {
            repository.store(testSudoku(modeLevel = 1, completed = true))
            val viewModel = newViewModel()
            lateinit var nextLevel: Sudoku

            viewModel.state.test {
                val shown = expectMostRecentItem()
                shown.hasNextLevelToStart shouldBe true
                nextLevel = shown.topLevel

                viewModel.confirmSudokuStart(0, nextLevel)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.levelStart.value shouldBe LevelStart.Open(nextLevel.id)
            repository.saves.map { it.rows.sudoku.id to it.onlyUpdate } shouldBe listOf(nextLevel.id.value to false)
        }

        should("confirming the next level returns to idle without saving it when its level is already saved") {
            repository.store(testSudoku(modeLevel = 1, completed = true))
            repository.observed = { it.take(1) }
            val viewModel = newViewModel()
            lateinit var nextLevel: Sudoku

            viewModel.state.test {
                val shown = expectMostRecentItem()
                shown.hasNextLevelToStart shouldBe true
                nextLevel = shown.topLevel
                repository.store(nextLevel)
                repository.observed = { it }

                viewModel.confirmSudokuStart(0, nextLevel)

                expectMostRecentItem().hasNextLevelToStart shouldBe false
            }

            viewModel.levelStart.value shouldBe LevelStart.Idle
            repository.saves shouldBe emptyList()
            viewModel.reveal.value shouldBe nextLevel.id
            viewModel.loadFailed.value shouldBe false
            repository.observeCalls shouldBe 2
        }

        should("a next-level start refused after the level list failed reloads the list, which then shows the saved level") {
            runTest {
                val completedLevel = testSudoku(modeLevel = 1, completed = true)
                repository.store(completedLevel)
                val listFailure = CompletableDeferred<Unit>()
                repository.observed = { upstream ->
                    flow {
                        emit(upstream.first())
                        listFailure.await()
                        throw IllegalStateException("observe failed")
                    }
                }
                val viewModel = newViewModel()

                viewModel.state.test {
                    advanceUntilIdle()
                    val shown = expectMostRecentItem()
                    shown.hasNextLevelToStart shouldBe true
                    val nextLevel = shown.topLevel
                    repository.store(nextLevel)
                    repository.observed = { it }
                    listFailure.complete(Unit)
                    advanceUntilIdle()
                    viewModel.loadFailed.value shouldBe true
                    viewModel.onLoadFailureHandled()

                    viewModel.confirmSudokuStart(0, nextLevel)
                    advanceUntilIdle()

                    expectMostRecentItem().shown() shouldBe
                        ShownLevels(
                            levels = listOf(nextLevel.id to "2", completedLevel.id to "1"),
                            isLoading = false,
                            isGeneratingNextLevel = false,
                            hasNextLevelToStart = false,
                        )
                }
                viewModel.levelStart.value shouldBe LevelStart.Idle
                viewModel.loadFailed.value shouldBe false
                repository.saves shouldBe emptyList()
            }
        }

        should("a next-level start refused after the level list failed reloads the list, which reports the load failure again") {
            runTest {
                repository.store(testSudoku(modeLevel = 1, completed = true))
                val listFailure = CompletableDeferred<Unit>()
                repository.observed = { upstream ->
                    flow {
                        emit(upstream.first())
                        listFailure.await()
                        throw IllegalStateException("observe failed")
                    }
                }
                val viewModel = newViewModel()

                viewModel.state.test {
                    advanceUntilIdle()
                    val shown = expectMostRecentItem()
                    shown.hasNextLevelToStart shouldBe true
                    val nextLevel = shown.topLevel
                    repository.store(nextLevel)
                    repository.observed = { flow { throw IllegalStateException("observe failed again") } }
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
                repository.saves shouldBe emptyList()
                repository.observeCalls shouldBe 2
            }
        }

        should("a failed next-level save ends the start as failed and keeps the next level reveal pending") {
            repository.store(testSudoku(modeLevel = 1, completed = true))
            repository.saveFailure = IllegalStateException("insert failed")
            val viewModel = newViewModel()
            lateinit var nextLevel: Sudoku

            viewModel.state.test {
                val shown = expectMostRecentItem()
                shown.hasNextLevelToStart shouldBe true
                nextLevel = shown.topLevel

                viewModel.confirmSudokuStart(0, nextLevel)
            }

            viewModel.levelStart.value shouldBe LevelStart.Failed
            viewModel.reveal.value shouldBe nextLevel.id
        }

        should("a canceled next-level save is not reported as a failed start") {
            repository.store(testSudoku(modeLevel = 1, completed = true))
            val saveAttempts = mutableListOf<String>()
            repository.beforeSave = { saveAttempts += it.rows.sudoku.id }
            repository.saveFailure = CancellationException("cancelled")
            val viewModel = newViewModel()
            lateinit var nextLevel: Sudoku

            viewModel.state.test {
                val shown = expectMostRecentItem()
                shown.hasNextLevelToStart shouldBe true
                nextLevel = shown.topLevel

                viewModel.confirmSudokuStart(0, nextLevel)
            }

            viewModel.levelStart.value shouldBe LevelStart.Running
            saveAttempts shouldBe listOf(nextLevel.id.value)
        }

        should("handling another id keeps the pending reveal and handling its own id clears it") {
            repository.store(testSudoku(modeLevel = 1, completed = true))
            val viewModel = newViewModel()
            viewModel.state.test { expectMostRecentItem().hasNextLevelToStart shouldBe true }
            val nextLevel = viewModel.state.value.topLevel
            viewModel.reveal.value shouldBe nextLevel.id

            viewModel.onRevealHandled(testSudoku(modeLevel = 1).id)
            viewModel.reveal.value shouldBe nextLevel.id

            viewModel.onRevealHandled(nextLevel.id)
            viewModel.reveal.value shouldBe null
        }

        should("a second confirm while the next level saves is refused and saves it once") {
            runTest {
                repository.store(testSudoku(modeLevel = 1, completed = true))
                val saveGate = CompletableDeferred<Unit>()
                repository.beforeSave = { saveGate.await() }
                val viewModel = newViewModel()
                lateinit var nextLevel: Sudoku

                viewModel.state.test {
                    val shown = expectMostRecentItem()
                    shown.hasNextLevelToStart shouldBe true
                    nextLevel = shown.topLevel

                    viewModel.confirmSudokuStart(0, nextLevel)
                    runCurrent()
                    viewModel.levelStart.value shouldBe LevelStart.Running
                    viewModel.confirmSudokuStart(0, nextLevel)
                    viewModel.confirmSudokuStart(1, testSudoku(modeLevel = 1, completed = true))
                    viewModel.levelStart.value shouldBe LevelStart.Running
                    saveGate.complete(Unit)
                    runCurrent()
                    cancelAndIgnoreRemainingEvents()
                }

                viewModel.levelStart.value shouldBe LevelStart.Open(nextLevel.id)
                repository.saves.map { it.rows.sudoku.id } shouldBe listOf(nextLevel.id.value)
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
