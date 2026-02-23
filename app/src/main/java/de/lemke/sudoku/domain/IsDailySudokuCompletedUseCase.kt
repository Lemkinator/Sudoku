package de.lemke.sudoku.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

class IsDailySudokuCompletedUseCase @Inject constructor(
    private val getAllSudokus: GetAllSudokusUseCase,
) {
    suspend operator fun invoke(date: LocalDate = LocalDate.now()): Boolean = withContext(Dispatchers.Default) {
        return@withContext getAllSudokus(GetAllSudokusUseCase.TYPE_DAILY or GetAllSudokusUseCase.DIFFICULTY_ALL or GetAllSudokusUseCase.SIZE_ALL)
            .find { it.created.toLocalDate() == date && it.completed } != null
    }
}
