package de.lemke.sudoku.data.database

import androidx.room.*

@Dao
interface SudokuDao {

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hymn: SudokuDb)

    @Transaction
    @Query("SELECT * FROM sudoku")
    suspend fun getAll(): List<SudokuWithFields>

    @Transaction
    @Query("SELECT * FROM sudoku WHERE id = :id")
    suspend fun getById(id: String): SudokuWithFields?

}
