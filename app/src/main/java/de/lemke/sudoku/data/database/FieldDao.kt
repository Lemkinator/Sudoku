package de.lemke.sudoku.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FieldDao {
    suspend fun insert(fields: List<FieldDb>) {
        fields.forEach { insert(it) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(field: FieldDb)

    @Query("DELETE FROM field WHERE id = :id")
    suspend fun delete(id: String)
}
