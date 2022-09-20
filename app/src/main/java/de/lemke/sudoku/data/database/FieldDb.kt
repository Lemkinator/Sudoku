package de.lemke.sudoku.data.database

import androidx.room.*

@Entity(tableName = "field",
    primaryKeys = ["id", "index"],
    foreignKeys = [
        ForeignKey(
            entity = SudokuDb::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("id"),
            onDelete = ForeignKey.NO_ACTION,
        )
    ],
)
data class FieldDb(
    @PrimaryKey
    val id: String,
    val gameSize: Int,
    val index: Int,
    val value: Int?,
    val solution: Int?,
    val notes: String,
    val given: Boolean,
    val hint: Boolean,
)
