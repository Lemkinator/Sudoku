package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.ui.utils.SudokuListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveSudokuLevelUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository
) {
    operator fun invoke(size: Int) = sudokusRepository.observeSudokuLevel(size)
        .map { sudokus -> sudokus.map { SudokuListItem.SudokuItem(it, it.modeLevel.toString()) } }
        .flowOn(Dispatchers.Default)
}
