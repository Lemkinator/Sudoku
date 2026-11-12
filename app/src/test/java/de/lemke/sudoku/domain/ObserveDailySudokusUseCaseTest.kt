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
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher

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
        lateinit var sudokusRepository: FakeSudokusRepository
        lateinit var settingsScope: CoroutineScope
        lateinit var userSettings: UserSettings
        lateinit var useCase: ObserveDailySudokusUseCase

        val today = LocalDateTime.of(2026, 2, 15, 10, 0)
        val todayDate = today.toLocalDate()
        val completedThisMonth =
            testSudoku(
                size = SudokuSize.NINE,
                mode = GameMode.Daily(LocalDate.of(2026, 2, 5)),
                difficulty = VERY_EASY,
                created = LocalDateTime.of(2026, 2, 5, 9, 0),
                completed = true,
            )
        val uncompletedToday =
            testSudoku(
                size = SudokuSize.NINE,
                mode = GameMode.Daily(todayDate),
                difficulty = VERY_EASY,
                created = today,
            )
        val uncompletedLastMonth =
            testSudoku(
                size = SudokuSize.NINE,
                mode = GameMode.Daily(LocalDate.of(2026, 1, 20)),
                difficulty = VERY_EASY,
                created = LocalDateTime.of(2026, 1, 20, 9, 0),
            )

        beforeEach {
            sudokusRepository = FakeSudokusRepository()
            settingsScope = CoroutineScope(Job() + UnconfinedTestDispatcher())
            userSettings = UserSettings(FakeSharedPreferences(), settingsScope)
            useCase =
                ObserveDailySudokusUseCase(
                    sudokusRepository,
                    userSettings,
                    Clock.fixed(today.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                    UnconfinedTestDispatcher(),
                )
            listOf(completedThisMonth, uncompletedToday, uncompletedLastMonth).forEach { sudokusRepository.saveSudoku(it) }
        }

        afterEach { settingsScope.cancel() }

        should("show every daily sudoku, including old uncompleted ones, when dailyShowUncompleted is on") {
            userSettings.dailyShowUncompleted = true

            useCase(todayDate).test {
                awaitItem().shape() shouldBe
                    listOf(
                        "separator" to "February 2026",
                        "item" to "February 2026",
                        "item" to "February 2026",
                        "separator" to "January 2026",
                        "item" to "January 2026",
                    )
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("hide old uncompleted daily sudokus but keep today's in-progress one when dailyShowUncompleted is off") {
            userSettings.dailyShowUncompleted = false

            useCase(todayDate).test {
                awaitItem().shape() shouldBe
                    listOf(
                        "separator" to "February 2026",
                        "item" to "February 2026",
                        "item" to "February 2026",
                    )
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("keep today's in-progress sudoku by the clock's date when called without a date") {
            userSettings.dailyShowUncompleted = false

            useCase().test {
                awaitItem().shape() shouldBe
                    listOf(
                        "separator" to "February 2026",
                        "item" to "February 2026",
                        "item" to "February 2026",
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

        should("label and filter a daily sudoku by its day when its creation stamp reads as another day") {
            userSettings.dailyShowUncompleted = false
            val firstOfMonth =
                testSudoku(
                    size = SudokuSize.NINE,
                    mode = GameMode.Daily(LocalDate.of(2026, 2, 15)),
                    difficulty = VERY_EASY,
                    created = LocalDateTime.of(2026, 2, 14, 23, 0),
                )
            val otherDay =
                testSudoku(
                    size = SudokuSize.NINE,
                    mode = GameMode.Daily(LocalDate.of(2026, 1, 31)),
                    difficulty = VERY_EASY,
                    created = LocalDateTime.of(2026, 2, 15, 1, 0),
                )
            sudokusRepository = FakeSudokusRepository()
            sudokusRepository.store(firstOfMonth, otherDay)
            useCase =
                ObserveDailySudokusUseCase(
                    sudokusRepository,
                    userSettings,
                    Clock.fixed(today.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                    UnconfinedTestDispatcher(),
                )

            useCase(todayDate).test {
                awaitItem().shape() shouldBe listOf("separator" to "February 2026", "item" to "February 2026")
                cancelAndIgnoreRemainingEvents()
            }
        }
    },
)
