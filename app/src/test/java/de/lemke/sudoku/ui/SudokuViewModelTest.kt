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
import de.lemke.sudoku.data.database.sudokuFromDb
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GenerateFieldsUseCase
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.ShareSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private fun SavedStateHandle.restoredAfterProcessDeath(): SavedStateHandle = SavedStateHandle(keys().associateWith { get<Any>(it) })

private val FakeSudokusRepository.savedIds: List<Pair<SudokuId, Boolean>>
    get() = saves.map { SudokuId(it.rows.sudoku.id) to it.onlyUpdate }

private fun FakeSudokusRepository.lastSaved(): Sudoku = checkNotNull(sudokuFromDb(saves.last().rows))

private fun winUnlocks(stopwatchAchievement: Int): List<Int> =
    listOf(R.string.achievement_first_win, R.string.achievement_no_hints, R.string.achievement_i_am_speed, stopwatchAchievement)

@OptIn(ExperimentalCoroutinesApi::class)
class SudokuViewModelTest : ShouldSpec(
    {
        val clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC)
        lateinit var repository: FakeSudokusRepository
        var maxLevelReads = 0
        var syncCalculations = 0

        fun mainScheduler() = StandardTestDispatcher().scheduler

        beforeEach {
            repository = FakeSudokusRepository()
            maxLevelReads = 0
            syncCalculations = 0
            repository.beforeGetMaxSudokuLevel = { maxLevelReads++ }
            repository.beforeGetAllSudokus = { syncCalculations++ }
        }

        fun viewModel(
            savedStateHandle: SavedStateHandle = SavedStateHandle(),
            generateDispatcher: CoroutineDispatcher = Dispatchers.Main,
            shareFiles: SudokuShareFiles = FakeSudokuShareFiles(File("game.sudoku")),
            shareDispatcher: CoroutineDispatcher = Dispatchers.Main,
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
                ShareSudokuUseCase(shareFiles, shareDispatcher),
                CalculatePlayGamesSyncUseCase(GetAllSudokusUseCase(repository, dispatcher), dispatcher),
                UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher())),
                clock,
            )
        }

        fun playing(
            sudoku: Sudoku,
            savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.id.value)),
            generateDispatcher: CoroutineDispatcher = Dispatchers.Main,
        ): SudokuViewModel {
            repository.store(sudoku)
            return viewModel(savedStateHandle, generateDispatcher).also { it.onGameStarted(SudokuGame.Ready(sudoku)) }
        }

        should("the sudoku of the passed id is ready to start") {
            val sudoku = testSudoku()
            repository.store(sudoku)

            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.id.value)))

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

            playing(sudoku).game.value shouldBe SudokuGame.Playing(sudoku, SudokuKind.Normal, 3, Phase.Playing)
        }

        should("onGameStarted for another sudoku keeps the ready one") {
            val sudoku = testSudoku()
            repository.store(sudoku)
            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.id.value)))

            viewModel.onGameStarted(SudokuGame.Ready(testSudoku()))

            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
        }

        should("onRestart clears the entries, saves the whole sudoku and makes it ready again") {
            val sudoku = testSudoku(completed = true)
            val viewModel = playing(sudoku)

            viewModel.onRestart()

            sudoku.fields.count { it.value == null } shouldBe 10
            repository.savedIds shouldBe listOf(sudoku.id to false)
            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
        }

        should("onRestart reports restarting until the sudoku is saved, and a second restart saves once") {
            val sudoku = testSudoku()
            val viewModel = playing(sudoku)
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { saved.await() }

            viewModel.onRestart()
            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Restarting
            saved.complete(Unit)
            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
            repository.savedIds shouldBe listOf(sudoku.id to false)
        }

        should("onRestart before a sudoku plays does nothing") {
            val viewModel = viewModel()

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.NotFound
            repository.saves shouldBe emptyList()
        }

        should("onRestart while the completed sudoku is saved keeps it and the pending wrap-up") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { if (it.onlyUpdate) saved.await() }
            viewModel.onCompleted()

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Playing(completed, SudokuKind.Normal, 3, Phase.Won)
            completed.completed shouldBe true
            saved.complete(Unit)
            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            repository.savedIds shouldBe listOf(completed.id to true)
        }

        should("onRestart while the completion summary is pending keeps the completed sudoku") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            viewModel.onCompleted()

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Playing(completed, SudokuKind.Normal, 3, Phase.Won)
            completed.completed shouldBe true
            repository.savedIds shouldBe listOf(completed.id to true)
        }

        should("onRestart during the Play Games sync resets the sudoku only once the sync is calculated") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            val sync = CompletableDeferred<Unit>()
            repository.beforeGetAllSudokus = { sync.await() }
            viewModel.onCompleted()
            viewModel.onCompletionDismissed(SudokuCompletion.Summary(completed, FollowUp.NEW_GAME))

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Restarting
            completed.completed shouldBe true
            repository.savedIds shouldBe listOf(completed.id to true)
            sync.complete(Unit)
            completed.completed shouldBe false
            viewModel.game.value shouldBe SudokuGame.Ready(completed)
            repository.savedIds shouldBe listOf(completed.id to true, completed.id to false)
        }

        should("a new game follows with the size and difficulty of the completed sudoku and becomes the current one") {
            val completed = testSudoku(SudokuSize.NINE, difficulty = Difficulty.HARD, completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            val viewModel = playing(completed, savedStateHandle)

            viewModel.onFollowUp(FollowUp.NEW_GAME)

            val next = repository.lastSaved()
            viewModel.game.value shouldBe SudokuGame.Ready(next)
            savedStateHandle.get<String>(KEY_SUDOKU_ID) shouldBe next.id.value
            (next.size to next.difficulty) shouldBe (SudokuSize.NINE to Difficulty.HARD)
            next.modeLevel shouldBe Sudoku.MODE_NORMAL
            repository.savedIds shouldBe listOf(next.id to false)
        }

        should("the next level follows the completed level") {
            val completed = testSudoku(SudokuSize.FOUR, modeLevel = 7, completed = true)
            val viewModel = playing(completed)

            viewModel.onFollowUp(FollowUp.NEXT_LEVEL)

            val next = repository.lastSaved()
            viewModel.game.value shouldBe SudokuGame.Ready(next)
            (next.size to next.modeLevel) shouldBe (SudokuSize.FOUR to 8)
            repository.savedIds shouldBe listOf(next.id to false)
        }

        should("a follow-up of an unfinished sudoku does nothing") {
            val unfinished = testSudoku()
            val viewModel = playing(unfinished)

            viewModel.onFollowUp(FollowUp.NEW_GAME)

            viewModel.game.value shouldBe SudokuGame.Playing(unfinished, SudokuKind.Normal, 3, Phase.Playing)
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
            repository.savedIds shouldBe listOf(next.id to false)
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
            shareFiles.writes.map { it.sudokuId } shouldBe listOf(sudoku.id)
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

        should("onPaused updates the saved playing sudoku with its rows at the time of the pause") {
            val sudoku = testSudoku().apply { fields[1].value = 2 }
            val viewModel = playing(sudoku)
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { saved.await() }

            viewModel.onPaused()
            sudoku.fields[1].value = 3
            saved.complete(Unit)

            repository.saves
                .single()
                .rows.fields[1]
                .value shouldBe 2
            repository.savedIds shouldBe listOf(sudoku.id to true)
        }

        should("onProgressChanged updates the saved playing sudoku") {
            val sudoku = testSudoku()

            playing(sudoku).onProgressChanged()

            repository.savedIds shouldBe listOf(sudoku.id to true)
        }

        should("onPaused before a sudoku plays saves nothing") {
            val sudoku = testSudoku()
            repository.store(sudoku)

            viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.id.value))).onPaused()

            repository.saves shouldBe emptyList()
        }

        should("progress saves and a restart save run one after another in the order they were started") {
            val sudoku = testSudoku().apply { fields[1].value = 1 }
            val viewModel = playing(sudoku)
            val firstSaved = CompletableDeferred<Unit>()
            repository.beforeSave = { firstSaved.await() }

            viewModel.onPaused()
            sudoku.fields[1].value = 2
            viewModel.onProgressChanged()
            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Restarting
            repository.saves shouldBe emptyList()
            firstSaved.complete(Unit)
            repository.saves.map { it.rows.fields[1].value to it.onlyUpdate } shouldBe listOf(1 to true, 2 to true, null to false)
            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
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
            repository.savedIds shouldBe listOf(sudoku.id to true)
        }

        should("onCompleted saves the completed level, offers the next level when it is the max level, then syncs Play Games") {
            val completed = testSudoku(SudokuSize.NINE, modeLevel = 5, completed = true)
            val viewModel = playing(completed)
            val calls = mutableListOf<String>()
            repository.beforeSave = { calls += "save" }
            repository.beforeGetMaxSudokuLevel = { calls += "max level" }
            repository.beforeGetAllSudokus = { calls += "sync" }

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEXT_LEVEL)
            viewModel.playGamesSync.value?.achievementUnlocks shouldBe winUnlocks(R.string.achievement_stopwatch_99)
            calls shouldBe listOf("save", "max level", "sync")
        }

        should("onCompleted offers no follow-up for a level below the max level") {
            val completed = testSudoku(SudokuSize.NINE, modeLevel = 4, completed = true)
            repository.store(testSudoku(SudokuSize.NINE, modeLevel = 5))
            val viewModel = playing(completed)

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, null)
        }

        should("onCompleted offers a new game for a normal sudoku without a level lookup") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            maxLevelReads shouldBe 0
        }

        should("onResumed starts the timer of a sudoku in progress") {
            val sudoku = testSudoku(updated = LocalDateTime.of(2026, 1, 15, 11, 0))
            val viewModel = playing(sudoku)

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(1000)
            mainScheduler().runCurrent()
            val resumed = sudoku.resumed
            sudoku.stopTimer()
            resumed shouldBe true

            sudoku.seconds shouldBe 1
            sudoku.updated shouldBe LocalDateTime.of(2026, 1, 15, 12, 0)
        }

        should("onResumed starts the timer of a daily sudoku created on the clock's date") {
            val sudoku =
                testSudoku(
                    modeLevel = Sudoku.MODE_DAILY,
                    created = LocalDateTime.of(2026, 1, 15, 0, 0),
                    updated = LocalDateTime.of(2026, 1, 15, 0, 0),
                )
            val viewModel = playing(sudoku)

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(1000)
            mainScheduler().runCurrent()
            sudoku.stopTimer()

            sudoku.seconds shouldBe 1
            sudoku.updated shouldBe LocalDateTime.of(2026, 1, 15, 12, 0)
        }

        should("onResumed ends a daily sudoku created before the clock's date without a timer") {
            val sudoku =
                testSudoku(
                    modeLevel = Sudoku.MODE_DAILY,
                    created = LocalDateTime.of(2026, 1, 14, 23, 59),
                    updated = LocalDateTime.of(2026, 1, 14, 23, 59),
                )
            val viewModel = playing(sudoku)

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(1000)
            mainScheduler().runCurrent()

            sudoku.resumed shouldBe false
            sudoku.seconds shouldBe 0
            sudoku.updated shouldBe LocalDateTime.of(2026, 1, 14, 23, 59)
        }

        should("onResumed ends a completed sudoku without a timer") {
            val sudoku = testSudoku(completed = true, updated = LocalDateTime.of(2026, 1, 15, 11, 0))
            val viewModel = playing(sudoku)

            viewModel.onResumed()
            mainScheduler().advanceTimeBy(1000)
            mainScheduler().runCurrent()

            sudoku.resumed shouldBe false
            sudoku.seconds shouldBe 0
            sudoku.updated shouldBe LocalDateTime.of(2026, 1, 15, 11, 0)
        }

        should("onCompleted offers no follow-up for a daily sudoku") {
            val completed = testSudoku(modeLevel = Sudoku.MODE_DAILY, completed = true)
            val viewModel = playing(completed)

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, null)
            maxLevelReads shouldBe 0
        }

        should("onCompleted reports running until the sudoku is saved, and a second call wraps up once") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { saved.await() }

            viewModel.onCompleted()
            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Running
            saved.complete(Unit)
            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            repository.savedIds shouldBe listOf(completed.id to true)
            syncCalculations shouldBe 1
        }

        should("onCompleted offers the summary before the Play Games sync is calculated") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            val sync = CompletableDeferred<Unit>()
            repository.beforeGetAllSudokus = { sync.await() }

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            viewModel.playGamesSync.value shouldBe null
            sync.complete(Unit)
            viewModel.playGamesSync.value?.achievementUnlocks shouldBe winUnlocks(R.string.achievement_stopwatch_44)
        }

        should("onCompleted reports a failed save without a Play Games sync") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            repository.saveFailure = IllegalStateException("disk full")

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Failed
            viewModel.playGamesSync.value shouldBe null
            syncCalculations shouldBe 0
        }

        should("onCompleted offers the summary without a follow-up and syncs Play Games when only the max-level read fails") {
            val completed = testSudoku(SudokuSize.NINE, modeLevel = 5, completed = true)
            val viewModel = playing(completed)
            repository.beforeGetMaxSudokuLevel = { throw IllegalStateException("query failed") }

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, null)
            viewModel.playGamesSync.value?.achievementUnlocks shouldBe winUnlocks(R.string.achievement_stopwatch_99)
            repository.savedIds shouldBe listOf(completed.id to true)
        }

        should("onCompleted keeps the summary and syncs nothing when the Play Games sync calculation fails") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            repository.beforeGetAllSudokus = { throw IllegalStateException("query failed") }

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            viewModel.playGamesSync.value shouldBe PlayGamesSync()
        }

        should("onCompleted maps a cancellation of the save in an active wrap-up to failed, so a restart works again") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            repository.beforeSave = { if (it.onlyUpdate) throw CancellationException() }

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Failed
            syncCalculations shouldBe 0
            viewModel.onCompletionHandled(SudokuCompletion.Failed)
            viewModel.onRestart()
            viewModel.game.value shouldBe SudokuGame.Ready(completed)
        }

        should("onCompleted falls back to an empty sync on a cancellation of the calculation in an active wrap-up") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            repository.beforeGetAllSudokus = { throw CancellationException() }

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            viewModel.playGamesSync.value shouldBe PlayGamesSync()
        }

        should("onCompleted of an unfinished sudoku does nothing") {
            val unfinished = testSudoku()
            val viewModel = playing(unfinished)

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Idle
            repository.saves shouldBe emptyList()
        }

        should("onCompleted of a completed sudoku that is ready but not started does nothing") {
            val completed = testSudoku(completed = true)
            repository.store(completed)
            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value)))

            viewModel.onCompleted()

            viewModel.game.value shouldBe SudokuGame.Ready(completed)
            viewModel.completion.value shouldBe SudokuCompletion.Idle
            repository.saves shouldBe emptyList()
        }

        should("onCompleted while the follow-up generates does nothing") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed, generateDispatcher = StandardTestDispatcher())
            viewModel.onFollowUp(FollowUp.NEW_GAME)

            viewModel.onCompleted()

            viewModel.game.value shouldBe SudokuGame.Generating
            viewModel.completion.value shouldBe SudokuCompletion.Idle
            repository.saves shouldBe emptyList()
        }

        should("the summary stays until it is dismissed, and a stale dismissal keeps it") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            val viewModel = playing(completed, savedStateHandle)
            viewModel.onCompleted()

            viewModel.onCompletionDismissed(SudokuCompletion.Summary(completed, null))
            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            viewModel.onCompletionDismissed(SudokuCompletion.Summary(completed, FollowUp.NEW_GAME))
            viewModel.completion.value shouldBe SudokuCompletion.Idle
            savedStateHandle.keys() shouldBe setOf(KEY_SUDOKU_ID)
        }

        should("onCompletionHandled returns a failed wrap-up to idle") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            repository.saveFailure = IllegalStateException("disk full")
            viewModel.onCompleted()

            viewModel.onCompletionHandled(SudokuCompletion.Failed)

            viewModel.completion.value shouldBe SudokuCompletion.Idle
        }

        should("a summary pending at process death shows again with its follow-up once the completed sudoku loads") {
            val completed = testSudoku(SudokuSize.NINE, modeLevel = 5, completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            playing(completed, savedStateHandle).onCompleted()

            val restored = viewModel(savedStateHandle.restoredAfterProcessDeath())

            restored.game.value shouldBe SudokuGame.Ready(completed)
            restored.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEXT_LEVEL)
            restored.playGamesSync.value shouldBe null
            syncCalculations shouldBe 1
            maxLevelReads shouldBe 1
        }

        should("a summary without a follow-up pending at process death shows again without one") {
            val completed = testSudoku(modeLevel = Sudoku.MODE_DAILY, completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            playing(completed, savedStateHandle).onCompleted()

            viewModel(savedStateHandle.restoredAfterProcessDeath()).completion.value shouldBe SudokuCompletion.Summary(completed, null)
        }

        should("a dismissed summary does not show again after process death") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            val viewModel = playing(completed, savedStateHandle)
            viewModel.onCompleted()
            viewModel.onCompletionDismissed(SudokuCompletion.Summary(completed, FollowUp.NEW_GAME))

            viewModel(savedStateHandle.restoredAfterProcessDeath()).completion.value shouldBe SudokuCompletion.Idle
        }

        should("dismissing the summary for its follow-up leaves only the next sudoku in the saved state") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            val viewModel = playing(completed, savedStateHandle)
            viewModel.onCompleted()

            viewModel.onCompletionDismissed(SudokuCompletion.Summary(completed, FollowUp.NEW_GAME))
            viewModel.onFollowUp(FollowUp.NEW_GAME)

            val next = repository.lastSaved()
            viewModel.completion.value shouldBe SudokuCompletion.Idle
            viewModel.game.value shouldBe SudokuGame.Ready(next)
            savedStateHandle.keys() shouldBe setOf(KEY_SUDOKU_ID)
            savedStateHandle.get<String>(KEY_SUDOKU_ID) shouldBe next.id.value
        }

        should("process death while the completed sudoku is saved shows no summary after the restore") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            repository.beforeSave = { CompletableDeferred<Unit>().await() }
            val viewModel = playing(completed, savedStateHandle)
            viewModel.onCompleted()
            viewModel.completion.value shouldBe SudokuCompletion.Running

            val restored = viewModel(savedStateHandle.restoredAfterProcessDeath())

            restored.game.value shouldBe SudokuGame.Ready(completed)
            restored.completion.value shouldBe SudokuCompletion.Idle
        }

        should("a pending summary of a sudoku that no longer loads completed is dropped") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            playing(completed, savedStateHandle).onCompleted()
            val restoredState = savedStateHandle.restoredAfterProcessDeath()
            completed.fields[1].value = null
            repository.store(completed)

            viewModel(restoredState).completion.value shouldBe SudokuCompletion.Idle
            restoredState.keys() shouldBe setOf(KEY_SUDOKU_ID)
        }

        should("a pending summary of a sudoku that no longer exists is dropped") {
            val completed = testSudoku(completed = true)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            playing(completed, savedStateHandle).onCompleted()
            val restoredState = savedStateHandle.restoredAfterProcessDeath()
            repository.deleteSudoku(completed)

            val restored = viewModel(restoredState)

            restored.game.value shouldBe SudokuGame.NotFound
            restored.completion.value shouldBe SudokuCompletion.Idle
            restoredState.keys() shouldBe setOf(KEY_SUDOKU_ID)
        }

        should("onPlayGamesSyncHandled clears the sync, and a stale handled call keeps the pending one") {
            val completed = testSudoku(completed = true)
            val viewModel = playing(completed)
            viewModel.onCompleted()
            val sync = checkNotNull(viewModel.playGamesSync.value)

            viewModel.onPlayGamesSyncHandled(PlayGamesSync())
            viewModel.playGamesSync.value shouldBe sync
            viewModel.onPlayGamesSyncHandled(sync)
            viewModel.playGamesSync.value shouldBe null
        }
    },
)
