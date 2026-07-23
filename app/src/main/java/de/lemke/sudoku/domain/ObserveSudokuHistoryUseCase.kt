/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.dateFormatShort
import de.lemke.sudoku.ui.utils.SudokuListItem
import java.time.LocalDate
import javax.inject.Inject
import kotlin.collections.map
import kotlin.collections.toMutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveSudokuHistoryUseCase @Inject constructor(
    private val observeAllNormalSudokus: ObserveAllNormalSudokusUseCase,
) {
    operator fun invoke() =
        observeAllNormalSudokus()
            .map { sudokus ->
                val sudokuHistory: MutableList<SudokuListItem> =
                    sudokus.map { SudokuListItem.SudokuItem(it, it.updated.dateFormatShort) }.toMutableList()
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
            }.flowOn(Dispatchers.Default)
}
