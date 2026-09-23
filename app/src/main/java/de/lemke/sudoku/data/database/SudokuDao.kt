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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SudokuDao {
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(
        sudoku: SudokuDb,
        fields: List<FieldDb>,
    )

    @Transaction
    @Query("SELECT * FROM sudoku ORDER BY updated DESC")
    suspend fun getAll(): List<SudokuWithFields>

    @Transaction
    @Query("SELECT * FROM sudoku ORDER BY updated DESC")
    fun observeAll(): Flow<List<SudokuWithFields>>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE modeLevel = 0 ORDER BY updated DESC")
    fun observeAllNormal(): Flow<List<SudokuWithFields>>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE size = :size AND modeLevel > 0 ORDER BY modeLevel DESC")
    fun observeSudokuLevel(size: Int): Flow<List<SudokuWithFields>>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE modeLevel = -1 ORDER BY created DESC")
    fun observeDailySudokus(): Flow<List<SudokuWithFields>>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE modeLevel = -1 ORDER BY created DESC")
    suspend fun getDailySudokus(): List<SudokuWithFields>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE size = :size AND modeLevel > 0 ORDER BY modeLevel DESC")
    suspend fun getAllSudokuLevel(size: Int): List<SudokuWithFields>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE size = :size AND modeLevel = :level")
    suspend fun getSudokuLevel(
        size: Int,
        level: Int,
    ): SudokuWithFields?

    @Query("SELECT MAX(modeLevel) FROM sudoku WHERE size = :size")
    suspend fun getMaxSudokuLevel(size: Int): Int?

    @Transaction
    @Query("SELECT * FROM sudoku WHERE id = :id")
    suspend fun getById(id: String): SudokuWithFields?

    @Transaction
    @Query("SELECT * FROM sudoku WHERE modeLevel = 0 AND updated = (SELECT MAX(updated) FROM sudoku WHERE modeLevel = 0)")
    suspend fun getRecentlyUpdatedNormalSudoku(): SudokuWithFields?

    @Transaction
    @Delete
    suspend fun delete(vararg sudokus: SudokuDb)
}
