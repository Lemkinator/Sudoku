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
import de.lemke.commonutils.ui.utils.stateInViewModel
import de.lemke.sudoku.domain.CalculateStatisticsUseCase
import de.lemke.sudoku.domain.ObserveSudokusAndStatisticsFilterFlagsUseCase
import de.lemke.sudoku.domain.model.SudokuStatistics
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest

data class TabStatisticsUiState(
    val statistics: SudokuStatistics? = null,
)

@HiltViewModel
class TabStatisticsViewModel @Inject constructor(
    private val observeSudokusAndStatisticsFilterFlags: ObserveSudokusAndStatisticsFilterFlagsUseCase,
    private val calculateStatistics: CalculateStatisticsUseCase,
) : ViewModel() {
    /** True after a load failed until the screen reports the error shown. */
    val loadFailed: StateFlow<Boolean>
        field = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TabStatisticsUiState> =
        observeSudokusAndStatisticsFilterFlags()
            .mapLatest { filterFlags -> TabStatisticsUiState(statistics = calculateStatistics(filterFlags)) }
            .catch { e ->
                if (e is CancellationException) throw e
                loadFailed.value = true
            }.stateInViewModel(viewModelScope, TabStatisticsUiState())

    fun onLoadFailureHandled() {
        loadFailed.value = false
    }
}
