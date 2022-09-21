package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ValidateNumberUseCase @Inject constructor() {
    suspend operator fun invoke(sudoku: Sudoku, position: Position, number:Int): Boolean = withContext(Dispatchers.Default) {
        if (sudoku.getPossibleValues(position).contains(number)) return@withContext true
        return@withContext false
    }
}
