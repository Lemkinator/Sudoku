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

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.ShareSudokuUseCase
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The sudoku the activity plays. The activity starts a [Ready] sudoku and then reports it started. */
sealed interface SudokuGame {
    data object Loading : SudokuGame

    data object NotFound : SudokuGame

    data object Restarting : SudokuGame

    data object Generating : SudokuGame

    data class Ready(val sudoku: Sudoku) : SudokuGame

    data class Playing(val sudoku: Sudoku) : SudokuGame
}

/** The sudoku that follows a completed one. */
enum class FollowUp {
    NEW_GAME,
    NEXT_LEVEL,
}

/** The export of a sudoku file to share. The activity shares a [Result] and then reports it handled. */
sealed interface SudokuShare {
    sealed interface Result : SudokuShare

    data object Idle : SudokuShare

    data object Running : SudokuShare

    data class File(val uri: Uri) : Result
}

@HiltViewModel
class SudokuViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getSudoku: GetSudokuUseCase,
    private val generateSudoku: GenerateSudokuUseCase,
    private val generateSudokuLevel: GenerateSudokuLevelUseCase,
    private val getMaxSudokuLevel: GetMaxSudokuLevelUseCase,
    private val saveSudoku: SaveSudokuUseCase,
    private val shareSudoku: ShareSudokuUseCase,
    private val calculatePlayGamesSync: CalculatePlayGamesSyncUseCase,
) : ViewModel() {
    val game: StateFlow<SudokuGame>
        field = MutableStateFlow<SudokuGame>(SudokuGame.Loading)

    val share: StateFlow<SudokuShare>
        field = MutableStateFlow<SudokuShare>(SudokuShare.Idle)

    init {
        val id = savedStateHandle.get<String>(KEY_SUDOKU_ID)
        if (id == null) {
            game.value = SudokuGame.NotFound
        } else {
            viewModelScope.launch { game.value = getSudoku(SudokuId(id))?.let(SudokuGame::Ready) ?: SudokuGame.NotFound }
        }
    }

    fun onGameStarted(ready: SudokuGame.Ready) {
        game.update { if (it == ready) SudokuGame.Playing(ready.sudoku) else it }
    }

    fun onRestart() {
        val sudoku = (game.value as? SudokuGame.Playing ?: return).sudoku
        game.value = SudokuGame.Restarting
        sudoku.reset()
        viewModelScope.launch {
            saveSudoku(sudoku)
            game.value = SudokuGame.Ready(sudoku)
        }
    }

    fun onFollowUp(followUp: FollowUp) {
        val completed = (game.value as? SudokuGame.Playing ?: return).sudoku.takeIf { it.completed } ?: return
        game.value = SudokuGame.Generating
        viewModelScope.launch {
            val next =
                when (followUp) {
                    FollowUp.NEW_GAME -> generateSudoku(completed.size, completed.difficulty)
                    FollowUp.NEXT_LEVEL -> generateSudokuLevel(completed.size, completed.modeLevel + 1)
                }
            saveSudoku(next)
            savedStateHandle[KEY_SUDOKU_ID] = next.id.value
            game.value = SudokuGame.Ready(next)
        }
    }

    fun onShare(sudoku: Sudoku) {
        if (share.value != SudokuShare.Idle) return
        share.value = SudokuShare.Running
        viewModelScope.launch { share.value = SudokuShare.File(shareSudoku(sudoku)) }
    }

    fun onShareHandled(result: SudokuShare.Result) {
        share.update { if (it == result) SudokuShare.Idle else it }
    }

    suspend fun isMaxSudokuLevel(
        size: SudokuSize,
        level: Int,
    ): Boolean = getMaxSudokuLevel(size) == level

    suspend fun saveSudokuProgress(sudoku: Sudoku) = saveSudoku(sudoku, onlyUpdate = true)

    suspend fun syncPlayGames(sudoku: Sudoku? = null): PlayGamesSync = calculatePlayGamesSync(sudoku)
}
