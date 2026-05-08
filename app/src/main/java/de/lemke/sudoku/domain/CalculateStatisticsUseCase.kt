package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuStatistics
import java.time.LocalDate
import javax.inject.Inject

class CalculateStatisticsUseCase @Inject constructor() {
    operator fun invoke(sudokus: List<Sudoku>): SudokuStatistics {
        val completed = sudokus.filter { it.completed }
        val n = sudokus.size
        val c = completed.size
        return SudokuStatistics(
            gamesStarted = n,
            gamesCompleted = c,
            winRate = if (n == 0) 0 else (c.toFloat() / n * 100).toInt(),
            bestTimeSudoku = completed.minByOrNull { it.seconds },
            averageTime = if (c == 0) 0 else completed.sumOf { it.seconds } / c,
            winsWithoutErrors = completed.count { it.errorsMade == 0 },
            mostErrors = sudokus.maxOfOrNull { it.errorsMade } ?: 0,
            averageErrors = if (c == 0) 0 else completed.sumOf { it.errorsMade } / c,
            winsWithoutHints = completed.count { it.hintsUsed == 0 },
            mostHints = sudokus.maxOfOrNull { it.hintsUsed } ?: 0,
            averageHints = if (c == 0) 0 else completed.sumOf { it.hintsUsed } / c,
            winsWithoutNotes = completed.count { it.notesMade == 0 },
            mostNotes = sudokus.maxOfOrNull { it.notesMade } ?: 0,
            averageNotes = if (c == 0) 0 else completed.sumOf { it.notesMade } / c,
            mostGamesStartedDifficulty = sudokus.groupingBy { it.difficulty }.eachCount().maxByOrNull { it.value }?.key,
            mostGamesWonDifficulty = completed.groupingBy { it.difficulty }.eachCount().maxByOrNull { it.value }?.key,
            mostGamesStartedSize = sudokus.groupingBy { it.size }.eachCount().maxByOrNull { it.value }?.key,
            mostGamesWonSize = completed.groupingBy { it.size }.eachCount().maxByOrNull { it.value }?.key,
            currentGameStreak = currentGameStreak(sudokus),
            bestGameStreak = bestGameStreak(sudokus),
            completedDailySudokus = completed.count { it.isDailySudoku },
            currentDailySudokuStreak = dailySudokuStreak(completed, current = true),
            bestDailySudokuStreak = dailySudokuStreak(completed, current = false),
            totalSecondsPlayed = sudokus.sumOf { it.seconds.toLong() },
            hintUsageRate = if (n == 0) 0f else sudokus.count { it.hintsUsed > 0 }.toFloat() / n,
            notesUsageRate = if (n == 0) 0f else sudokus.count { it.notesMade > 0 }.toFloat() / n,
            eraserUsageRate = if (n == 0) 0f else sudokus.count { it.eraserUsed }.toFloat() / n,
            perfectGames = completed.count { it.errorsMade == 0 && it.hintsUsed == 0 },
        )
    }

    private fun currentGameStreak(sudokus: List<Sudoku>): Int {
        var streak = 0
        for (sudoku in sudokus.sortedByDescending { it.updated }) {
            if (sudoku.completed) streak++ else break
        }
        return streak
    }

    private fun bestGameStreak(sudokus: List<Sudoku>): Int {
        var best = 0
        var current = 0
        for (sudoku in sudokus.sortedBy { it.updated }) {
            if (sudoku.completed) {
                current++
                if (current > best) best = current
            } else {
                current = 0
            }
        }
        return best
    }

    private fun dailySudokuStreak(completed: List<Sudoku>, current: Boolean): Int {
        val dates = completed
            .filter { it.isDailySudoku }
            .map { it.updated.toLocalDate() }
            .distinct()
            .sorted()
        if (dates.isEmpty()) return 0
        return if (current) currentDailyStreak(dates) else bestDailyStreak(dates)
    }

    private fun currentDailyStreak(sortedDates: List<LocalDate>): Int {
        var expected = LocalDate.now()
        if (expected !in sortedDates) expected = expected.minusDays(1)
        var streak = 0
        for (date in sortedDates.reversed()) {
            if (date == expected) {
                streak++
                expected = expected.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }

    private fun bestDailyStreak(sortedDates: List<LocalDate>): Int {
        var best = 1
        var current = 1
        for (i in 1 until sortedDates.size) {
            current = if (sortedDates[i] == sortedDates[i - 1].plusDays(1)) current + 1 else 1
            if (current > best) best = current
        }
        return best
    }
}
