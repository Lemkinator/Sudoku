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
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.data.database.sudokuFromDb
import de.lemke.sudoku.domain.GenerateFieldsUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.GetRecentlyUpdatedNormalSudokuUseCase
import de.lemke.sudoku.domain.IsDailySudokuCompletedUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SolvedBoardGenerator
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private const val GENERATE = "generate"
private const val SAVE = "save"

@OptIn(ExperimentalCoroutinesApi::class)
class TabSudokuViewModelTest : ShouldSpec(
    {
        val clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC)
        lateinit var userSettings: UserSettings
        lateinit var repository: FakeSudokusRepository
        lateinit var events: MutableList<String>
        lateinit var viewModel: TabSudokuViewModel

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            repository = FakeSudokusRepository()
            events = mutableListOf()
            repository.beforeSave = { events += SAVE }
            val dispatcher = UnconfinedTestDispatcher()
            val solvedBoardGenerator =
                SolvedBoardGenerator { schema ->
                    events += GENERATE
                    PatternSolvedBoardGenerator().generate(schema)
                }
            viewModel =
                TabSudokuViewModel(
                    userSettings,
                    GenerateSudokuUseCase(GenerateFieldsUseCase(dispatcher, solvedBoardGenerator), dispatcher),
                    SaveSudokuUseCase(repository, dispatcher),
                    GetRecentlyUpdatedNormalSudokuUseCase(repository, dispatcher),
                    IsDailySudokuCompletedUseCase(GetAllSudokusUseCase(repository, dispatcher), clock, dispatcher),
                )
        }

        fun savedIds() = repository.saves.map { SudokuId(it.rows.sudoku.id) }

        fun generations() = events.count { it == GENERATE }

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
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)

            viewModel.newGame.value shouldBe NewGame.Created(savedIds().single())
            events shouldBe listOf(GENERATE, SAVE)
            val save = repository.saves.single()
            save.onlyUpdate shouldBe false
            sudokuFromDb(save.rows)?.let { Triple(it.size, it.difficulty, it.modeLevel) } shouldBe
                Triple(SudokuSize.NINE, Difficulty.HARD, Sudoku.MODE_NORMAL)
        }

        should("onNewGame reports generating until the sudoku is saved") {
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { saved.await() }

            viewModel.onNewGame(SudokuSize.FOUR, Difficulty.EASY)

            viewModel.newGame.value shouldBe NewGame.Generating
            repository.saves shouldBe emptyList()
            saved.complete(Unit)
            viewModel.newGame.value shouldBe NewGame.Created(savedIds().single())
        }

        should("a second onNewGame while generating generates once") {
            val saved = CompletableDeferred<Unit>()
            repository.beforeSave = { saved.await() }

            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)
            saved.complete(Unit)

            generations() shouldBe 1
            repository.saves.size shouldBe 1
        }

        should("onNewGame while a created sudoku is not handled yet keeps that sudoku") {
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)

            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)

            viewModel.newGame.value shouldBe NewGame.Created(savedIds().single())
            generations() shouldBe 1
        }

        should("onNewGameHandled returns to idle, so the next onNewGame generates again") {
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)
            val first = viewModel.newGame.value.shouldBeInstanceOf<NewGame.Created>()

            viewModel.onNewGameHandled(first)

            viewModel.newGame.value shouldBe NewGame.Idle
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)
            val second = savedIds().last()
            second shouldNotBe first.sudokuId
            viewModel.newGame.value shouldBe NewGame.Created(second)
            generations() shouldBe 2
        }

        should("onNewGameHandled for another sudoku keeps the pending one") {
            viewModel.onNewGame(SudokuSize.NINE, Difficulty.HARD)

            viewModel.onNewGameHandled(NewGame.Created(testSudoku().id))

            viewModel.newGame.value shouldBe NewGame.Created(savedIds().single())
        }

        should("getContinuableSudoku returns null when getRecentSudoku finds nothing") {
            viewModel.getContinuableSudoku() shouldBe null
        }

        should("getContinuableSudoku returns null when the recent sudoku is already completed") {
            repository.store(testSudoku(completed = true))

            viewModel.getContinuableSudoku() shouldBe null
        }

        should("getContinuableSudoku returns null when the recent sudoku's error limit is reached") {
            val sudoku = testSudoku().apply { errorsMade = userSettings.errorLimit }
            repository.store(sudoku)

            sudoku.errorLimitReached(userSettings.errorLimit).shouldBeTrue()
            viewModel.getContinuableSudoku() shouldBe null
        }

        should("getContinuableSudoku returns the sudoku when it is neither completed nor error-limit-reached") {
            val sudoku = testSudoku()
            repository.store(sudoku)

            sudoku.errorLimitReached(userSettings.errorLimit).shouldBeFalse()
            viewModel.getContinuableSudoku() shouldBe sudoku
        }

        should("checkDailySudokuCompleted returns true when today's daily sudoku is completed") {
            repository.store(testSudoku(modeLevel = Sudoku.MODE_DAILY, created = LocalDateTime.now(clock), completed = true))

            viewModel.checkDailySudokuCompleted().shouldBeTrue()
        }

        should("checkDailySudokuCompleted returns false when isDailySudokuCompleted returns false") {
            repository.store(
                testSudoku(modeLevel = Sudoku.MODE_DAILY, created = LocalDateTime.now(clock)),
                testSudoku(modeLevel = Sudoku.MODE_DAILY, created = LocalDateTime.now(clock).minusDays(1), completed = true),
                testSudoku(completed = true),
            )

            viewModel.checkDailySudokuCompleted().shouldBeFalse()
        }
    },
)
