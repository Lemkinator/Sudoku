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
import de.lemke.commonutils.ui.utils.stateInViewModel
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.InitDailySudokusUseCase
import de.lemke.sudoku.domain.ObserveDailySudokusUseCase
import de.lemke.sudoku.domain.model.SudokuListItem
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

data class DailySudokuUiState(
    val sudokus: List<SudokuListItem> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface DailySudokuEvent {
    data object ShowLoadError : DailySudokuEvent
}

@HiltViewModel
class DailySudokuViewModel @Inject constructor(
    private val userSettings: UserSettings,
    private val initDailySudokus: InitDailySudokusUseCase,
    private val observeDailySudokus: ObserveDailySudokusUseCase,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<DailySudokuUiState> =
        flow {
            if (dailySudokusInitialized.await()) {
                emitAll(observeDailySudokus(today).map { sudokus -> DailySudokuUiState(sudokus = sudokus, isLoading = false) })
            } else {
                emit(DailySudokuUiState(isLoading = false))
            }
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(state.value.copy(isLoading = false))
            _events.send(DailySudokuEvent.ShowLoadError)
        }.stateInViewModel(viewModelScope, DailySudokuUiState())

    private val _events = Channel<DailySudokuEvent>(BUFFERED)
    val events: Flow<DailySudokuEvent> = _events.receiveAsFlow()

    var dailyShowUncompleted: Boolean
        get() = userSettings.dailyShowUncompleted
        set(value) {
            userSettings.dailyShowUncompleted = value
        }

    private val today = LocalDate.now(clock)

    private val dailySudokusInitialized: Deferred<Boolean> =
        viewModelScope.async {
            runCatching { initDailySudokus(today) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _events.send(DailySudokuEvent.ShowLoadError)
                }.isSuccess
        }
}
