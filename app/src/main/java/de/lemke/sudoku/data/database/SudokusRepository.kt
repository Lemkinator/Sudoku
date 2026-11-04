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
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import java.time.Clock
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

    /** Stores [sudoku], replacing the game with its id, the game of its level and size, or the daily game of its day. */
    suspend fun saveSudoku(sudoku: Sudoku)

    suspend fun getMaxSudokuLevel(size: SudokuSize): Int
}

class DefaultSudokusRepository @Inject constructor(
    private val gameDao: GameDao,
    private val clock: Clock,
) : SudokusRepository {
    override fun observeAllSudokus() = gameDao.observeAll().map { it.readable() }

    override fun observeAllNormalSudokus() = gameDao.observeNormal().map { it.readable() }

    override fun observeSudokuLevel(size: SudokuSize) = gameDao.observeLevels(size.value).map { it.readable() }

    override fun observeDailySudokus() = gameDao.observeDaily().map { it.readable() }

    override suspend fun getAllSudokus(): List<Sudoku> = gameDao.getAll().readable()

    override suspend fun getRecentlyUpdatedNormalSudoku(): Sudoku? = gameDao.getRecentlyUpdatedNormal()?.toSudoku(clock.zone)

    override suspend fun getSudokuLevel(size: SudokuSize): List<Sudoku> = gameDao.getLevels(size.value).readable()

    override suspend fun getDailySudokus(): List<Sudoku> = gameDao.getDaily().readable()

    override suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = gameDao.getById(sudokuId.value)?.toSudoku(clock.zone)

    override suspend fun deleteSudoku(sudoku: Sudoku) = gameDao.deleteByIds(listOf(sudoku.info.id.value))

    override suspend fun saveSudoku(sudoku: Sudoku) = gameDao.save(sudoku.toEntity(clock.zone))

    override suspend fun getMaxSudokuLevel(size: SudokuSize): Int = gameDao.maxLevel(size.value) ?: 0

    private fun List<GameEntity>.readable(): List<Sudoku> = mapNotNull { it.toSudoku(clock.zone) }
}
