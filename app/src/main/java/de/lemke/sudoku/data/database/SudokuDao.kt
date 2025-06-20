package de.lemke.sudoku.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SudokuDao {
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sudoku: SudokuDb, fields: List<FieldDb>)

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
    suspend fun getSudokuLevel(size: Int, level: Int): SudokuWithFields?

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
