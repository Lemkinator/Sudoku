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
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GetRecentlyUpdatedNormalSudokuUseCaseTest : ShouldSpec(
    {
        lateinit var sudokusRepository: FakeSudokusRepository
        lateinit var useCase: GetRecentlyUpdatedNormalSudokuUseCase

        beforeEach {
            sudokusRepository = FakeSudokusRepository()
            useCase = GetRecentlyUpdatedNormalSudokuUseCase(sudokusRepository, UnconfinedTestDispatcher())
        }

        should("returns null when the repository has no recently updated normal sudoku") {
            useCase() shouldBe null
        }

        should("passes through the repository's recently updated normal sudoku") {
            val sudoku = testSudoku(created = LocalDateTime.of(2026, 1, 2, 10, 0))
            sudokusRepository.saveSudoku(testSudoku(created = LocalDateTime.of(2026, 1, 1, 10, 0)))
            sudokusRepository.saveSudoku(sudoku)

            useCase() shouldBe sudoku
        }
    },
)
