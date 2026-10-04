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

import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetRecentlyUpdatedNormalSudokuUseCase
import de.lemke.sudoku.domain.IsDailySudokuCompletedUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.Ordering
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private fun testSudoku(
    size: SudokuSize = SudokuSize.NINE,
    completed: Boolean = false,
    errorsMade: Int = 0,
): Sudoku =
    Sudoku.create(
        size = size,
        difficulty = Difficulty.EASY,
        modeLevel = Sudoku.MODE_NORMAL,
        errorsMade = errorsMade,
        fields =
            MutableList(size.cellCount) { index ->
                Field(Position.create(index, size), solution = 1, value = if (completed) 1 else null)
            },
    )

@OptIn(ExperimentalCoroutinesApi::class)
class TabSudokuViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        val generateSudoku = mockk<GenerateSudokuUseCase>()
        val saveSudoku = mockk<SaveSudokuUseCase>(relaxUnitFun = true)
        val getRecentSudoku = mockk<GetRecentlyUpdatedNormalSudokuUseCase>()
        val isDailySudokuCompleted = mockk<IsDailySudokuCompletedUseCase>()
        lateinit var viewModel: TabSudokuViewModel

        beforeEach {
            clearMocks(generateSudoku, saveSudoku, getRecentSudoku, isDailySudokuCompleted)
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            viewModel = TabSudokuViewModel(userSettings, generateSudoku, saveSudoku, getRecentSudoku, isDailySudokuCompleted)
        }

        should("difficultySliderValue round-trips through the real UserSettings") {
            viewModel.difficultySliderValue = 3
            viewModel.difficultySliderValue shouldBe 3
            userSettings.difficultySliderValue shouldBe 3
        }

        should("sizeSliderValue round-trips through the real UserSettings") {
            viewModel.sizeSliderValue = 2
            viewModel.sizeSliderValue shouldBe 2
            userSettings.sizeSliderValue shouldBe 2
        }

        should("onNewGame generates then saves the sudoku, in order, and reports it created") {
            val sudoku = testSudoku()
            coEvery { generateSudoku(SudokuSize.NINE, Difficulty.HARD) } returns sudoku

            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)

            viewModel.newGame.value shouldBe NewGame.Created(sudoku.id)
            coVerify(ordering = Ordering.ORDERED) {
                generateSudoku(SudokuSize.NINE, Difficulty.HARD)
                saveSudoku(sudoku)
            }
        }

        should("onNewGame reports generating until the sudoku is saved") {
            val generated = CompletableDeferred<Sudoku>()
            coEvery { generateSudoku(SudokuSize.FOUR, Difficulty.EASY) } coAnswers { generated.await() }

            viewModel.onNewGame(SudokuSize.FOUR, Difficulty.EASY)

            viewModel.newGame.value shouldBe NewGame.Generating
            coVerify(exactly = 0) { saveSudoku(any()) }
            val sudoku = testSudoku(SudokuSize.FOUR)
            generated.complete(sudoku)
            viewModel.newGame.value shouldBe NewGame.Created(sudoku.id)
        }

        should("a second onNewGame while generating generates once") {
            val generated = CompletableDeferred<Sudoku>()
            coEvery { generateSudoku(SudokuSize.NINE, Difficulty.HARD) } coAnswers { generated.await() }

            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)
            generated.complete(testSudoku())

            coVerify(exactly = 1) { generateSudoku(SudokuSize.NINE, Difficulty.HARD) }
            coVerify(exactly = 1) { saveSudoku(any()) }
        }

        should("onNewGame while a created sudoku is not handled yet keeps that sudoku") {
            val sudoku = testSudoku()
            coEvery { generateSudoku(SudokuSize.NINE, Difficulty.HARD) } returns sudoku
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)

            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)

            viewModel.newGame.value shouldBe NewGame.Created(sudoku.id)
            coVerify(exactly = 1) { generateSudoku(SudokuSize.NINE, Difficulty.HARD) }
        }

        should("onNewGameHandled returns to idle, so the next onNewGame generates again") {
            val first = testSudoku()
            val second = testSudoku()
            coEvery { generateSudoku(SudokuSize.NINE, Difficulty.HARD) } returnsMany listOf(first, second)
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)

            viewModel.onNewGameHandled(NewGame.Created(first.id))

            viewModel.newGame.value shouldBe NewGame.Idle
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)
            viewModel.newGame.value shouldBe NewGame.Created(second.id)
        }

        should("onNewGameHandled for another sudoku keeps the pending one") {
            val sudoku = testSudoku()
            coEvery { generateSudoku(SudokuSize.NINE, Difficulty.HARD) } returns sudoku
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)

            viewModel.onNewGameHandled(NewGame.Created(testSudoku().id))

            viewModel.newGame.value shouldBe NewGame.Created(sudoku.id)
        }

        should("getContinuableSudoku returns null when getRecentSudoku finds nothing") {
            coEvery { getRecentSudoku() } returns null

            viewModel.getContinuableSudoku() shouldBe null
        }

        should("getContinuableSudoku returns null when the recent sudoku is already completed") {
            val sudoku = testSudoku(completed = true)
            coEvery { getRecentSudoku() } returns sudoku

            viewModel.getContinuableSudoku() shouldBe null
        }

        should("getContinuableSudoku returns null when the recent sudoku's error limit is reached") {
            val sudoku = testSudoku(completed = false, errorsMade = userSettings.errorLimit)
            coEvery { getRecentSudoku() } returns sudoku

            sudoku.errorLimitReached(userSettings.errorLimit).shouldBeTrue()
            viewModel.getContinuableSudoku() shouldBe null
        }

        should("getContinuableSudoku returns the sudoku when it is neither completed nor error-limit-reached") {
            val sudoku = testSudoku(completed = false, errorsMade = 0)
            coEvery { getRecentSudoku() } returns sudoku

            sudoku.errorLimitReached(userSettings.errorLimit).shouldBeFalse()
            viewModel.getContinuableSudoku() shouldBe sudoku
        }

        should("checkDailySudokuCompleted delegates to isDailySudokuCompleted") {
            coEvery { isDailySudokuCompleted() } returns true

            viewModel.checkDailySudokuCompleted().shouldBeTrue()

            coVerify(exactly = 1) { isDailySudokuCompleted() }
        }

        should("checkDailySudokuCompleted returns false when isDailySudokuCompleted returns false") {
            coEvery { isDailySudokuCompleted() } returns false

            viewModel.checkDailySudokuCompleted().shouldBeFalse()
        }
    },
)
