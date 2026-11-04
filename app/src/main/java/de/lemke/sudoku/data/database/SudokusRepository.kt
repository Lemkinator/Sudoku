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

import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SudokusRepository {
    fun observeAllSudokus(): Flow<List<Sudoku>>

    fun observeAllNormalSudokus(): Flow<List<Sudoku>>

    fun observeSudokuLevel(size: SudokuSize): Flow<List<Sudoku>>

    fun observeDailySudokus(): Flow<List<Sudoku>>

    suspend fun getAllSudokus(): List<Sudoku>

    suspend fun getRecentlyUpdatedNormalSudoku(): Sudoku?

    suspend fun getSudokuLevel(size: SudokuSize): List<Sudoku>

    suspend fun getDailySudokus(): List<Sudoku>

    suspend fun getSudokuById(sudokuId: SudokuId): Sudoku?

    suspend fun deleteSudoku(sudoku: Sudoku)

    suspend fun saveSudoku(sudoku: Sudoku)

    suspend fun getMaxSudokuLevel(size: SudokuSize): Int

    suspend fun deleteInvalidSudokus()
}

class DefaultSudokusRepository @Inject constructor(
    private val sudokuDao: SudokuDao,
) : SudokusRepository {
    override fun observeAllSudokus() = sudokuDao.observeAll().map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    override fun observeAllNormalSudokus() = sudokuDao.observeAllNormal().map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    override fun observeSudokuLevel(size: SudokuSize) =
        sudokuDao.observeSudokuLevel(size.value).map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    override fun observeDailySudokus() = sudokuDao.observeDailySudokus().map { sudokus -> sudokus.mapNotNull { sudokuFromDb(it) } }

    override suspend fun getAllSudokus(): List<Sudoku> = sudokuDao.getAll().mapNotNull { sudokuFromDb(it) }

    override suspend fun getRecentlyUpdatedNormalSudoku(): Sudoku? = sudokuFromDb(sudokuDao.getRecentlyUpdatedNormalSudoku())

    override suspend fun getSudokuLevel(size: SudokuSize): List<Sudoku> =
        sudokuDao.getAllSudokuLevel(size.value).mapNotNull { sudokuFromDb(it) }

    override suspend fun getDailySudokus(): List<Sudoku> = sudokuDao.getDailySudokus().mapNotNull { sudokuFromDb(it) }

    override suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = sudokuFromDb(sudokuDao.getById(sudokuId.value))

    override suspend fun deleteSudoku(sudoku: Sudoku) = sudokuDao.delete(sudokuToDb(sudoku))

    override suspend fun saveSudoku(sudoku: Sudoku) {
        val rows = sudokuWithFieldsToDb(sudoku)
        replacedBy(sudoku.info)?.let { sudokuDao.delete(it) }
        sudokuDao.insert(rows.sudoku, rows.fields)
    }

    override suspend fun getMaxSudokuLevel(size: SudokuSize): Int = sudokuDao.getMaxSudokuLevel(size.value) ?: 0

    override suspend fun deleteInvalidSudokus() {
        val invalidRows = sudokuDao.getAll().filter { sudokuFromDb(it) == null }
        if (invalidRows.isNotEmpty()) sudokuDao.delete(*invalidRows.map { it.sudoku }.toTypedArray())
    }

    private suspend fun replacedBy(info: GameInfo): SudokuDb? =
        when (val mode = info.mode) {
            GameMode.Daily -> {
                sudokuDao.getDailySudokus().filter { it.sudoku.created.toLocalDate() == info.created.toLocalDate() }
            }

            is GameMode.Level -> {
                sudokuDao.getSudokuLevels(info.size.value, mode.number)
            }

            GameMode.Normal -> {
                emptyList()
            }
        }.firstOrNull { sudokuFromDb(it) != null }?.takeIf { it.sudoku.id != info.id.value }?.sudoku
}
