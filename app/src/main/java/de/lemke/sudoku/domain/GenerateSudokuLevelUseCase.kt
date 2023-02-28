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
    suspend operator fun invoke(size: Int, level: Int): Sudoku = withContext(Dispatchers.Default) {
        val difficulty = when (size) {
            4 -> when (level) {
                in 1..30 -> Difficulty.VERY_EASY
                in 31..100 -> Difficulty.EASY
                in 101..200 -> Difficulty.MEDIUM
                in 201..500 -> Difficulty.HARD
                else -> Difficulty.EXPERT
            }
            9 -> when (level) {
                in 1..30 -> Difficulty.VERY_EASY
                in 31..100 -> Difficulty.EASY
                in 101..200 -> Difficulty.MEDIUM
                in 201..500 -> Difficulty.HARD
                else -> Difficulty.EXPERT
            }
            16 -> when (level) {
                in 1..30 -> Difficulty.VERY_EASY
                in 31..100 -> Difficulty.EASY
                in 101..200 -> Difficulty.MEDIUM
                in 201..500 -> Difficulty.HARD
                else -> Difficulty.EXPERT
            }
            else -> Difficulty.EXPERT
        }
        return@withContext Sudoku.create(
            sudokuId = SudokuId.generate(),
            size = size,
            difficulty = difficulty,
            fields = generateFields(size, difficulty),
            modeLevel = level,
        )
    }
}
