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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.InitDailySudokusUseCase
import de.lemke.sudoku.domain.ObserveDailySudokusUseCase
import de.lemke.sudoku.ui.utils.SudokuListItem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class DailySudokuUiState(
    val sudokus: List<SudokuListItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class DailySudokuViewModel @Inject constructor(
    private val userSettings: UserSettings,
    private val initDailySudokus: InitDailySudokusUseCase,
    private val observeDailySudokus: ObserveDailySudokusUseCase,
) : ViewModel() {
    val state: StateFlow<DailySudokuUiState>
        field = MutableStateFlow(DailySudokuUiState())

    var dailyShowUncompleted: Boolean
        get() = userSettings.dailyShowUncompleted
        set(value) {
            userSettings.dailyShowUncompleted = value
        }

    init {
        viewModelScope.launch {
            initDailySudokus()
            observeDailySudokus().collectLatest { sudokus ->
                state.value = DailySudokuUiState(sudokus = sudokus, isLoading = false)
            }
        }
    }
}
