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
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
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
import io.mockk.Ordering
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred

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

class SudokuViewModelTest : ShouldSpec(
    {
        val getSudoku = mockk<GetSudokuUseCase>()
        val generateSudoku = mockk<GenerateSudokuUseCase>()
        val generateSudokuLevel = mockk<GenerateSudokuLevelUseCase>()
        val getMaxSudokuLevel = mockk<GetMaxSudokuLevelUseCase>()
        val saveSudoku = mockk<SaveSudokuUseCase>(relaxUnitFun = true)
        val shareSudoku = mockk<ShareSudokuUseCase>()
        val calculatePlayGamesSync = mockk<CalculatePlayGamesSyncUseCase>()

        fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): SudokuViewModel =
            SudokuViewModel(
                savedStateHandle,
                getSudoku,
                generateSudoku,
                generateSudokuLevel,
                getMaxSudokuLevel,
                saveSudoku,
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
            clearMocks(getSudoku, generateSudoku, generateSudokuLevel, getMaxSudokuLevel, saveSudoku, shareSudoku, calculatePlayGamesSync)
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
            coVerify(exactly = 1) { saveSudoku(sudoku, false) }
            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
        }

        should("onRestart reports restarting until the sudoku is saved, and a second restart saves once") {
            val sudoku = testSudoku()
            val viewModel = playing(sudoku)
            val saved = CompletableDeferred<Unit>()
            coEvery { saveSudoku(sudoku, false) } coAnswers { saved.await() }

            viewModel.onRestart()
            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.Restarting
            saved.complete(Unit)
            viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
            coVerify(exactly = 1) { saveSudoku(sudoku, false) }
        }

        should("onRestart before a sudoku plays does nothing") {
            val viewModel = viewModel()

            viewModel.onRestart()

            viewModel.game.value shouldBe SudokuGame.NotFound
            coVerify(exactly = 0) { saveSudoku(any(), any()) }
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
                saveSudoku(next, false)
            }
        }

        should("the next level follows the completed level") {
            val completed = testSudoku(SudokuSize.FOUR, modeLevel = 7)
            val next = testSudoku(SudokuSize.FOUR, modeLevel = 8)
            val viewModel = playing(completed)
            coEvery { generateSudokuLevel(SudokuSize.FOUR, 8) } returns next

            viewModel.onFollowUp(FollowUp.NEXT_LEVEL)

            viewModel.game.value shouldBe SudokuGame.Ready(next)
            coVerify(exactly = 1) { saveSudoku(next, false) }
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

        should("saveSudokuProgress updates the saved sudoku") {
            val sudoku = testSudoku()
            viewModel().saveSudokuProgress(sudoku)
            coVerify(exactly = 1) { saveSudoku(sudoku, true) }
        }

        should("onCompleted saves the completed level, then offers the next level when it is the max level") {
            val completed = testSudoku(SudokuSize.NINE, modeLevel = 5)
            val viewModel = playing(completed)
            val sync = PlayGamesSync(achievementUnlocks = listOf(7))
            coEvery { getMaxSudokuLevel(SudokuSize.NINE) } returns 5
            coEvery { calculatePlayGamesSync(completed) } returns sync

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(FollowUp.NEXT_LEVEL, sync)
            coVerify(ordering = Ordering.ORDERED) {
                saveSudoku(completed, true)
                calculatePlayGamesSync(completed)
            }
        }

        should("onCompleted offers no follow-up for a level below the max level") {
            val completed = testSudoku(SudokuSize.NINE, modeLevel = 4)
            val viewModel = playing(completed)
            coEvery { getMaxSudokuLevel(SudokuSize.NINE) } returns 5
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(null, PlayGamesSync())
        }

        should("onCompleted offers a new game for a normal sudoku without a level lookup") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(FollowUp.NEW_GAME, PlayGamesSync())
            coVerify(exactly = 0) { getMaxSudokuLevel(any()) }
        }

        should("onCompleted offers no follow-up for a daily sudoku") {
            val completed = testSudoku(modeLevel = Sudoku.MODE_DAILY)
            val viewModel = playing(completed)
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Summary(null, PlayGamesSync())
            coVerify(exactly = 0) { getMaxSudokuLevel(any()) }
        }

        should("onCompleted reports running until the summary is ready, and a second call wraps up once") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            val sync = CompletableDeferred<PlayGamesSync>()
            coEvery { calculatePlayGamesSync(completed) } coAnswers { sync.await() }

            viewModel.onCompleted()
            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Running
            sync.complete(PlayGamesSync())
            viewModel.completion.value shouldBe SudokuCompletion.Summary(FollowUp.NEW_GAME, PlayGamesSync())
            coVerify(exactly = 1) { saveSudoku(completed, true) }
            coVerify(exactly = 1) { calculatePlayGamesSync(completed) }
        }

        should("onCompleted of an unfinished sudoku does nothing") {
            val unfinished = testSudoku().apply { fields[1].value = null }
            val viewModel = playing(unfinished)

            viewModel.onCompleted()

            viewModel.completion.value shouldBe SudokuCompletion.Idle
            coVerify(exactly = 0) { saveSudoku(any(), any()) }
        }

        should("onCompletionHandled returns to idle, and a stale handled call keeps the pending summary") {
            val completed = testSudoku()
            val viewModel = playing(completed)
            coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()
            viewModel.onCompleted()

            viewModel.onCompletionHandled(SudokuCompletion.Summary(null, PlayGamesSync()))
            viewModel.completion.value shouldBe SudokuCompletion.Summary(FollowUp.NEW_GAME, PlayGamesSync())
            viewModel.onCompletionHandled(SudokuCompletion.Summary(FollowUp.NEW_GAME, PlayGamesSync()))
            viewModel.completion.value shouldBe SudokuCompletion.Idle
        }
    },
)
