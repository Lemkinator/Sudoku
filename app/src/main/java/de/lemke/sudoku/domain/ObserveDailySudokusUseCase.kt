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

import de.lemke.sudoku.data.UserSettingsRepository
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.monthAndYear
import de.lemke.sudoku.ui.utils.SudokuListItem
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveDailySudokusUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(date: LocalDate = LocalDate.now()) =
        userSettingsRepository
            .observeDailyShowUncompleted()
            .flatMapLatest { includeUncompleted ->
                sudokusRepository
                    .observeDailySudokus()
                    .map { sudokus ->
                        // apply filter
                        sudokus.filter {
                            if (includeUncompleted) {
                                true
                            } else {
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
