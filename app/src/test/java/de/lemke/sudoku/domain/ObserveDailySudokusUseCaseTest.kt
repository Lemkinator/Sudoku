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
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.monthAndYear
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private fun dailySudoku(
    created: LocalDateTime,
    completed: Boolean,
): Sudoku =
    Sudoku.create(
        size = 9,
        difficulty = VERY_EASY,
        modeLevel = Sudoku.MODE_DAILY,
        created = created,
        fields = mutableListOf(Field(position = Position.create(0, 9), solution = 1, value = if (completed) 1 else null)),
    )

private fun List<SudokuListItem>.shape(): List<Pair<String, String>> =
    map {
        when (it) {
            is SudokuListItem.SudokuItem -> "item" to it.label
            is SudokuListItem.SeparatorItem -> "separator" to it.label
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDailySudokusUseCaseTest : ShouldSpec(
    {
        val sudokusRepository = mockk<SudokusRepository>()
        lateinit var userSettings: UserSettings
        lateinit var useCase: ObserveDailySudokusUseCase

        val today = LocalDateTime.of(2026, 2, 15, 10, 0)
        val todayDate = today.toLocalDate()
        val completedThisMonth = dailySudoku(created = LocalDateTime.of(2026, 2, 5, 9, 0), completed = true)
        val uncompletedToday = dailySudoku(created = today, completed = false)
        val uncompletedLastMonth = dailySudoku(created = LocalDateTime.of(2026, 1, 20, 9, 0), completed = false)

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            useCase = ObserveDailySudokusUseCase(sudokusRepository, userSettings, UnconfinedTestDispatcher())
            every { sudokusRepository.observeDailySudokus() } returns
                flowOf(listOf(completedThisMonth, uncompletedToday, uncompletedLastMonth))
        }

        should("show every daily sudoku, including old uncompleted ones, when dailyShowUncompleted is on") {
            userSettings.dailyShowUncompleted = true

            useCase(todayDate).test {
                awaitItem().shape() shouldBe
                    listOf(
                        "separator" to completedThisMonth.created.monthAndYear,
                        "item" to completedThisMonth.created.monthAndYear,
                        "item" to uncompletedToday.created.monthAndYear,
                        "separator" to uncompletedLastMonth.created.monthAndYear,
                        "item" to uncompletedLastMonth.created.monthAndYear,
                    )
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("hide old uncompleted daily sudokus but keep today's in-progress one when dailyShowUncompleted is off") {
            userSettings.dailyShowUncompleted = false

            useCase(todayDate).test {
                awaitItem().shape() shouldBe
                    listOf(
                        "separator" to completedThisMonth.created.monthAndYear,
                        "item" to completedThisMonth.created.monthAndYear,
                        "item" to uncompletedToday.created.monthAndYear,
                    )
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("re-filter when dailyShowUncompleted flips while being observed") {
            userSettings.dailyShowUncompleted = false

            useCase(todayDate).test {
                awaitItem().shape().size shouldBe 3

                userSettings.dailyShowUncompleted = true

                awaitItem().shape().size shouldBe 5
                cancelAndIgnoreRemainingEvents()
            }
        }
    },
)
