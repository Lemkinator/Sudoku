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

    private val saved: List<Pair<Int?, Boolean>>
        get() = sudokusRepository.saves.map { it.rows.fields[1].value to it.onlyUpdate }

    private fun queue(): QueueSudokuSaveUseCase {
        val dispatcher = UnconfinedTestDispatcher()
        return QueueSudokuSaveUseCase(sudokusRepository, CoroutineScope(SupervisorJob() + dispatcher), dispatcher)
    }

    private fun gateSaves(
        onlyUpdate: Boolean,
        gate: CompletableDeferred<Unit>,
    ) {
        sudokusRepository.beforeSave = { if (it.onlyUpdate == onlyUpdate) gate.await() }
    }

    private fun failureLogs(): List<ShadowLog.LogItem> = ShadowLog.getLogsForTag("QueueSudokuSaveUseCase").filter { it.type == Log.ERROR }

    @Before
    fun setUp() {
        ShadowLog.reset()
    }

    @Test
    fun `save the rows of the sudoku at the time it was queued`() =
        runTest {
            val sudoku = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(onlyUpdate = true, gate)

            val save = queueSudokuSave(sudoku, onlyUpdate = true)
            sudoku.fields[1].value = 1
            gate.complete(Unit)

            save.await()
            saved shouldBe listOf(null to true)
            failureLogs().shouldBeEmpty()
        }

    @Test
    fun `start a save only after every earlier one finished`() =
        runTest {
            val sudoku = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(onlyUpdate = true, gate)

            queueSudokuSave(sudoku, onlyUpdate = true)
            sudoku.fields[1].value = 1
            val last = queueSudokuSave(sudoku)

            saved shouldBe emptyList()
            gate.complete(Unit)
            last.await()
            saved shouldBe listOf(null to true, 1 to false)
        }

    @Test
    fun `fail only the failed save and run the next one, leaving the failure to the awaiting caller`() =
        runTest {
            val sudoku = testSudoku()
            val queueSudokuSave = queue()
            sudokusRepository.saveFailure = IllegalStateException("disk full")

            val failed = queueSudokuSave(sudoku, onlyUpdate = true)
            sudokusRepository.saveFailure = null
            queueSudokuSave(sudoku).await()

            failed.getCompletionExceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
            saved shouldBe listOf(null to false)
            failureLogs().shouldBeEmpty()
        }

    @Test
    fun `run an exclusive block only after every earlier save finished`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            gateSaves(onlyUpdate = false, gate)
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
            gateSaves(onlyUpdate = false, gate)
            queueSudokuSave(testSudoku())

            val waiting = async { queueSudokuSave.exclusive { } }
            runCurrent()
            waiting.cancel()
            val later = queueSudokuSave(testSudoku(), onlyUpdate = true)
            runCurrent()

            later.isCompleted shouldBe false
            sudokusRepository.saves.size shouldBe 0
            gate.complete(Unit)
            later.await()
            saved shouldBe listOf(null to false, null to true)
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
    fun `launch saves the rows after every earlier save`() {
        val sudoku = testSudoku()
        val gate = CompletableDeferred<Unit>()
        val queueSudokuSave = queue()
        gateSaves(onlyUpdate = false, gate)

        queueSudokuSave(sudoku)
        sudoku.fields[1].value = 1
        queueSudokuSave.launch(sudoku, onlyUpdate = true)
        gate.complete(Unit)

        saved shouldBe listOf(null to false, 1 to true)
        failureLogs().shouldBeEmpty()
    }

    @Test
    fun `launch logs a failed save`() {
        val disk = IllegalStateException("disk full")
        sudokusRepository.saveFailure = disk

        queue().launch(testSudoku(), onlyUpdate = true)

        failureLogs().map { it.msg to it.throwable } shouldBe listOf("Saving sudoku failed" to disk)
    }

    @Test
    fun `launch logs no failure for a cancelled save`() {
        sudokusRepository.saveFailure = CancellationException("closed")

        queue().launch(testSudoku(), onlyUpdate = true)

        failureLogs().shouldBeEmpty()
    }
}
