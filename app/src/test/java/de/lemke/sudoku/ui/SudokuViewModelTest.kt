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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.lemke.sudoku.data.database.SudokuWithFields
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.ShareSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.MockKMatcherScope
import io.mockk.Ordering
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive

private fun testSudoku(
    size: SudokuSize = SudokuSize.FOUR,
    difficulty: Difficulty = Difficulty.EASY,
    modeLevel: Int = Sudoku.MODE_NORMAL,
): Sudoku =
    Sudoku.create(
        size = size,
        difficulty = difficulty,
        modeLevel = modeLevel,
        fields = MutableList(size.cellCount) { index -> Field(Position.create(index, size), solution = 1, value = 1, given = index == 0) },
    )

private fun SavedStateHandle.restoredAfterProcessDeath(): SavedStateHandle = SavedStateHandle(keys().associateWith { get<Any>(it) })

private fun MockKMatcherScope.rowsOf(sudoku: Sudoku): SudokuWithFields = match { it.sudoku.id == sudoku.id.value }

class SudokuViewModelTest : ShouldSpec(
    {
        val getSudoku = mockk<GetSudokuUseCase>()
        val generateSudoku = mockk<GenerateSudokuUseCase>()
        val generateSudokuLevel = mockk<GenerateSudokuLevelUseCase>()
        val getMaxSudokuLevel = mockk<GetMaxSudokuLevelUseCase>()
        val sudokusRepository = mockk<SudokusRepository>(relaxUnitFun = true)
        val shareSudoku = mockk<ShareSudokuUseCase>()
        val calculatePlayGamesSync = mockk<CalculatePlayGamesSyncUseCase>()

        fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): SudokuViewModel =
            SudokuViewModel(
                savedStateHandle,
                getSudoku,
                generateSudoku,
                generateSudokuLevel,
                getMaxSudokuLevel,
                QueueSudokuSaveUseCase(sudokusRepository, CoroutineScope(SupervisorJob()), Dispatchers.Main),
                shareSudoku,
                calculatePlayGamesSync,
            )

        fun playing(
            sudoku: Sudoku,
            savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.id.value)),
        ): SudokuViewModel {
            coEvery { getSudoku(sudoku.id) } returns sudoku
            return viewModel(savedStateHandle).also { it.onGameStarted(SudokuGame.Ready(sudoku)) }
        }

        beforeEach {
            clearMocks(
                getSudoku,
                generateSudoku,
                generateSudokuLevel,
                getMaxSudokuLevel,
                sudokusRepository,
                shareSudoku,
                calculatePlayGamesSync,
            )
        }

        should("the sudoku of the passed id is ready to start") {
            val sudoku = testSudoku()
            coEvery { getSudoku(sudoku.id) } returns sudoku

            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.id.value)))

            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
        }

        should("a passed id without a saved sudoku is not found") {
            coEvery { getSudoku(SudokuId("missing")) } returns null

            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to "missing")))

            viewModel.game.value shouldBe SudokuGame.NotFound
        }

        should("a missing id is not found without a lookup") {
            val viewModel = viewModel()

            viewModel.game.value shouldBe SudokuGame.NotFound
            coVerify(exactly = 0) { getSudoku(any()) }
        }

        should("onGameStarted moves the ready sudoku to playing") {
            val sudoku = testSudoku()

            playing(sudoku).game.value shouldBe SudokuGame.Playing(sudoku)
        }

        should("onGameStarted for another sudoku keeps the ready one") {
            val sudoku = testSudoku()
            coEvery { getSudoku(sudoku.id) } returns sudoku
            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.id.value)))

            viewModel.onGameStarted(SudokuGame.Ready(testSudoku()))

            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
        }

        should("onRestart clears the entries, saves the whole sudoku and makes it ready again") {
            val sudoku = testSudoku()
            val viewModel = playing(sudoku)

            viewModel.onRestart()

            sudoku.fields.count { it.value == null } shouldBe 15
            coVerify(exactly = 1) { sudokusRepository.saveSudokuRows(rowsOf(sudoku), false) }
            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
        }

        should("onRestart reports restarting until the sudoku is saved, and a second restart saves once") {
            val sudoku = testSudoku()
            val viewModel = playing(sudoku)
            val saved = CompletableDeferred<Unit>()
            coEvery { sudokusRepository.saveSudokuRows(rowsOf(sudoku), false) } coAnswers { saved.await() }

            viewModel.onRestart()
            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Restarting
            saved.complete(Unit)
            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
            coVerify(exactly = 1) { sudokusRepository.saveSudokuRows(rowsOf(sudoku), false) }
        }

        should("onRestart before a sudoku plays does nothing") {
            val viewModel = viewModel()

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.NotFound
            coVerify(exactly = 0) { sudokusRepository.saveSudokuRows(any(), any()) }
        }

        should("onRestart while the completed sudoku is saved keeps it and the pending wrap-up") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            val saved = CompletableDeferred<Unit>()
            coEvery { sudokusRepository.saveSudokuRows(rowsOf(completed), true) } coAnswers { saved.await() }
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()
            viewModel.onCompleted()

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Playing(completed)
            completed.completed shouldBe true
            saved.complete(Unit)
            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            coVerify(exactly = 0) { sudokusRepository.saveSudokuRows(rowsOf(completed), false) }
        }

        should("onRestart while the completion summary is pending keeps the completed sudoku") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()
            viewModel.onCompleted()

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Playing(completed)
            completed.completed shouldBe true
            coVerify(exactly = 0) { sudokusRepository.saveSudokuRows(rowsOf(completed), false) }
        }

        should("onRestart during the Play Games sync resets the sudoku only once the sync is calculated") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            val sync = CompletableDeferred<PlayGamesSync>()
            coEvery { calculatePlayGamesSync(completed) } coAnswers { sync.await() }
            viewModel.onCompleted()
            viewModel.onCompletionDismissed(SudokuCompletion.Summary(completed, FollowUp.NEW_GAME))

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Restarting
            completed.completed shouldBe true
            sync.complete(PlayGamesSync())
            completed.completed shouldBe false
            viewModel.game.value shouldBe SudokuGame.Ready(completed)
            coVerify(ordering = Ordering.ORDERED) {
                calculatePlayGamesSync(completed)
                sudokusRepository.saveSudokuRows(rowsOf(completed), false)
            }
        }

        should("a new game follows with the size and difficulty of the completed sudoku and becomes the current one") {
            val completed = testSudoku(SudokuSize.NINE, Difficulty.HARD)
            val next = testSudoku(SudokuSize.NINE, Difficulty.HARD)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            val viewModel = playing(completed, savedStateHandle)
            coEvery { generateSudoku(SudokuSize.NINE, Difficulty.HARD) } returns next

            viewModel.onFollowUp(FollowUp.NEW_GAME)

            viewModel.game.value shouldBe SudokuGame.Ready(next)
            savedStateHandle.get<String>(KEY_SUDOKU_ID) shouldBe next.id.value
            coVerify(ordering = Ordering.ORDERED) {
                generateSudoku(SudokuSize.NINE, Difficulty.HARD)
                sudokusRepository.saveSudokuRows(rowsOf(next), false)
            }
        }

        should("the next level follows the completed level") {
            val completed = testSudoku(SudokuSize.FOUR, modeLevel = 7)
            val next = testSudoku(SudokuSize.FOUR, modeLevel = 8)
            val viewModel = playing(completed)
            coEvery { generateSudokuLevel(SudokuSize.FOUR, 8) } returns next

            viewModel.onFollowUp(FollowUp.NEXT_LEVEL)

            viewModel.game.value shouldBe SudokuGame.Ready(next)
            coVerify(exactly = 1) { sudokusRepository.saveSudokuRows(rowsOf(next), false) }
        }

        should("a follow-up of an unfinished sudoku does nothing") {
            val unfinished = testSudoku().apply { fields[1].value = null }
            val viewModel = playing(unfinished)

            viewModel.onFollowUp(FollowUp.NEW_GAME)

            viewModel.game.value shouldBe SudokuGame.Playing(unfinished)
            coVerify(exactly = 0) { generateSudoku(any(), any()) }
        }

        should("a second follow-up while one generates generates once") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            val generated = CompletableDeferred<Sudoku>()
            coEvery { generateSudoku(SudokuSize.FOUR, Difficulty.EASY) } coAnswers { generated.await() }

            viewModel.onFollowUp(FollowUp.NEW_GAME)
            viewModel.onFollowUp(FollowUp.NEW_GAME)

            viewModel.game.value shouldBe SudokuGame.Generating
            val next = testSudoku()
            generated.complete(next)
            viewModel.game.value shouldBe SudokuGame.Ready(next)
            coVerify(exactly = 1) { generateSudoku(SudokuSize.FOUR, Difficulty.EASY) }
        }

        should("onShare writes the sudoku file and reports it, and a second share while it runs writes once") {
            val viewModel = viewModel()
            val sudoku = testSudoku()
            val uri = mockk<Uri>()
            val written = CompletableDeferred<Uri>()
            coEvery { shareSudoku(sudoku) } coAnswers { written.await() }

            viewModel.onShare(sudoku)
            viewModel.onShare(sudoku)

            viewModel.share.value shouldBe SudokuShare.Running
            written.complete(uri)
            viewModel.share.value shouldBe SudokuShare.File(uri)
            coVerify(exactly = 1) { shareSudoku(sudoku) }
        }

        should("onShareHandled returns to idle, and a stale handled call keeps the pending file") {
            val viewModel = viewModel()
            val sudoku = testSudoku()
            val uri = mockk<Uri>()
            coEvery { shareSudoku(sudoku) } returns uri
            viewModel.onShare(sudoku)

            viewModel.onShareHandled(SudokuShare.File(mockk()))
            viewModel.share.value shouldBe SudokuShare.File(uri)
            viewModel.onShareHandled(SudokuShare.File(uri))
            viewModel.share.value shouldBe SudokuShare.Idle
        }

        should("onPaused updates the saved playing sudoku with its rows at the time of the pause") {
            val sudoku = testSudoku().apply { fields[1].value = 2 }
            val viewModel = playing(sudoku)
            val saved = CompletableDeferred<Unit>()
            val rows = mutableListOf<SudokuWithFields>()
            coEvery { sudokusRepository.saveSudokuRows(capture(rows), true) } coAnswers { saved.await() }

            viewModel.onPaused()
            sudoku.fields[1].value = 3
            saved.complete(Unit)

            rows.single().fields[1].value shouldBe 2
        }

        should("onProgressChanged updates the saved playing sudoku") {
            val sudoku = testSudoku()

            playing(sudoku).onProgressChanged()

            coVerify(exactly = 1) { sudokusRepository.saveSudokuRows(rowsOf(sudoku), true) }
        }

        should("onPaused before a sudoku plays saves nothing") {
            val sudoku = testSudoku()
            coEvery { getSudoku(sudoku.id) } returns sudoku

            viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.id.value))).onPaused()

            coVerify(exactly = 0) { sudokusRepository.saveSudokuRows(any(), any()) }
        }

        should("progress saves and a restart save run one after another in the order they were started") {
            val sudoku = testSudoku()
            val viewModel = playing(sudoku)
            val firstSaved = CompletableDeferred<Unit>()
            val savedValues = mutableListOf<Int?>()
            coEvery { sudokusRepository.saveSudokuRows(rowsOf(sudoku), any()) } coAnswers {
                if (savedValues.isEmpty()) firstSaved.await()
                savedValues += firstArg<SudokuWithFields>().fields[1].value
            }

            viewModel.onPaused()
            sudoku.fields[1].value = 2
            viewModel.onProgressChanged()
            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Restarting
            savedValues shouldBe emptyList()
            firstSaved.complete(Unit)
            savedValues shouldBe listOf(1, 2, null)
            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
            coVerify(ordering = Ordering.ORDERED) {
                sudokusRepository.saveSudokuRows(rowsOf(sudoku), true)
                sudokusRepository.saveSudokuRows(rowsOf(sudoku), true)
                sudokusRepository.saveSudokuRows(rowsOf(sudoku), false)
            }
        }

        should("the save on leaving finishes after the view model is cleared") {
            val sudoku = testSudoku()
            val store = ViewModelStore()
            val viewModel =
                ViewModelProvider.create(store, viewModelFactory { initializer { playing(sudoku) } })[SudokuViewModel::class]
            val saved = CompletableDeferred<Unit>()
            var finished = false
            coEvery { sudokusRepository.saveSudokuRows(rowsOf(sudoku), true) } coAnswers {
                saved.await()
                finished = true
            }

            viewModel.onPaused()
            store.clear()
            saved.complete(Unit)

            viewModel.viewModelScope.isActive shouldBe false
            finished shouldBe true
        }

        should("onCompleted saves the completed level, offers the next level when it is the max level, then syncs Play Games") {
            val completed = testSudoku(SudokuSize.NINE, modeLevel = 5)
            val viewModel = playing(completed)
            val sync = PlayGamesSync(achievementUnlocks = listOf(7))
            coEvery { getMaxSudokuLevel(SudokuSize.NINE) } returns 5
            coEvery { calculatePlayGamesSync(completed) } returns sync

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEXT_LEVEL)
            viewModel.playGamesSync.value shouldBe sync
            coVerify(ordering = Ordering.ORDERED) {
                sudokusRepository.saveSudokuRows(rowsOf(completed), true)
                getMaxSudokuLevel(SudokuSize.NINE)
                calculatePlayGamesSync(completed)
            }
        }

        should("onCompleted offers no follow-up for a level below the max level") {
            val completed = testSudoku(SudokuSize.NINE, modeLevel = 4)
            val viewModel = playing(completed)
            coEvery { getMaxSudokuLevel(SudokuSize.NINE) } returns 5
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, null)
        }

        should("onCompleted offers a new game for a normal sudoku without a level lookup") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            coVerify(exactly = 0) { getMaxSudokuLevel(any()) }
        }

        should("onCompleted offers no follow-up for a daily sudoku") {
            val completed = testSudoku(modeLevel = Sudoku.MODE_DAILY)
            val viewModel = playing(completed)
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, null)
            coVerify(exactly = 0) { getMaxSudokuLevel(any()) }
        }

        should("onCompleted reports running until the sudoku is saved, and a second call wraps up once") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            val saved = CompletableDeferred<Unit>()
            coEvery { sudokusRepository.saveSudokuRows(rowsOf(completed), true) } coAnswers { saved.await() }
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()

            viewModel.onCompleted()
            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Running
            saved.complete(Unit)
            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            coVerify(exactly = 1) { sudokusRepository.saveSudokuRows(rowsOf(completed), true) }
            coVerify(exactly = 1) { calculatePlayGamesSync(completed) }
        }

        should("onCompleted offers the summary before the Play Games sync is calculated") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            val sync = CompletableDeferred<PlayGamesSync>()
            coEvery { calculatePlayGamesSync(completed) } coAnswers { sync.await() }

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            viewModel.playGamesSync.value shouldBe null
            sync.complete(PlayGamesSync(achievementUnlocks = listOf(7)))
            viewModel.playGamesSync.value shouldBe PlayGamesSync(achievementUnlocks = listOf(7))
        }

        should("onCompleted reports a failed save without a Play Games sync") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            coEvery { sudokusRepository.saveSudokuRows(rowsOf(completed), true) } throws IllegalStateException("disk full")

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Failed
            viewModel.playGamesSync.value shouldBe null
            coVerify(exactly = 0) { calculatePlayGamesSync(any()) }
        }

        should("onCompleted offers the summary without a follow-up and syncs Play Games when only the max-level read fails") {
            val completed = testSudoku(SudokuSize.NINE, modeLevel = 5)
            val viewModel = playing(completed)
            coEvery { getMaxSudokuLevel(SudokuSize.NINE) } throws IllegalStateException("query failed")
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync(achievementUnlocks = listOf(7))

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, null)
            viewModel.playGamesSync.value shouldBe PlayGamesSync(achievementUnlocks = listOf(7))
            coVerify(exactly = 1) { sudokusRepository.saveSudokuRows(rowsOf(completed), true) }
        }

        should("onCompleted keeps the summary and syncs nothing when the Play Games sync calculation fails") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            coEvery { calculatePlayGamesSync(completed) } throws IllegalStateException("query failed")

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            viewModel.playGamesSync.value shouldBe PlayGamesSync()
        }

        should("onCompleted maps a cancellation of the save in an active wrap-up to failed, so a restart works again") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            coEvery { sudokusRepository.saveSudokuRows(rowsOf(completed), true) } throws CancellationException()

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Failed
            coVerify(exactly = 0) { calculatePlayGamesSync(any()) }
            viewModel.onCompletionHandled(SudokuCompletion.Failed)
            viewModel.onRestart()
            viewModel.game.value shouldBe SudokuGame.Ready(completed)
        }

        should("onCompleted falls back to an empty sync on a cancellation of the calculation in an active wrap-up") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            coEvery { calculatePlayGamesSync(completed) } throws CancellationException()

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            viewModel.playGamesSync.value shouldBe PlayGamesSync()
        }

        should("onCompleted of an unfinished sudoku does nothing") {
            val unfinished = testSudoku().apply { fields[1].value = null }
            val viewModel = playing(unfinished)

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Idle
            coVerify(exactly = 0) { sudokusRepository.saveSudokuRows(any(), any()) }
        }

        should("onCompleted of a completed sudoku that is ready but not started does nothing") {
            val completed = testSudoku()
            coEvery { getSudoku(completed.id) } returns completed
            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value)))

            viewModel.onCompleted()

            viewModel.game.value shouldBe SudokuGame.Ready(completed)
            viewModel.completion.value shouldBe SudokuCompletion.Idle
            coVerify(exactly = 0) { sudokusRepository.saveSudokuRows(any(), any()) }
        }

        should("onCompleted while the follow-up generates does nothing") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            coEvery { generateSudoku(SudokuSize.FOUR, Difficulty.EASY) } coAnswers { CompletableDeferred<Sudoku>().await() }
            viewModel.onFollowUp(FollowUp.NEW_GAME)

            viewModel.onCompleted()

            viewModel.game.value shouldBe SudokuGame.Generating
            viewModel.completion.value shouldBe SudokuCompletion.Idle
            coVerify(exactly = 0) { sudokusRepository.saveSudokuRows(any(), any()) }
        }

        should("the summary stays until it is dismissed, and a stale dismissal keeps it") {
            val completed = testSudoku()
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            val viewModel = playing(completed, savedStateHandle)
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()
            viewModel.onCompleted()

            viewModel.onCompletionDismissed(SudokuCompletion.Summary(completed, null))
            viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
            viewModel.onCompletionDismissed(SudokuCompletion.Summary(completed, FollowUp.NEW_GAME))
            viewModel.completion.value shouldBe SudokuCompletion.Idle
            savedStateHandle.keys() shouldBe setOf(KEY_SUDOKU_ID)
        }

        should("onCompletionHandled returns a failed wrap-up to idle") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            coEvery { sudokusRepository.saveSudokuRows(rowsOf(completed), true) } throws IllegalStateException("disk full")
            viewModel.onCompleted()

            viewModel.onCompletionHandled(SudokuCompletion.Failed)

            viewModel.completion.value shouldBe SudokuCompletion.Idle
        }

        should("a summary pending at process death shows again with its follow-up once the completed sudoku loads") {
            val completed = testSudoku(SudokuSize.NINE, modeLevel = 5)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            coEvery { getMaxSudokuLevel(SudokuSize.NINE) } returns 5
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync(achievementUnlocks = listOf(7))
            playing(completed, savedStateHandle).onCompleted()

            val restored = viewModel(savedStateHandle.restoredAfterProcessDeath())

            restored.game.value shouldBe SudokuGame.Ready(completed)
            restored.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEXT_LEVEL)
            restored.playGamesSync.value shouldBe null
            coVerify(exactly = 1) { calculatePlayGamesSync(completed) }
            coVerify(exactly = 1) { getMaxSudokuLevel(SudokuSize.NINE) }
        }

        should("a summary without a follow-up pending at process death shows again without one") {
            val completed = testSudoku(modeLevel = Sudoku.MODE_DAILY)
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()
            playing(completed, savedStateHandle).onCompleted()

            viewModel(savedStateHandle.restoredAfterProcessDeath()).completion.value shouldBe SudokuCompletion.Summary(completed, null)
        }

        should("a dismissed summary does not show again after process death") {
            val completed = testSudoku()
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()
            val viewModel = playing(completed, savedStateHandle)
            viewModel.onCompleted()
            viewModel.onCompletionDismissed(SudokuCompletion.Summary(completed, FollowUp.NEW_GAME))

            viewModel(savedStateHandle.restoredAfterProcessDeath()).completion.value shouldBe SudokuCompletion.Idle
        }

        should("dismissing the summary for its follow-up leaves only the next sudoku in the saved state") {
            val completed = testSudoku()
            val next = testSudoku().apply { fields[1].value = null }
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()
            coEvery { generateSudoku(SudokuSize.FOUR, Difficulty.EASY) } returns next
            val viewModel = playing(completed, savedStateHandle)
            viewModel.onCompleted()

            viewModel.onCompletionDismissed(SudokuCompletion.Summary(completed, FollowUp.NEW_GAME))
            viewModel.onFollowUp(FollowUp.NEW_GAME)

            viewModel.completion.value shouldBe SudokuCompletion.Idle
            viewModel.game.value shouldBe SudokuGame.Ready(next)
            savedStateHandle.keys() shouldBe setOf(KEY_SUDOKU_ID)
            savedStateHandle.get<String>(KEY_SUDOKU_ID) shouldBe next.id.value
        }

        should("process death while the completed sudoku is saved shows no summary after the restore") {
            val completed = testSudoku()
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            coEvery { sudokusRepository.saveSudokuRows(rowsOf(completed), true) } coAnswers { CompletableDeferred<Unit>().await() }
            val viewModel = playing(completed, savedStateHandle)
            viewModel.onCompleted()
            viewModel.completion.value shouldBe SudokuCompletion.Running

            val restored = viewModel(savedStateHandle.restoredAfterProcessDeath())

            restored.game.value shouldBe SudokuGame.Ready(completed)
            restored.completion.value shouldBe SudokuCompletion.Idle
        }

        should("a pending summary of a sudoku that no longer loads completed is dropped") {
            val completed = testSudoku()
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()
            playing(completed, savedStateHandle).onCompleted()
            val restoredState = savedStateHandle.restoredAfterProcessDeath()
            completed.fields[1].value = null

            viewModel(restoredState).completion.value shouldBe SudokuCompletion.Idle
            restoredState.keys() shouldBe setOf(KEY_SUDOKU_ID)
        }

        should("a pending summary of a sudoku that no longer exists is dropped") {
            val completed = testSudoku()
            val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()
            playing(completed, savedStateHandle).onCompleted()
            val restoredState = savedStateHandle.restoredAfterProcessDeath()
            coEvery { getSudoku(completed.id) } returns null

            val restored = viewModel(restoredState)

            restored.game.value shouldBe SudokuGame.NotFound
            restored.completion.value shouldBe SudokuCompletion.Idle
            restoredState.keys() shouldBe setOf(KEY_SUDOKU_ID)
        }

        should("onPlayGamesSyncHandled clears the sync, and a stale handled call keeps the pending one") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            val sync = PlayGamesSync(achievementUnlocks = listOf(7))
            coEvery { calculatePlayGamesSync(completed) } returns sync
            viewModel.onCompleted()

            viewModel.onPlayGamesSyncHandled(PlayGamesSync())
            viewModel.playGamesSync.value shouldBe sync
            viewModel.onPlayGamesSyncHandled(sync)
            viewModel.playGamesSync.value shouldBe null
        }
    },
)
