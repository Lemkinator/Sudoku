package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.collections.List

class PreloadSudokusUseCase @Inject constructor(
    private val generateSudoku: GenerateSudokuUseCase
) {
    suspend operator fun invoke(size: Int = 9): List<Sudoku> = withContext(Dispatchers.Default) {
        return@withContext List(Difficulty.values().size) { index -> generateSudoku(size, Difficulty.values()[index]) }
    }
}
