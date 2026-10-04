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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class DailySudokuUiState(
    val sudokus: List<SudokuListItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class DailySudokuViewModel @Inject constructor(
    private val userSettings: UserSettings,
    private val initDailySudokus: InitDailySudokusUseCase,
    private val observeDailySudokus: ObserveDailySudokusUseCase,
    private val clock: Clock,
) : ViewModel() {
    /** True after a load failed until the screen reports the error shown. */
    val loadFailed: StateFlow<Boolean>
        field = MutableStateFlow(false)

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
            loadFailed.value = true
        }.stateInViewModel(viewModelScope, DailySudokuUiState())

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
                    loadFailed.value = true
                }.isSuccess
        }

    fun onLoadFailureHandled() {
        loadFailed.value = false
    }
}
