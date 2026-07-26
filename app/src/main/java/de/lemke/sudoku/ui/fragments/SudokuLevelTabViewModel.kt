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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.InitSudokuLevelUseCase
import de.lemke.sudoku.domain.ObserveSudokuLevelUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.utils.SudokuListItem
import de.lemke.sudoku.ui.utils.SudokuListItem.SudokuItem
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val SCROLL_TO_TOP_DELAY_MS = 200L

data class SudokuLevelTabUiState(
    val sudokuLevel: List<SudokuListItem> = emptyList(),
    val isLoading: Boolean = true,
    val isGeneratingNextLevel: Boolean = false,
    val hasNextLevelToStart: Boolean = false,
)

sealed interface SudokuLevelTabEvent {
    data object ScrollToTop : SudokuLevelTabEvent

    data object ShowLoadError : SudokuLevelTabEvent
}

@HiltViewModel
class SudokuLevelTabViewModel @Inject constructor(
    private val initSudokuLevel: InitSudokuLevelUseCase,
    private val observeSudokuLevel: ObserveSudokuLevelUseCase,
    private val getMaxSudokuLevel: GetMaxSudokuLevelUseCase,
    private val generateSudokuLevel: GenerateSudokuLevelUseCase,
    private val saveSudoku: SaveSudokuUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val size: Int = savedStateHandle["size"] ?: 4
    private var nextLevelSudoku: Sudoku? = null

    val state: StateFlow<SudokuLevelTabUiState>
        field = MutableStateFlow(SudokuLevelTabUiState())

    private val _events = Channel<SudokuLevelTabEvent>(BUFFERED)
    val events: Flow<SudokuLevelTabEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            runCatching {
                initSudokuLevel(size)
                observeSudokuLevel(size).collectLatest { sudokuLevel ->
                    if (sudokuLevel.isEmpty() || (sudokuLevel.firstOrNull() as? SudokuItem)?.sudoku?.completed == true) {
                        state.value = state.value.copy(isGeneratingNextLevel = true)
                        nextLevelSudoku = generateSudokuLevel(size, level = getMaxSudokuLevel(size) + 1)
                        val levelWithNext = listOf(SudokuItem(nextLevelSudoku!!, nextLevelSudoku!!.modeLevel.toString())) + sudokuLevel
                        state.value =
                            SudokuLevelTabUiState(
                                sudokuLevel = levelWithNext,
                                isLoading = false,
                                isGeneratingNextLevel = false,
                                hasNextLevelToStart = true,
                            )
                        delay(SCROLL_TO_TOP_DELAY_MS)
                        _events.send(SudokuLevelTabEvent.ScrollToTop)
                    } else {
                        nextLevelSudoku = null
                        state.value =
                            SudokuLevelTabUiState(
                                sudokuLevel = sudokuLevel,
                                isLoading = false,
                                isGeneratingNextLevel = false,
                                hasNextLevelToStart = false,
                            )
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _events.send(SudokuLevelTabEvent.ShowLoadError)
            }
        }
    }

    suspend fun onNextLevelSudokuConfirmed(sudoku: Sudoku) {
        saveSudoku(sudoku)
    }
}
