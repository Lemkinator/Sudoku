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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SudokuLevelTabUiState(
    val sudokuLevel: List<SudokuListItem> = emptyList(),
    val isLoading: Boolean = true,
    val isGeneratingNextLevel: Boolean = false,
    val hasNextLevelToStart: Boolean = false,
)

/** The start of a level row. The tab opens a [Result] or shows its error, and then reports it handled. */
sealed interface LevelStart {
    sealed interface Result : LevelStart

    data object Idle : LevelStart

    data object Running : LevelStart

    data class Open(val sudokuId: SudokuId) : Result

    data object Failed : Result
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

    /** True after a load failed until the screen reports the error shown. */
    val loadFailed: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /** The sudoku the list scrolls to, until the screen reports it revealed. */
    val reveal: StateFlow<SudokuId?>
        field = MutableStateFlow<SudokuId?>(null)

    val state: StateFlow<SudokuLevelTabUiState> =
        flow {
            if (levelInitialized.await()) emitAll(levelStates()) else emit(SudokuLevelTabUiState(isLoading = false))
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(state.value.copy(isLoading = false, isGeneratingNextLevel = false))
            loadFailed.value = true
        }.stateInViewModel(viewModelScope, SudokuLevelTabUiState())

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
                            reveal.value = nextLevel.id
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

    fun confirmSudokuStart(
        position: Int,
        sudoku: Sudoku,
    ) {
        if (levelStart.value == LevelStart.Running) return
        if (position != 0 || !state.value.hasNextLevelToStart) {
            levelStart.value = LevelStart.Open(sudoku.id)
            return
        }
        levelStart.value = LevelStart.Running
        viewModelScope.launch { levelStart.value = saveNextLevel(sudoku) }
    }

    fun onLevelStartHandled(result: LevelStart.Result) {
        levelStart.update { if (it == result) LevelStart.Idle else it }
    }

    private suspend fun saveNextLevel(sudoku: Sudoku): LevelStart =
        runCatching {
            val levelUnsaved = getMaxSudokuLevel(size) < sudoku.modeLevel
            if (levelUnsaved) saveSudoku(sudoku)
            levelUnsaved
        }.map { levelUnsaved -> if (levelUnsaved) LevelStart.Open(sudoku.id) else LevelStart.Idle }
            .getOrElse { e -> if (e is CancellationException) throw e else LevelStart.Failed }

    fun onLoadFailureHandled() {
        loadFailed.value = false
    }

    fun onRevealHandled(sudokuId: SudokuId) {
        reveal.update { if (it == sudokuId) null else it }
    }
}
