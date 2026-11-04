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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.lemke.sudoku.domain.model.GameMode
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    /** Replaces the rows this game collides with: the same id, the same size and level, or the same day. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(game: GameEntity)

    @Query("SELECT * FROM game WHERE id = :id")
    suspend fun getById(id: String): GameEntity?

    @Query("SELECT * FROM game WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<GameEntity>

    @Query("SELECT * FROM game ORDER BY updatedAt DESC")
    suspend fun getAll(): List<GameEntity>

    @Query("SELECT * FROM game ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM game WHERE mode = ${GameMode.CODE_NORMAL} ORDER BY updatedAt DESC")
    fun observeNormal(): Flow<List<GameEntity>>

    @Query("SELECT * FROM game WHERE mode = ${GameMode.CODE_NORMAL} ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getRecentlyUpdatedNormal(): GameEntity?

    @Query("SELECT * FROM game WHERE mode = ${GameMode.CODE_DAILY} ORDER BY createdAt DESC")
    fun observeDaily(): Flow<List<GameEntity>>

    @Query("SELECT * FROM game WHERE mode = ${GameMode.CODE_DAILY} ORDER BY createdAt DESC")
    suspend fun getDaily(): List<GameEntity>

    @Query("SELECT * FROM game WHERE mode = ${GameMode.CODE_LEVEL} AND size = :size ORDER BY level DESC")
    fun observeLevels(size: Int): Flow<List<GameEntity>>

    @Query("SELECT * FROM game WHERE mode = ${GameMode.CODE_LEVEL} AND size = :size ORDER BY level DESC")
    suspend fun getLevels(size: Int): List<GameEntity>

    @Query("SELECT MAX(level) FROM game WHERE size = :size")
    suspend fun maxLevel(size: Int): Int?

    @Query("DELETE FROM game WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM game")
    suspend fun deleteAll()
}
