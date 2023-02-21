package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetMaxSudokuLevelUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
) {
    suspend operator fun invoke(): Int = withContext(Dispatchers.Default) {
        sudokusRepository.getMaxSudokuLevel()
    }
}
