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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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

                viewModel.reveal.value shouldBe sudokuB
            }
        }

        should("an upstream restart with an unchanged history reveals nothing") {
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

                viewModel.reveal.value shouldBe null
            }
        }

        should("the first emission sets sudokuHistory without a reveal") {
            val firstHistory = mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, NINE_O_CLOCK))
            every { observeSudokuHistory() } returns flowOf(firstHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe firstHistory }
            viewModel.reveal.value shouldBe null
        }

        should("a second emission with a new sudoku reveals it") {
            val firstHistory = mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, NINE_O_CLOCK))
            val secondHistory =
                mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuB, TEN_O_CLOCK), sudokuItem(sudokuA, NINE_O_CLOCK))
            every { observeSudokuHistory() } returns flowOf(firstHistory, secondHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe secondHistory }
            viewModel.reveal.value shouldBe sudokuB
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
            viewModel.reveal.value shouldBe sudokuB
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
            viewModel.reveal.value shouldBe sudokuC
        }

        should("a second emission that only removes a sudoku reveals nothing") {
            val firstHistory =
                mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, TEN_O_CLOCK), sudokuItem(sudokuB, NINE_O_CLOCK))
            val secondHistory = mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuB, NINE_O_CLOCK))
            every { observeSudokuHistory() } returns flowOf(firstHistory, secondHistory)

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe secondHistory }
            viewModel.reveal.value shouldBe null
        }

        should("init reports loadFailed when observeSudokuHistory throws") {
            every { observeSudokuHistory() } returns flow { throw IllegalStateException("observe failed") }

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe emptyList() }
            viewModel.loadFailed.value shouldBe true
        }

        should("onLoadFailureHandled clears a reported load failure") {
            every { observeSudokuHistory() } returns flow { throw IllegalStateException("observe failed") }
            val viewModel = newViewModel()
            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe emptyList() }
            viewModel.loadFailed.value shouldBe true

            viewModel.onLoadFailureHandled()

            viewModel.loadFailed.value shouldBe false
        }

        should("a newer change replaces the pending reveal, a stale handled id keeps it and its own id clears it") {
            val history = MutableSharedFlow<MutableList<SudokuListItem>>()
            every { observeSudokuHistory() } returns history
            val viewModel = newViewModel()

            viewModel.sudokuHistory.test {
                history.subscriptionCount.value shouldBe 1
                history.emit(mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuA, NINE_O_CLOCK)))

                history.emit(
                    mutableListOf(SeparatorItem("Jan 2026"), sudokuItem(sudokuB, TEN_O_CLOCK), sudokuItem(sudokuA, NINE_O_CLOCK)),
                )
                viewModel.reveal.value shouldBe sudokuB
                history.emit(
                    mutableListOf(
                        SeparatorItem("Jan 2026"),
                        sudokuItem(sudokuC, ELEVEN_O_CLOCK),
                        sudokuItem(sudokuB, TEN_O_CLOCK),
                        sudokuItem(sudokuA, NINE_O_CLOCK),
                    ),
                )
                viewModel.reveal.value shouldBe sudokuC

                viewModel.onRevealHandled(sudokuB)
                viewModel.reveal.value shouldBe sudokuC

                viewModel.onRevealHandled(sudokuC)
                viewModel.reveal.value shouldBe null
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("init does not treat CancellationException as a load failure") {
            every { observeSudokuHistory() } returns flow { throw CancellationException("cancelled") }

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe emptyList() }
            viewModel.loadFailed.value shouldBe false
        }

        should("deleting the selection delegates to deleteSudoku and finishes") {
            val sudokus = listOf(mockk<Sudoku>(), mockk<Sudoku>())
            val viewModel = newViewModel()

            viewModel.deletion.value shouldBe HistoryDeletion.Idle
            viewModel.onDeleteSelected(sudokus)

            viewModel.deletion.value shouldBe HistoryDeletion.Finished
            coVerify(exactly = 1) { deleteSudoku(sudokus) }
        }

        should("a second delete while the first runs is refused and deletes once") {
            runTest {
                val sudokus = listOf(mockk<Sudoku>())
                val deleteGate = CompletableDeferred<Unit>()
                coEvery { deleteSudoku(sudokus) } coAnswers { deleteGate.await() }
                val viewModel = newViewModel()

                viewModel.onDeleteSelected(sudokus)
                runCurrent()
                viewModel.deletion.value shouldBe HistoryDeletion.Running
                viewModel.onDeleteSelected(sudokus)
                deleteGate.complete(Unit)
                runCurrent()

                viewModel.deletion.value shouldBe HistoryDeletion.Finished
                coVerify(exactly = 1) { deleteSudoku(sudokus) }
            }
        }

        should("handling the finished deletion returns to idle and allows another delete") {
            val sudokus = listOf(mockk<Sudoku>())
            val viewModel = newViewModel()
            viewModel.onDeleteSelected(sudokus)

            viewModel.onDeletionHandled(HistoryDeletion.Finished)

            viewModel.deletion.value shouldBe HistoryDeletion.Idle
            viewModel.onDeleteSelected(sudokus)
            viewModel.deletion.value shouldBe HistoryDeletion.Finished
            coVerify(exactly = 2) { deleteSudoku(sudokus) }
        }

        should("handling a finished deletion while another runs keeps it running") {
            runTest {
                val sudokus = listOf(mockk<Sudoku>())
                val deleteGate = CompletableDeferred<Unit>()
                coEvery { deleteSudoku(sudokus) } coAnswers { deleteGate.await() }
                val viewModel = newViewModel()
                viewModel.onDeleteSelected(sudokus)
                runCurrent()

                viewModel.onDeletionHandled(HistoryDeletion.Finished)

                viewModel.deletion.value shouldBe HistoryDeletion.Running
                deleteGate.complete(Unit)
                runCurrent()
                viewModel.deletion.value shouldBe HistoryDeletion.Finished
            }
        }
    },
)

private val NINE_O_CLOCK = LocalDateTime.of(2026, 1, 15, 9, 0)
private val TEN_O_CLOCK = LocalDateTime.of(2026, 1, 15, 10, 0)
private val ELEVEN_O_CLOCK = LocalDateTime.of(2026, 1, 15, 11, 0)
