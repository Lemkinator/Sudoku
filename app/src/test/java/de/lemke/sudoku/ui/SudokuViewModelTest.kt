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
import androidx.lifecycle.viewModelScope
import de.lemke.commonutils.clearForTest
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.R
import de.lemke.sudoku.data.FakeSudokuShareFiles
import de.lemke.sudoku.data.SudokuShareFiles
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GenerateFieldsUseCase
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.ShareSudokuUseCase
import de.lemke.sudoku.domain.model.BoardMove
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.game.BoardInput
import de.lemke.sudoku.ui.game.InputTap
import de.lemke.sudoku.ui.game.Selection
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private const val KEY_BOARD_INPUT = "board_input"

private fun SavedStateHandle.restoredAfterProcessDeath(): SavedStateHandle = SavedStateHandle(keys().associateWith { get<Any>(it) })

private fun FakeSudokusRepository.lastSaved(): Sudoku = saves.last()

private fun Sudoku.withCell(
    index: Int,
    cell: Cell,
): Sudoku = copy(board = board.with(index, cell))

private fun Sudoku.withValue(
    index: Int,
    value: Int?,
): Sudoku = withCell(index, board[index].copy(value = value))

private fun Sudoku.withErrors(errors: Int): Sudoku = copy(info = info.copy(errorsMade = errors))

private fun playingOf(
    sudoku: Sudoku,
    phase: Phase = Phase.Playing,
    kind: SudokuKind = SudokuKind.Normal,
    running: Boolean = false,
): SudokuGame.Playing = SudokuGame.Playing(sudoku, kind, 3, phase, running, lastMove = null)

private fun SudokuViewModel.playingGame(): SudokuGame.Playing = game.value as SudokuGame.Playing

private fun winUnlocks(stopwatchAchievement: Int): List<Int> =
    listOf(R.string.achievement_first_win, R.string.achievement_no_hints, R.string.achievement_i_am_speed, stopwatchAchievement)

@OptIn(ExperimentalCoroutinesApi::class)
class SudokuViewModelTest : ShouldSpec(
    {
        val clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC)
        lateinit var repository: FakeSudokusRepository
        lateinit var settingsScope: CoroutineScope
        lateinit var userSettings: UserSettings
        var maxLevelReads = 0
        var syncCalculations = 0

        fun mainScheduler() = StandardTestDispatcher().scheduler

        beforeEach {
            repository = FakeSudokusRepository()
            settingsScope = CoroutineScope(Job() + UnconfinedTestDispatcher())
            userSettings = UserSettings(FakeSharedPreferences(), settingsScope)
            maxLevelReads = 0
            syncCalculations = 0
            repository.beforeGetMaxSudokuLevel = { maxLevelReads++ }
            repository.beforeGetCompletedSummaries = { syncCalculations++ }
        }

        afterEach { settingsScope.cancel() }

        fun viewModel(
            savedStateHandle: SavedStateHandle = SavedStateHandle(),
            generateDispatcher: CoroutineDispatcher = Dispatchers.Main,
            shareFiles: SudokuShareFiles = FakeSudokuShareFiles(File("game.sudoku")),
            shareDispatcher: CoroutineDispatcher = Dispatchers.Main,
            timeSource: TimeSource.WithComparableMarks = mainScheduler().timeSource,
        ): SudokuViewModel {
            val dispatcher = Dispatchers.Main
            val generateFields = GenerateFieldsUseCase(generateDispatcher, PatternSolvedBoardGenerator())
            return SudokuViewModel(
                savedStateHandle,
                GetSudokuUseCase(repository, dispatcher),
                GenerateSudokuUseCase(generateFields, generateDispatcher),
                GenerateSudokuLevelUseCase(generateFields, generateDispatcher),
                GetMaxSudokuLevelUseCase(repository, dispatcher),
                QueueSudokuSaveUseCase(repository, CoroutineScope(SupervisorJob()), dispatcher),
                ShareSudokuUseCase(shareFiles, clock, shareDispatcher),
                CalculatePlayGamesSyncUseCase(repository, dispatcher),
                userSettings,
                clock,
                timeSource,
            )
        }

        fun playing(
            sudoku: Sudoku,
            savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.info.id.value)),
            generateDispatcher: CoroutineDispatcher = Dispatchers.Main,
            timeSource: TimeSource.WithComparableMarks = mainScheduler().timeSource,
        ): SudokuViewModel {
            repository.store(sudoku)
            return viewModel(
                savedStateHandle,
                generateDispatcher,
                timeSource = timeSource,
            ).also { it.onGameStarted(SudokuGame.Ready(sudoku)) }
        }

        fun playingToWin(
            completed: Sudoku,
            savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value)),
        ): SudokuViewModel = playing(completed.withValue(1, null), savedStateHandle).also { it.onResumed() }

        fun SudokuViewModel.win(completed: Sudoku): Sudoku = checkNotNull(onMove(BoardMove.Enter(1, completed.board[1].solution)))

        should("the sudoku of the passed id is ready to start") {
            val sudoku = testSudoku()
            repository.store(sudoku)

            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.info.id.value)))

            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
        }

        should("a passed id without a saved sudoku is not found") {
            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to "missing")))

            viewModel.game.value shouldBe SudokuGame.NotFound
        }

        should("a missing id is not found without a lookup") {
            repository.store(testSudoku())

            val viewModel = viewModel()

            viewModel.game.value shouldBe SudokuGame.NotFound
        }

        should("onGameStarted moves the ready sudoku to playing") {
            val sudoku = testSudoku()

            playing(sudoku).game.value shouldBe playingOf(sudoku)
        }

        should("onGameStarted for another sudoku keeps the ready one") {
            val sudoku = testSudoku()
            repository.store(sudoku)
            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.info.id.value)))

            viewModel.onGameStarted(SudokuGame.Ready(testSudoku()))

            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
        }

        should("onRestart clears the entries, saves the whole sudoku and makes it ready again") {
            val sudoku = testSudoku(completed = true)
            val viewModel = playing(sudoku)

            viewModel.onRestart()

            val restarted = sudoku.restarted()
            restarted.board.cells.count { it.value == null } shouldBe 10
            repository.saves shouldBe listOf(restarted)
            viewModel.game.value shouldBe SudokuGame.Ready(restarted)
        }

        should("onRestart reports restarting until the sudoku is saved, and a second restart saves once") {
            val sudoku = testSudoku(completed = true)
            val viewModel = playing(sudoku)
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { saved.await() }

            viewModel.onRestart()
            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Restarting
            saved.complete(Unit)
            viewModel.game.value shouldBe SudokuGame.Ready(sudoku.restarted())
            repository.saves shouldBe listOf(sudoku.restarted())
        }

        should("onRestart before a sudoku plays does nothing") {
            val viewModel = viewModel()

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.NotFound
            repository.saves shouldBe emptyList()
        }

        should("onRestart while the completed sudoku is saved keeps it and the pending wrap-up") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { if (it.completed) saved.await() }
            val won = viewModel.win(completed)

            viewModel.onRestart()

            viewModel.playingGame().sudoku shouldBe won
            viewModel.playingGame().phase shouldBe Phase.Won
            saved.complete(Unit)
            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, FollowUp.NEW_GAME)
            repository.saves shouldBe listOf(won)
        }

        should("onRestart while the completion summary is pending keeps the completed sudoku") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            val won = viewModel.win(completed)

            viewModel.onRestart()

            viewModel.playingGame().sudoku shouldBe won
            viewModel.playingGame().phase shouldBe Phase.Won
            repository.saves shouldBe listOf(won)
        }

        should("onRestart during the Play Games sync resets the sudoku only once the sync is calculated") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            val sync = CompletableDeferred<Unit>()
            repository.beforeGetCompletedSummaries = { sync.await() }
            val won = viewModel.win(completed)
            viewModel.onCompletionDismissed(SudokuCompletion.Summary(won, FollowUp.NEW_GAME))

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Restarting
            repository.saves shouldBe listOf(won)
            sync.complete(Unit)
            viewModel.game.value shouldBe SudokuGame.Ready(won.restarted())
            repository.saves shouldBe listOf(won, won.restarted())
        }

        should("a new game follows with the size and difficulty of the completed sudoku and becomes the current one") {
            val completed = testSudoku(SudokuSize.NINE, difficulty = Difficulty.HARD, completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value))
            val viewModel = playing(completed, savedStateHandle)

            viewModel.onFollowUp(FollowUp.NEW_GAME)

            val next = repository.lastSaved()
            viewModel.game.value shouldBe SudokuGame.Ready(next)
            savedStateHandle.get<String>(KEY_SUDOKU_ID) shouldBe next.info.id.value
            (next.info.size to next.info.difficulty) shouldBe (SudokuSize.NINE to Difficulty.HARD)
            next.info.mode shouldBe GameMode.Normal
            repository.saves shouldBe listOf(next)
        }

        should("the next level follows the completed level") {
            val completed = testSudoku(SudokuSize.FOUR, mode = GameMode.Level(7), completed = true)
            val viewModel = playing(completed)

            viewModel.onFollowUp(FollowUp.NEXT_LEVEL)

            val next = repository.lastSaved()
            viewModel.game.value shouldBe SudokuGame.Ready(next)
            (next.info.size to next.info.mode) shouldBe (SudokuSize.FOUR to GameMode.Level(8))
            repository.saves shouldBe listOf(next)
        }

        should("a follow-up of an unfinished sudoku does nothing") {
            val unfinished = testSudoku()
            val viewModel = playing(unfinished)

            viewModel.onFollowUp(FollowUp.NEW_GAME)

            viewModel.game.value shouldBe playingOf(unfinished)
            repository.saves shouldBe emptyList()
        }

        should("a second follow-up while one generates generates once") {
            val completed = testSudoku(completed = true)
            val generating = StandardTestDispatcher()
            val viewModel = playing(completed, generateDispatcher = generating)

            viewModel.onFollowUp(FollowUp.NEW_GAME)
            viewModel.onFollowUp(FollowUp.NEW_GAME)

            viewModel.game.value shouldBe SudokuGame.Generating
            generating.scheduler.advanceUntilIdle()
            val next = repository.lastSaved()
            viewModel.game.value shouldBe SudokuGame.Ready(next)
            repository.saves shouldBe listOf(next)
        }

        should("onShare writes the sudoku file and reports it, and a second share while it runs writes once") {
            val file = File("game.sudoku")
            val shareFiles = FakeSudokuShareFiles(file)
            val writing = StandardTestDispatcher()
            val viewModel = viewModel(shareFiles = shareFiles, shareDispatcher = writing)
            val sudoku = testSudoku()

            viewModel.onShare(sudoku)
            viewModel.onShare(sudoku)

            viewModel.share.value shouldBe SudokuShare.Running
            writing.scheduler.advanceUntilIdle()
            viewModel.share.value shouldBe SudokuShare.File(file)
            shareFiles.writes.map { it.sudokuId } shouldBe listOf(sudoku.info.id)
        }

        should("onShareHandled returns to idle, and a stale handled call keeps the pending file") {
            val file = File("game.sudoku")
            val viewModel = viewModel(shareFiles = FakeSudokuShareFiles(file))
            val sudoku = testSudoku()
            viewModel.onShare(sudoku)

            viewModel.onShareHandled(SudokuShare.File(File("other.sudoku")))
            viewModel.share.value shouldBe SudokuShare.File(file)
            viewModel.onShareHandled(SudokuShare.File(file))
            viewModel.share.value shouldBe SudokuShare.Idle
        }

        should("onPaused saves the playing sudoku as it was at the pause") {
            val sudoku = testSudoku().withValue(1, 2)
            val viewModel = playing(sudoku)

            viewModel.onPaused()

            repository.saves shouldBe listOf(sudoku)
        }

        should("onPaused of a won sudoku saves nothing") {
            val viewModel = playing(testSudoku(completed = true))

            viewModel.onPaused()

            viewModel.playingGame().phase shouldBe Phase.Won
            repository.saves shouldBe emptyList()
        }

        should("onPaused of a lost sudoku saves nothing") {
            val viewModel = playing(testSudoku().withErrors(3))

            viewModel.onPaused()

            viewModel.playingGame().phase shouldBe Phase.Lost
            repository.saves shouldBe emptyList()
        }

        should("onPaused stops the running state of the playing sudoku") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()

            viewModel.onPaused()

            viewModel.playingGame().running shouldBe false
        }

        should("an accepted move saves the moved sudoku") {
            val sudoku = testSudoku()
            val viewModel = playing(sudoku)
            viewModel.onResumed()

            viewModel.onMove(BoardMove.Enter(1, 2)) shouldBe viewModel.playingGame().sudoku

            repository.saves shouldBe listOf(viewModel.playingGame().sudoku)
            repository.saves
                .single()
                .board[1]
                .value shouldBe 2
        }

        should("one correct move with three neighbor notes saves once") {
            val neighbors = listOf(2, 4, 5)
            val sudoku =
                neighbors.fold(testSudoku()) { current, index ->
                    current.withCell(index, current.board[index].copy(notes = NoteMask.of(2)))
                }
            val viewModel = playing(sudoku)
            viewModel.onResumed()

            viewModel.onMove(BoardMove.Enter(1, 2))

            repository.saves.size shouldBe 1
            val saved = repository.saves.single()
            saved.board[1].value shouldBe 2
            neighbors.map { saved.board[it].notes } shouldBe List(3) { NoteMask.EMPTY }
        }

        should("a pause after three queued moves persists the third snapshot") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()
            val firstSaved = CompletableDeferred<Unit>()
            repository.beforeSave = { firstSaved.await() }

            viewModel.onMove(BoardMove.Enter(1, 2))
            viewModel.onMove(BoardMove.Enter(2, 3))
            viewModel.onMove(BoardMove.Enter(7, 2))
            viewModel.onPaused()
            firstSaved.complete(Unit)

            repository.saves.size shouldBe 2
            repository.saves
                .first()
                .board[1]
                .value shouldBe 2
            repository
                .getSudokuById(
                    repository.saves
                        .last()
                        .info.id,
                )!!
                .board.cells
                .map { it.value }
                .slice(listOf(1, 2, 7)) shouldBe
                listOf(2, 3, 2)
        }

        should("a rejected move saves nothing and keeps the game") {
            val sudoku = testSudoku()
            val viewModel = playing(sudoku)
            viewModel.onResumed()

            viewModel.onMove(BoardMove.Enter(0, 2)).shouldBeNull()

            viewModel.game.value shouldBe playingOf(sudoku, running = true)
            repository.saves shouldBe emptyList()
        }

        should("a move before a sudoku plays is rejected") {
            val viewModel = viewModel()

            viewModel.onMove(BoardMove.Enter(1, 2)).shouldBeNull()

            viewModel.game.value shouldBe SudokuGame.NotFound
            repository.saves shouldBe emptyList()
        }

        should("a shown move result before a sudoku plays changes nothing") {
            val viewModel = viewModel()
            val result = testSudoku().play(BoardMove.Enter(1, 2))!!.result

            viewModel.onMoveShown(result)

            viewModel.game.value shouldBe SudokuGame.NotFound
        }

        should("a shown move result of an older move keeps the last move") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()
            val older = testSudoku().play(BoardMove.Enter(1, 2))!!.result
            viewModel.onMove(BoardMove.Enter(2, 3))

            viewModel.onMoveShown(older)

            viewModel.playingGame().lastMove?.move shouldBe BoardMove.Enter(2, 3)
        }

        should("a tap before a sudoku plays changes nothing") {
            val viewModel = viewModel()

            viewModel.onTap(InputTap.Cell(1))

            viewModel.game.value shouldBe SudokuGame.NotFound
            viewModel.input.value shouldBe BoardInput()
        }

        should("a move while paused is ignored") {
            val sudoku = testSudoku()
            val viewModel = playing(sudoku)
            viewModel.onResumed()
            viewModel.onPaused()
            repository.saves.size shouldBe 1

            viewModel.onMove(BoardMove.Enter(1, 2)).shouldBeNull()

            viewModel
                .playingGame()
                .sudoku.board[1]
                .value shouldBe null
            repository.saves.size shouldBe 1
        }

        should("a move before the sudoku resumed is ignored") {
            val viewModel = playing(testSudoku())

            viewModel.onMove(BoardMove.Enter(1, 2)).shouldBeNull()

            viewModel
                .playingGame()
                .sudoku.board[1]
                .value shouldBe null
            repository.saves shouldBe emptyList()
        }

        should("a move on a sudoku that is not playing is ignored") {
            val sudoku = testSudoku()
            repository.store(sudoku)
            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.info.id.value)))

            viewModel.onMove(BoardMove.Enter(1, 2)).shouldBeNull()

            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
            repository.saves shouldBe emptyList()
        }

        should("a wrong digit counts an error and keeps the sudoku playing below the limit") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()

            viewModel.onMove(BoardMove.Enter(1, 4))

            viewModel
                .playingGame()
                .sudoku.info.errorsMade shouldBe 1
            viewModel.playingGame().phase shouldBe Phase.Playing
        }

        should("the third error with limit 3 moves the phase to Lost") {
            val viewModel = playing(testSudoku().withErrors(2))
            viewModel.onResumed()

            viewModel.onMove(BoardMove.Enter(1, 4))

            viewModel.playingGame().phase shouldBe Phase.Lost
            viewModel
                .playingGame()
                .sudoku.info.errorsMade shouldBe 3
        }

        should("the move that completes the board moves the phase to Won") {
            val almost = testSudoku(completed = true).withValue(1, null)
            val viewModel = playing(almost)
            viewModel.onResumed()

            viewModel.onMove(BoardMove.Enter(1, 2))

            viewModel.playingGame().phase shouldBe Phase.Won
            viewModel.completion.value shouldNotBe SudokuCompletion.Idle
            viewModel
                .playingGame()
                .lastMove
                ?.completed
                ?.board shouldBe true
        }

        should("onRestart right after the winning move keeps the won sudoku until its wrap-up ran") {
            val almost = testSudoku(completed = true).withValue(1, null)
            val viewModel = playing(almost)
            viewModel.onResumed()
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { saved.await() }
            val won = checkNotNull(viewModel.onMove(BoardMove.Enter(1, 2)))

            viewModel.onRestart()

            viewModel.playingGame().phase shouldBe Phase.Won
            viewModel.completion.value shouldBe SudokuCompletion.Running
            saved.complete(Unit)
            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, FollowUp.NEW_GAME)
            viewModel.onCompletionDismissed(SudokuCompletion.Summary(won, FollowUp.NEW_GAME))
            viewModel.onRestart()
            viewModel.game.value shouldBe SudokuGame.Ready(won.restarted())
            repository.saves shouldBe listOf(won, won.restarted())
            syncCalculations shouldBe 1
        }

        should("lastMove holds the result of the move until onMoveShown clears it") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()
            viewModel.onMove(BoardMove.Enter(1, 2))
            val result = checkNotNull(viewModel.playingGame().lastMove)
            result.move shouldBe BoardMove.Enter(1, 2)
            result.changed shouldBe setOf(1)

            viewModel.onMoveShown(result)

            viewModel.playingGame().lastMove shouldBe null
        }

        should("onMoveShown of an older result keeps the newer last move") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()
            viewModel.onMove(BoardMove.Enter(1, 2))
            val first = checkNotNull(viewModel.playingGame().lastMove)
            viewModel.onMove(BoardMove.Enter(2, 3))
            val second = checkNotNull(viewModel.playingGame().lastMove)

            viewModel.onMoveShown(first)

            viewModel.playingGame().lastMove shouldBe second
        }

        should("selecting a cell marks the regional highlighting as used") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()

            viewModel.onTap(InputTap.Cell(1))

            viewModel
                .playingGame()
                .sudoku.info.regionalHighlightingUsed shouldBe true
            viewModel
                .playingGame()
                .sudoku.info.numberHighlightingUsed shouldBe false
        }

        should("selecting a digit marks the number highlighting as used") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()

            viewModel.onTap(InputTap.Digit(2))

            viewModel
                .playingGame()
                .sudoku.info.numberHighlightingUsed shouldBe true
            viewModel
                .playingGame()
                .sudoku.info.regionalHighlightingUsed shouldBe false
        }

        should("selecting a cell with a value marks both highlightings as used") {
            val viewModel = playing(testSudoku().withValue(1, 2))
            viewModel.onResumed()

            viewModel.onTap(InputTap.Cell(1))

            viewModel
                .playingGame()
                .sudoku.info.numberHighlightingUsed shouldBe true
            viewModel
                .playingGame()
                .sudoku.info.regionalHighlightingUsed shouldBe true
        }

        should("highlightings the player switched off are not marked as used") {
            userSettings.highlightRegional = false
            userSettings.highlightNumber = false
            val viewModel = playing(testSudoku().withValue(1, 2))
            viewModel.onResumed()

            viewModel.onTap(InputTap.Cell(1))
            viewModel.onTap(InputTap.Clear)
            viewModel.onTap(InputTap.Digit(2))

            viewModel
                .playingGame()
                .sudoku.info.regionalHighlightingUsed shouldBe false
            viewModel
                .playingGame()
                .sudoku.info.numberHighlightingUsed shouldBe false
        }

        should("a cell tap followed by a digit tap plays the digit and clears the selection") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()

            viewModel.onTap(InputTap.Cell(1))
            viewModel.input.value shouldBe BoardInput(Selection.Cell(1))
            viewModel.onTap(InputTap.Digit(2))

            viewModel
                .playingGame()
                .sudoku.board[1]
                .value shouldBe 2
            viewModel.input.value shouldBe BoardInput()
        }

        should("a digit tap in notes mode toggles a note") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()
            viewModel.onNotesToggled()

            viewModel.onTap(InputTap.Cell(1))
            viewModel.onTap(InputTap.Digit(3))

            viewModel
                .playingGame()
                .sudoku.board[1]
                .notes shouldBe NoteMask.of(3)
            viewModel.input.value shouldBe BoardInput(notesMode = true)
        }

        should("toggling the notes mode twice restores it") {
            val viewModel = playing(testSudoku())

            viewModel.onNotesToggled()
            viewModel.onNotesToggled()

            viewModel.input.value.notesMode shouldBe false
        }

        should("a tap while the game is paused changes nothing") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()
            viewModel.onPaused()

            viewModel.onTap(InputTap.Cell(1))

            viewModel.input.value shouldBe BoardInput()
        }

        should("a tap after the game was lost changes nothing") {
            val viewModel = playing(testSudoku().withErrors(3))
            viewModel.onResumed()

            viewModel.onTap(InputTap.Cell(1))

            viewModel.input.value shouldBe BoardInput()
        }

        should("a restart drops the selection and keeps the notes mode") {
            val viewModel = playing(testSudoku(completed = true))
            viewModel.onNotesToggled()
            viewModel.onTap(InputTap.Clear)

            viewModel.onRestart()

            viewModel.input.value shouldBe BoardInput(notesMode = true)
        }

        should("a selection survives a restore of the saved state") {
            val sudoku = testSudoku()
            val handle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.info.id.value))
            val viewModel = playing(sudoku, handle)
            viewModel.onResumed()
            viewModel.onNotesToggled()
            viewModel.onTap(InputTap.Cell(7))

            val restored = viewModel(handle.restoredAfterProcessDeath())

            restored.input.value shouldBe BoardInput(Selection.Cell(7), notesMode = true)
        }

        should("onPaused before a sudoku plays saves nothing") {
            val sudoku = testSudoku()
            repository.store(sudoku)

            viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.info.id.value))).onPaused()

            repository.saves shouldBe emptyList()
        }

        should("a move save and a restart save run one after another in the order they were started") {
            val viewModel = playing(testSudoku().withErrors(2))
            viewModel.onResumed()
            val firstSaved = CompletableDeferred<Unit>()
            repository.beforeSave = { firstSaved.await() }

            viewModel.onMove(BoardMove.Enter(1, 4))
            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Restarting
            repository.saves shouldBe emptyList()
            firstSaved.complete(Unit)
            repository.saves.map { it.info.errorsMade } shouldBe listOf(3, 0)
            viewModel.game.value shouldBe SudokuGame.Ready(repository.saves.last())
        }

        should("the save on leaving finishes after the view model is cleared") {
            val sudoku = testSudoku()
            val viewModel = playing(sudoku)
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { saved.await() }

            viewModel.onPaused()
            viewModel.clearForTest()
            saved.complete(Unit)

            viewModel.viewModelScope.isActive shouldBe false
            repository.saves shouldBe listOf(sudoku)
        }

        should("the winning move saves the completed level, offers the next level when it is the max level, then syncs Play Games") {
            val completed = testSudoku(SudokuSize.NINE, mode = GameMode.Level(5), completed = true)
            val viewModel = playingToWin(completed)
            val calls = mutableListOf<String>()
            repository.beforeSave = { calls += "save" }
            repository.beforeGetMaxSudokuLevel = { calls += "max level" }
            repository.beforeGetCompletedSummaries = { calls += "sync" }

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, FollowUp.NEXT_LEVEL)
            viewModel.playGamesSync.value
                ?.achievementUnlocks shouldBe winUnlocks(R.string.achievement_stopwatch_99)
            calls shouldBe listOf("save", "max level", "sync")
        }

        should("the winning move offers no follow-up for a level below the max level") {
            val completed = testSudoku(SudokuSize.NINE, mode = GameMode.Level(4), completed = true)
            repository.store(testSudoku(SudokuSize.NINE, mode = GameMode.Level(5)))
            val viewModel = playingToWin(completed)

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, null)
        }

        should("the winning move offers a new game for a normal sudoku without a level lookup") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, FollowUp.NEW_GAME)
            maxLevelReads shouldBe 0
        }

        should("onResumed starts the timer of a sudoku in progress") {
            val sudoku = testSudoku(updated = LocalDateTime.of(2026, 1, 15, 11, 0))
            val viewModel = playing(sudoku)

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(1000)
            mainScheduler().runCurrent()
            viewModel.onPaused()

            val played = viewModel.playingGame()
            played.sudoku.info.seconds shouldBe 1
            played.sudoku.info.updated shouldBe LocalDateTime.of(2026, 1, 15, 12, 0)
        }

        should("the timer counts 3 seconds in 3 seconds of virtual time") {
            val viewModel = playing(testSudoku())

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(3_000)
            mainScheduler().runCurrent()
            viewModel.onPaused()

            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 3
        }

        should("the timer counts a minute and 6 seconds in 66 seconds of virtual time") {
            val viewModel = playing(testSudoku())

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(66_000)
            mainScheduler().runCurrent()
            viewModel.onPaused()

            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 66
        }

        should("onResumed twice still ticks once per second") {
            val viewModel = playing(testSudoku())

            viewModel.onResumed()
            viewModel.onResumed()
            mainScheduler().advanceTimeBy(2_000)
            mainScheduler().runCurrent()
            viewModel.onPaused()

            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 2
        }

        should("onPaused stops the timer") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()
            mainScheduler().advanceTimeBy(1_000)
            mainScheduler().runCurrent()

            viewModel.onPaused()
            mainScheduler().advanceTimeBy(5_000)
            mainScheduler().runCurrent()

            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 1
        }

        should("a pause keeps the part of the second already played") {
            val viewModel = playing(testSudoku())
            viewModel.onResumed()
            mainScheduler().advanceTimeBy(2_600)
            mainScheduler().runCurrent()
            viewModel.onPaused()
            mainScheduler().advanceTimeBy(5_000)
            mainScheduler().runCurrent()

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(399)
            mainScheduler().runCurrent()
            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 2
            mainScheduler().advanceTimeBy(1)
            mainScheduler().runCurrent()

            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 3
        }

        should("a resume after a stall longer than a second fires the owed ticks at once") {
            val stalledClock = TestTimeSource()
            val viewModel = playing(testSudoku(), timeSource = stalledClock)
            viewModel.onResumed()
            mainScheduler().runCurrent()
            stalledClock += 2_500.milliseconds
            viewModel.onPaused()

            viewModel.onResumed()
            mainScheduler().runCurrent()
            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 2
            mainScheduler().advanceTimeBy(499)
            mainScheduler().runCurrent()
            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 2
            mainScheduler().advanceTimeBy(1)
            mainScheduler().runCurrent()

            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 3
        }

        should("repeated pauses and resumes lose no time") {
            val viewModel = playing(testSudoku())

            repeat(10) {
                viewModel.onResumed()
                mainScheduler().advanceTimeBy(700)
                mainScheduler().runCurrent()
                viewModel.onPaused()
            }

            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 7
        }

        should("a restarted sudoku starts its first second from zero") {
            val viewModel = playing(testSudoku().withErrors(2))
            viewModel.onResumed()
            mainScheduler().advanceTimeBy(600)
            mainScheduler().runCurrent()
            viewModel.onMove(BoardMove.Enter(1, 4))
            viewModel.onRestart()
            mainScheduler().advanceUntilIdle()

            viewModel.onGameStarted(viewModel.game.value as SudokuGame.Ready)
            viewModel.onResumed()
            mainScheduler().advanceTimeBy(999)
            mainScheduler().runCurrent()
            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 0
            mainScheduler().advanceTimeBy(1)
            mainScheduler().runCurrent()

            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 1
        }

        should("the timer stops when the sudoku is lost") {
            val viewModel = playing(testSudoku().withErrors(2))
            viewModel.onResumed()

            viewModel.onMove(BoardMove.Enter(1, 4))
            mainScheduler().advanceTimeBy(5_000)
            mainScheduler().runCurrent()

            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 0
        }

        should("onResumed starts the timer of a daily sudoku created on the clock's date") {
            val sudoku =
                testSudoku(
                    mode = GameMode.Daily(LocalDate.of(2026, 1, 15)),
                    created = LocalDateTime.of(2026, 1, 15, 0, 0),
                    updated = LocalDateTime.of(2026, 1, 15, 0, 0),
                )
            val viewModel = playing(sudoku)

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(1000)
            mainScheduler().runCurrent()
            viewModel.onPaused()

            val played = viewModel.playingGame()
            played.sudoku.info.seconds shouldBe 1
            played.sudoku.info.updated shouldBe LocalDateTime.of(2026, 1, 15, 12, 0)
        }

        should("onResumed ends a daily sudoku created before the clock's date without a timer") {
            val sudoku =
                testSudoku(
                    mode = GameMode.Daily(LocalDate.of(2026, 1, 14)),
                    created = LocalDateTime.of(2026, 1, 14, 23, 59),
                    updated = LocalDateTime.of(2026, 1, 14, 23, 59),
                )
            val viewModel = playing(sudoku)

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(1000)
            mainScheduler().runCurrent()

            viewModel.playingGame().phase shouldBe Phase.Expired
            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 0
            viewModel
                .playingGame()
                .sudoku.info.updated shouldBe LocalDateTime.of(2026, 1, 14, 23, 59)
        }

        should("onResumed ends a completed sudoku without a timer") {
            val sudoku = testSudoku(completed = true, updated = LocalDateTime.of(2026, 1, 15, 11, 0))
            val viewModel = playing(sudoku)

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(1000)
            mainScheduler().runCurrent()

            viewModel.playingGame().phase shouldBe Phase.Won
            viewModel
                .playingGame()
                .sudoku.info.seconds shouldBe 0
            viewModel
                .playingGame()
                .sudoku.info.updated shouldBe LocalDateTime.of(2026, 1, 15, 11, 0)
        }

        should("the winning move offers no follow-up for a daily sudoku") {
            val completed = testSudoku(mode = GameMode.Daily(LocalDate.now(clock)), created = LocalDateTime.now(clock), completed = true)
            val viewModel = playingToWin(completed)

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, null)
            maxLevelReads shouldBe 0
        }

        should("the winning move reports running until the sudoku is saved, and a second call wraps up once") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { saved.await() }

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Running
            saved.complete(Unit)
            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, FollowUp.NEW_GAME)
            repository.saves shouldBe listOf(won)
            syncCalculations shouldBe 1
        }

        should("the winning move offers the summary before the Play Games sync is calculated") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            val sync = CompletableDeferred<Unit>()
            repository.beforeGetCompletedSummaries = { sync.await() }

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, FollowUp.NEW_GAME)
            viewModel.playGamesSync.value shouldBe null
            sync.complete(Unit)
            viewModel.playGamesSync.value
                ?.achievementUnlocks shouldBe winUnlocks(R.string.achievement_stopwatch_44)
        }

        should("the winning move reports a failed save without a Play Games sync") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            repository.saveFailure = IllegalStateException("disk full")

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Failed
            viewModel.playGamesSync.value shouldBe null
            syncCalculations shouldBe 0
        }

        should("the winning move offers the summary without a follow-up and syncs Play Games when only the max-level read fails") {
            val completed = testSudoku(SudokuSize.NINE, mode = GameMode.Level(5), completed = true)
            val viewModel = playingToWin(completed)
            repository.beforeGetMaxSudokuLevel = { throw IllegalStateException("query failed") }

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, null)
            viewModel.playGamesSync.value
                ?.achievementUnlocks shouldBe winUnlocks(R.string.achievement_stopwatch_99)
            repository.saves shouldBe listOf(won)
        }

        should("the winning move keeps the summary and syncs nothing when the Play Games sync calculation fails") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            repository.beforeGetCompletedSummaries = { throw IllegalStateException("query failed") }

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, FollowUp.NEW_GAME)
            viewModel.playGamesSync.value shouldBe PlayGamesSync()
        }

        should("the winning move maps a cancellation of the save in an active wrap-up to failed, so a restart works again") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            repository.beforeSave = { throw CancellationException() }

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Failed
            syncCalculations shouldBe 0
            viewModel.onCompletionHandled(SudokuCompletion.Failed)
            repository.beforeSave = {}
            viewModel.onRestart()
            viewModel.game.value shouldBe SudokuGame.Ready(won.restarted())
        }

        should("the winning move falls back to an empty sync on a cancellation of the calculation in an active wrap-up") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            repository.beforeGetCompletedSummaries = { throw CancellationException() }

            val won = viewModel.win(completed)

            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, FollowUp.NEW_GAME)
            viewModel.playGamesSync.value shouldBe PlayGamesSync()
        }

        should("the summary stays until it is dismissed, and a stale dismissal keeps it") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value))
            val viewModel = playingToWin(completed, savedStateHandle)
            val won = viewModel.win(completed)

            viewModel.onCompletionDismissed(SudokuCompletion.Summary(won, null))
            viewModel.completion.value shouldBe SudokuCompletion.Summary(won, FollowUp.NEW_GAME)
            viewModel.onCompletionDismissed(SudokuCompletion.Summary(won, FollowUp.NEW_GAME))
            viewModel.completion.value shouldBe SudokuCompletion.Idle
            savedStateHandle.keys() shouldBe setOf(KEY_SUDOKU_ID, KEY_BOARD_INPUT)
        }

        should("onCompletionHandled returns a failed wrap-up to idle") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            repository.saveFailure = IllegalStateException("disk full")
            val won = viewModel.win(completed)

            viewModel.onCompletionHandled(SudokuCompletion.Failed)

            viewModel.completion.value shouldBe SudokuCompletion.Idle
        }

        should("a summary pending at process death shows again with its follow-up and reads no summaries") {
            val completed = testSudoku(SudokuSize.NINE, mode = GameMode.Level(5), completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value))
            val won = playingToWin(completed, savedStateHandle).win(completed)
            syncCalculations = 0

            val restored = viewModel(savedStateHandle.restoredAfterProcessDeath())

            restored.game.value shouldBe SudokuGame.Ready(won)
            restored.completion.value shouldBe SudokuCompletion.Summary(won, FollowUp.NEXT_LEVEL)
            restored.playGamesSync.value shouldBe null
            syncCalculations shouldBe 0
            maxLevelReads shouldBe 1
        }

        should("a summary without a follow-up pending at process death shows again without one") {
            val completed = testSudoku(mode = GameMode.Daily(LocalDate.now(clock)), created = LocalDateTime.now(clock), completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value))
            val won = playingToWin(completed, savedStateHandle).win(completed)

            viewModel(savedStateHandle.restoredAfterProcessDeath()).completion.value shouldBe SudokuCompletion.Summary(won, null)
        }

        should("a dismissed summary does not show again after process death") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value))
            val viewModel = playingToWin(completed, savedStateHandle)
            val won = viewModel.win(completed)
            viewModel.onCompletionDismissed(SudokuCompletion.Summary(won, FollowUp.NEW_GAME))

            viewModel(savedStateHandle.restoredAfterProcessDeath()).completion.value shouldBe SudokuCompletion.Idle
        }

        should("dismissing the summary for its follow-up leaves only the next sudoku in the saved state") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value))
            val viewModel = playingToWin(completed, savedStateHandle)
            val won = viewModel.win(completed)

            viewModel.onCompletionDismissed(SudokuCompletion.Summary(won, FollowUp.NEW_GAME))
            viewModel.onFollowUp(FollowUp.NEW_GAME)

            val next = repository.lastSaved()
            viewModel.completion.value shouldBe SudokuCompletion.Idle
            viewModel.game.value shouldBe SudokuGame.Ready(next)
            savedStateHandle.keys() shouldBe setOf(KEY_SUDOKU_ID, KEY_BOARD_INPUT)
            savedStateHandle.get<String>(KEY_SUDOKU_ID) shouldBe next.info.id.value
        }

        should("process death while the completed sudoku is saved shows no summary after the restore") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value))
            repository.beforeSave = { CompletableDeferred<Unit>().await() }
            val viewModel = playingToWin(completed, savedStateHandle)
            val won = viewModel.win(completed)
            viewModel.completion.value shouldBe SudokuCompletion.Running

            val restored = viewModel(savedStateHandle.restoredAfterProcessDeath())

            restored.game.value shouldBe SudokuGame.Ready(completed.withValue(1, null))
            restored.completion.value shouldBe SudokuCompletion.Idle
        }

        should("a pending summary of a sudoku that no longer loads completed is dropped") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value))
            val won = playingToWin(completed, savedStateHandle).win(completed)
            val restoredState = savedStateHandle.restoredAfterProcessDeath()
            repository.store(won.withValue(1, null))

            viewModel(restoredState).completion.value shouldBe SudokuCompletion.Idle
            restoredState.keys() shouldBe setOf(KEY_SUDOKU_ID, KEY_BOARD_INPUT)
        }

        should("a pending summary of a sudoku that no longer exists is dropped") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value))
            val won = playingToWin(completed, savedStateHandle).win(completed)
            val restoredState = savedStateHandle.restoredAfterProcessDeath()
            repository.deleteSudokus(listOf(won.info.id))

            val restored = viewModel(restoredState)

            restored.game.value shouldBe SudokuGame.NotFound
            restored.completion.value shouldBe SudokuCompletion.Idle
            restoredState.keys() shouldBe setOf(KEY_SUDOKU_ID, KEY_BOARD_INPUT)
        }

        should("onPlayGamesSyncHandled clears the sync, and a stale handled call keeps the current one") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            viewModel.win(completed)
            val sync = checkNotNull(viewModel.playGamesSync.value)

            viewModel.onPlayGamesSyncHandled(PlayGamesSync())
            viewModel.playGamesSync.value shouldBe sync
            viewModel.onPlayGamesSyncHandled(sync)
            viewModel.playGamesSync.value shouldBe null
        }

        should("opening a missing id or a completed sudoku reads no summaries and offers no sync") {
            val completed = testSudoku(completed = true)
            repository.store(completed)

            val opened =
                listOf(
                    viewModel(),
                    viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.info.id.value))),
                )

            opened.map { it.playGamesSync.value } shouldBe listOf(null, null)
            syncCalculations shouldBe 0
        }

        should("the winning move offers the sync only after the save committed, counting the won game") {
            val completed = testSudoku(completed = true)
            val viewModel = playingToWin(completed)
            val saved = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            repository.beforeSave = {
                calls += "save started"
                saved.await()
            }
            repository.beforeGetCompletedSummaries = { calls += "summaries read" }

            viewModel.win(completed)

            viewModel.playGamesSync.value shouldBe null
            calls shouldBe listOf("save started")
            saved.complete(Unit)
            calls shouldBe listOf("save started", "summaries read")
            val sync = checkNotNull(viewModel.playGamesSync.value)
            sync.leaderboardScores.first() shouldBe (R.string.leaderboard_total_wins to 1L)
            sync.achievementUnlocks shouldBe winUnlocks(R.string.achievement_stopwatch_44)
        }
    },
)
