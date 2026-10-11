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
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testLevelSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class InitSudokuLevelUseCaseTest : ShouldSpec(
    {
        lateinit var sudokusRepository: FakeSudokusRepository
        lateinit var useCase: InitSudokuLevelUseCase

        beforeEach {
            val dispatcher = UnconfinedTestDispatcher()
            sudokusRepository = FakeSudokusRepository()
            useCase =
                InitSudokuLevelUseCase(
                    sudokusRepository,
                    GenerateSudokuLevelUseCase(GenerateFieldsUseCase(dispatcher, PatternSolvedBoardGenerator()), dispatcher),
                    GetMaxSudokuLevelUseCase(sudokusRepository, dispatcher),
                    dispatcher,
                )
        }

        should("generates nothing when every level up to max already exists") {
            (1..3).forEach { level -> sudokusRepository.insertRows(sudokuWithFieldsToDb(testLevelSudoku(SudokuSize.FOUR, level))) }

            useCase(SudokuSize.FOUR)

            sudokusRepository.saves.shouldBeEmpty()
        }

        should("generates and saves exactly the missing levels") {
            sudokusRepository.insertRows(sudokuWithFieldsToDb(testLevelSudoku(SudokuSize.FOUR, level = 1)))
            sudokusRepository.insertRows(sudokuWithFieldsToDb(testLevelSudoku(SudokuSize.FOUR, level = 3)))
            sudokusRepository.insertRows(sudokuWithFieldsToDb(testLevelSudoku(SudokuSize.FOUR, level = 4)).copy(fields = emptyList()))

            useCase(SudokuSize.FOUR)

            sudokusRepository.saves.map { Triple(it.rows.sudoku.size, it.rows.sudoku.modeLevel, it.onlyUpdate) } shouldBe
                listOf(Triple(4, 2, false), Triple(4, 4, false))
        }
    },
)
