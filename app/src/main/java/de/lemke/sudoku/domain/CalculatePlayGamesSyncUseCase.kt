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
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Difficulty.EASY
import de.lemke.sudoku.domain.model.Difficulty.EXPERT
import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Difficulty.MEDIUM
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.GameSummary
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.SudokuSize
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val MILLIS_PER_SECOND = 1000L
private const val SPEED_ACHIEVEMENT_SECONDS = 10

class CalculatePlayGamesSyncUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    /** What Play Games must hold for every completed sudoku now stored, whatever was reported before. */
    suspend operator fun invoke(): PlayGamesSync =
        withContext(defaultDispatcher) {
            val wins = sudokusRepository.getCompletedSummaries().map(GameSummary::info)
            val bySize = wins.groupedBy(SudokuSize.entries) { it.size }
            val byDifficulty = wins.groupedBy(Difficulty.entries) { it.difficulty }
            PlayGamesSync(
                leaderboardScores = leaderboardScores(wins, bySize),
                achievementUnlocks = achievementUnlocks(wins, bySize),
                achievementSteps = achievementSteps(bySize, byDifficulty),
            )
        }

    private fun leaderboardScores(
        wins: List<GameInfo>,
        bySize: Map<SudokuSize, List<GameInfo>>,
    ): List<Pair<Int, Long>> =
        buildList {
            add(R.string.leaderboard_total_wins to wins.size.toLong())
            add(R.string.leaderboard_daily_sudokus to wins.count { it.mode is GameMode.Daily }.toLong())
            SudokuSize.entries.forEach { size ->
                add(size.playGames.levelLeaderboard to wins.count { it.size == size && it.mode is GameMode.Level }.toLong())
            }
            if (wins.isNotEmpty()) add(R.string.leaderboard_best_time to wins.fastest())
            bySize.forEach { (size, ofSize) ->
                add(size.playGames.winsLeaderboard to ofSize.size.toLong())
                ofSize.groupedBy(Difficulty.entries) { it.difficulty }.forEach { (difficulty, ofDifficulty) ->
                    val leaderboards = size.leaderboards(difficulty)
                    add(leaderboards.time to ofDifficulty.fastest())
                    add(leaderboards.wins to ofDifficulty.size.toLong())
                }
            }
        }

    private fun achievementUnlocks(
        wins: List<GameInfo>,
        bySize: Map<SudokuSize, List<GameInfo>>,
    ): List<Int> =
        buildList {
            if (wins.isNotEmpty()) add(R.string.achievement_first_win)
            if (wins.any { it.eraserUsed }) add(R.string.achievement_eraser)
            if (wins.any { it.hintsUsed == 0 }) add(R.string.achievement_no_hints)
            if (wins.any { it.notesMade > 0 }) add(R.string.achievement_use_notes)
            if (wins.any { it.isChecklist }) add(R.string.achievement_checklist)
            if (wins.any { it.isReverseChecklist }) add(R.string.achievement_reverse_checklist)
            if (wins.any { it.seconds < SPEED_ACHIEVEMENT_SECONDS }) add(R.string.achievement_i_am_speed)
            bySize.forEach { (size, ofSize) ->
                val stats = size.playGames
                if (ofSize.any { it.seconds < stats.stopwatchSeconds }) add(stats.stopwatchAchievement)
            }
        }

    private fun achievementSteps(
        bySize: Map<SudokuSize, List<GameInfo>>,
        byDifficulty: Map<Difficulty, List<GameInfo>>,
    ): List<Pair<Int, Int>> =
        buildList {
            bySize.forEach { (size, ofSize) ->
                add(size.playGames.achievement10 to ofSize.size)
                add(size.playGames.achievement50 to ofSize.size)
            }
            byDifficulty.forEach { (difficulty, ofDifficulty) ->
                add(difficulty.achievements.achievement10 to ofDifficulty.size)
                add(difficulty.achievements.achievement50 to ofDifficulty.size)
            }
        }

    private fun List<GameInfo>.fastest(): Long = minOf { it.seconds } * MILLIS_PER_SECOND

    private fun <K> List<GameInfo>.groupedBy(
        keys: Iterable<K>,
        key: (GameInfo) -> K,
    ): Map<K, List<GameInfo>> = keys.associateWith { wanted -> filter { key(it) == wanted } }.filterValues { it.isNotEmpty() }
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
