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
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class InitDailySudokusUseCaseTest : ShouldSpec(
    {
        lateinit var sudokusRepository: FakeSudokusRepository
        lateinit var useCase: InitDailySudokusUseCase
        val date = LocalDate.of(2026, 1, 1)

        fun dailySudoku(on: LocalDate) =
            testSudoku(size = SudokuSize.NINE, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_DAILY, created = on.atStartOfDay())

        beforeEach {
            val dispatcher = UnconfinedTestDispatcher()
            val clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)
            sudokusRepository = FakeSudokusRepository()
            useCase =
                InitDailySudokusUseCase(
                    sudokusRepository,
                    SaveSudokuUseCase(sudokusRepository, dispatcher),
                    GenerateDailySudokuUseCase(GenerateFieldsUseCase(dispatcher, PatternSolvedBoardGenerator()), clock, dispatcher),
                    clock,
                    dispatcher,
                )
        }

        should("generates and saves a new daily sudoku when none exists for the date") {
            useCase(date)

            sudokusRepository.saves.map { Triple(it.rows.sudoku.modeLevel, it.rows.sudoku.created, it.onlyUpdate) } shouldBe
                listOf(Triple(Sudoku.MODE_DAILY, LocalDateTime.of(2026, 1, 1, 10, 0), false))
        }

        should("uses the clock's date when called without a date") {
            sudokusRepository.insertRows(sudokuWithFieldsToDb(dailySudoku(LocalDate.of(2026, 1, 1))))

            useCase()

            sudokusRepository.saves.shouldBeEmpty()
        }

        should("generates for the clock's date when the existing daily sudoku is of another date") {
            sudokusRepository.insertRows(sudokuWithFieldsToDb(dailySudoku(LocalDate.of(2025, 12, 31))))

            useCase()

            sudokusRepository.saves.map { it.rows.sudoku.created } shouldBe listOf(LocalDateTime.of(2026, 1, 1, 10, 0))
        }

        should("does nothing when a daily sudoku already exists for the date") {
            sudokusRepository.insertRows(sudokuWithFieldsToDb(dailySudoku(date)))

            useCase(date)

            sudokusRepository.saves.shouldBeEmpty()
        }
    },
)
