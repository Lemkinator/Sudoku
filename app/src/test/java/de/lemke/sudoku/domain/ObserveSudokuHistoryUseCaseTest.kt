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
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.dateFormatShort
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private fun sudokuUpdatedAt(updated: LocalDateTime): Sudoku =
    Sudoku.create(
        size = 4,
        difficulty = VERY_EASY,
        modeLevel = Sudoku.MODE_NORMAL,
        updated = updated,
        fields = mutableListOf(Field(position = Position.create(0, 4), solution = 1, value = 1)),
    )

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
        val observeAllNormalSudokus = mockk<ObserveAllNormalSudokusUseCase>()
        val useCase = ObserveSudokuHistoryUseCase(observeAllNormalSudokus, UnconfinedTestDispatcher())
        val day1 = LocalDateTime.of(2026, 1, 1, 10, 0)
        val day2 = LocalDateTime.of(2026, 1, 2, 10, 0)
        val label1 = day1.dateFormatShort
        val label2 = day2.dateFormatShort

        should("emit an empty list when there is no history") {
            every { observeAllNormalSudokus() } returns flowOf(emptyList())

            useCase().test {
                awaitItem() shouldBe emptyList()
                awaitComplete()
            }
        }

        should("insert exactly one separator when every game is on the same day") {
            every { observeAllNormalSudokus() } returns flowOf(listOf(sudokuUpdatedAt(day1), sudokuUpdatedAt(day1)))

            useCase().test {
                awaitItem().shape() shouldBe
                    listOf(
                        "separator" to label1,
                        "item" to label1,
                        "item" to label1,
                    )
                awaitComplete()
            }
        }

        should("insert a new separator when the day changes mid-list") {
            every { observeAllNormalSudokus() } returns
                flowOf(listOf(sudokuUpdatedAt(day1), sudokuUpdatedAt(day2), sudokuUpdatedAt(day2)))

            useCase().test {
                awaitItem().shape() shouldBe
                    listOf(
                        "separator" to label1,
                        "item" to label1,
                        "separator" to label2,
                        "item" to label2,
                        "item" to label2,
                    )
                awaitComplete()
            }
        }
    },
)
