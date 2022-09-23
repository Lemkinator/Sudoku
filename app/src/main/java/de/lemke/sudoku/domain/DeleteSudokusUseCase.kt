package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeleteSudokusUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
) {
    suspend operator fun invoke(sudokus: List<Sudoku>) = withContext(Dispatchers.Default) {
        sudokus.forEach { sudokusRepository.deleteSudoku(it) }
    }
}
