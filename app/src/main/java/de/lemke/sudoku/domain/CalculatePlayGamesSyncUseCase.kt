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
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

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
                scores += R.string.leaderboard_best_time to sudoku.seconds * 1000L
                addSizeStats(sudoku, sudokus, scores, unlocks, increments)
                addDifficultyStats(sudoku, sudokus, scores, increments)
            }
            PlayGamesSync(scores, unlocks, increments)
        }

    private fun baseScores(sudokus: List<Sudoku>): List<Pair<Int, Long>> =
        listOf(
            R.string.leaderboard_total_wins to sudokus.size.toLong(),
            R.string.leaderboard_daily_sudokus to sudokus.count { it.isDailySudoku }.toLong(),
            R.string.leaderboard_level_44 to sudokus.count { it.size == 4 && it.isSudokuLevel }.toLong(),
            R.string.leaderboard_level_99 to sudokus.count { it.size == 9 && it.isSudokuLevel }.toLong(),
            R.string.leaderboard_level_1616 to sudokus.count { it.size == 16 && it.isSudokuLevel }.toLong(),
        )

    private fun winUnlocks(sudoku: Sudoku): List<Int> =
        buildList {
            add(R.string.achievement_first_win)
            if (sudoku.eraserUsed) add(R.string.achievement_eraser)
            if (sudoku.hintsUsed == 0) add(R.string.achievement_no_hints)
            if (sudoku.notesMade > 0) add(R.string.achievement_use_notes)
            if (sudoku.isChecklist) add(R.string.achievement_checklist)
            if (sudoku.isReverseChecklist) add(R.string.achievement_reverse_checklist)
            if (sudoku.seconds < 10) add(R.string.achievement_i_am_speed)
        }

    private fun addSizeStats(
        sudoku: Sudoku,
        sudokus: List<Sudoku>,
        scores: MutableList<Pair<Int, Long>>,
        unlocks: MutableList<Int>,
        increments: MutableList<Pair<Int, Int>>,
    ) {
        val stats = sizeStats.getValue(sudoku.size)
        increments += stats.achievement10 to 1
        increments += stats.achievement50 to 1
        if (sudoku.seconds < stats.stopwatchSeconds) unlocks += stats.stopwatchAchievement
        scores += stats.winsId to sudokus.count { it.size == sudoku.size }.toLong()
    }

    private fun addDifficultyStats(
        sudoku: Sudoku,
        sudokus: List<Sudoku>,
        scores: MutableList<Pair<Int, Long>>,
        increments: MutableList<Pair<Int, Int>>,
    ) {
        val (achievement10, achievement50) = difficultyAchievements.getValue(sudoku.difficulty)
        increments += achievement10 to 1
        increments += achievement50 to 1
        val (timeId, winsId) = sizeDifficultyLeaderboard.getValue(sudoku.size to sudoku.difficulty)
        scores += timeId to sudoku.seconds * 1000L
        scores += winsId to sudokus.count { it.size == sudoku.size && it.difficulty == sudoku.difficulty }.toLong()
    }

    private data class SizeStats(
        val achievement10: Int,
        val achievement50: Int,
        val stopwatchAchievement: Int,
        val stopwatchSeconds: Int,
        val winsId: Int,
    )

    companion object {
        private val sizeStats: Map<Int, SizeStats> =
            mapOf(
                4 to
                    SizeStats(
                        R.string.achievement_10_sudokus_44,
                        R.string.achievement_50_sudokus_44,
                        R.string.achievement_stopwatch_44,
                        30,
                        R.string.leaderboard_wins_44,
                    ),
                9 to
                    SizeStats(
                        R.string.achievement_10_sudokus_99,
                        R.string.achievement_50_sudokus_99,
                        R.string.achievement_stopwatch_99,
                        120,
                        R.string.leaderboard_wins_99,
                    ),
                16 to
                    SizeStats(
                        R.string.achievement_10_sudokus_1616,
                        R.string.achievement_50_sudokus_1616,
                        R.string.achievement_stopwatch_1616,
                        420,
                        R.string.leaderboard_wins_1616,
                    ),
            )

        private val difficultyAchievements: Map<Difficulty, Pair<Int, Int>> =
            mapOf(
                VERY_EASY to (R.string.achievement_10_sudokus_very_easy to R.string.achievement_50_sudokus_very_easy),
                EASY to (R.string.achievement_10_sudokus_easy to R.string.achievement_50_sudokus_easy),
                MEDIUM to (R.string.achievement_10_sudokus_medium to R.string.achievement_50_sudokus_medium),
                HARD to (R.string.achievement_10_sudokus_hard to R.string.achievement_50_sudokus_hard),
                EXPERT to (R.string.achievement_10_sudokus_expert to R.string.achievement_50_sudokus_expert),
            )

        private val sizeDifficultyLeaderboard: Map<Pair<Int, Difficulty>, Pair<Int, Int>> =
            mapOf(
                (4 to VERY_EASY) to (R.string.leaderboard_time_44_very_easy to R.string.leaderboard_wins_44_very_easy),
                (9 to VERY_EASY) to (R.string.leaderboard_time_99_very_easy to R.string.leaderboard_wins_99_very_easy),
                (16 to VERY_EASY) to
                    (R.string.leaderboard_time_1616_very_easy to R.string.leaderboard_wins_1616_very_easy),
                (4 to EASY) to (R.string.leaderboard_time_44_easy to R.string.leaderboard_wins_44_easy),
                (9 to EASY) to (R.string.leaderboard_time_99_easy to R.string.leaderboard_wins_99_easy),
                (16 to EASY) to (R.string.leaderboard_time_1616_easy to R.string.leaderboard_wins_1616_easy),
                (4 to MEDIUM) to (R.string.leaderboard_time_44_medium to R.string.leaderboard_wins_44_medium),
                (9 to MEDIUM) to (R.string.leaderboard_time_99_medium to R.string.leaderboard_wins_99_medium),
                (16 to MEDIUM) to (R.string.leaderboard_time_1616_medium to R.string.leaderboard_wins_1616_medium),
                (4 to HARD) to (R.string.leaderboard_time_44_hard to R.string.leaderboard_wins_44_hard),
                (9 to HARD) to (R.string.leaderboard_time_99_hard to R.string.leaderboard_wins_99_hard),
                (16 to HARD) to (R.string.leaderboard_time_1616_hard to R.string.leaderboard_wins_1616_hard),
                (4 to EXPERT) to (R.string.leaderboard_time_44_expert to R.string.leaderboard_wins_44_expert),
                (9 to EXPERT) to (R.string.leaderboard_time_99_expert to R.string.leaderboard_wins_99_expert),
                (16 to EXPERT) to (R.string.leaderboard_time_1616_expert to R.string.leaderboard_wins_1616_expert),
            )
    }
}
