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

import android.net.Uri
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.ImportSudokuUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        val importSudoku = mockk<ImportSudokuUseCase>()
        val sendDailyNotification = mockk<SendDailyNotificationUseCase>(relaxUnitFun = true)
        val calculatePlayGamesSync = mockk<CalculatePlayGamesSyncUseCase>()
        lateinit var viewModel: MainViewModel

        beforeEach {
            clearMocks(importSudoku, sendDailyNotification, calculatePlayGamesSync)
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            viewModel = MainViewModel(userSettings, importSudoku, sendDailyNotification, calculatePlayGamesSync)
        }

        should("handleImportedSudoku returns null and does not call importSudoku's business logic when uri is null") {
            coEvery { importSudoku(null) } returns null
            viewModel.handleImportedSudoku(null) shouldBe null
            coVerify(exactly = 1) { importSudoku(null) }
        }

        should("handleImportedSudoku delegates to importSudoku and returns its result when uri is non-null") {
            val uri = mockk<Uri>()
            val sudoku = mockk<Sudoku>()
            coEvery { importSudoku(uri) } returns sudoku
            viewModel.handleImportedSudoku(uri) shouldBe sudoku
            coVerify(exactly = 1) { importSudoku(uri) }
        }

        should("onScreenReady enables the daily notification when userSettings.dailySudokuNotificationEnabled is true") {
            userSettings.dailySudokuNotificationEnabled = true
            val sync = mockk<PlayGamesSync>()
            coEvery { calculatePlayGamesSync() } returns sync
            viewModel.onScreenReady()
            verify(exactly = 1) { sendDailyNotification.setDailySudokuNotification(enable = true) }
        }

        should("onScreenReady disables the daily notification when userSettings.dailySudokuNotificationEnabled is false") {
            userSettings.dailySudokuNotificationEnabled = false
            val sync = mockk<PlayGamesSync>()
            coEvery { calculatePlayGamesSync() } returns sync
            viewModel.onScreenReady()
            verify(exactly = 1) { sendDailyNotification.setDailySudokuNotification(enable = false) }
        }

        should("onScreenReady returns calculatePlayGamesSync's result") {
            val sync = mockk<PlayGamesSync>()
            coEvery { calculatePlayGamesSync() } returns sync
            viewModel.onScreenReady() shouldBe sync
        }
    },
)
