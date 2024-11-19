package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ObserveSudokuLevelUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository
) {
    operator fun invoke(size: Int) = sudokusRepository.observeSudokuLevel(size).flowOn(Dispatchers.Default)
}
