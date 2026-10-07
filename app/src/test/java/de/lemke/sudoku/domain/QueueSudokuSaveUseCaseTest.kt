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

import de.lemke.sudoku.data.database.SudokuWithFields
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private fun testSudoku(): Sudoku =
    Sudoku.create(
        size = SudokuSize.FOUR,
        difficulty = Difficulty.EASY,
        modeLevel = Sudoku.MODE_NORMAL,
        fields = MutableList(SudokuSize.FOUR.cellCount) { Field(Position.create(it, SudokuSize.FOUR), solution = 1, value = null) },
    )

@OptIn(ExperimentalCoroutinesApi::class)
class QueueSudokuSaveUseCaseTest : ShouldSpec(
    {
        val sudokusRepository = mockk<SudokusRepository>()
        val saved = mutableListOf<Pair<Int?, Boolean>>()

        fun queue(): QueueSudokuSaveUseCase {
            val dispatcher = UnconfinedTestDispatcher()
            return QueueSudokuSaveUseCase(sudokusRepository, CoroutineScope(SupervisorJob() + dispatcher), dispatcher)
        }

        beforeEach {
            clearMocks(sudokusRepository)
            saved.clear()
            coEvery { sudokusRepository.saveSudokuRows(any(), any()) } coAnswers {
                saved += firstArg<SudokuWithFields>().fields[0].value to secondArg<Boolean>()
            }
        }

        should("save the rows of the sudoku at the time it was queued") {
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
        }

        should("start a save only after every earlier one finished") {
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

        should("fail only the failed save and run the next one") {
            val sudoku = testSudoku()
            val queueSudokuSave = queue()
            coEvery { sudokusRepository.saveSudokuRows(any(), true) } throws IllegalStateException("disk full")

            val failed = queueSudokuSave(sudoku, onlyUpdate = true)
            queueSudokuSave(sudoku).await()

            failed.getCompletionExceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
            saved shouldBe listOf(null to false)
        }
    },
)
