package de.lemke.sudoku.domain

import de.lemke.sudoku.data.UserSettingsRepository
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.monthAndYear
import de.lemke.sudoku.ui.utils.SudokuListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class ObserveDailySudokusUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    private val userSettingsRepository: UserSettingsRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(date: LocalDate = LocalDate.now()) =
        userSettingsRepository.observeDailyShowUncompleted()
            .flatMapLatest { includeUncompleted ->
                sudokusRepository.observeDailySudokus().map { sudokus ->
                    // apply filter
                    sudokus.filter {
                        if (includeUncompleted) true
                        else {
                            it.completed
                        } || it.created.toLocalDate() == date
                    }
                }.map { sudokus ->
                    // map and add separators
                    val dailySudokus: MutableList<SudokuListItem> =
                        sudokus.map { SudokuListItem.SudokuItem(it, it.created.monthAndYear) }.toMutableList()
                    var offset = 0
                    var oldDate: LocalDate? = null
                    sudokus.forEachIndexed { index, sudoku ->
                        if (oldDate == null || oldDate.month != sudoku.created.month) {
                            dailySudokus.add(index + offset, SudokuListItem.SeparatorItem(sudoku.created.monthAndYear))
                            oldDate = sudoku.created.toLocalDate()
                            offset++
                        }
                    }
                    dailySudokus
                }
            }.flowOn(Dispatchers.Default)
}
