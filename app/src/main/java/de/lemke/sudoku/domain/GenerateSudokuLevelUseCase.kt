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
        val size4VeryEasy = 1
        val size4Easy = 5
        val size4Medium = 10
        val size4Hard = 15
        val size4Expert = 20

        val size9VeryEasy = 30
        val size9easy = 60
        val size9medium = 100
        val size9hard = 200
        val size9expert = 500

        val size16veryEasy = 1000
        val size16easy = 1200
        val size16medium = 1400
        val size16hard = 1700
        val size16expert = 2000

        val difficulty: Difficulty
        val size: Int
        when (level) {
            in size4VeryEasy until size4Easy -> {
                difficulty = Difficulty.VERY_EASY
                size = 4
            }
            in size4Easy until size4Medium -> {
                difficulty = Difficulty.EASY
                size = 4
            }
            in size4Medium until size4Hard -> {
                difficulty = Difficulty.MEDIUM
                size = 4
            }
            in size4Hard until size4Expert -> {
                difficulty = Difficulty.HARD
                size = 4
            }
            in size4Expert until size9VeryEasy -> {
                difficulty = Difficulty.EXPERT
                size = 4
            }
            in size9VeryEasy until size9easy -> {
                difficulty = Difficulty.VERY_EASY
                size = 9
            }
            in size9easy until size9medium -> {
                difficulty = Difficulty.EASY
                size = 9
            }
            in size9medium until size9hard -> {
                difficulty = Difficulty.MEDIUM
                size = 9
            }
            in size9hard until size9expert -> {
                difficulty = Difficulty.HARD
                size = 9
            }
            in size9expert until size16veryEasy -> {
                difficulty = Difficulty.EXPERT
                size = 9
            }
            in size16veryEasy until size16easy -> {
                difficulty = Difficulty.VERY_EASY
                size = 16
            }
            in size16easy until size16medium -> {
                difficulty = Difficulty.EASY
                size = 16
            }
            in size16medium until size16hard -> {
                difficulty = Difficulty.MEDIUM
                size = 16
            }
            in size16hard until size16expert -> {
                difficulty = Difficulty.HARD
                size = 16
            }
            else -> {
                difficulty = Difficulty.EXPERT
                size = 16
            }
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
