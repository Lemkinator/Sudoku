package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import kotlin.collections.List

class GetSudokuHistoryUseCase @Inject constructor(
    private val getAllSudokus: GetAllSudokusUseCase
) {
    suspend operator fun invoke(): List<Pair<Sudoku?, LocalDateTime>> = withContext(Dispatchers.Default) {
        val sudokuHistory: MutableList<Pair<Sudoku?, LocalDateTime>> = getAllSudokus().map { it to it.updated }.toMutableList()
        val sudokuHistoryCopy = sudokuHistory.toMutableList()
        var offset = 0
        var oldDate: LocalDate? = null
        sudokuHistoryCopy.forEachIndexed { index, pair ->
            if (oldDate == null || oldDate != pair.second.toLocalDate()) {
                sudokuHistory.add(index + offset, Pair(null, pair.second))
                oldDate = pair.second.toLocalDate()
                offset++
            }
        }
        return@withContext sudokuHistory
    }
}
