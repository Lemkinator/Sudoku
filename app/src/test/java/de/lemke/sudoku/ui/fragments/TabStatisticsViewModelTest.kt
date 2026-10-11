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
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.CalculateStatisticsUseCase
import de.lemke.sudoku.domain.ObserveSudokusAndStatisticsFilterFlagsUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.domain.model.SudokuStatistics
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val NEW_YEAR_NOON = LocalDateTime.of(2026, 1, 1, 12, 0)

private fun wonSudoku(): Sudoku =
    testSudoku(sudokuId = SudokuId("won"), created = NEW_YEAR_NOON, completed = true).apply {
        seconds = 90
        errorsMade = 1
        notesMade = 2
    }

private fun startedSudoku(): Sudoku =
    testSudoku(sudokuId = SudokuId("started"), created = NEW_YEAR_NOON.plusDays(1)).apply {
        seconds = 30
        hintsUsed = 2
        eraserUsed = true
    }

private val noStatistics =
    SudokuStatistics(
        gamesStarted = 0,
        gamesCompleted = 0,
        winRate = 0,
        bestTimeSudoku = null,
        averageTime = -1,
        winsWithoutErrors = 0,
        mostErrors = 0,
        averageErrors = 0,
        winsWithoutHints = 0,
        mostHints = 0,
        averageHints = 0,
        winsWithoutNotes = 0,
        mostNotes = 0,
        averageNotes = 0,
        mostGamesStartedDifficulty = null,
        mostGamesWonDifficulty = null,
        mostGamesStartedSize = null,
        mostGamesWonSize = null,
        currentGameStreak = 0,
        bestGameStreak = 0,
        totalSecondsPlayed = 0L,
        hintUsageRate = 0f,
        notesUsageRate = 0f,
        eraserUsageRate = 0f,
        perfectGames = 0,
    )

private val wonStatistics =
    SudokuStatistics(
        gamesStarted = 1,
        gamesCompleted = 1,
        winRate = 100,
        bestTimeSudoku = wonSudoku(),
        averageTime = 90,
        winsWithoutErrors = 0,
        mostErrors = 1,
        averageErrors = 1,
        winsWithoutHints = 1,
        mostHints = 0,
        averageHints = 0,
        winsWithoutNotes = 0,
        mostNotes = 2,
        averageNotes = 2,
        mostGamesStartedDifficulty = Difficulty.EASY,
        mostGamesWonDifficulty = Difficulty.EASY,
        mostGamesStartedSize = SudokuSize.FOUR,
        mostGamesWonSize = SudokuSize.FOUR,
        currentGameStreak = 1,
        bestGameStreak = 1,
        totalSecondsPlayed = 90L,
        hintUsageRate = 0f,
        notesUsageRate = 1f,
        eraserUsageRate = 0f,
        perfectGames = 0,
    )

private val wonAndStartedStatistics =
    SudokuStatistics(
        gamesStarted = 2,
        gamesCompleted = 1,
        winRate = 50,
        bestTimeSudoku = wonSudoku(),
        averageTime = 90,
        winsWithoutErrors = 0,
        mostErrors = 1,
        averageErrors = 1,
        winsWithoutHints = 1,
        mostHints = 2,
        averageHints = 0,
        winsWithoutNotes = 0,
        mostNotes = 2,
        averageNotes = 2,
        mostGamesStartedDifficulty = Difficulty.EASY,
        mostGamesWonDifficulty = Difficulty.EASY,
        mostGamesStartedSize = SudokuSize.FOUR,
        mostGamesWonSize = SudokuSize.FOUR,
        currentGameStreak = 0,
        bestGameStreak = 1,
        totalSecondsPlayed = 120L,
        hintUsageRate = 0.5f,
        notesUsageRate = 0.5f,
        eraserUsageRate = 0.5f,
        perfectGames = 0,
    )

private object FailingDispatcher : CoroutineDispatcher() {
    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ): Unit = throw IllegalStateException("calculate failed")
}

@OptIn(ExperimentalCoroutinesApi::class)
class TabStatisticsViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        lateinit var repository: FakeSudokusRepository

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            repository = FakeSudokusRepository()
        }

        fun newViewModel(
            calculateDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
            observeDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
        ) = TabStatisticsViewModel(
            ObserveSudokusAndStatisticsFilterFlagsUseCase(repository, userSettings, observeDispatcher),
            CalculateStatisticsUseCase(calculateDispatcher),
        )

        should("observe the sudokus and calculate statistics only while state is collected") {
            repository.store(wonSudoku())

            val viewModel = newViewModel()

            repository.activeObservers shouldBe 0
            viewModel.state.value shouldBe TabStatisticsUiState()
            viewModel.state.test {
                expectMostRecentItem() shouldBe TabStatisticsUiState(statistics = wonStatistics)
                repository.activeObservers shouldBe 1
            }
        }

        should("shows no statistics until the first ones, then keeps them shown while recomputing") {
            val calculation = StandardTestDispatcher(TestCoroutineScheduler())

            val viewModel = newViewModel(calculateDispatcher = calculation)

            viewModel.state.test {
                awaitItem() shouldBe TabStatisticsUiState()
                repository.activeObservers shouldBe 1

                calculation.scheduler.runCurrent()
                awaitItem() shouldBe TabStatisticsUiState(statistics = noStatistics)

                repository.store(wonSudoku())
                expectNoEvents()
                viewModel.state.value shouldBe TabStatisticsUiState(statistics = noStatistics)

                calculation.scheduler.runCurrent()
                awaitItem() shouldBe TabStatisticsUiState(statistics = wonStatistics)

                cancelAndIgnoreRemainingEvents()
            }
        }

        should("collecting again after the stop timeout recomputes the shown statistics") {
            runTest {
                val calculation = StandardTestDispatcher(TestCoroutineScheduler())
                repository.store(wonSudoku())
                val viewModel = newViewModel(calculation, UnconfinedTestDispatcher(testScheduler))
                viewModel.state.test {
                    runCurrent()
                    calculation.scheduler.runCurrent()
                    runCurrent()
                    expectMostRecentItem() shouldBe TabStatisticsUiState(statistics = wonStatistics)
                }
                advanceTimeBy(5_001)
                runCurrent()
                repository.activeObservers shouldBe 0
                repository.store(startedSudoku())

                viewModel.state.test {
                    awaitItem() shouldBe TabStatisticsUiState(statistics = wonStatistics)
                    runCurrent()
                    repository.activeObservers shouldBe 1
                    expectNoEvents()
                    calculation.scheduler.runCurrent()
                    runCurrent()
                    awaitItem() shouldBe TabStatisticsUiState(statistics = wonAndStartedStatistics)
                }
            }
        }

        should("init keeps no statistics and reports loadFailed when observeSudokusAndStatisticsFilterFlags throws") {
            repository.observed = { flow { throw IllegalStateException("observe failed") } }

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe TabStatisticsUiState()
            }
            viewModel.loadFailed.value shouldBe true
        }

        should("init keeps no statistics and reports loadFailed when calculateStatistics throws") {
            val viewModel = newViewModel(calculateDispatcher = FailingDispatcher)

            viewModel.state.test {
                expectMostRecentItem() shouldBe TabStatisticsUiState()
            }
            viewModel.loadFailed.value shouldBe true
        }

        should("onLoadFailureHandled clears a reported load failure") {
            repository.observed = { flow { throw IllegalStateException("observe failed") } }
            val viewModel = newViewModel()
            viewModel.state.test { expectMostRecentItem() shouldBe TabStatisticsUiState() }
            viewModel.loadFailed.value shouldBe true

            viewModel.onLoadFailureHandled()

            viewModel.loadFailed.value shouldBe false
        }

        should("init does not treat CancellationException as a load failure") {
            repository.observed = { flow { throw CancellationException("cancelled") } }

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe TabStatisticsUiState()
            }
            viewModel.loadFailed.value shouldBe false
        }
    },
)
