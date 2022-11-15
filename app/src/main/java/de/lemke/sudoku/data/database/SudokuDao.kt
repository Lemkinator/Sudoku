package de.lemke.sudoku.data.database

import androidx.room.*

@Dao
interface SudokuDao {

    @Transaction
    suspend fun upsert(sudoku: SudokuDb) {
        val rowId = insert(sudoku)
        if (rowId == -1L) {
            update(sudoku)
        }
    }

    @Transaction
    suspend fun upsert(sudokus: List<SudokuDb>) {
        sudokus.forEach { upsert(it) }
    }

    @Update
    suspend fun update(sudoku: SudokuDb)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sudoku: SudokuDb): Long

    @Transaction
    @Query("SELECT * FROM sudoku ORDER BY updated DESC")
    suspend fun getAll(): List<SudokuWithFields>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE modeLevel = -1 ORDER BY created DESC")
    suspend fun getAllDaily(): List<SudokuWithFields>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE modeLevel > 0 ORDER BY modeLevel DESC")
    suspend fun getAllLevel(): List<SudokuWithFields>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE id = :id")
    suspend fun getById(id: String): SudokuWithFields?

    @Transaction
    @Query("SELECT * FROM sudoku WHERE modeLevel = 0 AND updated = (SELECT MAX(updated) FROM sudoku WHERE modeLevel = 0)")
    suspend fun getRecentlyUpdatedNormalSudoku(): SudokuWithFields?

    @Delete
    fun delete(sudoku: SudokuDb)

}
