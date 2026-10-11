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

import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class SudokusRepository @Inject constructor(
    private val sudokuDao: SudokuDao,
) {
    fun observeAllSudokus() = sudokuDao.observeAll().map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    fun observeAllNormalSudokus() = sudokuDao.observeAllNormal().map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    fun observeSudokuLevel(size: SudokuSize) =
        sudokuDao.observeSudokuLevel(size.value).map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    fun observeDailySudokus() = sudokuDao.observeDailySudokus().map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    suspend fun getAllSudokus(): List<Sudoku> = sudokuDao.getAll().mapNotNull { sudokuFromDb(it) }

    suspend fun getRecentlyUpdatedNormalSudoku(): Sudoku? = sudokuFromDb(sudokuDao.getRecentlyUpdatedNormalSudoku())

    suspend fun getSudokuLevel(size: SudokuSize): List<Sudoku> = sudokuDao.getAllSudokuLevel(size.value).mapNotNull { sudokuFromDb(it) }

    suspend fun getDailySudokus(): List<Sudoku> = sudokuDao.getDailySudokus().mapNotNull { sudokuFromDb(it) }

    suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = sudokuFromDb(sudokuDao.getById(sudokuId.value))

    suspend fun deleteSudoku(sudoku: Sudoku) = sudokuDao.delete(sudokuToDb(sudoku))

    suspend fun saveSudoku(
        sudoku: Sudoku,
        onlyUpdate: Boolean = false,
    ) = saveSudokuRows(sudokuWithFieldsToDb(sudoku), onlyUpdate)

    suspend fun saveSudokuRows(
        rows: SudokuWithFields,
        onlyUpdate: Boolean,
    ) {
        if (!onlyUpdate) replacedBy(rows.sudoku)?.let { sudokuDao.delete(it) }
        sudokuDao.insert(rows.sudoku, rows.fields)
    }

    suspend fun getMaxSudokuLevel(size: SudokuSize): Int = sudokuDao.getMaxSudokuLevel(size.value) ?: 0

    suspend fun deleteInvalidSudokus() {
        val invalidRows = sudokuDao.getAll().filter { sudokuFromDb(it) == null }
        if (invalidRows.isNotEmpty()) sudokuDao.delete(*invalidRows.map { it.sudoku }.toTypedArray())
    }

    private suspend fun replacedBy(sudoku: SudokuDb): SudokuDb? =
        when {
            sudoku.modeLevel == MODE_DAILY -> {
                sudokuDao.getDailySudokus().filter { it.sudoku.created.toLocalDate() == sudoku.created.toLocalDate() }
            }

            sudoku.modeLevel > 0 -> {
                sudokuDao.getSudokuLevels(sudoku.size, sudoku.modeLevel)
            }

            else -> {
                emptyList()
            }
        }.firstOrNull { sudokuFromDb(it) != null }?.takeIf { it.sudoku.id != sudoku.id }?.sudoku
}
