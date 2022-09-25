package de.lemke.sudoku.data.database

import androidx.room.*

@Entity(tableName = "field",
    primaryKeys = ["sudokuId", "index"],
    foreignKeys = [
        ForeignKey(
            entity = SudokuDb::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("sudokuId"),
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class FieldDb(
    val sudokuId: String,
    val gameSize: Int,
    val index: Int,
    val value: Int?,
    val solution: Int?,
    val notes: String,
    val given: Boolean,
    val hint: Boolean,
)
