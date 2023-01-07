package de.lemke.sudoku.domain

import android.app.Activity
import com.google.android.gms.games.PlayGames
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UpdatePlayGamesUseCase @Inject constructor(
    private val getAllSudokus: GetAllSudokusUseCase,
) {

    suspend operator fun invoke(activity: Activity, completedSudoku: Sudoku? = null) = withContext(Dispatchers.Default) {
        val achievementsClient = PlayGames.getAchievementsClient(activity)
        val leaderboardsClient = PlayGames.getLeaderboardsClient(activity)
        val sudokus = getAllSudokus().filter { it.completed }
        leaderboardsClient.submitScore(activity.getString(R.string.leaderboard_total_wins), sudokus.size.toLong())
        leaderboardsClient.submitScore(activity.getString(R.string.leaderboard_daily_sudokus), sudokus.count { it.isDailySudoku }.toLong())
        if (completedSudoku == null) return@withContext
        achievementsClient.unlock(activity.getString(R.string.achievement_first_win))
        leaderboardsClient.submitScore(activity.getString(R.string.leaderboard_best_time), completedSudoku.seconds * 1000L)
        if (completedSudoku.eraserUsed) achievementsClient.unlock(activity.getString(R.string.achievement_eraser))
        if (completedSudoku.hintsUsed == 0) achievementsClient.unlock(activity.getString(R.string.achievement_no_hints))
        if (completedSudoku.notesMade > 0) achievementsClient.unlock(activity.getString(R.string.achievement_use_notes))
        if (completedSudoku.isChecklist) achievementsClient.unlock(activity.getString(R.string.achievement_checklist))
        if (completedSudoku.isReverseChecklist) achievementsClient.unlock(activity.getString(R.string.achievement_reverse_checklist))
        if (completedSudoku.seconds < 10) achievementsClient.unlock(activity.getString(R.string.achievement_i_am_speed))
        if (completedSudoku.isSudokuLevel) leaderboardsClient.submitScore(
            activity.getString(R.string.leaderboard_level),
            completedSudoku.modeLevel.toLong()
        )
        when (completedSudoku.size) {
            4 -> {
                achievementsClient.increment(activity.getString(R.string.achievement_10_sudokus_4x4), 1)
                achievementsClient.increment(activity.getString(R.string.achievement_50_sudokus_4x4), 1)
                if (completedSudoku.seconds < 30) achievementsClient.unlock(activity.getString(R.string.achievement_stopwatch_4x4))
                leaderboardsClient.submitScore(activity.getString(R.string.leaderboard_wins_44), sudokus.count { it.size == 4 }.toLong())
            }
            9 -> {
                achievementsClient.increment(activity.getString(R.string.achievement_10_sudokus_9x9), 1)
                achievementsClient.increment(activity.getString(R.string.achievement_50_sudokus_9x9), 1)
                if (completedSudoku.seconds < 120) achievementsClient.unlock(activity.getString(R.string.achievement_stopwatch_9x9))
                leaderboardsClient.submitScore(activity.getString(R.string.leaderboard_wins_99), sudokus.count { it.size == 9 }.toLong())
            }
            16 -> {
                achievementsClient.increment(activity.getString(R.string.achievement_10_sudokus_16x16), 1)
                achievementsClient.increment(activity.getString(R.string.achievement_50_sudokus_16x16), 1)
                if (completedSudoku.seconds < 420) achievementsClient.unlock(activity.getString(R.string.achievement_stopwatch_16x16))
                leaderboardsClient.submitScore(activity.getString(R.string.leaderboard_wins_1616), sudokus.count { it.size == 16 }.toLong())
            }
        }
        when (completedSudoku.difficulty) {
            Difficulty.VERY_EASY -> {
                achievementsClient.increment(activity.getString(R.string.achievement_10_sudokus_very_easy), 1)
                achievementsClient.increment(activity.getString(R.string.achievement_50_sudokus_very_easy), 1)
                when (completedSudoku.size) {
                    4 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_44_very_easy),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_44_very_easy),
                            sudokus.count { it.size == 4 && it.difficulty == Difficulty.VERY_EASY }.toLong()
                        )
                    }
                    9 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_99_very_easy),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_99_very_easy),
                            sudokus.count { it.size == 9 && it.difficulty == Difficulty.VERY_EASY }.toLong()
                        )
                    }
                    16 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_1616_very_easy),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_1616_very_easy),
                            sudokus.count { it.size == 16 && it.difficulty == Difficulty.VERY_EASY }.toLong()
                        )
                    }
                }
            }
            Difficulty.EASY -> {
                achievementsClient.increment(activity.getString(R.string.achievement_10_sudokus_easy), 1)
                achievementsClient.increment(activity.getString(R.string.achievement_50_sudokus_easy), 1)
                when (completedSudoku.size) {
                    4 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_44_easy),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_44_easy),
                            sudokus.count { it.size == 4 && it.difficulty == Difficulty.EASY }.toLong()
                        )
                    }
                    9 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_99_easy),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_99_easy),
                            sudokus.count { it.size == 9 && it.difficulty == Difficulty.EASY }.toLong()
                        )
                    }
                    16 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_1616_easy),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_1616_easy),
                            sudokus.count { it.size == 16 && it.difficulty == Difficulty.EASY }.toLong()
                        )
                    }
                }
            }
            Difficulty.MEDIUM -> {
                achievementsClient.increment(activity.getString(R.string.achievement_10_sudokus_medium), 1)
                achievementsClient.increment(activity.getString(R.string.achievement_50_sudokus_medium), 1)
                when (completedSudoku.size) {
                    4 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_44_medium),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_44_medium),
                            sudokus.count { it.size == 4 && it.difficulty == Difficulty.MEDIUM }.toLong()
                        )
                    }
                    9 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_99_medium),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_99_medium),
                            sudokus.count { it.size == 9 && it.difficulty == Difficulty.MEDIUM }.toLong()
                        )
                    }
                    16 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_1616_medium),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_1616_medium),
                            sudokus.count { it.size == 16 && it.difficulty == Difficulty.MEDIUM }.toLong()
                        )
                    }
                }
            }
            Difficulty.HARD -> {
                achievementsClient.increment(activity.getString(R.string.achievement_10_sudokus_hard), 1)
                achievementsClient.increment(activity.getString(R.string.achievement_50_sudokus_hard), 1)
                when (completedSudoku.size) {
                    4 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_44_hard),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_44_hard),
                            sudokus.count { it.size == 4 && it.difficulty == Difficulty.HARD }.toLong()
                        )
                    }
                    9 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_99_hard),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_99_hard),
                            sudokus.count { it.size == 9 && it.difficulty == Difficulty.HARD }.toLong()
                        )
                    }
                    16 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_1616_hard),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_1616_hard),
                            sudokus.count { it.size == 16 && it.difficulty == Difficulty.HARD }.toLong()
                        )
                    }
                }
            }
            Difficulty.EXPERT -> {
                achievementsClient.increment(activity.getString(R.string.achievement_10_sudokus_expert), 1)
                achievementsClient.increment(activity.getString(R.string.achievement_50_sudokus_expert), 1)
                when (completedSudoku.size) {
                    4 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_44_expert),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_44_expert),
                            sudokus.count { it.size == 4 && it.difficulty == Difficulty.EXPERT }.toLong()
                        )
                    }
                    9 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_99_expert),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_99_expert),
                            sudokus.count { it.size == 9 && it.difficulty == Difficulty.EXPERT }.toLong()
                        )
                    }
                    16 -> {
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_time_1616_expert),
                            completedSudoku.seconds * 1000L
                        )
                        leaderboardsClient.submitScore(
                            activity.getString(R.string.leaderboard_wins_1616_expert),
                            sudokus.count { it.size == 16 && it.difficulty == Difficulty.EXPERT }.toLong()
                        )
                    }
                }
            }
        }
    }
}
