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
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Difficulty.EASY
import de.lemke.sudoku.domain.model.Difficulty.EXPERT
import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Difficulty.MEDIUM
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private data class SizeExpectation(
    val size: SudokuSize,
    val achievement10: Int,
    val achievement50: Int,
    val stopwatchAchievement: Int,
    val stopwatchSeconds: Int,
    val winsLeaderboard: Int,
)

private data class SizeDifficultyExpectation(
    val size: SudokuSize,
    val difficulty: Difficulty,
    val timeLeaderboard: Int,
    val winsLeaderboard: Int,
)

private val sizeExpectations =
    listOf(
        SizeExpectation(
            size = SudokuSize.FOUR,
            achievement10 = R.string.achievement_10_sudokus_44,
            achievement50 = R.string.achievement_50_sudokus_44,
            stopwatchAchievement = R.string.achievement_stopwatch_44,
            stopwatchSeconds = 30,
            winsLeaderboard = R.string.leaderboard_wins_44,
        ),
        SizeExpectation(
            size = SudokuSize.NINE,
            achievement10 = R.string.achievement_10_sudokus_99,
            achievement50 = R.string.achievement_50_sudokus_99,
            stopwatchAchievement = R.string.achievement_stopwatch_99,
            stopwatchSeconds = 120,
            winsLeaderboard = R.string.leaderboard_wins_99,
        ),
        SizeExpectation(
            size = SudokuSize.SIXTEEN,
            achievement10 = R.string.achievement_10_sudokus_1616,
            achievement50 = R.string.achievement_50_sudokus_1616,
            stopwatchAchievement = R.string.achievement_stopwatch_1616,
            stopwatchSeconds = 420,
            winsLeaderboard = R.string.leaderboard_wins_1616,
        ),
    )

private val sizeDifficultyExpectations =
    listOf(
        SizeDifficultyExpectation(
            SudokuSize.FOUR,
            VERY_EASY,
            R.string.leaderboard_time_44_very_easy,
            R.string.leaderboard_wins_44_very_easy,
        ),
        SizeDifficultyExpectation(SudokuSize.FOUR, EASY, R.string.leaderboard_time_44_easy, R.string.leaderboard_wins_44_easy),
        SizeDifficultyExpectation(SudokuSize.FOUR, MEDIUM, R.string.leaderboard_time_44_medium, R.string.leaderboard_wins_44_medium),
        SizeDifficultyExpectation(SudokuSize.FOUR, HARD, R.string.leaderboard_time_44_hard, R.string.leaderboard_wins_44_hard),
        SizeDifficultyExpectation(SudokuSize.FOUR, EXPERT, R.string.leaderboard_time_44_expert, R.string.leaderboard_wins_44_expert),
        SizeDifficultyExpectation(
            SudokuSize.NINE,
            VERY_EASY,
            R.string.leaderboard_time_99_very_easy,
            R.string.leaderboard_wins_99_very_easy,
        ),
        SizeDifficultyExpectation(SudokuSize.NINE, EASY, R.string.leaderboard_time_99_easy, R.string.leaderboard_wins_99_easy),
        SizeDifficultyExpectation(SudokuSize.NINE, MEDIUM, R.string.leaderboard_time_99_medium, R.string.leaderboard_wins_99_medium),
        SizeDifficultyExpectation(SudokuSize.NINE, HARD, R.string.leaderboard_time_99_hard, R.string.leaderboard_wins_99_hard),
        SizeDifficultyExpectation(SudokuSize.NINE, EXPERT, R.string.leaderboard_time_99_expert, R.string.leaderboard_wins_99_expert),
        SizeDifficultyExpectation(
            SudokuSize.SIXTEEN,
            VERY_EASY,
            R.string.leaderboard_time_1616_very_easy,
            R.string.leaderboard_wins_1616_very_easy,
        ),
        SizeDifficultyExpectation(SudokuSize.SIXTEEN, EASY, R.string.leaderboard_time_1616_easy, R.string.leaderboard_wins_1616_easy),
        SizeDifficultyExpectation(SudokuSize.SIXTEEN, MEDIUM, R.string.leaderboard_time_1616_medium, R.string.leaderboard_wins_1616_medium),
        SizeDifficultyExpectation(SudokuSize.SIXTEEN, HARD, R.string.leaderboard_time_1616_hard, R.string.leaderboard_wins_1616_hard),
        SizeDifficultyExpectation(SudokuSize.SIXTEEN, EXPERT, R.string.leaderboard_time_1616_expert, R.string.leaderboard_wins_1616_expert),
    )

private val difficultyAchievementExpectations =
    listOf(
        Triple(VERY_EASY, R.string.achievement_10_sudokus_very_easy, R.string.achievement_50_sudokus_very_easy),
        Triple(EASY, R.string.achievement_10_sudokus_easy, R.string.achievement_50_sudokus_easy),
        Triple(MEDIUM, R.string.achievement_10_sudokus_medium, R.string.achievement_50_sudokus_medium),
        Triple(HARD, R.string.achievement_10_sudokus_hard, R.string.achievement_50_sudokus_hard),
        Triple(EXPERT, R.string.achievement_10_sudokus_expert, R.string.achievement_50_sudokus_expert),
    )

private fun testSudoku(
    difficulty: Difficulty = VERY_EASY,
    size: SudokuSize = SudokuSize.FOUR,
    modeLevel: Int = Sudoku.MODE_NORMAL,
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
        modeLevel = modeLevel,
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

        should("unlock the notes and checklist achievements when their flags are set") {
            val sudoku = testSudoku(notesMade = 3, isChecklist = true, isReverseChecklist = true)
            coEvery { getAllSudokus() } returns listOf(sudoku)

            val unlocks = useCase(sudoku).achievementUnlocks

            unlocks shouldContain R.string.achievement_use_notes
            unlocks shouldContain R.string.achievement_checklist
            unlocks shouldContain R.string.achievement_reverse_checklist
        }

        should("unlock the speed achievement only under its 10-second threshold") {
            val fast = testSudoku(seconds = 9)
            val slow = testSudoku(seconds = 10)
            coEvery { getAllSudokus() } returns listOf(fast, slow)

            useCase(fast).achievementUnlocks shouldContain R.string.achievement_i_am_speed
            useCase(slow).achievementUnlocks shouldNotContain R.string.achievement_i_am_speed
        }

        should("not unlock the no-hints achievement when a hint was used") {
            val sudoku = testSudoku(hintsUsed = 1)
            coEvery { getAllSudokus() } returns listOf(sudoku)

            useCase(sudoku).achievementUnlocks shouldNotContain R.string.achievement_no_hints
        }

        should("count only sudokus matching size, and matching both size and difficulty, for their respective win totals") {
            val sudoku = testSudoku(size = SudokuSize.NINE, difficulty = HARD, seconds = 42)
            val sameSizeDifferentDifficulty = testSudoku(size = SudokuSize.NINE, difficulty = VERY_EASY)
            val differentSize = testSudoku(size = SudokuSize.FOUR)
            coEvery { getAllSudokus() } returns listOf(sudoku, sameSizeDifferentDifficulty, differentSize)

            val scores = useCase(sudoku).leaderboardScores.toMap()

            scores[R.string.leaderboard_wins_99] shouldBe 2L
            scores[R.string.leaderboard_wins_99_hard] shouldBe 1L
        }

        should("submit the base scores, counting level sudokus only toward the level leaderboard of their size") {
            coEvery { getAllSudokus() } returns
                listOf(
                    testSudoku(size = SudokuSize.FOUR, modeLevel = 1),
                    testSudoku(size = SudokuSize.NINE, modeLevel = 1),
                    testSudoku(size = SudokuSize.NINE, modeLevel = 2),
                    testSudoku(size = SudokuSize.SIXTEEN, modeLevel = 1),
                    testSudoku(size = SudokuSize.SIXTEEN, modeLevel = 2),
                    testSudoku(size = SudokuSize.SIXTEEN, modeLevel = 3),
                    testSudoku(size = SudokuSize.FOUR),
                    testSudoku(size = SudokuSize.NINE, modeLevel = Sudoku.MODE_DAILY),
                )

            useCase(null).leaderboardScores shouldBe
                listOf(
                    R.string.leaderboard_total_wins to 8L,
                    R.string.leaderboard_daily_sudokus to 1L,
                    R.string.leaderboard_level_44 to 1L,
                    R.string.leaderboard_level_99 to 2L,
                    R.string.leaderboard_level_1616 to 3L,
                )
        }

        should("submit the time and win-count leaderboards of each size and difficulty pair") {
            sizeDifficultyExpectations.forEach { expected ->
                val sudoku = testSudoku(size = expected.size, difficulty = expected.difficulty, seconds = 42)
                coEvery { getAllSudokus() } returns listOf(sudoku)

                val scores = useCase(sudoku).leaderboardScores

                withClue("${expected.size} ${expected.difficulty}") {
                    scores shouldContain (expected.timeLeaderboard to 42_000L)
                    scores shouldContain (expected.winsLeaderboard to 1L)
                }
            }
        }

        should("increment the 10 and 50 sudokus achievements and submit the wins leaderboard of each size") {
            sizeExpectations.forEach { expected ->
                val sudoku = testSudoku(size = expected.size)
                coEvery { getAllSudokus() } returns listOf(sudoku)

                val sync = useCase(sudoku)

                withClue(expected.size) {
                    sync.achievementIncrements shouldContain (expected.achievement10 to 1)
                    sync.achievementIncrements shouldContain (expected.achievement50 to 1)
                    sync.leaderboardScores shouldContain (expected.winsLeaderboard to 1L)
                }
            }
        }

        should("unlock the stopwatch achievement of each size only below its threshold") {
            sizeExpectations.forEach { expected ->
                val fast = testSudoku(size = expected.size, seconds = expected.stopwatchSeconds - 1)
                val slow = testSudoku(size = expected.size, seconds = expected.stopwatchSeconds)
                coEvery { getAllSudokus() } returns listOf(fast, slow)

                withClue(expected.size) {
                    useCase(fast).achievementUnlocks shouldContain expected.stopwatchAchievement
                    useCase(slow).achievementUnlocks shouldNotContain expected.stopwatchAchievement
                }
            }
        }

        should("increment the 10 and 50 sudokus achievements of each difficulty") {
            difficultyAchievementExpectations.forEach { (difficulty, achievement10, achievement50) ->
                val sudoku = testSudoku(difficulty = difficulty)
                coEvery { getAllSudokus() } returns listOf(sudoku)

                val increments = useCase(sudoku).achievementIncrements

                withClue(difficulty) {
                    increments shouldContain (achievement10 to 1)
                    increments shouldContain (achievement50 to 1)
                }
            }
        }
    },
)
