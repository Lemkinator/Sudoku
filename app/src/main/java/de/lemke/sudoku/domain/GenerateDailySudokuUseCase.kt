package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GenerateDailySudokuUseCase @Inject constructor(
    private val generateFields: GenerateFieldsUseCase,
) {
    suspend operator fun invoke(size: Int = 9): Sudoku = withContext(Dispatchers.Default) {
        val randomDifficulty = listOf(
            Difficulty.VERY_EASY, Difficulty.VERY_EASY, Difficulty.VERY_EASY,
            Difficulty.EASY, Difficulty.EASY, Difficulty.EASY, Difficulty.EASY,
            Difficulty.MEDIUM, Difficulty.MEDIUM,
            Difficulty.HARD
        ).random()
        return@withContext Sudoku.create(
            sudokuId = SudokuId.generate(),
            size = size,
            difficulty = randomDifficulty,
            fields = generateFields(size, randomDifficulty),
            modeLevel = Sudoku.MODE_DAILY,
        )
    }
}
