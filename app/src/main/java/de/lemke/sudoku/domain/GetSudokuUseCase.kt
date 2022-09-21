package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import kotlin.math.pow

class GetSudokuUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
) {
    suspend operator fun invoke(sudokuId: SudokuId): Sudoku = withContext(Dispatchers.Default) {
        sudokusRepository.getSudokuById(sudokuId)
    }
}
