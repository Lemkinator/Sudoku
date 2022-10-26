package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GenerateSudokuLevelUseCase @Inject constructor(
    private val generateFields: GenerateFieldsUseCase,
) {
    suspend operator fun invoke(size: Int = 9, level: Int): Sudoku = withContext(Dispatchers.Default) {
        val sudokuId = SudokuId.generate()
        val easy = 20
        val medium = 60
        val hard = 300
        val expert = 400
        val difficulty = when (level) {
            in 1..easy -> Difficulty.VERY_EASY
            in easy + 1..medium -> Difficulty.EASY
            in medium + 1..hard -> Difficulty.MEDIUM
            in hard + 1..expert -> Difficulty.HARD
            else -> Difficulty.EXPERT
        }
        return@withContext Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = difficulty,
            fields = generateFields(size, difficulty, sudokuId),
            modeLevel = level,
        )
    }
}
