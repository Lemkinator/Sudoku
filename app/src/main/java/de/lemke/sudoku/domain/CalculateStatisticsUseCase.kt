package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuStatistics
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalculateStatisticsUseCase @Inject constructor() {
    suspend operator fun invoke(sudokus: List<Sudoku>): SudokuStatistics =
        withContext(Dispatchers.Default) {
            val sortedAsc = sudokus.sortedBy { it.updated }
            val completed = sortedAsc.filter { it.completed }
            val n = sudokus.size
            val c = completed.size
            SudokuStatistics(
                gamesStarted = n,
                gamesCompleted = c,
                winRate = winRate(n, c),
                bestTimeSudoku = completed.minByOrNull { it.seconds },
                averageTime = average(completed, c) { it.seconds.toLong() },
                winsWithoutErrors = completed.count { it.errorsMade == 0 },
                mostErrors = sudokus.maxOfOrNull { it.errorsMade } ?: 0,
                averageErrors = average(completed, c) { it.errorsMade.toLong() },
                winsWithoutHints = completed.count { it.hintsUsed == 0 },
                mostHints = sudokus.maxOfOrNull { it.hintsUsed } ?: 0,
                averageHints = average(completed, c) { it.hintsUsed.toLong() },
                winsWithoutNotes = completed.count { it.notesMade == 0 },
                mostNotes = sudokus.maxOfOrNull { it.notesMade } ?: 0,
                averageNotes = average(completed, c) { it.notesMade.toLong() },
                mostGamesStartedDifficulty = sudokus.groupingBy { it.difficulty }.eachCount().maxByOrNull { it.value }?.key,
                mostGamesWonDifficulty = completed.groupingBy { it.difficulty }.eachCount().maxByOrNull { it.value }?.key,
                mostGamesStartedSize = sudokus.groupingBy { it.size }.eachCount().maxByOrNull { it.value }?.key,
                mostGamesWonSize = completed.groupingBy { it.size }.eachCount().maxByOrNull { it.value }?.key,
                currentGameStreak = currentGameStreak(sortedAsc),
                bestGameStreak = bestGameStreak(sortedAsc),
                totalSecondsPlayed = sudokus.sumOf { it.seconds.toLong() },
                hintUsageRate = usageRate(sudokus, n) { it.hintsUsed > 0 },
                notesUsageRate = usageRate(sudokus, n) { it.notesMade > 0 },
                eraserUsageRate = usageRate(sudokus, n) { it.eraserUsed },
                perfectGames = completed.count { it.errorsMade == 0 && it.hintsUsed == 0 },
            )
        }

    private fun winRate(n: Int, c: Int): Int =
        if (n == 0) 0 else (c.toFloat() / n * 100).roundToInt()

    private fun average(completed: List<Sudoku>, c: Int, selector: (Sudoku) -> Long): Int =
        if (c == 0) 0 else (completed.sumOf(selector) / c).toInt()

    private fun usageRate(sudokus: List<Sudoku>, n: Int, predicate: (Sudoku) -> Boolean): Float =
        if (n == 0) 0f else sudokus.count(predicate).toFloat() / n

    private fun currentGameStreak(sortedAsc: List<Sudoku>): Int {
        var streak = 0
        for (sudoku in sortedAsc.reversed()) {
            if (sudoku.completed) streak++ else break
        }
        return streak
    }

    private fun bestGameStreak(sortedAsc: List<Sudoku>): Int {
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
