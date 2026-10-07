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
import de.lemke.sudoku.data.database.SudokuWithFields
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

private fun testSudoku(): Sudoku =
    Sudoku.create(
        size = SudokuSize.FOUR,
        difficulty = Difficulty.EASY,
        modeLevel = Sudoku.MODE_NORMAL,
        fields = MutableList(SudokuSize.FOUR.cellCount) { Field(Position.create(it, SudokuSize.FOUR), solution = 1, value = null) },
    )

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class QueueSudokuSaveUseCaseTest {
    private val sudokusRepository = mockk<SudokusRepository>()
    private val saved = mutableListOf<Pair<Int?, Boolean>>()

    private fun queue(): QueueSudokuSaveUseCase {
        val dispatcher = UnconfinedTestDispatcher()
        return QueueSudokuSaveUseCase(sudokusRepository, CoroutineScope(SupervisorJob() + dispatcher), dispatcher)
    }

    private fun failureLogs(): List<ShadowLog.LogItem> = ShadowLog.getLogsForTag("QueueSudokuSaveUseCase").filter { it.type == Log.ERROR }

    @Before
    fun setUp() {
        ShadowLog.reset()
        coEvery { sudokusRepository.saveSudokuRows(any(), any()) } coAnswers {
            saved += firstArg<SudokuWithFields>().fields[0].value to secondArg<Boolean>()
        }
    }

    @Test
    fun `save the rows of the sudoku at the time it was queued`() =
        runTest {
            val sudoku = testSudoku()
            val gate = CompletableDeferred<Unit>()
            val queueSudokuSave = queue()
            coEvery { sudokusRepository.saveSudokuRows(any(), true) } coAnswers {
                gate.await()
                saved += firstArg<SudokuWithFields>().fields[0].value to true
            }

            val save = queueSudokuSave(sudoku, onlyUpdate = true)
            sudoku.fields[0].value = 1
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
            coEvery { sudokusRepository.saveSudokuRows(any(), true) } coAnswers {
                gate.await()
                saved += firstArg<SudokuWithFields>().fields[0].value to true
            }

            queueSudokuSave(sudoku, onlyUpdate = true)
            sudoku.fields[0].value = 1
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
            coEvery { sudokusRepository.saveSudokuRows(any(), true) } throws IllegalStateException("disk full")

            val failed = queueSudokuSave(sudoku, onlyUpdate = true)
            queueSudokuSave(sudoku).await()

            failed.getCompletionExceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
            saved shouldBe listOf(null to false)
            failureLogs().shouldBeEmpty()
        }

    @Test
    fun `launch saves the rows after every earlier save`() {
        val sudoku = testSudoku()
        val gate = CompletableDeferred<Unit>()
        val queueSudokuSave = queue()
        coEvery { sudokusRepository.saveSudokuRows(any(), false) } coAnswers {
            gate.await()
            saved += firstArg<SudokuWithFields>().fields[0].value to false
        }

        queueSudokuSave(sudoku)
        sudoku.fields[0].value = 1
        queueSudokuSave.launch(sudoku, onlyUpdate = true)
        gate.complete(Unit)

        saved shouldBe listOf(null to false, 1 to true)
        failureLogs().shouldBeEmpty()
    }

    @Test
    fun `launch logs a failed save`() {
        val disk = IllegalStateException("disk full")
        coEvery { sudokusRepository.saveSudokuRows(any(), true) } throws disk

        queue().launch(testSudoku(), onlyUpdate = true)

        failureLogs().map { it.msg to it.throwable } shouldBe listOf("Saving sudoku failed" to disk)
    }

    @Test
    fun `launch logs no failure for a cancelled save`() {
        coEvery { sudokusRepository.saveSudokuRows(any(), true) } throws CancellationException("closed")

        queue().launch(testSudoku(), onlyUpdate = true)

        failureLogs().shouldBeEmpty()
    }
}
