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
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.ShareSudokuUseCase
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

/** The wrap-up of the completed sudoku. The activity shows a [Result] and then reports it handled. */
sealed interface SudokuCompletion {
    sealed interface Result : SudokuCompletion

    data object Idle : SudokuCompletion

    data object Running : SudokuCompletion

    data class Summary(
        val sudoku: Sudoku,
        val followUp: FollowUp?,
    ) : Result

    data object Failed : Result
}

@HiltViewModel
class SudokuViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getSudoku: GetSudokuUseCase,
    private val generateSudoku: GenerateSudokuUseCase,
    private val generateSudokuLevel: GenerateSudokuLevelUseCase,
    private val getMaxSudokuLevel: GetMaxSudokuLevelUseCase,
    private val queueSudokuSave: QueueSudokuSaveUseCase,
    private val shareSudoku: ShareSudokuUseCase,
    private val calculatePlayGamesSync: CalculatePlayGamesSyncUseCase,
) : ViewModel() {
    val game: StateFlow<SudokuGame>
        field = MutableStateFlow<SudokuGame>(SudokuGame.Loading)

    val share: StateFlow<SudokuShare>
        field = MutableStateFlow<SudokuShare>(SudokuShare.Idle)

    val completion: StateFlow<SudokuCompletion>
        field = MutableStateFlow<SudokuCompletion>(SudokuCompletion.Idle)

    /** The Play Games sync of the completed sudoku. The activity applies it and then reports it handled. */
    val playGamesSync: StateFlow<PlayGamesSync?>
        field = MutableStateFlow<PlayGamesSync?>(null)

    private var wrapUp: Job? = null

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
        if (completion.value != SudokuCompletion.Idle) return
        val sudoku = (game.value as? SudokuGame.Playing ?: return).sudoku
        game.value = SudokuGame.Restarting
        viewModelScope.launch {
            wrapUp?.join()
            sudoku.reset()
            queueSudokuSave(sudoku).await()
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
            queueSudokuSave(next).await()
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

    fun onCompleted() {
        val playing = game.value as? SudokuGame.Playing
        if (playing == null || !playing.sudoku.completed || completion.value != SudokuCompletion.Idle) return
        val completed = playing.sudoku
        completion.value = SudokuCompletion.Running
        wrapUp =
            viewModelScope.launch {
                val result = summaryOf(completed)
                completion.value = result
                if (result is SudokuCompletion.Summary) playGamesSync.value = playGamesSyncOf(completed)
            }
    }

    fun onCompletionHandled(result: SudokuCompletion.Result) {
        completion.update { if (it == result) SudokuCompletion.Idle else it }
    }

    fun onPlayGamesSyncHandled(sync: PlayGamesSync) {
        playGamesSync.update { if (it == sync) null else it }
    }

    fun onPaused() = savePlayingSudokuProgress()

    fun onProgressChanged() = savePlayingSudokuProgress()

    private fun savePlayingSudokuProgress() {
        (game.value as? SudokuGame.Playing)?.let { saveSudokuProgress(it.sudoku) }
    }

    private fun saveSudokuProgress(sudoku: Sudoku) = queueSudokuSave(sudoku, onlyUpdate = true)

    private suspend fun summaryOf(completed: Sudoku): SudokuCompletion.Result {
        val saved = orOnFailure(false) { saveSudokuProgress(completed).await().let { true } }
        return if (saved) SudokuCompletion.Summary(completed, orOnFailure(null) { followUpOf(completed) }) else SudokuCompletion.Failed
    }

    private suspend fun playGamesSyncOf(completed: Sudoku): PlayGamesSync =
        orOnFailure(PlayGamesSync()) { calculatePlayGamesSync(completed) }

    private suspend inline fun <T> orOnFailure(
        fallback: T,
        block: () -> T,
    ): T =
        runCatching(block).getOrElse {
            currentCoroutineContext().ensureActive()
            fallback
        }

    private suspend fun followUpOf(completed: Sudoku): FollowUp? =
        when {
            completed.isSudokuLevel -> FollowUp.NEXT_LEVEL.takeIf { getMaxSudokuLevel(completed.size) == completed.modeLevel }
            completed.isNormalSudoku -> FollowUp.NEW_GAME
            else -> null
        }
}
