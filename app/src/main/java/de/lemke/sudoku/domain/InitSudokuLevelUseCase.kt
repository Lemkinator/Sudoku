package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class InitSudokuLevelUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    private val generateSudokuLevel: GenerateSudokuLevelUseCase,
    private val getMaxSudokuLevel: GetMaxSudokuLevelUseCase
) {
    suspend operator fun invoke(size: Int) = withContext(Dispatchers.Default) {
        val sudokuLevel = sudokusRepository.getSudokuLevel(size)
        val maxLevel = getMaxSudokuLevel(size)
        if (sudokuLevel.size < maxLevel) {
            val missingLevels = (1..maxLevel).filter { level -> sudokuLevel.none { it.modeLevel == level } }
            missingLevels.forEach { sudokusRepository.saveSudoku(generateSudokuLevel(size, it)) }
        }
    }
}
