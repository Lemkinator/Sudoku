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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.ClearPlayGamesWinsUseCase
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetPendingPlayGamesSyncUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.QueuePlayGamesWinUseCase
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.ShareSudokuUseCase
import de.lemke.sudoku.domain.model.BoardMove
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Highlighting
import de.lemke.sudoku.domain.model.MoveResult
import de.lemke.sudoku.domain.model.PendingPlayGamesSync
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY_ERROR_LIMIT
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_LEVEL_ERROR_LIMIT
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import java.io.Serializable
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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

    data class Playing(
        val sudoku: Sudoku,
        val kind: SudokuKind,
        val errorLimit: Int,
        val phase: Phase,
        val running: Boolean,
        val lastMove: MoveResult?,
    ) : SudokuGame {
        val ended: Boolean get() = phase != Phase.Playing
        val canRestart: Boolean get() = restartPolicy(kind, phase)
    }
}

/** What a sudoku is played as. A [Daily] sudoku carries the day it was created for. */
sealed interface SudokuKind {
    data object Normal : SudokuKind

    data object Level : SudokuKind

    data class Daily(val date: LocalDate) : SudokuKind
}

/** How far the played sudoku is. [Expired] is a daily sudoku of an earlier day, which can no longer be played. */
enum class Phase {
    Playing,
    Won,
    Lost,
    Expired,
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

    data class File(val file: java.io.File) : Result
}

/**
 * The wrap-up of the completed sudoku. The winning move starts it and it is [Running] until the sudoku is saved and its follow-up
 * is known, and no restart can overwrite the sudoku before then. The winning move also queues the win durably for the Play Games
 * sync, which stays queued until an activity applied it. The activity shows a [Summary] until the player dismisses it, and reports
 * a [Failed] wrap-up handled once it showed it.
 */
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

/** Whether a sudoku of [kind] in [phase] can be restarted, which erases its record. */
internal fun restartPolicy(
    kind: SudokuKind,
    phase: Phase,
): Boolean =
    when (kind) {
        SudokuKind.Normal -> {
            when (phase) {
                Phase.Won, Phase.Lost -> true
                Phase.Playing, Phase.Expired -> false
            }
        }

        SudokuKind.Level, is SudokuKind.Daily -> {
            when (phase) {
                Phase.Lost -> true
                Phase.Playing, Phase.Won, Phase.Expired -> false
            }
        }
    }

/** A shown completion summary the player has not dismissed yet, kept in the saved state. */
private data class PendingSummary(
    val followUp: FollowUp?,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1
    }
}

private fun Sudoku.kind(): SudokuKind =
    when (val mode = info.mode) {
        is GameMode.Daily -> SudokuKind.Daily(mode.date)
        is GameMode.Level -> SudokuKind.Level
        GameMode.Normal -> SudokuKind.Normal
    }

private const val KEY_PENDING_SUMMARY = "pending_summary"

@HiltViewModel
class SudokuViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getSudoku: GetSudokuUseCase,
    private val generateSudoku: GenerateSudokuUseCase,
    private val generateSudokuLevel: GenerateSudokuLevelUseCase,
    private val getMaxSudokuLevel: GetMaxSudokuLevelUseCase,
    private val queueSudokuSave: QueueSudokuSaveUseCase,
    private val shareSudoku: ShareSudokuUseCase,
    private val queuePlayGamesWin: QueuePlayGamesWinUseCase,
    private val getPendingPlayGamesSync: GetPendingPlayGamesSyncUseCase,
    private val clearPlayGamesWins: ClearPlayGamesWinsUseCase,
    private val userSettings: UserSettings,
    private val clock: Clock,
    private val timeSource: TimeSource.WithComparableMarks,
) : ViewModel() {
    val game: StateFlow<SudokuGame>
        field = MutableStateFlow<SudokuGame>(SudokuGame.Loading)

    val share: StateFlow<SudokuShare>
        field = MutableStateFlow<SudokuShare>(SudokuShare.Idle)

    val completion: StateFlow<SudokuCompletion>
        field = MutableStateFlow<SudokuCompletion>(SudokuCompletion.Idle)

    /** The Play Games sync of the queued wins. The activity applies it and then reports it handled, which dequeues the wins. */
    val playGamesSync: StateFlow<PendingPlayGamesSync?>
        field = MutableStateFlow<PendingPlayGamesSync?>(null)

    private var wrapUp: Job? = null
    private var ticker: Job? = null
    private lateinit var nextTick: ComparableTimeMark
    private var untilNextTick: Duration = 1.seconds

    init {
        viewModelScope.launch { playGamesSync.compareAndSet(null, orOnFailure(null) { getPendingPlayGamesSync() }) }
        val id = savedStateHandle.get<String>(KEY_SUDOKU_ID)
        if (id == null) {
            game.value = SudokuGame.NotFound
        } else {
            viewModelScope.launch {
                val sudoku = getSudoku(SudokuId(id))
                game.value = sudoku?.let(SudokuGame::Ready) ?: SudokuGame.NotFound
                restorePendingSummary(sudoku)
            }
        }
    }

    fun onGameStarted(ready: SudokuGame.Ready) {
        val kind = ready.sudoku.kind()
        val expired = kind is SudokuKind.Daily && kind.date != LocalDate.now(clock)
        if (game.compareAndSet(ready, playingOf(ready.sudoku, kind, expired))) untilNextTick = 1.seconds
    }

    fun onRestart() {
        val playing = game.value as? SudokuGame.Playing
        if (playing == null || !playing.canRestart || completion.value != SudokuCompletion.Idle) return
        val sudoku = playing.sudoku
        game.value = SudokuGame.Restarting
        updateTicker()
        viewModelScope.launch {
            wrapUp?.join()
            val restarted = sudoku.restarted()
            queueSudokuSave(restarted).await()
            game.value = SudokuGame.Ready(restarted)
        }
    }

    fun onFollowUp(followUp: FollowUp) {
        val completed = (game.value as? SudokuGame.Playing ?: return).sudoku.takeIf { it.completed } ?: return
        game.value = SudokuGame.Generating
        updateTicker()
        viewModelScope.launch {
            val next =
                when (followUp) {
                    FollowUp.NEW_GAME -> generateSudoku(completed.info.size, completed.info.difficulty)
                    FollowUp.NEXT_LEVEL -> generateSudokuLevel(completed.info.size, (completed.info.mode as GameMode.Level).number + 1)
                }
            queueSudokuSave(next).await()
            savedStateHandle[KEY_SUDOKU_ID] = next.info.id.value
            game.value = SudokuGame.Ready(next)
        }
    }

    fun onShare(sudoku: Sudoku) {
        if (share.value != SudokuShare.Idle) return
        share.value = SudokuShare.Running
        viewModelScope.launch { share.value = SudokuShare.File(shareSudoku(sudoku)) }
    }

    fun onShareHandled(result: SudokuShare.Result) {
        share.compareAndSet(result, SudokuShare.Idle)
    }

    fun onCompletionDismissed(summary: SudokuCompletion.Summary) {
        if (completion.compareAndSet(summary, SudokuCompletion.Idle)) savedStateHandle.remove<PendingSummary>(KEY_PENDING_SUMMARY)
    }

    fun onCompletionHandled(failure: SudokuCompletion.Failed) {
        completion.compareAndSet(failure, SudokuCompletion.Idle)
    }

    fun onPlayGamesSyncHandled(sync: PendingPlayGamesSync) {
        clearPlayGamesWins(sync.wins)
        playGamesSync.compareAndSet(sync, null)
    }

    /** Moves a playing sudoku to won or lost, and starts the timer of one that is still playable. */
    fun onResumed() {
        updatePlaying { it.refreshed().copy(running = true) }
        updateTicker()
    }

    fun onPaused() {
        updatePlaying { it.copy(running = false) }
        updateTicker()
        (game.value as? SudokuGame.Playing)?.takeIf { it.phase == Phase.Playing }?.let { queueSudokuSave.launch(it.sudoku) }
    }

    /** Applies [move] to a running sudoku and queues one save; returns the sudoku after the move, or null when it was refused. */
    fun onMove(move: BoardMove): Sudoku? {
        val playing = (game.value as? SudokuGame.Playing)?.takeIf { it.running && it.phase == Phase.Playing }
        val played = playing?.let { it.sudoku.play(move) }
        if (playing != null && played != null) {
            game.value = playingOf(played.sudoku, playing.kind, expired = false).copy(running = true, lastMove = played.result)
            updateTicker()
            if (played.sudoku.completed) {
                queuePlayGamesWin(played.sudoku.info.id)
                wrapUp(played.sudoku)
            } else {
                queueSudokuSave.launch(played.sudoku)
            }
        }
        return played?.sudoku
    }

    fun onMoveShown(result: MoveResult) {
        updatePlaying { if (it.lastMove == result) it.copy(lastMove = null) else it }
    }

    fun onHighlightingUsed(highlighting: Highlighting) {
        updatePlaying { it.copy(sudoku = it.sudoku.withHighlightingUsed(highlighting)) }
    }

    private fun refreshPhase() {
        updatePlaying { it.refreshed() }
    }

    private fun updatePlaying(transform: (SudokuGame.Playing) -> SudokuGame.Playing) {
        game.update { if (it is SudokuGame.Playing) transform(it) else it }
    }

    private fun SudokuGame.Playing.refreshed(): SudokuGame.Playing =
        playingOf(sudoku, kind, expired = phase == Phase.Expired).copy(running = running, lastMove = lastMove)

    private fun updateTicker() {
        val playing = game.value as? SudokuGame.Playing
        if (playing?.running == true && playing.phase == Phase.Playing) {
            if (ticker == null) {
                nextTick = timeSource.markNow() + untilNextTick
                ticker = viewModelScope.launch { tickEverySecond() }
            }
        } else {
            ticker?.let {
                it.cancel()
                untilNextTick = -nextTick.elapsedNow()
            }
            ticker = null
        }
    }

    private suspend fun tickEverySecond() {
        while (true) {
            delay(-nextTick.elapsedNow())
            nextTick += 1.seconds
            updatePlaying { it.copy(sudoku = it.sudoku.tick(LocalDateTime.now(clock))) }
        }
    }

    private fun playingOf(
        sudoku: Sudoku,
        kind: SudokuKind,
        expired: Boolean,
    ): SudokuGame.Playing {
        val errorLimit =
            when (kind) {
                is SudokuKind.Daily -> MODE_DAILY_ERROR_LIMIT
                SudokuKind.Level -> MODE_LEVEL_ERROR_LIMIT
                SudokuKind.Normal -> userSettings.errorLimit
            }
        return SudokuGame.Playing(sudoku, kind, errorLimit, phaseOf(sudoku, errorLimit, expired), running = false, lastMove = null)
    }

    private fun phaseOf(
        sudoku: Sudoku,
        errorLimit: Int,
        expired: Boolean,
    ): Phase =
        when {
            sudoku.completed -> Phase.Won
            expired -> Phase.Expired
            sudoku.info.errorLimitReached(errorLimit) -> Phase.Lost
            else -> Phase.Playing
        }

    private fun wrapUp(completed: Sudoku) {
        completion.value = SudokuCompletion.Running
        wrapUp =
            viewModelScope.launch {
                when (val result = summaryOf(completed)) {
                    is SudokuCompletion.Summary -> {
                        savedStateHandle[KEY_PENDING_SUMMARY] = PendingSummary(result.followUp)
                        completion.value = result
                        playGamesSync.value = pendingPlayGamesSync()
                    }

                    SudokuCompletion.Failed -> {
                        clearPlayGamesWins(setOf(completed.info.id))
                        completion.value = result
                    }
                }
            }
    }

    private suspend fun summaryOf(completed: Sudoku): SudokuCompletion.Result {
        val saved = orOnFailure(false) { queueSudokuSave(completed).await().let { true } }
        return if (saved) SudokuCompletion.Summary(completed, orOnFailure(null) { followUpOf(completed) }) else SudokuCompletion.Failed
    }

    private fun restorePendingSummary(sudoku: Sudoku?) {
        val pending = savedStateHandle.get<PendingSummary>(KEY_PENDING_SUMMARY) ?: return
        if (sudoku?.completed == true) {
            completion.value = SudokuCompletion.Summary(sudoku, pending.followUp)
        } else {
            savedStateHandle.remove<PendingSummary>(KEY_PENDING_SUMMARY)
        }
    }

    private suspend fun pendingPlayGamesSync(): PendingPlayGamesSync =
        orOnFailure(null) { getPendingPlayGamesSync() } ?: PendingPlayGamesSync(emptySet(), PlayGamesSync())

    private suspend inline fun <T> orOnFailure(
        fallback: T,
        block: () -> T,
    ): T =
        runCatching(block).getOrElse {
            currentCoroutineContext().ensureActive()
            fallback
        }

    private suspend fun followUpOf(completed: Sudoku): FollowUp? =
        when (val mode = completed.info.mode) {
            is GameMode.Level -> FollowUp.NEXT_LEVEL.takeIf { getMaxSudokuLevel(completed.info.size) == mode.number }
            GameMode.Normal -> FollowUp.NEW_GAME
            is GameMode.Daily -> null
        }
}
