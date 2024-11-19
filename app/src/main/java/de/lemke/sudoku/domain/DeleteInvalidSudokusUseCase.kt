package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeleteInvalidSudokusUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
) {
    suspend operator fun invoke() = withContext(Dispatchers.Default) {
        sudokusRepository.deleteInvalidSudokus()
    }
}
