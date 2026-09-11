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
import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private fun testSudoku(
    difficulty: de.lemke.sudoku.domain.model.Difficulty = VERY_EASY,
    size: Int = 4,
    seconds: Int = 0,
    eraserUsed: Boolean = false,
    hintsUsed: Int = 0,
    notesMade: Int = 0,
    isChecklist: Boolean = false,
    isReverseChecklist: Boolean = false,
): Sudoku =
    Sudoku.create(
        size = size,
        difficulty = difficulty,
        modeLevel = Sudoku.MODE_NORMAL,
        seconds = seconds,
        eraserUsed = eraserUsed,
        hintsUsed = hintsUsed,
        notesMade = notesMade,
        isChecklist = isChecklist,
        isReverseChecklist = isReverseChecklist,
        fields = mutableListOf(Field(position = Position.create(0, size), solution = 1, value = 1)),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatePlayGamesSyncUseCaseTest : ShouldSpec(
    {
        val getAllSudokus = mockk<GetAllSudokusUseCase>()
        val useCase = CalculatePlayGamesSyncUseCase(getAllSudokus, UnconfinedTestDispatcher())

        beforeEach { clearMocks(getAllSudokus) }

        should("only submit base leaderboard scores, no unlocks or increments, when no sudoku just finished") {
            coEvery { getAllSudokus() } returns listOf(testSudoku())

            val sync = useCase(null)

            sync.leaderboardScores.map { it.first } shouldContain R.string.leaderboard_total_wins
            sync.leaderboardScores.map { it.first } shouldNotContain R.string.leaderboard_best_time
            sync.achievementUnlocks shouldBe emptyList()
            sync.achievementIncrements shouldBe emptyList()
        }

        should("unlock the no-hints and eraser achievements but not the checklist ones for a plain win") {
            val won = testSudoku(eraserUsed = true, hintsUsed = 0, notesMade = 0)
            coEvery { getAllSudokus() } returns listOf(won)

            val unlocks = useCase(won).achievementUnlocks

            unlocks shouldContain R.string.achievement_first_win
            unlocks shouldContain R.string.achievement_eraser
            unlocks shouldContain R.string.achievement_no_hints
            unlocks shouldNotContain R.string.achievement_use_notes
            unlocks shouldNotContain R.string.achievement_checklist
        }

        should("unlock the size-specific stopwatch achievement only under its threshold") {
            val fast = testSudoku(size = 4, seconds = 29)
            val slow = testSudoku(size = 4, seconds = 31)
            coEvery { getAllSudokus() } returns listOf(fast, slow)

            useCase(fast).achievementUnlocks shouldContain R.string.achievement_stopwatch_44
            useCase(slow).achievementUnlocks shouldNotContain R.string.achievement_stopwatch_44
        }

        should("submit the size+difficulty leaderboard time and win-count scores for the finished sudoku") {
            val sudoku = testSudoku(size = 9, difficulty = HARD, seconds = 42)
            coEvery { getAllSudokus() } returns listOf(sudoku)

            val scoreIds = useCase(sudoku).leaderboardScores.map { it.first }

            scoreIds shouldContain R.string.leaderboard_time_99_hard
            scoreIds shouldContain R.string.leaderboard_wins_99_hard
        }
    },
)
