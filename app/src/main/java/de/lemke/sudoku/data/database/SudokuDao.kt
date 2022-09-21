package de.lemke.sudoku.data.database

import androidx.room.*

@Dao
interface SudokuDao {

    @Transaction
    open suspend fun upsert(sudoku: SudokuDb) {
        val rowId = insert(sudoku)
        if (rowId == -1L) {
            update(sudoku)
        }
    }

    @Transaction
    open suspend fun upsert(sudokus: List<SudokuDb>) {
        sudokus.forEach { upsert(it) }
    }

    @Update
    suspend fun update(sudoku: SudokuDb)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sudoku: SudokuDb): Long

    @Transaction
    @Query("SELECT * FROM sudoku")
    suspend fun getAll(): List<SudokuWithFields>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE id = :id")
    suspend fun getById(id: String): SudokuWithFields?

}
