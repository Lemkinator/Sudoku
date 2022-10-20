package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar
import javax.inject.Inject

class GetAllDailySudokusUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
) {
    suspend operator fun invoke(): List<Pair<Sudoku?, LocalDate>> = withContext(Dispatchers.Default) {
        val sudokuList: MutableList<Pair<Sudoku?, LocalDate>> =
            sudokusRepository.getAllDailySudokus().map { it to it.created.toLocalDate() }.toMutableList()
        val sudokuListCopy = sudokuList.toMutableList()
        var offset = 0
        var oldDate: LocalDate? = null
        sudokuListCopy.forEachIndexed { index, pair -> // add a null entry for date separator
            if (oldDate == null || oldDate?.month != pair.second.month) {
                sudokuList.add(index + offset, Pair(null, pair.second))
                oldDate = pair.second
                offset++
            }
        }
        return@withContext sudokuList
    }
}
