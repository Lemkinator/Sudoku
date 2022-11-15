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
    suspend operator fun invoke(level: Int): Sudoku = withContext(Dispatchers.Default) {
        val sudokuId = SudokuId.generate()
        val size4easy = 2
        val size4medium = 4
        val size4hard = 6
        val size4expert = 8

        val size9veryEasy = 10
        val size9easy = 20
        val size9medium = 40
        val size9hard = 100
        val size9expert = 200

        val size16veryEasy = 300
        val size16easy = 500
        val size16medium = 700
        val size16hard = 800
        val size16expert = 900

        val difficulty: Difficulty
        val size: Int
        when (level) {
            in 1..size4easy -> {
                size = 4
                difficulty = Difficulty.VERY_EASY
            }
            in size4easy + 1..size4medium -> {
                size = 4
                difficulty = Difficulty.EASY
            }
            in size4medium + 1..size4hard -> {
                size = 4
                difficulty = Difficulty.MEDIUM
            }
            in size4hard + 1..size4expert -> {
                size = 4
                difficulty = Difficulty.HARD
            }
            in size4expert + 1..size9veryEasy -> {
                size = 9
                difficulty = Difficulty.VERY_EASY
            }
            in size9veryEasy + 1..size9easy -> {
                size = 9
                difficulty = Difficulty.EASY
            }
            in size9easy + 1..size9medium -> {
                size = 9
                difficulty = Difficulty.MEDIUM
            }
            in size9medium + 1..size9hard -> {
                size = 9
                difficulty = Difficulty.HARD
            }
            in size9hard + 1..size9expert -> {
                size = 9
                difficulty = Difficulty.EXPERT
            }
            in size9expert + 1..size16veryEasy -> {
                size = 16
                difficulty = Difficulty.VERY_EASY
            }
            in size16veryEasy + 1..size16easy -> {
                size = 16
                difficulty = Difficulty.EASY
            }
            in size16easy + 1..size16medium -> {
                size = 16
                difficulty = Difficulty.MEDIUM
            }
            in size16medium + 1..size16hard -> {
                size = 16
                difficulty = Difficulty.HARD
            }
            in size16hard + 1..size16expert -> {
                size = 16
                difficulty = Difficulty.EXPERT
            }
            else -> {
                size = 16
                difficulty = Difficulty.EXPERT
            }
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
