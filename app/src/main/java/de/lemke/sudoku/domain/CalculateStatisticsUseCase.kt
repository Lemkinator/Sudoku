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
import de.lemke.sudoku.domain.model.GameSummary
import de.lemke.sudoku.domain.model.SudokuStatistics
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val PERCENT_SCALE = 100

class CalculateStatisticsUseCase @Inject constructor(
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(sudokus: List<GameSummary>): SudokuStatistics =
        withContext(defaultDispatcher) {
            val sortedAsc = sudokus.sortedBy { it.info.updated }
            val completed = sortedAsc.filter { it.completed }
            val n = sudokus.size
            val c = completed.size
            SudokuStatistics(
                gamesStarted = n,
                gamesCompleted = c,
                winRate = winRate(n, c),
                bestTime = completed.minByOrNull { it.info.seconds },
                averageTime = if (c == 0) -1 else average(completed, c) { it.info.seconds.toLong() },
                winsWithoutErrors = completed.count { it.info.errorsMade == 0 },
                mostErrors = sudokus.maxOfOrNull { it.info.errorsMade } ?: 0,
                averageErrors = average(completed, c) { it.info.errorsMade.toLong() },
                winsWithoutHints = completed.count { it.info.hintsUsed == 0 },
                mostHints = sudokus.maxOfOrNull { it.info.hintsUsed } ?: 0,
                averageHints = average(completed, c) { it.info.hintsUsed.toLong() },
                winsWithoutNotes = completed.count { it.info.notesMade == 0 },
                mostNotes = sudokus.maxOfOrNull { it.info.notesMade } ?: 0,
                averageNotes = average(completed, c) { it.info.notesMade.toLong() },
                mostGamesStartedDifficulty =
                    sudokus
                        .groupingBy { it.info.difficulty }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key,
                mostGamesWonDifficulty =
                    completed
                        .groupingBy { it.info.difficulty }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key,
                mostGamesStartedSize =
                    sudokus
                        .groupingBy { it.info.size }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key,
                mostGamesWonSize =
                    completed
                        .groupingBy { it.info.size }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key,
                currentGameStreak = currentGameStreak(sortedAsc),
                bestGameStreak = bestGameStreak(sortedAsc),
                totalSecondsPlayed = sudokus.sumOf { it.info.seconds.toLong() },
                hintUsageRate = usageRate(sudokus, n) { it.info.hintsUsed > 0 },
                notesUsageRate = usageRate(sudokus, n) { it.info.notesMade > 0 },
                eraserUsageRate = usageRate(sudokus, n) { it.info.eraserUsed },
                perfectGames = completed.count { it.info.errorsMade == 0 && it.info.hintsUsed == 0 },
            )
        }

    private fun winRate(
        n: Int,
        c: Int,
    ): Int = if (n == 0) 0 else (c.toFloat() / n * PERCENT_SCALE).roundToInt()

    private fun average(
        completed: List<GameSummary>,
        c: Int,
        selector: (GameSummary) -> Long,
    ): Int = if (c == 0) 0 else (completed.sumOf(selector).toFloat() / c).roundToInt()

    private fun usageRate(
        sudokus: List<GameSummary>,
        n: Int,
        predicate: (GameSummary) -> Boolean,
    ): Float = if (n == 0) 0f else sudokus.count(predicate).toFloat() / n

    private fun currentGameStreak(sortedAsc: List<GameSummary>): Int {
        var streak = 0
        for (sudoku in sortedAsc.reversed()) {
            if (sudoku.completed) streak++ else break
        }
        return streak
    }

    private fun bestGameStreak(sortedAsc: List<GameSummary>): Int {
        var best = 0
        var current = 0
        for (sudoku in sortedAsc) {
            if (sudoku.completed) {
                current++
                if (current > best) best = current
            } else {
                current = 0
            }
        }
        return best
    }
}
