package de.lemke.sudoku.data.database

import androidx.room.*

@Dao
interface FieldDao {

    @Transaction
    suspend fun upsert(field: FieldDb) {
        val rowId = insert(field)
        if (rowId == -1L) {
            update(field)
        }
    }

    @Transaction
    suspend fun upsert(fields: List<FieldDb>) {
        fields.forEach { upsert(it) }
    }

    @Update
    suspend fun update(field: FieldDb)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(field: FieldDb): Long

    @Transaction
    @Query("DELETE FROM field WHERE sudokuId = :sudokuId")
    suspend fun delete(sudokuId: String)
}
