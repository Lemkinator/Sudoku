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

package de.lemke.sudoku.domain

import de.lemke.sudoku.R
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.FollowUp
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testLevelSudoku
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class WinAftermathTest : ShouldSpec(
    {
        val dispatcher = UnconfinedTestDispatcher()
        val sudokusRepository = FakeSudokusRepository()
        val generateFields = GenerateFieldsUseCase(dispatcher, PatternSolvedBoardGenerator())
        val winAftermath =
            WinAftermath(
                GetMaxSudokuLevelUseCase(sudokusRepository, dispatcher),
                GenerateSudokuUseCase(generateFields, dispatcher),
                GenerateSudokuLevelUseCase(generateFields, dispatcher),
                CalculatePlayGamesSyncUseCase(sudokusRepository, dispatcher),
            )

        should("offer a new game after a normal sudoku") {
            winAftermath.followUpOf(testSudoku(mode = GameMode.Normal)) shouldBe FollowUp.NEW_GAME
        }

        should("offer nothing after a daily sudoku") {
            winAftermath.followUpOf(testSudoku(mode = GameMode.Daily(LocalDate.of(2026, 3, 5)))).shouldBeNull()
        }

        should("offer the next level after the highest stored level of its size") {
            sudokusRepository.saveSudoku(testLevelSudoku(SudokuSize.FOUR, level = 7))

            winAftermath.followUpOf(testLevelSudoku(SudokuSize.FOUR, level = 7)) shouldBe FollowUp.NEXT_LEVEL
        }

        should("offer nothing after a level below the highest stored level of its size") {
            sudokusRepository.saveSudoku(testLevelSudoku(SudokuSize.FOUR, level = 7))

            winAftermath.followUpOf(testLevelSudoku(SudokuSize.FOUR, level = 6)).shouldBeNull()
        }

        should("start a new game of the size and difficulty of the completed sudoku") {
            val completed = testSudoku(size = SudokuSize.FOUR, difficulty = Difficulty.HARD)

            val started = winAftermath.start(completed, FollowUp.NEW_GAME)

            started.info.size shouldBe SudokuSize.FOUR
            started.info.difficulty shouldBe Difficulty.HARD
            started.info.mode shouldBe GameMode.Normal
            started.board.cells shouldHaveSize 16
        }

        should("start the level after the completed level of the same size") {
            val completed = testLevelSudoku(SudokuSize.FOUR, level = 30)

            val started = winAftermath.start(completed, FollowUp.NEXT_LEVEL)

            started.info.size shouldBe SudokuSize.FOUR
            started.info.mode shouldBe GameMode.Level(31)
            started.info.difficulty shouldBe Difficulty.EASY
        }

        should("calculate the Play Games sync from the completed sudokus in the repository") {
            sudokusRepository.saveSudoku(testSudoku(size = SudokuSize.FOUR, completed = true))

            val sync = winAftermath.playGamesSync()

            sync.leaderboardScores shouldContain (R.string.leaderboard_total_wins to 1L)
            sync.leaderboardScores shouldContain (R.string.leaderboard_wins_44 to 1L)
        }
    },
)
