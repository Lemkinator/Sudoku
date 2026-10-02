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
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.domain.model.SudokuSize
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformLatest

data class SudokuLevelTabUiState(
    val sudokuLevel: List<SudokuListItem> = emptyList(),
    val isLoading: Boolean = true,
    val isGeneratingNextLevel: Boolean = false,
    val hasNextLevelToStart: Boolean = false,
)

sealed interface SudokuLevelTabEvent {
    data class RevealSudoku(val sudokuId: SudokuId) : SudokuLevelTabEvent

    data object ShowLoadError : SudokuLevelTabEvent

    data object ShowStartError : SudokuLevelTabEvent
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
    private val size: SudokuSize =
        SudokuSize.fromValue(
            checkNotNull(savedStateHandle.get<Int>(SudokuLevelTab.KEY_SIZE)) { "Missing ${SudokuLevelTab.KEY_SIZE} argument" },
        )

    val state: StateFlow<SudokuLevelTabUiState> =
        flow {
            if (levelInitialized.await()) emitAll(levelStates()) else emit(SudokuLevelTabUiState(isLoading = false))
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(state.value.copy(isLoading = false, isGeneratingNextLevel = false))
            _events.send(SudokuLevelTabEvent.ShowLoadError)
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
    private var revealedNextLevelId: SudokuId? = null
    private var sudokuStarting = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun levelStates(): Flow<SudokuLevelTabUiState> =
        observeSudokuLevel(size).transformLatest { sudokuLevel ->
            if (sudokuLevel.isEmpty() || (sudokuLevel.firstOrNull() as? SudokuItem)?.sudoku?.completed == true) {
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
                        if (nextLevel.id != revealedNextLevelId) {
                            revealedNextLevelId = nextLevel.id
                            _events.send(SudokuLevelTabEvent.RevealSudoku(nextLevel.id))
                        }
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        emit(state.value.copy(isLoading = false, isGeneratingNextLevel = false))
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

    private suspend fun FlowCollector<SudokuLevelTabUiState>.findOrGenerateNextLevelSudoku(): Sudoku {
        val level = getMaxSudokuLevel(size) + 1
        nextLevelSudoku?.takeIf { it.modeLevel == level }?.let { return it }
        emit(state.value.copy(isGeneratingNextLevel = true))
        return generateSudokuLevel(size, level).also { nextLevelSudoku = it }
    }

    suspend fun confirmSudokuStart(
        position: Int,
        sudoku: Sudoku,
    ): Boolean {
        if (sudokuStarting) return false
        sudokuStarting = true
        if (position == 0 && state.value.hasNextLevelToStart) sudokuStarting = saveNextLevel(sudoku)
        return sudokuStarting
    }

    private suspend fun saveNextLevel(sudoku: Sudoku): Boolean {
        val saveResult =
            viewModelScope
                .async {
                    runCatching {
                        val levelUnsaved = getMaxSudokuLevel(size) < sudoku.modeLevel
                        if (levelUnsaved) saveSudoku(sudoku)
                        levelUnsaved
                    }
                }.await()
        val saveFailure = saveResult.exceptionOrNull()
        if (saveFailure is CancellationException) {
            sudokuStarting = false
            throw saveFailure
        }
        if (saveFailure != null) _events.send(SudokuLevelTabEvent.ShowStartError)
        return saveResult.getOrDefault(false)
    }

    fun onTabResumed() {
        sudokuStarting = false
    }
}
