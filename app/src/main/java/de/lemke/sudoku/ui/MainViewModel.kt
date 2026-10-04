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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.ImportSudokuUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.SudokuId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The import of a sudoku file opened with the app. The activity opens or reports a [Result] and then reports it handled. */
sealed interface ImportedSudoku {
    sealed interface Result : ImportedSudoku

    data object Idle : ImportedSudoku

    data object Importing : ImportedSudoku

    data class Imported(val sudokuId: SudokuId) : Result

    data object Failed : Result
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userSettings: UserSettings,
    private val importSudoku: ImportSudokuUseCase,
    private val sendDailyNotification: SendDailyNotificationUseCase,
    private val calculatePlayGamesSync: CalculatePlayGamesSyncUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val importedSudoku: StateFlow<ImportedSudoku>
        field = MutableStateFlow<ImportedSudoku>(ImportedSudoku.Idle)

    private var importingFile: String? = null

    /**
     * Imports the file at [uri] once, also when a recreated or restored activity hands the same [uri] again.
     * A process death during the import imports the file again, which replaces the sudoku stored under the file's id.
     */
    fun onFileOpened(uri: Uri) {
        val uriString = uri.toString()
        if (uriString == importingFile || savedStateHandle.get<String>(KEY_OPENED_FILE) == uriString) return
        importingFile = uriString
        importedSudoku.value = ImportedSudoku.Importing
        viewModelScope.launch {
            val result = importSudoku(uri)?.let { ImportedSudoku.Imported(it.id) } ?: ImportedSudoku.Failed
            savedStateHandle[KEY_OPENED_FILE] = uriString
            importingFile = null
            importedSudoku.value = result
        }
    }

    fun onImportedSudokuHandled(result: ImportedSudoku.Result) {
        importedSudoku.update { if (it == result) ImportedSudoku.Idle else it }
    }

    suspend fun onScreenReady(): PlayGamesSync {
        sendDailyNotification.setDailySudokuNotification(enable = userSettings.dailySudokuNotificationEnabled)
        return calculatePlayGamesSync()
    }

    private companion object {
        const val KEY_OPENED_FILE = "openedFile"
    }
}
