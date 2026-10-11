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
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteInvalidSudokusUseCaseTest : ShouldSpec(
    {
        val sudokusRepository = FakeSudokusRepository()
        val useCase = DeleteInvalidSudokusUseCase(sudokusRepository, UnconfinedTestDispatcher())

        should("delegates to sudokusRepository.deleteInvalidSudokus") {
            val valid = testSudoku()
            val invalid = testSudoku()
            sudokusRepository.insertRows(sudokuWithFieldsToDb(valid))
            sudokusRepository.insertRows(sudokuWithFieldsToDb(invalid).copy(fields = emptyList()))

            useCase()

            sudokusRepository.deletedIds shouldBe listOf(invalid.id)
            sudokusRepository.storedRows.map { it.sudoku.id } shouldBe listOf(valid.id.value)
        }
    },
)
