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
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
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
 * Keeps [SudokuWithFields] rows in memory in insertion order and answers with the DAO's orderings.
 * Records every landed save in [saves], every deleted id in [deletedIds], every observe call in [observeCalls]
 * and counts the collectors of observed flows in [activeObservers].
 */
class FakeSudokusRepository : SudokusRepository {
    data class Save(
        val rows: SudokuWithFields,
        val onlyUpdate: Boolean,
    )

    /** Counts [writes] so that a write leaving [rows] equal still emits, as Room's invalidation does. */
    private data class Table(
        val rows: List<SudokuWithFields> = emptyList(),
        val writes: Int = 0,
    )

    private val table = MutableStateFlow(Table())
    private val rows: List<SudokuWithFields> get() = table.value.rows
    private val recordedSaves = mutableListOf<Save>()
    private val recordedDeletions = mutableListOf<SudokuId>()

    val saves: List<Save> get() = synchronized(recordedSaves) { recordedSaves.toList() }
    val deletedIds: List<SudokuId> get() = synchronized(recordedDeletions) { recordedDeletions.toList() }
    val storedRows: List<SudokuWithFields> get() = rows

    var observeCalls = 0
        private set
    var activeObservers = 0
        private set

    var observed: (Flow<List<Sudoku>>) -> Flow<List<Sudoku>> = { it }
    var beforeSave: suspend (Save) -> Unit = {}
    var beforeDelete: suspend (SudokuId) -> Unit = {}
    var beforeGetMaxSudokuLevel: suspend () -> Unit = {}
    var beforeGetAllSudokus: suspend () -> Unit = {}
    var saveFailure: Throwable? = null

    /** Stores [sudokus] without recording a save. */
    fun store(vararg sudokus: Sudoku) = sudokus.forEach { insertRows(sudokuWithFieldsToDb(it)) }

    /** Stores [sudokuWithFields] as given, like a raw DAO insert, so a test can add an unreadable row. */
    fun insertRows(sudokuWithFields: SudokuWithFields) {
        table.update { current ->
            Table(current.rows.filterNot { it.sudoku.id == sudokuWithFields.sudoku.id } + sudokuWithFields, current.writes + 1)
        }
    }

    override fun observeAllSudokus(): Flow<List<Sudoku>> = observe { it.byUpdatedDescending() }

    override fun observeAllNormalSudokus(): Flow<List<Sudoku>> = observe { it.normal().byUpdatedDescending() }

    override fun observeSudokuLevel(size: SudokuSize): Flow<List<Sudoku>> = observe { it.levels(size) }

    override fun observeDailySudokus(): Flow<List<Sudoku>> = observe { it.dailies() }

    override suspend fun getAllSudokus(): List<Sudoku> {
        beforeGetAllSudokus()
        return rows.byUpdatedDescending().readable()
    }

    override suspend fun getRecentlyUpdatedNormalSudoku(): Sudoku? = sudokuFromDb(rows.normal().maxByOrNull { it.sudoku.updated })

    override suspend fun getSudokuLevel(size: SudokuSize): List<Sudoku> = rows.levels(size).readable()

    override suspend fun getDailySudokus(): List<Sudoku> = rows.dailies().readable()

    override suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = sudokuFromDb(rows.firstOrNull { it.sudoku.id == sudokuId.value })

    override suspend fun deleteSudoku(sudoku: Sudoku) {
        beforeDelete(sudoku.id)
        delete(setOf(sudoku.id.value))
    }

    override suspend fun saveSudokuRows(
        rows: SudokuWithFields,
        onlyUpdate: Boolean,
    ) {
        val save = Save(rows, onlyUpdate)
        beforeSave(save)
        saveFailure?.let { throw it }
        if (!onlyUpdate) replacedBy(rows.sudoku)?.let { delete(setOf(it.id)) }
        insertRows(rows)
        synchronized(recordedSaves) { recordedSaves += save }
    }

    override suspend fun getMaxSudokuLevel(size: SudokuSize): Int {
        beforeGetMaxSudokuLevel()
        return rows.filter { it.sudoku.size == size.value && it.sudoku.modeLevel > 0 }.maxOfOrNull { it.sudoku.modeLevel } ?: 0
    }

    override suspend fun deleteInvalidSudokus() {
        val invalidIds =
            rows
                .filter { sudokuFromDb(it) == null }
                .map { it.sudoku.id }
                .toSet()
        if (invalidIds.isNotEmpty()) delete(invalidIds)
    }

    private fun observe(query: (List<SudokuWithFields>) -> List<SudokuWithFields>): Flow<List<Sudoku>> {
        observeCalls++
        return observed(table.map { query(it.rows).readable() })
            .onStart { activeObservers++ }
            .onCompletion { activeObservers-- }
    }

    private fun delete(ids: Set<String>) {
        val before =
            table.getAndUpdate { current ->
                val kept = current.rows.filterNot { it.sudoku.id in ids }
                if (kept.size == current.rows.size) current else Table(kept, current.writes + 1)
            }
        val removedIds = ids.filter { id -> before.rows.any { it.sudoku.id == id } }
        synchronized(recordedDeletions) { recordedDeletions += removedIds.map(::SudokuId) }
    }

    private fun replacedBy(sudoku: SudokuDb): SudokuDb? =
        when {
            sudoku.modeLevel == MODE_DAILY -> {
                rows.dailies().filter { it.sudoku.created.toLocalDate() == sudoku.created.toLocalDate() }
            }

            sudoku.modeLevel > 0 -> {
                rows.filter { it.sudoku.size == sudoku.size && it.sudoku.modeLevel == sudoku.modeLevel }
            }

            else -> {
                emptyList()
            }
        }.firstOrNull { sudokuFromDb(it) != null }?.takeIf { it.sudoku.id != sudoku.id }?.sudoku

    private fun List<SudokuWithFields>.readable(): List<Sudoku> = mapNotNull(::sudokuFromDb)

    private fun List<SudokuWithFields>.byUpdatedDescending() = sortedByDescending { it.sudoku.updated }

    private fun List<SudokuWithFields>.normal() = filter { it.sudoku.modeLevel == MODE_NORMAL }

    private fun List<SudokuWithFields>.levels(size: SudokuSize) =
        filter { it.sudoku.size == size.value && it.sudoku.modeLevel > 0 }.sortedByDescending { it.sudoku.modeLevel }

    private fun List<SudokuWithFields>.dailies() = filter { it.sudoku.modeLevel == MODE_DAILY }.sortedByDescending { it.sudoku.created }
}
