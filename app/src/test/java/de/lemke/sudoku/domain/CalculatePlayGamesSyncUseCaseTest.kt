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
import de.lemke.sudoku.domain.model.Difficulty.EASY
import de.lemke.sudoku.domain.model.Difficulty.EXPERT
import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Difficulty.MEDIUM
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.time.LocalDate
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
    mode: GameMode = GameMode.Normal,
    seconds: Int = 0,
    eraserUsed: Boolean = false,
    hintsUsed: Int = 0,
    notesMade: Int = 0,
    isChecklist: Boolean = false,
    isReverseChecklist: Boolean = false,
): Sudoku =
    testSudoku(size = size, mode = mode, difficulty = difficulty, completed = true).let {
        it.copy(
            info =
                it.info.copy(
                    seconds = seconds,
                    eraserUsed = eraserUsed,
                    hintsUsed = hintsUsed,
                    notesMade = notesMade,
                    isChecklist = isChecklist,
                    isReverseChecklist = isReverseChecklist,
                ),
        )
    }

private fun repositoryOver(vararg sudokus: Sudoku): FakeSudokusRepository = FakeSudokusRepository().also { it.store(*sudokus) }

@OptIn(ExperimentalCoroutinesApi::class)
private fun calculatePlayGamesSyncOver(sudokusRepository: FakeSudokusRepository): CalculatePlayGamesSyncUseCase =
    CalculatePlayGamesSyncUseCase(sudokusRepository, UnconfinedTestDispatcher())

private fun calculatePlayGamesSyncOver(vararg sudokus: Sudoku): CalculatePlayGamesSyncUseCase =
    calculatePlayGamesSyncOver(repositoryOver(*sudokus))

private val fastFour = wonSudoku(seconds = 5, eraserUsed = true, hintsUsed = 0)
private val slowFourLevel = wonSudoku(mode = GameMode.Level(1), seconds = 40, hintsUsed = 2, notesMade = 3)
private val nineDaily =
    wonSudoku(
        size = SudokuSize.NINE,
        difficulty = HARD,
        mode = GameMode.Daily(LocalDate.of(2026, 1, 1)),
        seconds = 200,
        hintsUsed = 1,
        isChecklist = true,
    )
private val nineNormal = wonSudoku(size = SudokuSize.NINE, difficulty = HARD, seconds = 100, hintsUsed = 1, isReverseChecklist = true)
private val sixteenLevel =
    wonSudoku(size = SudokuSize.SIXTEEN, difficulty = EXPERT, mode = GameMode.Level(1), seconds = 500, hintsUsed = 3)

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatePlayGamesSyncUseCaseTest : ShouldSpec(
    {
        should("derive every leaderboard score, unlock and step count from the stored completed games") {
            val useCase = calculatePlayGamesSyncOver(fastFour, slowFourLevel, nineDaily, nineNormal, sixteenLevel)

            useCase() shouldBe
                PlayGamesSync(
                    leaderboardScores =
                        listOf(
                            R.string.leaderboard_total_wins to 5L,
                            R.string.leaderboard_daily_sudokus to 1L,
                            R.string.leaderboard_level_44 to 1L,
                            R.string.leaderboard_level_99 to 0L,
                            R.string.leaderboard_level_1616 to 1L,
                            R.string.leaderboard_best_time to 5_000L,
                            R.string.leaderboard_wins_44 to 2L,
                            R.string.leaderboard_time_44_very_easy to 5_000L,
                            R.string.leaderboard_wins_44_very_easy to 2L,
                            R.string.leaderboard_wins_99 to 2L,
                            R.string.leaderboard_time_99_hard to 100_000L,
                            R.string.leaderboard_wins_99_hard to 2L,
                            R.string.leaderboard_wins_1616 to 1L,
                            R.string.leaderboard_time_1616_expert to 500_000L,
                            R.string.leaderboard_wins_1616_expert to 1L,
                        ),
                    achievementUnlocks =
                        listOf(
                            R.string.achievement_first_win,
                            R.string.achievement_eraser,
                            R.string.achievement_no_hints,
                            R.string.achievement_use_notes,
                            R.string.achievement_checklist,
                            R.string.achievement_reverse_checklist,
                            R.string.achievement_i_am_speed,
                            R.string.achievement_stopwatch_44,
                            R.string.achievement_stopwatch_99,
                        ),
                    achievementSteps =
                        listOf(
                            R.string.achievement_10_sudokus_44 to 2,
                            R.string.achievement_50_sudokus_44 to 2,
                            R.string.achievement_10_sudokus_99 to 2,
                            R.string.achievement_50_sudokus_99 to 2,
                            R.string.achievement_10_sudokus_1616 to 1,
                            R.string.achievement_50_sudokus_1616 to 1,
                            R.string.achievement_10_sudokus_very_easy to 2,
                            R.string.achievement_50_sudokus_very_easy to 2,
                            R.string.achievement_10_sudokus_hard to 2,
                            R.string.achievement_50_sudokus_hard to 2,
                            R.string.achievement_10_sudokus_expert to 1,
                            R.string.achievement_50_sudokus_expert to 1,
                        ),
                )
        }

        should("return an equal sync when invoked a second time") {
            val useCase = calculatePlayGamesSyncOver(fastFour, slowFourLevel, nineDaily, nineNormal, sixteenLevel)

            val first = useCase()

            useCase() shouldBe first
        }

        should("only submit the zero base scores, no unlocks or steps, for an empty store") {
            val useCase = calculatePlayGamesSyncOver()

            useCase() shouldBe
                PlayGamesSync(
                    leaderboardScores =
                        listOf(
                            R.string.leaderboard_total_wins to 0L,
                            R.string.leaderboard_daily_sudokus to 0L,
                            R.string.leaderboard_level_44 to 0L,
                            R.string.leaderboard_level_99 to 0L,
                            R.string.leaderboard_level_1616 to 0L,
                        ),
                    achievementUnlocks = emptyList(),
                    achievementSteps = emptyList(),
                )
        }

        should("ignore sudokus that are not completed") {
            val unfinished = testSudoku(size = SudokuSize.NINE, difficulty = HARD)
            val useCase = calculatePlayGamesSyncOver(fastFour, unfinished)

            useCase().leaderboardScores.first() shouldBe (R.string.leaderboard_total_wins to 1L)
        }

        should("drop a deleted game from the sync and from the statistics") {
            val repository = repositoryOver(fastFour, slowFourLevel, nineDaily, nineNormal, sixteenLevel)
            val sync = calculatePlayGamesSyncOver(repository)
            val statistics = CalculateStatisticsUseCase(UnconfinedTestDispatcher())
            val delete = DeleteSudokusUseCase(repository, UnconfinedTestDispatcher())
            statistics(repository.getCompletedSummaries()).gamesCompleted shouldBe 5

            delete(listOf(nineNormal.info.id))

            sync() shouldBe
                PlayGamesSync(
                    leaderboardScores =
                        listOf(
                            R.string.leaderboard_total_wins to 4L,
                            R.string.leaderboard_daily_sudokus to 1L,
                            R.string.leaderboard_level_44 to 1L,
                            R.string.leaderboard_level_99 to 0L,
                            R.string.leaderboard_level_1616 to 1L,
                            R.string.leaderboard_best_time to 5_000L,
                            R.string.leaderboard_wins_44 to 2L,
                            R.string.leaderboard_time_44_very_easy to 5_000L,
                            R.string.leaderboard_wins_44_very_easy to 2L,
                            R.string.leaderboard_wins_99 to 1L,
                            R.string.leaderboard_time_99_hard to 200_000L,
                            R.string.leaderboard_wins_99_hard to 1L,
                            R.string.leaderboard_wins_1616 to 1L,
                            R.string.leaderboard_time_1616_expert to 500_000L,
                            R.string.leaderboard_wins_1616_expert to 1L,
                        ),
                    achievementUnlocks =
                        listOf(
                            R.string.achievement_first_win,
                            R.string.achievement_eraser,
                            R.string.achievement_no_hints,
                            R.string.achievement_use_notes,
                            R.string.achievement_checklist,
                            R.string.achievement_i_am_speed,
                            R.string.achievement_stopwatch_44,
                        ),
                    achievementSteps =
                        listOf(
                            R.string.achievement_10_sudokus_44 to 2,
                            R.string.achievement_50_sudokus_44 to 2,
                            R.string.achievement_10_sudokus_99 to 1,
                            R.string.achievement_50_sudokus_99 to 1,
                            R.string.achievement_10_sudokus_1616 to 1,
                            R.string.achievement_50_sudokus_1616 to 1,
                            R.string.achievement_10_sudokus_very_easy to 2,
                            R.string.achievement_50_sudokus_very_easy to 2,
                            R.string.achievement_10_sudokus_hard to 1,
                            R.string.achievement_50_sudokus_hard to 1,
                            R.string.achievement_10_sudokus_expert to 1,
                            R.string.achievement_50_sudokus_expert to 1,
                        ),
                )
            val remaining = statistics(repository.getCompletedSummaries())
            remaining.gamesCompleted shouldBe 4
            remaining.totalSecondsPlayed shouldBe 745L
        }

        should("unlock the no-hints and eraser achievements but not the notes and checklist ones for a plain win") {
            val unlocks = calculatePlayGamesSyncOver(wonSudoku(eraserUsed = true, hintsUsed = 0))().achievementUnlocks

            unlocks shouldContain R.string.achievement_first_win
            unlocks shouldContain R.string.achievement_eraser
            unlocks shouldContain R.string.achievement_no_hints
            unlocks shouldNotContain R.string.achievement_use_notes
            unlocks shouldNotContain R.string.achievement_checklist
        }

        should("unlock the speed achievement only under its 10-second threshold") {
            calculatePlayGamesSyncOver(wonSudoku(seconds = 9))().achievementUnlocks shouldContain R.string.achievement_i_am_speed
            calculatePlayGamesSyncOver(wonSudoku(seconds = 10))().achievementUnlocks shouldNotContain R.string.achievement_i_am_speed
        }

        should("not unlock the no-hints achievement when every win used a hint") {
            calculatePlayGamesSyncOver(wonSudoku(hintsUsed = 1))().achievementUnlocks shouldNotContain R.string.achievement_no_hints
        }

        should("submit the time and win-count leaderboards of each size and difficulty pair") {
            sizeDifficultyExpectations.forEach { expected ->
                val scores =
                    calculatePlayGamesSyncOver(wonSudoku(size = expected.size, difficulty = expected.difficulty, seconds = 42))()
                        .leaderboardScores

                withClue("${expected.size} ${expected.difficulty}") {
                    scores shouldContain (expected.timeLeaderboard to 42_000L)
                    scores shouldContain (expected.winsLeaderboard to 1L)
                }
            }
        }

        should("set the 10 and 50 sudokus achievements and submit the wins leaderboard of each size") {
            sizeExpectations.forEach { expected ->
                val sync = calculatePlayGamesSyncOver(wonSudoku(size = expected.size))()

                withClue(expected.size) {
                    sync.achievementSteps shouldContain (expected.achievement10 to 1)
                    sync.achievementSteps shouldContain (expected.achievement50 to 1)
                    sync.leaderboardScores shouldContain (expected.winsLeaderboard to 1L)
                }
            }
        }

        should("unlock the stopwatch achievement of each size only below its threshold") {
            sizeExpectations.forEach { expected ->
                val fast = calculatePlayGamesSyncOver(wonSudoku(size = expected.size, seconds = expected.stopwatchSeconds - 1))()
                val slow = calculatePlayGamesSyncOver(wonSudoku(size = expected.size, seconds = expected.stopwatchSeconds))()

                withClue(expected.size) {
                    fast.achievementUnlocks shouldContain expected.stopwatchAchievement
                    slow.achievementUnlocks shouldNotContain expected.stopwatchAchievement
                }
            }
        }

        should("set the 10 and 50 sudokus achievements of each difficulty") {
            difficultyAchievementExpectations.forEach { (difficulty, achievement10, achievement50) ->
                val steps = calculatePlayGamesSyncOver(wonSudoku(difficulty = difficulty))().achievementSteps

                withClue(difficulty) {
                    steps shouldContain (achievement10 to 1)
                    steps shouldContain (achievement50 to 1)
                }
            }
        }
    },
)
