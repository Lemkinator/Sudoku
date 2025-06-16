package de.lemke.sudoku.domain

import android.app.Activity
import com.google.android.gms.games.PlayGames
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Difficulty.EASY
import de.lemke.sudoku.domain.model.Difficulty.EXPERT
import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Difficulty.MEDIUM
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UpdatePlayGamesUseCase @Inject constructor(
    private val getAllSudokus: GetAllSudokusUseCase,
) {

    suspend operator fun invoke(activity: Activity, sudoku: Sudoku? = null) = withContext(Dispatchers.Default) {
        val achievements = PlayGames.getAchievementsClient(activity)
        val leaderboards = PlayGames.getLeaderboardsClient(activity)
        val sudokus = getAllSudokus().filter { it.completed }
        leaderboards.submitScore(activity.getString(R.string.leaderboard_total_wins), sudokus.size.toLong())
        leaderboards.submitScore(activity.getString(R.string.leaderboard_daily_sudokus), sudokus.count { it.isDailySudoku }.toLong())
        leaderboards.submitScore(
            activity.getString(R.string.leaderboard_level_44),
            sudokus.count { it.size == 4 && it.isSudokuLevel }.toLong()
        )
        leaderboards.submitScore(
            activity.getString(R.string.leaderboard_level_99),
            sudokus.count { it.size == 9 && it.isSudokuLevel }.toLong()
        )
        leaderboards.submitScore(
            activity.getString(R.string.leaderboard_level_1616),
            sudokus.count { it.size == 16 && it.isSudokuLevel }.toLong()
        )
        if (sudoku == null) return@withContext
        achievements.unlock(activity.getString(R.string.achievement_first_win))
        leaderboards.submitScore(activity.getString(R.string.leaderboard_best_time), sudoku.seconds * 1000L)
        if (sudoku.eraserUsed) achievements.unlock(activity.getString(R.string.achievement_eraser))
        if (sudoku.hintsUsed == 0) achievements.unlock(activity.getString(R.string.achievement_no_hints))
        if (sudoku.notesMade > 0) achievements.unlock(activity.getString(R.string.achievement_use_notes))
        if (sudoku.isChecklist) achievements.unlock(activity.getString(R.string.achievement_checklist))
        if (sudoku.isReverseChecklist) achievements.unlock(activity.getString(R.string.achievement_reverse_checklist))
        if (sudoku.seconds < 10) achievements.unlock(activity.getString(R.string.achievement_i_am_speed))
        when (sudoku.size) {
            4 -> {
                achievements.increment(activity.getString(R.string.achievement_10_sudokus_44), 1)
                achievements.increment(activity.getString(R.string.achievement_50_sudokus_44), 1)
                if (sudoku.seconds < 30) achievements.unlock(activity.getString(R.string.achievement_stopwatch_44))
                leaderboards.submitScore(activity.getString(R.string.leaderboard_wins_44), sudokus.count { it.size == 4 }.toLong())
            }

            9 -> {
                achievements.increment(activity.getString(R.string.achievement_10_sudokus_99), 1)
                achievements.increment(activity.getString(R.string.achievement_50_sudokus_99), 1)
                if (sudoku.seconds < 120) achievements.unlock(activity.getString(R.string.achievement_stopwatch_99))
                leaderboards.submitScore(activity.getString(R.string.leaderboard_wins_99), sudokus.count { it.size == 9 }.toLong())
            }

            16 -> {
                achievements.increment(activity.getString(R.string.achievement_10_sudokus_1616), 1)
                achievements.increment(activity.getString(R.string.achievement_50_sudokus_1616), 1)
                if (sudoku.seconds < 420) achievements.unlock(activity.getString(R.string.achievement_stopwatch_1616))
                leaderboards.submitScore(activity.getString(R.string.leaderboard_wins_1616), sudokus.count { it.size == 16 }.toLong())
            }
        }
        when (sudoku.difficulty) {
            VERY_EASY -> {
                achievements.increment(activity.getString(R.string.achievement_10_sudokus_very_easy), 1)
                achievements.increment(activity.getString(R.string.achievement_50_sudokus_very_easy), 1)
                when (sudoku.size) {
                    4 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_44_very_easy), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_44_very_easy),
                            sudokus.count { it.size == 4 && it.difficulty == VERY_EASY }.toLong()
                        )
                    }

                    9 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_99_very_easy), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_99_very_easy),
                            sudokus.count { it.size == 9 && it.difficulty == VERY_EASY }.toLong()
                        )
                    }

                    16 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_1616_very_easy), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_1616_very_easy),
                            sudokus.count { it.size == 16 && it.difficulty == VERY_EASY }.toLong()
                        )
                    }
                }
            }

            EASY -> {
                achievements.increment(activity.getString(R.string.achievement_10_sudokus_easy), 1)
                achievements.increment(activity.getString(R.string.achievement_50_sudokus_easy), 1)
                when (sudoku.size) {
                    4 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_44_easy), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_44_easy),
                            sudokus.count { it.size == 4 && it.difficulty == EASY }.toLong()
                        )
                    }

                    9 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_99_easy), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_99_easy),
                            sudokus.count { it.size == 9 && it.difficulty == EASY }.toLong()
                        )
                    }

                    16 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_1616_easy), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_1616_easy),
                            sudokus.count { it.size == 16 && it.difficulty == EASY }.toLong()
                        )
                    }
                }
            }

            MEDIUM -> {
                achievements.increment(activity.getString(R.string.achievement_10_sudokus_medium), 1)
                achievements.increment(activity.getString(R.string.achievement_50_sudokus_medium), 1)
                when (sudoku.size) {
                    4 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_44_medium), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_44_medium),
                            sudokus.count { it.size == 4 && it.difficulty == MEDIUM }.toLong()
                        )
                    }

                    9 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_99_medium), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_99_medium),
                            sudokus.count { it.size == 9 && it.difficulty == MEDIUM }.toLong()
                        )
                    }

                    16 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_1616_medium), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_1616_medium),
                            sudokus.count { it.size == 16 && it.difficulty == MEDIUM }.toLong()
                        )
                    }
                }
            }

            HARD -> {
                achievements.increment(activity.getString(R.string.achievement_10_sudokus_hard), 1)
                achievements.increment(activity.getString(R.string.achievement_50_sudokus_hard), 1)
                when (sudoku.size) {
                    4 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_44_hard), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_44_hard),
                            sudokus.count { it.size == 4 && it.difficulty == HARD }.toLong()
                        )
                    }

                    9 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_99_hard), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_99_hard),
                            sudokus.count { it.size == 9 && it.difficulty == HARD }.toLong()
                        )
                    }

                    16 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_1616_hard), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_1616_hard),
                            sudokus.count { it.size == 16 && it.difficulty == HARD }.toLong()
                        )
                    }
                }
            }

            EXPERT -> {
                achievements.increment(activity.getString(R.string.achievement_10_sudokus_expert), 1)
                achievements.increment(activity.getString(R.string.achievement_50_sudokus_expert), 1)
                when (sudoku.size) {
                    4 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_44_expert), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_44_expert),
                            sudokus.count { it.size == 4 && it.difficulty == EXPERT }.toLong()
                        )
                    }

                    9 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_99_expert), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_99_expert),
                            sudokus.count { it.size == 9 && it.difficulty == EXPERT }.toLong()
                        )
                    }

                    16 -> {
                        leaderboards.submitScore(activity.getString(R.string.leaderboard_time_1616_expert), sudoku.seconds * 1000L)
                        leaderboards.submitScore(
                            activity.getString(R.string.leaderboard_wins_1616_expert),
                            sudokus.count { it.size == 16 && it.difficulty == EXPERT }.toLong()
                        )
                    }
                }
            }
        }
    }
}
