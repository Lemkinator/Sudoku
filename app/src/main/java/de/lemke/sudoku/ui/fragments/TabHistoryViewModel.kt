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

package de.lemke.sudoku.ui.fragments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.DeleteSudokusUseCase
import de.lemke.sudoku.domain.ObserveSudokuHistoryUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.utils.SudokuListItem
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface TabHistoryEvent {
    data object ScrollToTop : TabHistoryEvent

    data object ShowLoadError : TabHistoryEvent
}

@HiltViewModel
class TabHistoryViewModel @Inject constructor(
    userSettings: UserSettings,
    private val observeSudokuHistory: ObserveSudokuHistoryUseCase,
    private val deleteSudoku: DeleteSudokusUseCase,
) : ViewModel() {
    val errorLimit: StateFlow<Int> = userSettings.errorLimitFlow

    val sudokuHistory: StateFlow<List<SudokuListItem>>
        field = MutableStateFlow(emptyList())

    private val _events = Channel<TabHistoryEvent>(BUFFERED)
    val events: Flow<TabHistoryEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            runCatching {
                observeSudokuHistory().collectLatest { newHistory ->
                    val grew = newHistory.size > sudokuHistory.value.size
                    sudokuHistory.value = newHistory
                    if (grew) _events.send(TabHistoryEvent.ScrollToTop)
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _events.send(TabHistoryEvent.ShowLoadError)
            }
        }
    }

    suspend fun deleteSelectedSudokus(sudokus: List<Sudoku>) = deleteSudoku(sudokus)
}
