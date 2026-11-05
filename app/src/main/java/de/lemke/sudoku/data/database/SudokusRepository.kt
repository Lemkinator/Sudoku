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

import de.lemke.sudoku.domain.model.GameSummary
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val MAX_IDS_PER_DELETE = 500

/** Reads games as summaries wherever a list, the statistics or an alarm needs only their outcome, and the board only by id. */
interface SudokusRepository {
    fun observeAllSummaries(): Flow<List<GameSummary>>

    fun observeNormalSummaries(): Flow<List<GameSummary>>

    fun observeDailySummaries(): Flow<List<GameSummary>>

    fun observeLevelSummaries(size: SudokuSize): Flow<List<GameSummary>>

    suspend fun getCompletedSummaries(): List<GameSummary>

    suspend fun isDailyCompleted(date: LocalDate): Boolean

    suspend fun hasDaily(date: LocalDate): Boolean

    suspend fun getLevelNumbers(size: SudokuSize): Set<Int>

    suspend fun getRecentlyUpdatedNormalSummary(): GameSummary?

    /** The summary of the game with [sudokuId], or null if there is none or its header cannot be read. The board is never read. */
    suspend fun getSummaryById(sudokuId: SudokuId): GameSummary?

    /** The game with [sudokuId], or null if there is none or its board cannot be read. */
    suspend fun getSudokuById(sudokuId: SudokuId): Sudoku?

    suspend fun deleteSudokus(ids: List<SudokuId>)

    /** Stores [sudoku], replacing the game with its id, the game of its level and size, or the daily game of its day. */
    suspend fun saveSudoku(sudoku: Sudoku)

    suspend fun getMaxSudokuLevel(size: SudokuSize): Int
}

class DefaultSudokusRepository @Inject constructor(
    private val gameDao: GameDao,
    private val clock: Clock,
) : SudokusRepository {
    override fun observeAllSummaries() = gameDao.observeAllHeaders().map { it.summaries() }

    override fun observeNormalSummaries() = gameDao.observeNormalHeaders().map { it.summaries() }

    override fun observeDailySummaries() = gameDao.observeDailyHeaders().map { it.summaries() }

    override fun observeLevelSummaries(size: SudokuSize) = gameDao.observeLevelHeaders(size.value).map { it.summaries() }

    override suspend fun getCompletedSummaries(): List<GameSummary> = gameDao.getCompletedHeaders().summaries()

    override suspend fun isDailyCompleted(date: LocalDate): Boolean = gameDao.isDailyCompleted(date.toEpochDay())

    override suspend fun hasDaily(date: LocalDate): Boolean = gameDao.hasDaily(date.toEpochDay())

    override suspend fun getLevelNumbers(size: SudokuSize): Set<Int> = gameDao.getLevelNumbers(size.value).toSet()

    override suspend fun getRecentlyUpdatedNormalSummary(): GameSummary? = gameDao.getRecentlyUpdatedNormalHeader()?.toSummary(clock.zone)

    override suspend fun getSummaryById(sudokuId: SudokuId): GameSummary? = gameDao.getHeaderById(sudokuId.value)?.toSummary(clock.zone)

    override suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = gameDao.getById(sudokuId.value)?.toSudoku(clock.zone)

    override suspend fun deleteSudokus(ids: List<SudokuId>) =
        ids.chunked(MAX_IDS_PER_DELETE).forEach {
            gameDao.deleteByIds(it.map(SudokuId::value))
        }

    override suspend fun saveSudoku(sudoku: Sudoku) = gameDao.save(sudoku.toEntity(clock.zone))

    override suspend fun getMaxSudokuLevel(size: SudokuSize): Int = gameDao.maxLevel(size.value) ?: 0

    private fun List<GameHeader>.summaries(): List<GameSummary> = mapNotNull { it.toSummary(clock.zone) }
}
