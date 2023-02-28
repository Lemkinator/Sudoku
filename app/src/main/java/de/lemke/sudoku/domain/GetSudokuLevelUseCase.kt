package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetSudokuLevelUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    private val generateSudokuLevel: GenerateSudokuLevelUseCase
) {
    suspend operator fun invoke(size: Int): List<Sudoku> = withContext(Dispatchers.Default) {
        val sudokuLevel = sudokusRepository.getAllLevelSudokus(size)
        if (sudokuLevel.isEmpty()) return@withContext sudokuLevel
        val maxLevel = sudokuLevel.first().modeLevel
        if (sudokuLevel.size < maxLevel) {
            val missingLevels = (1..maxLevel).filter { level -> sudokuLevel.none { it.modeLevel == level } }
            missingLevels.forEach { sudokusRepository.saveSudoku(generateSudokuLevel(size, it)) }
            return@withContext sudokusRepository.getAllLevelSudokus(size)
        }
        sudokuLevel
    }
}
