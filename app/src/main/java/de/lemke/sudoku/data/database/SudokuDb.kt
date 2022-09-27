package de.lemke.sudoku.data.database

import androidx.room.*
import de.lemke.sudoku.domain.model.*
import java.time.LocalDateTime

@Entity(tableName = "sudoku")
data class SudokuDb(
    @PrimaryKey
    val id: String,
    val size: Int,
    val difficulty: Int,
    var hintsUsed: Int,
    var errorsMade: Int,
    val seconds: Int,
    val created: LocalDateTime,
    val updated: LocalDateTime,
    val neighborHighlightingUsed: Boolean,
    val numberHighlightingUsed: Boolean,
)

data class SudokuWithFields(
    @Embedded
    val sudoku: SudokuDb,
    @Relation(
        parentColumn = "id",
        entityColumn = "sudokuId",
    )
    val fields: List<FieldDb>
)