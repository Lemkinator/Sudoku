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
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Difficulty.EASY
import de.lemke.sudoku.domain.model.Difficulty.EXPERT
import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Difficulty.MEDIUM
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
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

private fun wonSudoku(
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
    testSudoku(size = size, modeLevel = modeLevel, difficulty = difficulty, completed = true).apply {
        this.seconds = seconds
        this.eraserUsed = eraserUsed
        this.hintsUsed = hintsUsed
        this.notesMade = notesMade
        this.isChecklist = isChecklist
        this.isReverseChecklist = isReverseChecklist
    }

@OptIn(ExperimentalCoroutinesApi::class)
private fun calculatePlayGamesSyncOver(vararg sudokus: Sudoku): CalculatePlayGamesSyncUseCase {
    val dispatcher = UnconfinedTestDispatcher()
    val sudokusRepository = FakeSudokusRepository()
    sudokus.forEach { sudokusRepository.insertRows(sudokuWithFieldsToDb(it)) }
    return CalculatePlayGamesSyncUseCase(GetAllSudokusUseCase(sudokusRepository, dispatcher), dispatcher)
}

class CalculatePlayGamesSyncUseCaseTest : ShouldSpec(
    {
        should("only submit base leaderboard scores, no unlocks or increments, when no sudoku just finished") {
            val useCase = calculatePlayGamesSyncOver(wonSudoku())

            val sync = useCase(null)

            sync.leaderboardScores.map { it.first } shouldContain R.string.leaderboard_total_wins
            sync.leaderboardScores.map { it.first } shouldNotContain R.string.leaderboard_best_time
            sync.achievementUnlocks shouldBe emptyList()
            sync.achievementIncrements shouldBe emptyList()
        }

        should("unlock the no-hints and eraser achievements but not the checklist ones for a plain win") {
            val won = wonSudoku(eraserUsed = true, hintsUsed = 0, notesMade = 0)
            val useCase = calculatePlayGamesSyncOver(won)

            val unlocks = useCase(won).achievementUnlocks

            unlocks shouldContain R.string.achievement_first_win
            unlocks shouldContain R.string.achievement_eraser
            unlocks shouldContain R.string.achievement_no_hints
            unlocks shouldNotContain R.string.achievement_use_notes
            unlocks shouldNotContain R.string.achievement_checklist
        }

        should("unlock the notes and checklist achievements when their flags are set") {
            val sudoku = wonSudoku(notesMade = 3, isChecklist = true, isReverseChecklist = true)
            val useCase = calculatePlayGamesSyncOver(sudoku)

            val unlocks = useCase(sudoku).achievementUnlocks

            unlocks shouldContain R.string.achievement_use_notes
            unlocks shouldContain R.string.achievement_checklist
            unlocks shouldContain R.string.achievement_reverse_checklist
        }

        should("unlock the speed achievement only under its 10-second threshold") {
            val fast = wonSudoku(seconds = 9)
            val slow = wonSudoku(seconds = 10)
            val useCase = calculatePlayGamesSyncOver(fast, slow)

            useCase(fast).achievementUnlocks shouldContain R.string.achievement_i_am_speed
            useCase(slow).achievementUnlocks shouldNotContain R.string.achievement_i_am_speed
        }

        should("not unlock the no-hints achievement when a hint was used") {
            val sudoku = wonSudoku(hintsUsed = 1)
            val useCase = calculatePlayGamesSyncOver(sudoku)

            useCase(sudoku).achievementUnlocks shouldNotContain R.string.achievement_no_hints
        }

        should("count only sudokus matching size, and matching both size and difficulty, for their respective win totals") {
            val sudoku = wonSudoku(size = SudokuSize.NINE, difficulty = HARD, seconds = 42)
            val sameSizeDifferentDifficulty = wonSudoku(size = SudokuSize.NINE, difficulty = VERY_EASY)
            val differentSize = wonSudoku(size = SudokuSize.FOUR)
            val useCase = calculatePlayGamesSyncOver(sudoku, sameSizeDifferentDifficulty, differentSize)

            val scores = useCase(sudoku).leaderboardScores.toMap()

            scores[R.string.leaderboard_wins_99] shouldBe 2L
            scores[R.string.leaderboard_wins_99_hard] shouldBe 1L
        }

        should("submit the base scores, counting level sudokus only toward the level leaderboard of their size") {
            val useCase =
                calculatePlayGamesSyncOver(
                    wonSudoku(size = SudokuSize.FOUR, modeLevel = 1),
                    wonSudoku(size = SudokuSize.NINE, modeLevel = 1),
                    wonSudoku(size = SudokuSize.NINE, modeLevel = 2),
                    wonSudoku(size = SudokuSize.SIXTEEN, modeLevel = 1),
                    wonSudoku(size = SudokuSize.SIXTEEN, modeLevel = 2),
                    wonSudoku(size = SudokuSize.SIXTEEN, modeLevel = 3),
                    wonSudoku(size = SudokuSize.FOUR),
                    wonSudoku(size = SudokuSize.NINE, modeLevel = Sudoku.MODE_DAILY),
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
                val sudoku = wonSudoku(size = expected.size, difficulty = expected.difficulty, seconds = 42)
                val useCase = calculatePlayGamesSyncOver(sudoku)

                val scores = useCase(sudoku).leaderboardScores

                withClue("${expected.size} ${expected.difficulty}") {
                    scores shouldContain (expected.timeLeaderboard to 42_000L)
                    scores shouldContain (expected.winsLeaderboard to 1L)
                }
            }
        }

        should("increment the 10 and 50 sudokus achievements and submit the wins leaderboard of each size") {
            sizeExpectations.forEach { expected ->
                val sudoku = wonSudoku(size = expected.size)
                val useCase = calculatePlayGamesSyncOver(sudoku)

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
                val fast = wonSudoku(size = expected.size, seconds = expected.stopwatchSeconds - 1)
                val slow = wonSudoku(size = expected.size, seconds = expected.stopwatchSeconds)
                val useCase = calculatePlayGamesSyncOver(fast, slow)

                withClue(expected.size) {
                    useCase(fast).achievementUnlocks shouldContain expected.stopwatchAchievement
                    useCase(slow).achievementUnlocks shouldNotContain expected.stopwatchAchievement
                }
            }
        }

        should("increment the 10 and 50 sudokus achievements of each difficulty") {
            difficultyAchievementExpectations.forEach { (difficulty, achievement10, achievement50) ->
                val sudoku = wonSudoku(difficulty = difficulty)
                val useCase = calculatePlayGamesSyncOver(sudoku)

                val increments = useCase(sudoku).achievementIncrements

                withClue(difficulty) {
                    increments shouldContain (achievement10 to 1)
                    increments shouldContain (achievement50 to 1)
                }
            }
        }
    },
)
