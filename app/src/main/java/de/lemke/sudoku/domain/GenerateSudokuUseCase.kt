package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GenerateSudokuUseCase @Inject constructor(
    private val generateFields: GenerateFieldsUseCase,
) {
    suspend operator fun invoke(size: Int, difficulty: Difficulty): Sudoku = withContext(Dispatchers.Default) {
        return@withContext Sudoku.create(
            sudokuId = SudokuId.generate(),
            size = size,
            difficulty = difficulty,
            fields = generateFields(size, difficulty),
            modeLevel = Sudoku.MODE_NORMAL,
        )
    }
}
