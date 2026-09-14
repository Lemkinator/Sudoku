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

package de.lemke.sudoku.data.database

import javax.inject.Inject
import kotlinx.coroutines.flow.map

// Split out of SudokusRepository (detekt TooManyFunctions): the Flow-returning observation queries.
class SudokuObservationsRepository @Inject constructor(
    private val sudokuObserveDao: SudokuObserveDao,
) {
    fun observeAllSudokus() = sudokuObserveDao.observeAll().map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    fun observeAllNormalSudokus() = sudokuObserveDao.observeAllNormal().map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    fun observeSudokuLevel(size: Int) = sudokuObserveDao.observeSudokuLevel(size).map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    fun observeDailySudokus() = sudokuObserveDao.observeDailySudokus().map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }
}
