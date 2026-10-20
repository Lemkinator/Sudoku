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

package de.lemke.sudoku.ui

import app.cash.turbine.test
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import de.lemke.sudoku.domain.GenerateDailySudokuUseCase
import de.lemke.sudoku.domain.GenerateFieldsUseCase
import de.lemke.sudoku.domain.InitDailySudokusUseCase
import de.lemke.sudoku.domain.ObserveDailySudokusUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private data class ShownDailies(
    val sudokus: List<String>,
    val isLoading: Boolean,
)

private fun DailySudokuUiState.shown() =
    ShownDailies(
        sudokus = sudokus.map { item -> if (item is SudokuItem) item.sudoku.id.value else "separator" },
        isLoading = isLoading,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class DailySudokuViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        lateinit var repository: FakeSudokusRepository
        val clock = Clock.fixed(ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            repository = FakeSudokusRepository()
        }

        fun newViewModel(): DailySudokuViewModel {
            val dispatcher = UnconfinedTestDispatcher()
            val generateDailySudoku =
                GenerateDailySudokuUseCase(GenerateFieldsUseCase(dispatcher, PatternSolvedBoardGenerator()), clock, dispatcher)
            return DailySudokuViewModel(
                userSettings,
                InitDailySudokusUseCase(repository, SaveSudokuUseCase(repository, dispatcher), generateDailySudoku, clock, dispatcher),
                ObserveDailySudokusUseCase(repository, userSettings, clock, dispatcher),
                clock,
            )
        }

        fun dailySudoku(
            created: LocalDateTime,
            completed: Boolean = false,
        ) = testSudoku(modeLevel = MODE_DAILY, created = created, completed = completed)

        should("observe today's daily sudokus only while state is collected and map them to the UI state") {
            val today = dailySudoku(created = LocalDateTime.of(2026, 1, 15, 9, 0))
            repository.store(today)

            val viewModel = newViewModel()

            repository.activeObservers shouldBe 0
            viewModel.state.value shouldBe DailySudokuUiState()
            viewModel.state.test {
                expectMostRecentItem().shown() shouldBe ShownDailies(sudokus = listOf("separator", today.id.value), isLoading = false)
                repository.activeObservers shouldBe 1
            }
        }

        should("initialize today's daily sudoku once, also when state is collected again after the stop timeout") {
            runTest {
                val viewModel = newViewModel()

                viewModel.state.test { cancelAndIgnoreRemainingEvents() }
                advanceTimeBy(5_001)
                runCurrent()
                repository.activeObservers shouldBe 0
                repository.deleteSudoku(repository.getDailySudokus().single())
                viewModel.state.test {
                    runCurrent()
                    repository.activeObservers shouldBe 1
                    cancelAndIgnoreRemainingEvents()
                }

                repository.saves.map { it.rows.sudoku.modeLevel to it.rows.sudoku.created } shouldBe
                    listOf(MODE_DAILY to LocalDateTime.of(2026, 1, 15, 12, 0))
            }
        }

        should("init loads sudokus from observeDailySudokus and sets isLoading false on success") {
            val today = dailySudoku(created = LocalDateTime.of(2026, 1, 15, 9, 0))
            val lastMonth = dailySudoku(created = LocalDateTime.of(2025, 12, 10, 9, 0), completed = true)
            repository.store(lastMonth, today)

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem().shown() shouldBe
                    ShownDailies(sudokus = listOf("separator", today.id.value, "separator", lastMonth.id.value), isLoading = false)
            }
            viewModel.loadFailed.value shouldBe false
        }

        should("init sets isLoading false and reports loadFailed when initDailySudokus throws") {
            repository.saveFailure = RuntimeException("init failed")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe DailySudokuUiState(isLoading = false)
            }
            viewModel.loadFailed.value shouldBe true
        }

        should("init sets isLoading false and reports loadFailed when observeDailySudokus throws") {
            repository.observed = { flow { throw IllegalStateException("observe failed") } }

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe DailySudokuUiState(isLoading = false)
            }
            viewModel.loadFailed.value shouldBe true
        }

        should("onLoadFailureHandled clears a reported load failure") {
            repository.saveFailure = RuntimeException("init failed")
            val viewModel = newViewModel()
            viewModel.state.test { expectMostRecentItem() shouldBe DailySudokuUiState(isLoading = false) }
            viewModel.loadFailed.value shouldBe true

            viewModel.onLoadFailureHandled()

            viewModel.loadFailed.value shouldBe false
        }

        should("init does not treat CancellationException as a load failure: no isLoading flip, no loadFailed") {
            repository.saveFailure = CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.state.test {
                expectMostRecentItem() shouldBe DailySudokuUiState()
            }
            viewModel.loadFailed.value shouldBe false
        }

        should("dailyShowUncompleted round-trips through the real UserSettings") {
            val viewModel = newViewModel()

            viewModel.dailyShowUncompleted = false

            viewModel.dailyShowUncompleted.shouldBeFalse()
            userSettings.dailyShowUncompleted.shouldBeFalse()
        }
    },
)
