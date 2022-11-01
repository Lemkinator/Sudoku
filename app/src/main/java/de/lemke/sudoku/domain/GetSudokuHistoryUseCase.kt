package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class GetSudokuHistoryUseCase @Inject constructor() {
    suspend operator fun invoke(sudokuList: List<Sudoku>): List<Pair<Sudoku?, LocalDateTime>> = withContext(Dispatchers.Default) {
        val sudokuHistory: MutableList<Pair<Sudoku?, LocalDateTime>> = sudokuList.map { it to it.updated }.toMutableList()
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
