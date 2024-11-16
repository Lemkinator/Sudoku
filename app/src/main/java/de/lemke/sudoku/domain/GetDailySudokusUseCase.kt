package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

class GetDailySudokusUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    private val saveSudoku: SaveSudokuUseCase,
    private val generateDailySudoku: GenerateDailySudokuUseCase,
) {
    suspend operator fun invoke(includeUncompleted: Boolean, date: LocalDate = LocalDate.now()): List<Pair<Sudoku?, LocalDate>> =
        withContext(Dispatchers.Default) {
            var dailySudokus = sudokusRepository.getAllDailySudokus()
            if (dailySudokus.none { it.created.toLocalDate() == LocalDate.now() }) {
                saveSudoku(generateDailySudoku())
                dailySudokus = sudokusRepository.getAllDailySudokus()
            }
            val sudokuDatePairs: MutableList<Pair<Sudoku?, LocalDate>> = dailySudokus.filter {
                if (includeUncompleted) true
                else {
                    it.completed
                } || it.created.toLocalDate() == date
            }.map { it to it.created.toLocalDate() }.toMutableList()
            val sudokuDatePairsCopy = sudokuDatePairs.toMutableList()
            var offset = 0
            var oldDate: LocalDate? = null
            sudokuDatePairsCopy.forEachIndexed { index, pair -> // add a null entry for date separator
                if (oldDate == null || oldDate?.month != pair.second.month) {
                    sudokuDatePairs.add(index + offset, Pair(null, pair.second))
                    oldDate = pair.second
                    offset++
                }
            }
            return@withContext sudokuDatePairs
        }
}
