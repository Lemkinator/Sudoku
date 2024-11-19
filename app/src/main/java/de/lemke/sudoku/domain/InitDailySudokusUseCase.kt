package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

class InitDailySudokusUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    private val saveSudoku: SaveSudokuUseCase,
    private val generateDailySudoku: GenerateDailySudokuUseCase,
) {
    suspend operator fun invoke(date: LocalDate = LocalDate.now()) = withContext(Dispatchers.Default) {
        if (sudokusRepository.getDailySudokus().none { it.created.toLocalDate() == date }) {
            saveSudoku(generateDailySudoku())
        }
    }
}
