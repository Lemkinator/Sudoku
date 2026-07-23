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

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.ui.utils.SudokuListItem
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveSudokuLevelUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
) {
    operator fun invoke(size: Int) =
        sudokusRepository
            .observeSudokuLevel(size)
            .map { sudokus -> sudokus.map { SudokuListItem.SudokuItem(it, it.modeLevel.toString()) } }
            .flowOn(Dispatchers.Default)
}
