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
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import de.lemke.sudoku.domain.DeleteSudokusUseCase
import de.lemke.sudoku.domain.ObserveAllNormalSudokusUseCase
import de.lemke.sudoku.domain.ObserveSudokuHistoryUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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

private fun List<SudokuListItem>.shape(): List<String> = map { item -> if (item is SudokuItem) item.sudoku.id.value else "separator" }

@OptIn(ExperimentalCoroutinesApi::class)
class TabHistoryViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        lateinit var repository: FakeSudokusRepository

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            repository = FakeSudokusRepository()
        }

        fun newViewModel(): TabHistoryViewModel {
            val dispatcher = UnconfinedTestDispatcher()
            return TabHistoryViewModel(
                userSettings,
                ObserveSudokuHistoryUseCase(ObserveAllNormalSudokusUseCase(repository, dispatcher), dispatcher),
                DeleteSudokusUseCase(repository, dispatcher),
            )
        }

        val sudokuA = SudokuId("sudoku-a")
        val sudokuB = SudokuId("sudoku-b")
        val sudokuC = SudokuId("sudoku-c")

        fun sudoku(
            sudokuId: SudokuId,
            updated: LocalDateTime,
            seconds: Int = 0,
        ) = testSudoku(sudokuId = sudokuId, created = NINE_O_CLOCK, updated = updated).also { it.seconds = seconds }

        should("errorLimit reflects the real UserSettings.errorLimitFlow") {
            val viewModel = newViewModel()
            userSettings.errorLimit = 7
            viewModel.errorLimit.value shouldBe 7
        }

        should("observe the history only while sudokuHistory is collected") {
            val viewModel = newViewModel()
            repository.store(sudoku(sudokuA, NINE_O_CLOCK))

            repository.activeObservers shouldBe 0
            viewModel.sudokuHistory.value shouldBe emptyList()
            viewModel.sudokuHistory.test {
                expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-a")
                repository.activeObservers shouldBe 1
            }
        }

        should("an emission within the stop timeout reaches the next collector, and the history stops being observed after it") {
            runTest {
                repository.store(sudoku(sudokuA, NINE_O_CLOCK))
                val viewModel = newViewModel()
                viewModel.sudokuHistory.test { expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-a") }

                advanceTimeBy(4_000)
                repository.store(sudoku(sudokuB, TEN_O_CLOCK))
                runCurrent()

                viewModel.sudokuHistory.value.shape() shouldBe listOf("separator", "sudoku-b", "sudoku-a")
                viewModel.sudokuHistory.test {
                    expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-b", "sudoku-a")
                }
                advanceTimeBy(5_001)
                runCurrent()
                repository.activeObservers shouldBe 0
            }
        }

        should("a sudoku added while the history was not observed is revealed once the history is collected again") {
            runTest {
                repository.store(sudoku(sudokuA, NINE_O_CLOCK))
                val viewModel = newViewModel()
                viewModel.sudokuHistory.test { expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-a") }
                advanceTimeBy(5_001)
                runCurrent()

                repository.store(sudoku(sudokuB, TEN_O_CLOCK))
                viewModel.sudokuHistory.test {
                    expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-b", "sudoku-a")
                }

                viewModel.reveal.value shouldBe sudokuB
            }
        }

        should("an upstream restart with an unchanged history reveals nothing") {
            runTest {
                repository.store(sudoku(sudokuA, NINE_O_CLOCK))
                val viewModel = newViewModel()
                viewModel.sudokuHistory.test { expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-a") }
                advanceTimeBy(5_001)
                runCurrent()
                repository.activeObservers shouldBe 0

                viewModel.sudokuHistory.test {
                    expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-a")
                    repository.activeObservers shouldBe 1
                }

                viewModel.reveal.value shouldBe null
            }
        }

        should("the first emission sets sudokuHistory without a reveal") {
            repository.store(sudoku(sudokuA, NINE_O_CLOCK))

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-a") }
            viewModel.reveal.value shouldBe null
        }

        should("a second emission with a new sudoku reveals it") {
            repository.store(sudoku(sudokuA, NINE_O_CLOCK))

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test {
                repository.store(sudoku(sudokuB, TEN_O_CLOCK))
                expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-b", "sudoku-a")
            }
            viewModel.reveal.value shouldBe sudokuB
        }

        should("a second emission with a played sudoku that moved to the top reveals it") {
            repository.store(sudoku(sudokuA, TEN_O_CLOCK), sudoku(sudokuB, NINE_O_CLOCK))

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test {
                repository.store(sudoku(sudokuB, ELEVEN_O_CLOCK, seconds = 75))
                expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-b", "sudoku-a")
            }
            viewModel.reveal.value shouldBe sudokuB
        }

        should("one emission with two new sudokus reveals the newest") {
            runTest {
                repository.store(sudoku(sudokuA, NINE_O_CLOCK))
                val viewModel = newViewModel()
                viewModel.sudokuHistory.test { expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-a") }
                advanceTimeBy(5_001)
                runCurrent()

                repository.store(sudoku(sudokuB, TEN_O_CLOCK), sudoku(sudokuC, ELEVEN_O_CLOCK))
                viewModel.sudokuHistory.test {
                    expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-c", "sudoku-b", "sudoku-a")
                }

                viewModel.reveal.value shouldBe sudokuC
            }
        }

        should("a second emission that only removes a sudoku reveals nothing") {
            val removed = sudoku(sudokuA, TEN_O_CLOCK)
            repository.store(removed, sudoku(sudokuB, NINE_O_CLOCK))

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test {
                repository.deleteSudoku(removed)
                expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-b")
            }
            viewModel.reveal.value shouldBe null
        }

        should("init reports loadFailed when observeSudokuHistory throws") {
            repository.observed = { flow { throw IllegalStateException("observe failed") } }

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe emptyList() }
            viewModel.loadFailed.value shouldBe true
        }

        should("onLoadFailureHandled clears a reported load failure") {
            repository.observed = { flow { throw IllegalStateException("observe failed") } }
            val viewModel = newViewModel()
            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe emptyList() }
            viewModel.loadFailed.value shouldBe true

            viewModel.onLoadFailureHandled()

            viewModel.loadFailed.value shouldBe false
        }

        should("a newer change replaces the pending reveal, a stale handled id keeps it and its own id clears it") {
            repository.store(sudoku(sudokuA, NINE_O_CLOCK))
            val viewModel = newViewModel()

            viewModel.sudokuHistory.test {
                repository.activeObservers shouldBe 1

                repository.store(sudoku(sudokuB, TEN_O_CLOCK))
                viewModel.reveal.value shouldBe sudokuB
                repository.store(sudoku(sudokuC, ELEVEN_O_CLOCK))
                viewModel.reveal.value shouldBe sudokuC

                viewModel.onRevealHandled(sudokuB)
                viewModel.reveal.value shouldBe sudokuC

                viewModel.onRevealHandled(sudokuC)
                viewModel.reveal.value shouldBe null
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("handling another id keeps the pending reveal and handling its own id clears it") {
            repository.store(sudoku(sudokuA, NINE_O_CLOCK))
            val viewModel = newViewModel()
            viewModel.sudokuHistory.test {
                repository.store(sudoku(sudokuB, TEN_O_CLOCK))
                expectMostRecentItem().shape() shouldBe listOf("separator", "sudoku-b", "sudoku-a")
            }
            viewModel.reveal.value shouldBe sudokuB

            viewModel.onRevealHandled(sudokuA)
            viewModel.reveal.value shouldBe sudokuB

            viewModel.onRevealHandled(sudokuB)
            viewModel.reveal.value shouldBe null
        }

        should("init does not treat CancellationException as a load failure") {
            repository.observed = { flow { throw CancellationException("cancelled") } }

            val viewModel = newViewModel()

            viewModel.sudokuHistory.test { expectMostRecentItem() shouldBe emptyList() }
            viewModel.loadFailed.value shouldBe false
        }

        should("deleting the selection delegates to deleteSudoku and finishes") {
            val sudokus = listOf(sudoku(sudokuA, NINE_O_CLOCK), sudoku(sudokuB, TEN_O_CLOCK))
            repository.store(*sudokus.toTypedArray(), sudoku(sudokuC, ELEVEN_O_CLOCK))
            val viewModel = newViewModel()

            viewModel.deletion.value shouldBe HistoryDeletion.Idle
            viewModel.onDeleteSelected(sudokus)

            viewModel.deletion.value shouldBe HistoryDeletion.Finished
            repository.deletedIds shouldBe listOf(sudokuA, sudokuB)
            repository.storedRows.map { it.sudoku.id } shouldBe listOf("sudoku-c")
        }

        should("a second delete while the first runs is refused and deletes once") {
            runTest {
                val sudokus = listOf(sudoku(sudokuA, NINE_O_CLOCK))
                repository.store(*sudokus.toTypedArray())
                val deleteGate = CompletableDeferred<Unit>()
                repository.beforeDelete = { deleteGate.await() }
                val viewModel = newViewModel()

                viewModel.onDeleteSelected(sudokus)
                runCurrent()
                viewModel.deletion.value shouldBe HistoryDeletion.Running
                viewModel.onDeleteSelected(sudokus)
                deleteGate.complete(Unit)
                runCurrent()

                viewModel.deletion.value shouldBe HistoryDeletion.Finished
                repository.deletedIds shouldBe listOf(sudokuA)
            }
        }

        should("handling the finished deletion returns to idle and allows another delete") {
            val sudokus = listOf(sudoku(sudokuA, NINE_O_CLOCK))
            repository.store(*sudokus.toTypedArray())
            val viewModel = newViewModel()
            viewModel.onDeleteSelected(sudokus)

            viewModel.onDeletionHandled(HistoryDeletion.Finished)

            viewModel.deletion.value shouldBe HistoryDeletion.Idle
            viewModel.onDeleteSelected(sudokus)
            viewModel.deletion.value shouldBe HistoryDeletion.Finished
        }

        should("handling a finished deletion while another runs keeps it running") {
            runTest {
                val sudokus = listOf(sudoku(sudokuA, NINE_O_CLOCK))
                repository.store(*sudokus.toTypedArray())
                val deleteGate = CompletableDeferred<Unit>()
                repository.beforeDelete = { deleteGate.await() }
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
