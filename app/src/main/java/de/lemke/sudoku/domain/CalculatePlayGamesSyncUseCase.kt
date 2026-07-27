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
import de.lemke.sudoku.domain.model.Difficulty.EASY
import de.lemke.sudoku.domain.model.Difficulty.EXPERT
import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Difficulty.MEDIUM
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalculatePlayGamesSyncUseCase @Inject constructor(
    private val getAllSudokus: GetAllSudokusUseCase,
) {
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    suspend operator fun invoke(sudoku: Sudoku? = null): PlayGamesSync =
        withContext(Dispatchers.Default) {
            val sudokus = getAllSudokus().filter { it.completed }
            val leaderboardScores = mutableListOf<Pair<Int, Long>>()
            val achievementUnlocks = mutableListOf<Int>()
            val achievementIncrements = mutableListOf<Pair<Int, Int>>()

            leaderboardScores += R.string.leaderboard_total_wins to sudokus.size.toLong()
            leaderboardScores += R.string.leaderboard_daily_sudokus to sudokus.count { it.isDailySudoku }.toLong()
            leaderboardScores += R.string.leaderboard_level_44 to sudokus.count { it.size == 4 && it.isSudokuLevel }.toLong()
            leaderboardScores += R.string.leaderboard_level_99 to sudokus.count { it.size == 9 && it.isSudokuLevel }.toLong()
            leaderboardScores += R.string.leaderboard_level_1616 to sudokus.count { it.size == 16 && it.isSudokuLevel }.toLong()

            if (sudoku != null) {
                achievementUnlocks += R.string.achievement_first_win
                leaderboardScores += R.string.leaderboard_best_time to sudoku.seconds * 1000L
                if (sudoku.eraserUsed) achievementUnlocks += R.string.achievement_eraser
                if (sudoku.hintsUsed == 0) achievementUnlocks += R.string.achievement_no_hints
                if (sudoku.notesMade > 0) achievementUnlocks += R.string.achievement_use_notes
                if (sudoku.isChecklist) achievementUnlocks += R.string.achievement_checklist
                if (sudoku.isReverseChecklist) achievementUnlocks += R.string.achievement_reverse_checklist
                if (sudoku.seconds < 10) achievementUnlocks += R.string.achievement_i_am_speed

                when (sudoku.size) {
                    4 -> {
                        achievementIncrements += R.string.achievement_10_sudokus_44 to 1
                        achievementIncrements += R.string.achievement_50_sudokus_44 to 1
                        if (sudoku.seconds < 30) achievementUnlocks += R.string.achievement_stopwatch_44
                        leaderboardScores += R.string.leaderboard_wins_44 to sudokus.count { it.size == 4 }.toLong()
                    }

                    9 -> {
                        achievementIncrements += R.string.achievement_10_sudokus_99 to 1
                        achievementIncrements += R.string.achievement_50_sudokus_99 to 1
                        if (sudoku.seconds < 120) achievementUnlocks += R.string.achievement_stopwatch_99
                        leaderboardScores += R.string.leaderboard_wins_99 to sudokus.count { it.size == 9 }.toLong()
                    }

                    16 -> {
                        achievementIncrements += R.string.achievement_10_sudokus_1616 to 1
                        achievementIncrements += R.string.achievement_50_sudokus_1616 to 1
                        if (sudoku.seconds < 420) achievementUnlocks += R.string.achievement_stopwatch_1616
                        leaderboardScores += R.string.leaderboard_wins_1616 to sudokus.count { it.size == 16 }.toLong()
                    }
                }

                when (sudoku.difficulty) {
                    VERY_EASY -> {
                        achievementIncrements += R.string.achievement_10_sudokus_very_easy to 1
                        achievementIncrements += R.string.achievement_50_sudokus_very_easy to 1
                        when (sudoku.size) {
                            4 -> {
                                leaderboardScores += R.string.leaderboard_time_44_very_easy to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_44_very_easy to
                                    sudokus.count { it.size == 4 && it.difficulty == VERY_EASY }.toLong()
                            }

                            9 -> {
                                leaderboardScores += R.string.leaderboard_time_99_very_easy to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_99_very_easy to
                                    sudokus.count { it.size == 9 && it.difficulty == VERY_EASY }.toLong()
                            }

                            16 -> {
                                leaderboardScores += R.string.leaderboard_time_1616_very_easy to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_1616_very_easy to
                                    sudokus.count { it.size == 16 && it.difficulty == VERY_EASY }.toLong()
                            }
                        }
                    }

                    EASY -> {
                        achievementIncrements += R.string.achievement_10_sudokus_easy to 1
                        achievementIncrements += R.string.achievement_50_sudokus_easy to 1
                        when (sudoku.size) {
                            4 -> {
                                leaderboardScores += R.string.leaderboard_time_44_easy to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_44_easy to
                                    sudokus.count { it.size == 4 && it.difficulty == EASY }.toLong()
                            }

                            9 -> {
                                leaderboardScores += R.string.leaderboard_time_99_easy to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_99_easy to
                                    sudokus.count { it.size == 9 && it.difficulty == EASY }.toLong()
                            }

                            16 -> {
                                leaderboardScores += R.string.leaderboard_time_1616_easy to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_1616_easy to
                                    sudokus.count { it.size == 16 && it.difficulty == EASY }.toLong()
                            }
                        }
                    }

                    MEDIUM -> {
                        achievementIncrements += R.string.achievement_10_sudokus_medium to 1
                        achievementIncrements += R.string.achievement_50_sudokus_medium to 1
                        when (sudoku.size) {
                            4 -> {
                                leaderboardScores += R.string.leaderboard_time_44_medium to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_44_medium to
                                    sudokus.count { it.size == 4 && it.difficulty == MEDIUM }.toLong()
                            }

                            9 -> {
                                leaderboardScores += R.string.leaderboard_time_99_medium to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_99_medium to
                                    sudokus.count { it.size == 9 && it.difficulty == MEDIUM }.toLong()
                            }

                            16 -> {
                                leaderboardScores += R.string.leaderboard_time_1616_medium to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_1616_medium to
                                    sudokus.count { it.size == 16 && it.difficulty == MEDIUM }.toLong()
                            }
                        }
                    }

                    HARD -> {
                        achievementIncrements += R.string.achievement_10_sudokus_hard to 1
                        achievementIncrements += R.string.achievement_50_sudokus_hard to 1
                        when (sudoku.size) {
                            4 -> {
                                leaderboardScores += R.string.leaderboard_time_44_hard to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_44_hard to
                                    sudokus.count { it.size == 4 && it.difficulty == HARD }.toLong()
                            }

                            9 -> {
                                leaderboardScores += R.string.leaderboard_time_99_hard to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_99_hard to
                                    sudokus.count { it.size == 9 && it.difficulty == HARD }.toLong()
                            }

                            16 -> {
                                leaderboardScores += R.string.leaderboard_time_1616_hard to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_1616_hard to
                                    sudokus.count { it.size == 16 && it.difficulty == HARD }.toLong()
                            }
                        }
                    }

                    EXPERT -> {
                        achievementIncrements += R.string.achievement_10_sudokus_expert to 1
                        achievementIncrements += R.string.achievement_50_sudokus_expert to 1
                        when (sudoku.size) {
                            4 -> {
                                leaderboardScores += R.string.leaderboard_time_44_expert to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_44_expert to
                                    sudokus.count { it.size == 4 && it.difficulty == EXPERT }.toLong()
                            }

                            9 -> {
                                leaderboardScores += R.string.leaderboard_time_99_expert to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_99_expert to
                                    sudokus.count { it.size == 9 && it.difficulty == EXPERT }.toLong()
                            }

                            16 -> {
                                leaderboardScores += R.string.leaderboard_time_1616_expert to sudoku.seconds * 1000L
                                leaderboardScores +=
                                    R.string.leaderboard_wins_1616_expert to
                                    sudokus.count { it.size == 16 && it.difficulty == EXPERT }.toLong()
                            }
                        }
                    }
                }
            }

            PlayGamesSync(leaderboardScores, achievementUnlocks, achievementIncrements)
        }
}
