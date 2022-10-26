package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

class GetDailySudokusUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
) {
    suspend operator fun invoke(date: LocalDate = LocalDate.now()): List<Pair<Sudoku?, LocalDate>> = withContext(Dispatchers.Default) {
        val sudokuList: MutableList<Pair<Sudoku?, LocalDate>> =
            sudokusRepository.getAllDailySudokus().filter { it.completed || it.created.toLocalDate() == date }
                .map { it to it.created.toLocalDate() }.toMutableList()
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
