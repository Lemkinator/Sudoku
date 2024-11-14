package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.dateFormatShort
import de.lemke.sudoku.ui.utils.SudokuListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import kotlin.collections.map
import kotlin.collections.toMutableList

class ObserveSudokuHistoryUseCase @Inject constructor(
    private val observeAllNormalSudokus: ObserveAllNormalSudokusUseCase
) {
    operator fun invoke() = observeAllNormalSudokus()
        .map { sudokus ->
            val sudokuHistory: MutableList<SudokuListItem> = sudokus.map { SudokuListItem.SudokuItem(it) }.toMutableList()
            var offset = 0
            var oldDate: LocalDate? = null
            sudokus.forEachIndexed { index, sudoku ->
                if (oldDate == null || oldDate != sudoku.updated.toLocalDate()) {
                    sudokuHistory.add(index + offset, SudokuListItem.SeparatorItem(sudoku.updated.dateFormatShort))
                    oldDate = sudoku.updated.toLocalDate()
                    offset++
                }
            }
            sudokuHistory
        }
        .flowOn(Dispatchers.Default)
}
