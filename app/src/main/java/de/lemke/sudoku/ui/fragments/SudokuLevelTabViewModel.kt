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
import de.lemke.sudoku.domain.model.GameMode
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SudokuLevelTabUiState(
    val sudokuLevel: List<SudokuListItem> = emptyList(),
    val isLoading: Boolean = true,
    val isGeneratingNextLevel: Boolean = false,
    /** The level the list offers on top and that is not saved until the player starts it. */
    val nextLevel: Sudoku? = null,
) {
    val hasNextLevelToStart: Boolean get() = nextLevel != null
}

/** The start of a level row. The tab opens a [Result] or shows its error, and then reports it handled. */
sealed interface LevelStart {
    sealed interface Result : LevelStart

    data object Idle : LevelStart

    data object Running : LevelStart

    data class Open(val sudokuId: SudokuId) : Result

    data object Failed : Result
}

private val Sudoku.levelNumber: Int get() = (info.mode as GameMode.Level).number

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

    /** True after a load failed until the screen reports the error shown. */
    val loadFailed: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /** The sudoku the list scrolls to, until the screen reports it revealed. */
    val reveal: StateFlow<SudokuId?>
        field = MutableStateFlow<SudokuId?>(null)

    private val levelListReloads = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SudokuLevelTabUiState> =
        levelListReloads
            .flatMapLatest { levelList() }
            .stateInViewModel(viewModelScope, SudokuLevelTabUiState())

    val levelStart: StateFlow<LevelStart>
        field = MutableStateFlow<LevelStart>(LevelStart.Idle)

    private val levelInitialized: Deferred<Boolean> =
        viewModelScope.async {
            runCatching { initSudokuLevel(size) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    loadFailed.value = true
                }.isSuccess
        }

    private var nextLevelSudoku: Sudoku? = null
    private var revealedNextLevelId: SudokuId? = null

    private fun levelList(): Flow<SudokuLevelTabUiState> =
        flow {
            if (levelInitialized.await()) emitAll(levelStates()) else emit(SudokuLevelTabUiState(isLoading = false))
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(state.value.copy(isLoading = false, isGeneratingNextLevel = false))
            loadFailed.value = true
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun levelStates(): Flow<SudokuLevelTabUiState> =
        observeSudokuLevel(size).transformLatest { sudokuLevel ->
            if (sudokuLevel.isEmpty() || (sudokuLevel.first() as SudokuItem).summary.completed) {
                runCatching { findOrGenerateNextLevelSudoku() }
                    .onSuccess { nextLevel ->
                        emit(
                            SudokuLevelTabUiState(
                                sudokuLevel = listOf(SudokuItem(nextLevel.summary(), nextLevel.levelNumber.toString())) + sudokuLevel,
                                isLoading = false,
                                isGeneratingNextLevel = false,
                                nextLevel = nextLevel,
                            ),
                        )
                        if (nextLevel.info.id != revealedNextLevelId) {
                            revealedNextLevelId = nextLevel.info.id
                            reveal.value = nextLevel.info.id
                        }
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        emit(state.value.copy(isLoading = false, isGeneratingNextLevel = false))
                        loadFailed.value = true
                    }
            } else {
                emit(
                    SudokuLevelTabUiState(
                        sudokuLevel = sudokuLevel,
                        isLoading = false,
                        isGeneratingNextLevel = false,
                    ),
                )
            }
        }

    private suspend fun FlowCollector<SudokuLevelTabUiState>.findOrGenerateNextLevelSudoku(): Sudoku {
        val level = getMaxSudokuLevel(size) + 1
        nextLevelSudoku?.takeIf { it.info.mode == GameMode.Level(level) }?.let { return it }
        emit(state.value.copy(isGeneratingNextLevel = true))
        return generateSudokuLevel(size, level).also { nextLevelSudoku = it }
    }

    fun confirmSudokuStart(sudokuId: SudokuId) {
        if (levelStart.value == LevelStart.Running) return
        val nextLevel = state.value.nextLevel
        if (nextLevel == null || nextLevel.info.id != sudokuId) {
            levelStart.value = LevelStart.Open(sudokuId)
            return
        }
        levelStart.value = LevelStart.Running
        viewModelScope.launch { levelStart.value = saveNextLevel(nextLevel) }
    }

    fun onLevelStartHandled(result: LevelStart.Result) {
        levelStart.compareAndSet(result, LevelStart.Idle)
    }

    private suspend fun saveNextLevel(sudoku: Sudoku): LevelStart =
        runCatching {
            val levelUnsaved = getMaxSudokuLevel(size) < sudoku.levelNumber
            if (levelUnsaved) saveSudoku(sudoku)
            levelUnsaved
        }.map { levelUnsaved -> if (levelUnsaved) LevelStart.Open(sudoku.info.id) else refuseSavedLevel() }
            .getOrElse { e -> if (e is CancellationException) throw e else LevelStart.Failed }

    private fun refuseSavedLevel(): LevelStart {
        levelListReloads.update { it + 1 }
        return LevelStart.Idle
    }

    fun onLoadFailureHandled() {
        loadFailed.value = false
    }

    fun onRevealHandled(sudokuId: SudokuId) {
        reveal.compareAndSet(sudokuId, null)
    }
}
