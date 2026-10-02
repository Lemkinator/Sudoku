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

import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Difficulty.EASY
import de.lemke.sudoku.domain.model.Difficulty.EXPERT
import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Difficulty.MEDIUM
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val MILLIS_PER_SECOND = 1000L
private const val SPEED_ACHIEVEMENT_SECONDS = 10

class CalculatePlayGamesSyncUseCase @Inject constructor(
    private val getAllSudokus: GetAllSudokusUseCase,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(sudoku: Sudoku? = null): PlayGamesSync =
        withContext(defaultDispatcher) {
            val sudokus = getAllSudokus().filter { it.completed }
            val scores = baseScores(sudokus).toMutableList()
            val unlocks = mutableListOf<Int>()
            val increments = mutableListOf<Pair<Int, Int>>()
            if (sudoku != null) {
                unlocks += winUnlocks(sudoku)
                scores += R.string.leaderboard_best_time to sudoku.seconds * MILLIS_PER_SECOND
                addSizeStats(sudoku, sudokus, scores, unlocks, increments)
                addDifficultyStats(sudoku, sudokus, scores, increments)
            }
            PlayGamesSync(scores, unlocks, increments)
        }

    private fun baseScores(sudokus: List<Sudoku>): List<Pair<Int, Long>> =
        listOf(
            R.string.leaderboard_total_wins to sudokus.size.toLong(),
            R.string.leaderboard_daily_sudokus to sudokus.count { it.isDailySudoku }.toLong(),
        ) +
            SudokuSize.entries.map { size ->
                size.playGames.levelLeaderboard to sudokus.count { it.size == size && it.isSudokuLevel }.toLong()
            }

    private fun winUnlocks(sudoku: Sudoku): List<Int> =
        buildList {
            add(R.string.achievement_first_win)
            if (sudoku.eraserUsed) add(R.string.achievement_eraser)
            if (sudoku.hintsUsed == 0) add(R.string.achievement_no_hints)
            if (sudoku.notesMade > 0) add(R.string.achievement_use_notes)
            if (sudoku.isChecklist) add(R.string.achievement_checklist)
            if (sudoku.isReverseChecklist) add(R.string.achievement_reverse_checklist)
            if (sudoku.seconds < SPEED_ACHIEVEMENT_SECONDS) add(R.string.achievement_i_am_speed)
        }

    private fun addSizeStats(
        sudoku: Sudoku,
        sudokus: List<Sudoku>,
        scores: MutableList<Pair<Int, Long>>,
        unlocks: MutableList<Int>,
        increments: MutableList<Pair<Int, Int>>,
    ) {
        val stats = sudoku.size.playGames
        increments += stats.achievement10 to 1
        increments += stats.achievement50 to 1
        if (sudoku.seconds < stats.stopwatchSeconds) unlocks += stats.stopwatchAchievement
        scores += stats.winsLeaderboard to sudokus.count { it.size == sudoku.size }.toLong()
    }

    private fun addDifficultyStats(
        sudoku: Sudoku,
        sudokus: List<Sudoku>,
        scores: MutableList<Pair<Int, Long>>,
        increments: MutableList<Pair<Int, Int>>,
    ) {
        val achievements = sudoku.difficulty.achievements
        increments += achievements.achievement10 to 1
        increments += achievements.achievement50 to 1
        val leaderboards = sudoku.size.leaderboards(sudoku.difficulty)
        scores += leaderboards.time to sudoku.seconds * MILLIS_PER_SECOND
        scores += leaderboards.wins to sudokus.count { it.size == sudoku.size && it.difficulty == sudoku.difficulty }.toLong()
    }
}

private data class SizePlayGames(
    val achievement10: Int,
    val achievement50: Int,
    val stopwatchAchievement: Int,
    val stopwatchSeconds: Int,
    val winsLeaderboard: Int,
    val levelLeaderboard: Int,
)

private data class DifficultyAchievements(
    val achievement10: Int,
    val achievement50: Int,
)

private data class DifficultyLeaderboards(
    val time: Int,
    val wins: Int,
)

private val SudokuSize.playGames: SizePlayGames
    get() =
        when (this) {
            SudokuSize.FOUR -> {
                SizePlayGames(
                    achievement10 = R.string.achievement_10_sudokus_44,
                    achievement50 = R.string.achievement_50_sudokus_44,
                    stopwatchAchievement = R.string.achievement_stopwatch_44,
                    stopwatchSeconds = 30,
                    winsLeaderboard = R.string.leaderboard_wins_44,
                    levelLeaderboard = R.string.leaderboard_level_44,
                )
            }

            SudokuSize.NINE -> {
                SizePlayGames(
                    achievement10 = R.string.achievement_10_sudokus_99,
                    achievement50 = R.string.achievement_50_sudokus_99,
                    stopwatchAchievement = R.string.achievement_stopwatch_99,
                    stopwatchSeconds = 120,
                    winsLeaderboard = R.string.leaderboard_wins_99,
                    levelLeaderboard = R.string.leaderboard_level_99,
                )
            }

            SudokuSize.SIXTEEN -> {
                SizePlayGames(
                    achievement10 = R.string.achievement_10_sudokus_1616,
                    achievement50 = R.string.achievement_50_sudokus_1616,
                    stopwatchAchievement = R.string.achievement_stopwatch_1616,
                    stopwatchSeconds = 420,
                    winsLeaderboard = R.string.leaderboard_wins_1616,
                    levelLeaderboard = R.string.leaderboard_level_1616,
                )
            }
        }

private val Difficulty.achievements: DifficultyAchievements
    get() =
        when (this) {
            VERY_EASY -> DifficultyAchievements(R.string.achievement_10_sudokus_very_easy, R.string.achievement_50_sudokus_very_easy)
            EASY -> DifficultyAchievements(R.string.achievement_10_sudokus_easy, R.string.achievement_50_sudokus_easy)
            MEDIUM -> DifficultyAchievements(R.string.achievement_10_sudokus_medium, R.string.achievement_50_sudokus_medium)
            HARD -> DifficultyAchievements(R.string.achievement_10_sudokus_hard, R.string.achievement_50_sudokus_hard)
            EXPERT -> DifficultyAchievements(R.string.achievement_10_sudokus_expert, R.string.achievement_50_sudokus_expert)
        }

private fun SudokuSize.leaderboards(difficulty: Difficulty): DifficultyLeaderboards =
    when (this) {
        SudokuSize.FOUR -> leaderboards4x4(difficulty)
        SudokuSize.NINE -> leaderboards9x9(difficulty)
        SudokuSize.SIXTEEN -> leaderboards16x16(difficulty)
    }

private fun leaderboards4x4(difficulty: Difficulty): DifficultyLeaderboards =
    when (difficulty) {
        VERY_EASY -> DifficultyLeaderboards(R.string.leaderboard_time_44_very_easy, R.string.leaderboard_wins_44_very_easy)
        EASY -> DifficultyLeaderboards(R.string.leaderboard_time_44_easy, R.string.leaderboard_wins_44_easy)
        MEDIUM -> DifficultyLeaderboards(R.string.leaderboard_time_44_medium, R.string.leaderboard_wins_44_medium)
        HARD -> DifficultyLeaderboards(R.string.leaderboard_time_44_hard, R.string.leaderboard_wins_44_hard)
        EXPERT -> DifficultyLeaderboards(R.string.leaderboard_time_44_expert, R.string.leaderboard_wins_44_expert)
    }

private fun leaderboards9x9(difficulty: Difficulty): DifficultyLeaderboards =
    when (difficulty) {
        VERY_EASY -> DifficultyLeaderboards(R.string.leaderboard_time_99_very_easy, R.string.leaderboard_wins_99_very_easy)
        EASY -> DifficultyLeaderboards(R.string.leaderboard_time_99_easy, R.string.leaderboard_wins_99_easy)
        MEDIUM -> DifficultyLeaderboards(R.string.leaderboard_time_99_medium, R.string.leaderboard_wins_99_medium)
        HARD -> DifficultyLeaderboards(R.string.leaderboard_time_99_hard, R.string.leaderboard_wins_99_hard)
        EXPERT -> DifficultyLeaderboards(R.string.leaderboard_time_99_expert, R.string.leaderboard_wins_99_expert)
    }

private fun leaderboards16x16(difficulty: Difficulty): DifficultyLeaderboards =
    when (difficulty) {
        VERY_EASY -> DifficultyLeaderboards(R.string.leaderboard_time_1616_very_easy, R.string.leaderboard_wins_1616_very_easy)
        EASY -> DifficultyLeaderboards(R.string.leaderboard_time_1616_easy, R.string.leaderboard_wins_1616_easy)
        MEDIUM -> DifficultyLeaderboards(R.string.leaderboard_time_1616_medium, R.string.leaderboard_wins_1616_medium)
        HARD -> DifficultyLeaderboards(R.string.leaderboard_time_1616_hard, R.string.leaderboard_wins_1616_hard)
        EXPERT -> DifficultyLeaderboards(R.string.leaderboard_time_1616_expert, R.string.leaderboard_wins_1616_expert)
    }
