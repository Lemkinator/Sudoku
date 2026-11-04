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
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
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

    var observed: (Flow<List<Sudoku>>) -> Flow<List<Sudoku>> = { it }
    var beforeSave: suspend (Sudoku) -> Unit = {}
    var beforeDelete: suspend (SudokuId) -> Unit = {}
    var beforeGetMaxSudokuLevel: suspend () -> Unit = {}
    var beforeGetAllSudokus: suspend () -> Unit = {}
    var saveFailure: Throwable? = null

    /** Stores [sudokus] without recording a save. */
    fun store(vararg sudokus: Sudoku) = sudokus.forEach { put(Stored(it, readable = true)) }

    /** Stores [sudoku] as a game whose board cannot be read, so every read of it returns nothing. */
    fun storeUnreadable(sudoku: Sudoku) = put(Stored(sudoku, readable = false))

    override fun observeAllSudokus(): Flow<List<Sudoku>> = observe { it.byUpdatedDescending() }

    override fun observeAllNormalSudokus(): Flow<List<Sudoku>> = observe { it.normal().byUpdatedDescending() }

    override fun observeSudokuLevel(size: SudokuSize): Flow<List<Sudoku>> = observe { it.levels(size) }

    override fun observeDailySudokus(): Flow<List<Sudoku>> = observe { it.dailies() }

    override suspend fun getAllSudokus(): List<Sudoku> {
        beforeGetAllSudokus()
        return games.byUpdatedDescending().readable()
    }

    override suspend fun getRecentlyUpdatedNormalSudoku(): Sudoku? =
        games
            .normal()
            .maxByOrNull { it.sudoku.info.updated }
            ?.takeIf { it.readable }
            ?.sudoku

    override suspend fun getSudokuLevel(size: SudokuSize): List<Sudoku> = games.levels(size).readable()

    override suspend fun getDailySudokus(): List<Sudoku> = games.dailies().readable()

    override suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = games.firstOrNull { it.id == sudokuId && it.readable }?.sudoku

    override suspend fun deleteSudoku(sudoku: Sudoku) {
        beforeDelete(sudoku.info.id)
        val before =
            table.getAndUpdate { current ->
                val kept = current.games.filterNot { it.id == sudoku.info.id }
                if (kept.size == current.games.size) current else Table(kept, current.writes + 1)
            }
        if (before.games.any { it.id == sudoku.info.id }) synchronized(recordedDeletions) { recordedDeletions += sudoku.info.id }
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
            (mine.mode == GameMode.Daily && theirs.mode == GameMode.Daily && mine.created.toLocalDate() == theirs.created.toLocalDate())
    }

    private fun observe(query: (List<Stored>) -> List<Stored>): Flow<List<Sudoku>> {
        observeCalls++
        return observed(table.map { query(it.games).readable() })
            .onStart { activeObservers++ }
            .onCompletion { activeObservers-- }
    }

    private fun List<Stored>.readable(): List<Sudoku> = filter { it.readable }.map { it.sudoku }

    private fun List<Stored>.byUpdatedDescending() = sortedByDescending { it.sudoku.info.updated }

    private fun List<Stored>.normal() = filter { it.sudoku.info.mode == GameMode.Normal }

    private fun List<Stored>.levels(size: SudokuSize) =
        filter { it.sudoku.info.size == size && it.sudoku.info.mode is GameMode.Level }
            .sortedByDescending { (it.sudoku.info.mode as GameMode.Level).number }

    private fun List<Stored>.dailies() = filter { it.sudoku.info.mode == GameMode.Daily }.sortedByDescending { it.sudoku.info.created }
}
