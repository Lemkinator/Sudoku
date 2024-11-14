package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ObserveAllNormalSudokusUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository
) {
    operator fun invoke() = sudokusRepository.observeAllNormalSudokus().flowOn(Dispatchers.Default)
}
