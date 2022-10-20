package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetRecentlyUpdatedNormalSudokuUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
) {
    suspend operator fun invoke(): Sudoku? = withContext(Dispatchers.Default) {
        sudokusRepository.getRecentlyUpdatedNormalSudoku()
    }
}
