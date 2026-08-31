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
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.ImportSudokuUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userSettings: UserSettings,
    private val importSudoku: ImportSudokuUseCase,
    private val sendDailyNotification: SendDailyNotificationUseCase,
    private val calculatePlayGamesSync: CalculatePlayGamesSyncUseCase,
) : ViewModel() {
    suspend fun handleImportedSudoku(uri: Uri?): Sudoku? = importSudoku(uri)

    suspend fun onScreenReady(): PlayGamesSync {
        sendDailyNotification.setDailySudokuNotification(enable = userSettings.dailySudokuNotificationEnabled)
        return calculatePlayGamesSync()
    }
}
