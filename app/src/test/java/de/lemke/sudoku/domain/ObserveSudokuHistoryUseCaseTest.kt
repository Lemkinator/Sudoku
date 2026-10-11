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

import app.cash.turbine.test
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.dateFormatShort
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private fun List<SudokuListItem>.shape(): List<Pair<String, String>> =
    map {
        when (it) {
            is SudokuListItem.SudokuItem -> "item" to it.label
            is SudokuListItem.SeparatorItem -> "separator" to it.label
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSudokuHistoryUseCaseTest : ShouldSpec(
    {
        lateinit var sudokusRepository: FakeSudokusRepository
        lateinit var useCase: ObserveSudokuHistoryUseCase
        val day1 = LocalDateTime.of(2026, 1, 2, 10, 0)
        val day2 = LocalDateTime.of(2026, 1, 1, 10, 0)
        val label1 = day1.dateFormatShort
        val label2 = day2.dateFormatShort

        beforeEach {
            val dispatcher = UnconfinedTestDispatcher()
            sudokusRepository = FakeSudokusRepository()
            useCase = ObserveSudokuHistoryUseCase(ObserveAllNormalSudokusUseCase(sudokusRepository, dispatcher), dispatcher)
        }

        should("emit an empty list when there is no history") {
            useCase().test {
                awaitItem() shouldBe emptyList()
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("insert exactly one separator when every game is on the same day") {
            repeat(2) { sudokusRepository.saveSudoku(testSudoku(difficulty = VERY_EASY, updated = day1)) }

            useCase().test {
                awaitItem().shape() shouldBe
                    listOf(
                        "separator" to label1,
                        "item" to label1,
                        "item" to label1,
                    )
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("insert a new separator when the day changes mid-list") {
            listOf(day1, day2, day2).forEach { sudokusRepository.saveSudoku(testSudoku(difficulty = VERY_EASY, updated = it)) }

            useCase().test {
                awaitItem().shape() shouldBe
                    listOf(
                        "separator" to label1,
                        "item" to label1,
                        "separator" to label2,
                        "item" to label2,
                        "item" to label2,
                    )
                cancelAndIgnoreRemainingEvents()
            }
        }
    },
)
