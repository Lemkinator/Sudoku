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
import de.lemke.sudoku.domain.CalculateStatisticsUseCase
import de.lemke.sudoku.domain.ObserveSudokusAndStatisticsFilterFlagsUseCase
import de.lemke.sudoku.domain.model.SudokuStatistics
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

data class TabStatisticsUiState(
    val statistics: SudokuStatistics? = null,
    val isLoading: Boolean = true,
)

sealed interface TabStatisticsEvent {
    data object ShowLoadError : TabStatisticsEvent
}

@HiltViewModel
class TabStatisticsViewModel @Inject constructor(
    private val observeSudokusAndStatisticsFilterFlags: ObserveSudokusAndStatisticsFilterFlagsUseCase,
    private val calculateStatistics: CalculateStatisticsUseCase,
) : ViewModel() {
    val state: StateFlow<TabStatisticsUiState>
        field = MutableStateFlow(TabStatisticsUiState())

    private val _events = Channel<TabStatisticsEvent>(BUFFERED)
    val events: Flow<TabStatisticsEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            runCatching {
                observeSudokusAndStatisticsFilterFlags().collectLatest { filterFlags ->
                    state.value = state.value.copy(isLoading = true)
                    val statistics = calculateStatistics(filterFlags)
                    state.value = TabStatisticsUiState(statistics = statistics, isLoading = false)
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                state.value = state.value.copy(isLoading = false)
                _events.send(TabStatisticsEvent.ShowLoadError)
            }
        }
    }
}
