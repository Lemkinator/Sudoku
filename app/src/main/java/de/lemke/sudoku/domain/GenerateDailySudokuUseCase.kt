package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

class GenerateDailySudokuUseCase @Inject constructor(
    private val generateFields: GenerateFieldsUseCase,
) {
    suspend operator fun invoke(size: Int = 9): Sudoku = withContext(Dispatchers.Default) {
        val sudokuId = SudokuId.generate()
        val difficulty = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD).random()
        return@withContext Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = difficulty,
            fields = generateFields(size, difficulty, sudokuId),
            modeLevel = Sudoku.MODE_DAILY,
        )
    }
}
