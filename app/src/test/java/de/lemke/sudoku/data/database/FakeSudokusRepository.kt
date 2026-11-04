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

import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.GameSummary
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * Keeps games in memory in insertion order and answers with the DAO's orderings. A save replaces the games it collides with,
 * as the table's unique indices do. Records every landed save as the saved sudoku in [saves], every deleted id in [deletedIds],
 * every observe call in [observeCalls] and counts the collectors of observed flows in [activeObservers].
 */
class FakeSudokusRepository : SudokusRepository {
    private data class Stored(
        val sudoku: Sudoku,
        val readable: Boolean,
    ) {
        val id: SudokuId get() = sudoku.info.id
    }

    /** Counts [writes] so that a write leaving [games] equal still emits, as Room's invalidation does. */
    private data class Table(
        val games: List<Stored> = emptyList(),
        val writes: Int = 0,
    )

    private val table = MutableStateFlow(Table())
    private val games: List<Stored> get() = table.value.games
    private val recordedSaves = mutableListOf<Sudoku>()
    private val recordedDeletions = mutableListOf<SudokuId>()

    val saves: List<Sudoku> get() = synchronized(recordedSaves) { recordedSaves.toList() }
    val deletedIds: List<SudokuId> get() = synchronized(recordedDeletions) { recordedDeletions.toList() }

    /** The ids of every stored game, readable or not. */
    val storedIds: List<SudokuId> get() = games.map { it.id }

    var observeCalls = 0
        private set
    var activeObservers = 0
        private set

    var observed: (Flow<List<GameSummary>>) -> Flow<List<GameSummary>> = { it }
    var beforeSave: suspend (Sudoku) -> Unit = {}
    var beforeDelete: suspend (SudokuId) -> Unit = {}
    var beforeGetMaxSudokuLevel: suspend () -> Unit = {}
    var beforeGetCompletedSummaries: suspend () -> Unit = {}
    var beforeGetRecentlyUpdatedNormalSummary: suspend () -> Unit = {}
    var saveFailure: Throwable? = null

    /** Stores [sudokus] without recording a save. */
    fun store(vararg sudokus: Sudoku) = sudokus.forEach { put(Stored(it, readable = true)) }

    /** Stores [sudoku] as a game whose board cannot be read: its summary is listed, but reading the game returns nothing. */
    fun storeUnreadable(sudoku: Sudoku) = put(Stored(sudoku, readable = false))

    override fun observeAllSummaries(): Flow<List<GameSummary>> = observe { it.byUpdatedDescending() }

    override fun observeNormalSummaries(): Flow<List<GameSummary>> = observe { it.normal().byUpdatedDescending() }

    override fun observeDailySummaries(): Flow<List<GameSummary>> = observe { it.dailies() }

    override fun observeLevelSummaries(size: SudokuSize): Flow<List<GameSummary>> = observe { it.levels(size) }

    override suspend fun getCompletedSummaries(): List<GameSummary> {
        beforeGetCompletedSummaries()
        return games.byUpdatedDescending().summaries().filter { it.completed }
    }

    override suspend fun isDailyCompleted(date: LocalDate): Boolean =
        games.dailies().summaries().any {
            it.completed &&
                it.info.dailyDate == date
        }

    override suspend fun hasDaily(date: LocalDate): Boolean =
        games.dailies().any {
            it.sudoku.info.dailyDate == date
        }

    override suspend fun getLevelNumbers(size: SudokuSize): Set<Int> =
        games
            .levels(size)
            .map {
                (it.sudoku.info.mode as GameMode.Level).number
            }.toSet()

    override suspend fun getRecentlyUpdatedNormalSummary(): GameSummary? {
        beforeGetRecentlyUpdatedNormalSummary()
        return games
            .normal()
            .maxByOrNull { it.sudoku.info.updated }
            ?.sudoku
            ?.summary()
    }

    override suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = games.firstOrNull { it.id == sudokuId && it.readable }?.sudoku

    override suspend fun deleteSudokus(ids: List<SudokuId>) {
        ids.forEach { beforeDelete(it) }
        val before =
            table.getAndUpdate { current ->
                val kept = current.games.filterNot { it.id in ids }
                if (kept.size == current.games.size) current else Table(kept, current.writes + 1)
            }
        val removed = ids.filter { id -> before.games.any { it.id == id } }
        synchronized(recordedDeletions) { recordedDeletions += removed }
    }

    override suspend fun saveSudoku(sudoku: Sudoku) {
        beforeSave(sudoku)
        saveFailure?.let { throw it }
        put(Stored(sudoku, readable = true))
        synchronized(recordedSaves) { recordedSaves += sudoku }
    }

    override suspend fun getMaxSudokuLevel(size: SudokuSize): Int {
        beforeGetMaxSudokuLevel()
        return games.levels(size).maxOfOrNull { (it.sudoku.info.mode as GameMode.Level).number } ?: 0
    }

    private fun put(stored: Stored) {
        table.update { current ->
            Table(current.games.filterNot { it.collidesWith(stored) } + stored, current.writes + 1)
        }
    }

    private fun Stored.collidesWith(other: Stored): Boolean {
        val mine = sudoku.info
        val theirs = other.sudoku.info
        return id == other.id ||
            (mine.mode is GameMode.Level && mine.mode == theirs.mode && mine.size == theirs.size) ||
            (mine.dailyDate != null && mine.dailyDate == theirs.dailyDate)
    }

    private fun observe(query: (List<Stored>) -> List<Stored>): Flow<List<GameSummary>> {
        observeCalls++
        return observed(table.map { query(it.games).summaries() })
            .onStart { activeObservers++ }
            .onCompletion { activeObservers-- }
    }

    private fun List<Stored>.summaries(): List<GameSummary> = map { it.sudoku.summary() }

    private fun List<Stored>.byUpdatedDescending() = sortedByDescending { it.sudoku.info.updated }

    private fun List<Stored>.normal() = filter { it.sudoku.info.mode == GameMode.Normal }

    private fun List<Stored>.levels(size: SudokuSize) =
        filter { it.sudoku.info.size == size && it.sudoku.info.mode is GameMode.Level }
            .sortedByDescending { (it.sudoku.info.mode as GameMode.Level).number }

    private fun List<Stored>.dailies() = filter { it.sudoku.info.mode is GameMode.Daily }.sortedByDescending { it.sudoku.info.created }
}
