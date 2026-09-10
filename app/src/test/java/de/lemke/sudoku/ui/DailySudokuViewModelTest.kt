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

package de.lemke.sudoku.ui

import app.cash.turbine.test
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.InitDailySudokusUseCase
import de.lemke.sudoku.domain.ObserveDailySudokusUseCase
import de.lemke.sudoku.domain.model.SudokuListItem
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class DailySudokuViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        val initDailySudokus = mockk<InitDailySudokusUseCase>()
        val observeDailySudokus = mockk<ObserveDailySudokusUseCase>()
        val clock = Clock.fixed(ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            coEvery { initDailySudokus(any()) } returns Unit
            every { observeDailySudokus(any()) } returns flowOf(mutableListOf())
        }

        fun newViewModel() = DailySudokuViewModel(userSettings, initDailySudokus, observeDailySudokus, clock)

        should("init loads sudokus from observeDailySudokus and sets isLoading false on success") {
            val items: MutableList<SudokuListItem> =
                mutableListOf(SudokuListItem.SeparatorItem("Jan 2026"), SudokuListItem.SeparatorItem("Feb 2026"))
            every { observeDailySudokus(any()) } returns flowOf(items)

            val viewModel = newViewModel()

            viewModel.state.value shouldBe DailySudokuUiState(sudokus = items, isLoading = false)
            viewModel.events.test { expectNoEvents() }
        }

        should("init sets isLoading false and emits ShowLoadError when initDailySudokus throws") {
            coEvery { initDailySudokus(any()) } throws RuntimeException("init failed")

            val viewModel = newViewModel()

            viewModel.state.value.isLoading
                .shouldBeFalse()
            viewModel.events.test {
                awaitItem() shouldBe DailySudokuEvent.ShowLoadError
            }
        }

        should("init sets isLoading false and emits ShowLoadError when observeDailySudokus throws") {
            every { observeDailySudokus(any()) } throws RuntimeException("observe failed")

            val viewModel = newViewModel()

            viewModel.state.value.isLoading
                .shouldBeFalse()
            viewModel.events.test {
                awaitItem() shouldBe DailySudokuEvent.ShowLoadError
            }
        }

        should("init does not treat CancellationException as a load failure: no isLoading flip, no ShowLoadError event") {
            coEvery { initDailySudokus(any()) } throws CancellationException("cancelled")

            val viewModel = newViewModel()

            viewModel.state.value shouldBe DailySudokuUiState()
            viewModel.events.test { expectNoEvents() }
        }

        should("dailyShowUncompleted round-trips through the real UserSettings") {
            val viewModel = newViewModel()

            viewModel.dailyShowUncompleted = false

            viewModel.dailyShowUncompleted.shouldBeFalse()
            userSettings.dailyShowUncompleted.shouldBeFalse()
        }
    },
)
