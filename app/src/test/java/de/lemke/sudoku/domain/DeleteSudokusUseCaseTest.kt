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

import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteSudokusUseCaseTest : ShouldSpec(
    {
        lateinit var sudokusRepository: FakeSudokusRepository
        lateinit var useCase: DeleteSudokusUseCase

        beforeEach {
            sudokusRepository = FakeSudokusRepository()
            useCase = DeleteSudokusUseCase(sudokusRepository, UnconfinedTestDispatcher())
        }

        should("deletes nothing when the list is empty") {
            val kept = testSudoku()
            sudokusRepository.saveSudoku(kept)

            useCase(emptyList())

            sudokusRepository.deletedIds.shouldBeEmpty()
            sudokusRepository.storedRows.map { it.sudoku.id } shouldBe listOf(kept.id.value)
        }

        should("deletes every sudoku in the list") {
            val sudokus = listOf(testSudoku(), testSudoku(), testSudoku())
            sudokus.forEach { sudokusRepository.saveSudoku(it) }

            useCase(sudokus)

            sudokusRepository.deletedIds shouldBe sudokus.map { it.id }
            sudokusRepository.storedRows.shouldBeEmpty()
        }
    },
)
