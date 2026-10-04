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
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.DeleteSudokusUseCase
import de.lemke.sudoku.domain.ObserveSudokuHistoryUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The deletion of the selected sudokus. The tab ends its action mode on a [Result] and then reports it handled. */
sealed interface HistoryDeletion {
    sealed interface Result : HistoryDeletion

    data object Idle : HistoryDeletion

    data object Running : HistoryDeletion

    data object Finished : Result
}

@HiltViewModel
class TabHistoryViewModel @Inject constructor(
    userSettings: UserSettings,
    private val observeSudokuHistory: ObserveSudokuHistoryUseCase,
    private val deleteSudoku: DeleteSudokusUseCase,
) : ViewModel() {
    val errorLimit: StateFlow<Int> = userSettings.errorLimitFlow

    /** True after a load failed until the screen reports the error shown. */
    val loadFailed: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /** The sudoku the list scrolls to, until the screen reports it revealed. */
    val reveal: StateFlow<SudokuId?>
        field = MutableStateFlow<SudokuId?>(null)

    private var previousUpdates: Map<SudokuId, LocalDateTime>? = null

    val sudokuHistory: StateFlow<List<SudokuListItem>> =
        observeSudokuHistory()
            .transform { newHistory ->
                val sudokus = newHistory.filterIsInstance<SudokuItem>().map { it.sudoku }
                val previous = previousUpdates
                previousUpdates = sudokus.associate { it.id to it.updated }
                emit(newHistory)
                if (previous != null) {
                    sudokus.firstOrNull { previous[it.id] != it.updated }?.let { reveal.value = it.id }
                }
            }.catch { e ->
                if (e is CancellationException) throw e
                loadFailed.value = true
            }.stateInViewModel(viewModelScope, emptyList())

    val deletion: StateFlow<HistoryDeletion>
        field = MutableStateFlow<HistoryDeletion>(HistoryDeletion.Idle)

    fun onDeleteSelected(sudokus: List<Sudoku>) {
        if (deletion.value == HistoryDeletion.Running) return
        deletion.value = HistoryDeletion.Running
        viewModelScope.launch {
            deleteSudoku(sudokus)
            deletion.value = HistoryDeletion.Finished
        }
    }

    fun onDeletionHandled(result: HistoryDeletion.Result) {
        deletion.update { if (it == result) HistoryDeletion.Idle else it }
    }

    fun onLoadFailureHandled() {
        loadFailed.value = false
    }

    fun onRevealHandled(sudokuId: SudokuId) {
        reveal.update { if (it == sudokuId) null else it }
    }
}
