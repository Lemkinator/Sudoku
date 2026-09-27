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
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.SudokuListItem.SeparatorItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.ui.utils.listSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

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

        val sudokuA = SudokuId("sudoku-a")
        val sudokuB = SudokuId("sudoku-b")
        val sudokuC = SudokuId("sudoku-c")

        fun sudokuItem(
            sudokuId: SudokuId,
            updated: LocalDateTime,
            seconds: Int = 0,
        ) = SudokuItem(
            listSudoku(
                sudokuId = sudokuId,
                modeLevel = MODE_NORMAL,
                filled = 0,
                errorsMade = 0,
                seconds = seconds,
                created = NINE_O_CLOCK,
                updated = updated,
            ),
            "Jan 2026",
        )

        should("errorLimit reflects the real UserSettings.errorLimitFlow") {
            val viewModel = newViewModel()
            userSettings.errorLimit = 7
            viewModel.errorLimit.value shouldBe 7
        }

        should("observe the history only while sudokuHistory is collected") {
            val history = MutableSharedFlow<MutableList<SudokuListItem>>(replay = 1)
            every { observeSudokuHistory() } returns history
            val firstHistory = mutableListOf<SudokuListItem>(SeparatorItem("Jan 2026"))

            val viewModel = newViewModel()
            history.emit(firstHistory)

            history.subscriptionCount.value shouldBe 0
            viewModel.sudokuHistory.value shouldBe emptyList()
            viewModel.sudokuHistory.test {
                expectMostRecentItem() shouldBe firstHistory
                history.subscriptionCount.value shouldBe 1
            }
        }

        should("an emission within the stop timeout reaches the next collector, and the history stops being observed after it") {
            runTest {
                val history = MutableSharedFlow<MutableList<SudokuListItem>>(replay = 1)
                every { observeSudokuHistory() } returns history
                val firstHistory = mutableListOf<SudokuListItem>(SeparatorItem("Jan 2026"))
                val secondHistory = mutableListOf<SudokuListItem>(SeparatorItem("Jan 2026"), SeparatorItem("Feb 2026"))
                val viewModel = newViewModel()
                history.emit(firstHistory)
                viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe firstHistory }

                advanceTimeBy(4_000)
                history.emit(secondHistory)
                runCurrent()

                viewModel.sudokuHistory.value shouldBe secondHistory
                viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe secondHistory }
                advanceTimeBy(5_001)
                runCurrent()
                history.subscriptionCount.value shouldBe 0
            }
        }

        should("a sudoku added while the history was not observed is revealed once the history is collected again") {
            runTest {
                val history = MutableSharedFlow<MutableList<SudokuListItem>>(replay = 1)
                every { observeSudokuHistory() } returns history
                val firstHistory = mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, NINE_O_CLOCK))
                val grownHistory =
                    mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuB, TEN_O_CLOCK), sudokuItem(sudokuA, NINE_O_CLOCK))
                val viewModel = newViewModel()
                history.emit(firstHistory)
                viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe firstHistory }
                advanceTimeBy(5_001)
                runCurrent()

                history.emit(grownHistory)
                viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe grownHistory }

                viewModel.events.test {
                    awaitItem() shouldBe TabHistoryEvent.RevealSudoku(sudokuB)
                    expectNoEvents()
                }
            }
        }

        should("an upstream restart with an unchanged history emits no event") {
            runTest {
                val history = MutableSharedFlow<MutableList<SudokuListItem>>(replay = 1)
                every { observeSudokuHistory() } returns history
                val firstHistory = mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, NINE_O_CLOCK))
                val requeriedHistory = mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, NINE_O_CLOCK))
                val viewModel = newViewModel()
                history.emit(firstHistory)
                viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe firstHistory }
                advanceTimeBy(5_001)
                runCurrent()
                history.subscriptionCount.value shouldBe 0

                history.emit(requeriedHistory)
                viewModel.sudokuHistory.test {
                    expectMostRecentItem() shouldBe requeriedHistory
                    history.subscriptionCount.value shouldBe 1
                }

                viewModel.events.test { expectNoEvents() }
            }
        }

        should("the first emission sets sudokuHistory without an event") {
            val firstHistory = mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, NINE_O_CLOCK))
            every { observeSudokuHistory() } returns flowOf(firstHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe firstHistory }
            viewModel.events.test { expectNoEvents() }
        }

        should("a second emission with a new sudoku reveals it") {
            val firstHistory = mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, NINE_O_CLOCK))
            val secondHistory =
                mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuB, TEN_O_CLOCK), sudokuItem(sudokuA, NINE_O_CLOCK))
            every { observeSudokuHistory() } returns flowOf(firstHistory, secondHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe secondHistory }
            viewModel.events.test {
                awaitItem() shouldBe TabHistoryEvent.RevealSudoku(sudokuB)
                expectNoEvents()
            }
        }

        should("a second emission with a played sudoku that moved to the top reveals it") {
            val firstHistory =
                mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, TEN_O_CLOCK), sudokuItem(sudokuB, NINE_O_CLOCK))
            val secondHistory =
                mutableListOf(
                    SeparatorItem("Jan 2026"),
                    sudokuItem(sudokuB, ELEVEN_O_CLOCK, seconds = 75),
                    sudokuItem(sudokuA, TEN_O_CLOCK),
                )
            every { observeSudokuHistory() } returns flowOf(firstHistory, secondHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe secondHistory }
            viewModel.events.test {
                awaitItem() shouldBe TabHistoryEvent.RevealSudoku(sudokuB)
                expectNoEvents()
            }
        }

        should("one emission with two new sudokus reveals the newest") {
            val firstHistory = mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, NINE_O_CLOCK))
            val secondHistory =
                mutableListOf(
                    SeparatorItem("Jan 2026"),
                    sudokuItem(sudokuC, ELEVEN_O_CLOCK),
                    sudokuItem(sudokuB, TEN_O_CLOCK),
                    sudokuItem(sudokuA, NINE_O_CLOCK),
                )
            every { observeSudokuHistory() } returns flowOf(firstHistory, secondHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe secondHistory }
            viewModel.events.test {
                awaitItem() shouldBe TabHistoryEvent.RevealSudoku(sudokuC)
                expectNoEvents()
            }
        }

        should("a second emission that only removes a sudoku emits no event") {
            val firstHistory =
                mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, TEN_O_CLOCK), sudokuItem(sudokuB, NINE_O_CLOCK))
            val secondHistory = mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuB, NINE_O_CLOCK))
            every { observeSudokuHistory() } returns flowOf(firstHistory, secondHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe secondHistory }
            viewModel.events.test { expectNoEvents() }
        }

        should("init emits ShowLoadError when observeSudokuHistory throws") {
            every { observeSudokuHistory() } returns flow { throw IllegalStateException("observe failed") }

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe emptyList() }
            viewModel.events.test {
                awaitItem() shouldBe TabHistoryEvent.ShowLoadError
            }
        }

        should("init does not treat CancellationException as a load failure") {
            every { observeSudokuHistory() } returns flow { throw CancellationException("cancelled") }

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe emptyList() }
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

private val NINE_O_CLOCK = LocalDateTime.of(2026, 1, 15, 9, 0)
private val TEN_O_CLOCK = LocalDateTime.of(2026, 1, 15, 10, 0)
private val ELEVEN_O_CLOCK = LocalDateTime.of(2026, 1, 15, 11, 0)
