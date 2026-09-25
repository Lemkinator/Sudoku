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
import de.lemke.commonutils.ui.utils.stateInViewModel
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.InitSudokuLevelUseCase
import de.lemke.sudoku.domain.ObserveSudokuLevelUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformLatest

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
    private val size: Int = savedStateHandle["size"] ?: Sudoku.SIZE_4X4

    val state: StateFlow<SudokuLevelTabUiState> =
        flow {
            if (levelInitialized.await()) emitAll(levelStates()) else emit(SudokuLevelTabUiState(isLoading = false))
        }.stateInViewModel(viewModelScope, SudokuLevelTabUiState())

    private val _events = Channel<SudokuLevelTabEvent>(BUFFERED)
    val events: Flow<SudokuLevelTabEvent> = _events.receiveAsFlow()

    private val levelInitialized: Deferred<Boolean> =
        viewModelScope.async {
            runCatching { initSudokuLevel(size) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _events.send(SudokuLevelTabEvent.ShowLoadError)
                }.isSuccess
        }

    private var nextLevelSudoku: Sudoku? = null
    private var scrolledToNextLevelSudoku: Sudoku? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun levelStates(): Flow<SudokuLevelTabUiState> =
        observeSudokuLevel(size).transformLatest { sudokuLevel ->
            if (sudokuLevel.isEmpty() || (sudokuLevel.firstOrNull() as? SudokuItem)?.sudoku?.completed == true) {
                emit(state.value.copy(isGeneratingNextLevel = true))
                runCatching { findOrGenerateNextLevelSudoku() }
                    .onSuccess { nextLevel ->
                        emit(
                            SudokuLevelTabUiState(
                                sudokuLevel = listOf(SudokuItem(nextLevel, nextLevel.modeLevel.toString())) + sudokuLevel,
                                isLoading = false,
                                isGeneratingNextLevel = false,
                                hasNextLevelToStart = true,
                            ),
                        )
                        if (nextLevel !== scrolledToNextLevelSudoku) {
                            delay(SCROLL_TO_TOP_DELAY_MS.milliseconds)
                            scrolledToNextLevelSudoku = nextLevel
                            _events.send(SudokuLevelTabEvent.ScrollToTop)
                        }
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        emit(state.value.copy(isGeneratingNextLevel = false))
                        _events.send(SudokuLevelTabEvent.ShowLoadError)
                    }
            } else {
                emit(
                    SudokuLevelTabUiState(
                        sudokuLevel = sudokuLevel,
                        isLoading = false,
                        isGeneratingNextLevel = false,
                        hasNextLevelToStart = false,
                    ),
                )
            }
        }

    private suspend fun findOrGenerateNextLevelSudoku(): Sudoku {
        val level = getMaxSudokuLevel(size) + 1
        return nextLevelSudoku?.takeIf { it.modeLevel == level }
            ?: generateSudokuLevel(size, level).also { nextLevelSudoku = it }
    }

    suspend fun onNextLevelSudokuConfirmed(sudoku: Sudoku) {
        saveSudoku(sudoku)
    }
}
