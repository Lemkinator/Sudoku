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

package de.lemke.sudoku.domain

import android.app.Application
import android.util.Log
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.testSudoku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class QueueSudokuSaveUseCaseTest {
    private val sudokusRepository = FakeSudokusRepository()

    private val saved: List<Int?>
        get() = sudokusRepository.saves.map { it.board[1].value }

    private fun Sudoku.withValueInSecondCell(): Sudoku = copy(board = board.with(1, board[1].copy(value = 1)))

    private fun queue(): QueueSudokuSaveUseCase {
        val dispatcher = UnconfinedTestDispatcher()
        return QueueSudokuSaveUseCase(sudokusRepository, CoroutineScope(SupervisorJob() + dispatcher), dispatcher)
    }

    private fun gateSaves(gate: CompletableDeferred<Unit>) {
        sudokusRepository.beforeSave = { gate.await() }
    }

    private fun failureLogs(): List<ShadowLog.LogItem> = ShadowLog.getLogsForTag("QueueSudokuSaveUseCase").filter { it.type == Log.ERROR }

    @Before
    fun setUp() {
        ShadowLog.reset()
    }

    @Test
    fun `save the queued sudoku after the repository is free`() =
        runTest {
            val sudoku = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(gate)

            val save = queueSudokuSave(sudoku)
            sudokusRepository.saves shouldBe emptyList()
            gate.complete(Unit)

            save.await()
            sudokusRepository.saves shouldBe listOf(sudoku)
            failureLogs().shouldBeEmpty()
        }

    @Test
    fun `start a save only after every earlier one finished`() =
        runTest {
            val sudoku = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(gate)

            queueSudokuSave(sudoku)
            val last = queueSudokuSave(sudoku.withValueInSecondCell())

            saved shouldBe emptyList()
            gate.complete(Unit)
            last.await()
            saved shouldBe listOf(null, 1)
        }

    @Test
    fun `fail only the failed save and run the next one, leaving the failure to the awaiting caller`() =
        runTest {
            val sudoku = testSudoku()
            val queueSudokuSave = queue()
            sudokusRepository.saveFailure = IllegalStateException("disk full")

            val failed = queueSudokuSave(sudoku)
            sudokusRepository.saveFailure = null
            queueSudokuSave(sudoku).await()

            failed.getCompletionExceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
            saved shouldBe listOf(null)
            failureLogs().shouldBeEmpty()
        }

    @Test
    fun `run an exclusive block only after every earlier save finished`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(gate)
            queueSudokuSave(testSudoku())

            val savesSeenByBlock = async { queueSudokuSave.exclusive { sudokusRepository.saves.size } }
            runCurrent()

            savesSeenByBlock.isCompleted shouldBe false
            gate.complete(Unit)
            savesSeenByBlock.await() shouldBe 1
        }

    @Test
    fun `hold a save queued during an exclusive block until the block ends`() =
        runTest {
            val queueSudokuSave = queue()
            var savesDuringBlock = -1

            val queuedDuringBlock =
                queueSudokuSave.exclusive {
                    val queued = queueSudokuSave(testSudoku())
                    savesDuringBlock = sudokusRepository.saves.size
                    queued
                }
            queuedDuringBlock.await()

            savesDuringBlock shouldBe 0
            sudokusRepository.saves.size shouldBe 1
        }

    @Test
    fun `release the saves queued behind an exclusive block that failed`() =
        runTest {
            val queueSudokuSave = queue()

            shouldThrow<IllegalStateException> { queueSudokuSave.exclusive { error("restore failed") } }.message shouldBe "restore failed"
            queueSudokuSave(testSudoku()).await()

            sudokusRepository.saves.size shouldBe 1
        }

    @Test
    fun `keep later saves behind an earlier save when the exclusive caller is cancelled while waiting`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(gate)
            val first = testSudoku()
            queueSudokuSave(first)

            val waiting = async { queueSudokuSave.exclusive { } }
            runCurrent()
            waiting.cancel()
            val later = queueSudokuSave(first.withValueInSecondCell())
            runCurrent()

            later.isCompleted shouldBe false
            sudokusRepository.saves.size shouldBe 0
            gate.complete(Unit)
            later.await()
            saved shouldBe listOf(null, 1)
        }

    @Test
    fun `hold a later save until the block ends when the exclusive caller is cancelled while it runs`() =
        runTest {
            val blockGate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()

            val running = async { queueSudokuSave.exclusive { withContext(NonCancellable) { blockGate.await() } } }
            runCurrent()
            running.cancel()
            val later = queueSudokuSave(testSudoku())
            runCurrent()

            later.isCompleted shouldBe false
            sudokusRepository.saves.size shouldBe 0
            blockGate.complete(Unit)
            later.await()
            sudokusRepository.saves.size shouldBe 1
        }

    @Test
    fun `launch saves the sudoku after every earlier save`() {
        val sudoku = testSudoku()
        val gate = CompletableDeferred<Unit>()
        val queueSudokuSave = queue()
        gateSaves(gate)

        queueSudokuSave(sudoku)
        queueSudokuSave.launch(sudoku.withValueInSecondCell())
        gate.complete(Unit)

        saved shouldBe listOf(null, 1)
        failureLogs().shouldBeEmpty()
    }

    @Test
    fun `launch logs a failed save`() {
        val disk = IllegalStateException("disk full")
        sudokusRepository.saveFailure = disk

        queue().launch(testSudoku())

        failureLogs().map { it.msg to it.throwable } shouldBe listOf("Saving sudoku failed" to disk)
    }

    @Test
    fun `launch logs no failure for a cancelled save`() {
        sudokusRepository.saveFailure = CancellationException("closed")

        queue().launch(testSudoku())

        failureLogs().shouldBeEmpty()
    }

    private fun Sudoku.atSecond(seconds: Int): Sudoku = copy(info = info.copy(seconds = seconds))

    @Test
    fun `write three snapshots of one game queued behind a running save once more, with the third`() =
        runTest {
            val sudoku = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(gate)

            queueSudokuSave(sudoku.atSecond(0))
            queueSudokuSave(sudoku.atSecond(1))
            queueSudokuSave(sudoku.atSecond(2))
            val last = queueSudokuSave(sudoku.atSecond(3))
            gate.complete(Unit)
            last.await()

            sudokusRepository.saves.map { it.info.seconds } shouldBe listOf(0, 3)
        }

    @Test
    fun `complete every deferred of a coalesced save`() =
        runTest {
            val sudoku = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(gate)

            val running = queueSudokuSave(sudoku.atSecond(0))
            val second = queueSudokuSave(sudoku.atSecond(1))
            val third = queueSudokuSave(sudoku.atSecond(2))
            gate.complete(Unit)

            listOf(running, second, third).forEach { it.await() }
            listOf(running, second, third).map { it.isCompleted } shouldBe listOf(true, true, true)
            second shouldBe third
        }

    @Test
    fun `fail every deferred of a coalesced save together`() =
        runTest {
            val sudoku = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(gate)
            queueSudokuSave(sudoku.atSecond(0))
            val second = queueSudokuSave(sudoku.atSecond(1))
            val third = queueSudokuSave(sudoku.atSecond(2))
            sudokusRepository.saveFailure = IllegalStateException("disk full")

            gate.complete(Unit)
            second.join()

            third.getCompletionExceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
        }

    @Test
    fun `do not merge the saves of two games`() =
        runTest {
            val first = testSudoku()
            val second = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(gate)

            queueSudokuSave(first.atSecond(0))
            queueSudokuSave(second.atSecond(1))
            val last = queueSudokuSave(first.atSecond(2))
            gate.complete(Unit)
            last.await()

            sudokusRepository.saves.map { it.info.id to it.info.seconds } shouldBe
                listOf(first.info.id to 0, second.info.id to 1, first.info.id to 2)
        }

    @Test
    fun `run an exclusive block after a coalesced save`() =
        runTest {
            val sudoku = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(gate)
            queueSudokuSave(sudoku.atSecond(0))
            queueSudokuSave(sudoku.atSecond(1))
            queueSudokuSave(sudoku.atSecond(2))

            val savesSeenByBlock = async { queueSudokuSave.exclusive { sudokusRepository.saves.map { it.info.seconds } } }
            runCurrent()
            gate.complete(Unit)

            savesSeenByBlock.await() shouldBe listOf(0, 2)
        }

    @Test
    fun `keep a save queued after an exclusive block behind it instead of merging it into an earlier save`() =
        runTest {
            val sudoku = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(gate)
            queueSudokuSave(sudoku.atSecond(0))
            queueSudokuSave(sudoku.atSecond(1))
            val block = async { queueSudokuSave.exclusive { sudokusRepository.saves.map { it.info.seconds } } }
            runCurrent()

            val after = queueSudokuSave(sudoku.atSecond(2))
            gate.complete(Unit)
            after.await()

            block.await() shouldBe listOf(0, 1)
            sudokusRepository.saves.map { it.info.seconds } shouldBe listOf(0, 1, 2)
        }
}
