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
        val veryEasy = 20
        val easy = 60
        val medium = 300
        val hard = 400
        val expert = 500
        val difficulty = when (level) {
            in 1..veryEasy -> Difficulty.VERY_EASY
            in veryEasy + 1..easy -> Difficulty.EASY
            in easy + 1..medium -> Difficulty.MEDIUM
            in medium + 1..hard -> Difficulty.HARD
            in hard + 1..expert -> Difficulty.EXPERT
            else -> Difficulty.MEDIUM
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
