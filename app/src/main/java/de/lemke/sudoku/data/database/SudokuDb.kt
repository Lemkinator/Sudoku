package de.lemke.sudoku.data.database

import androidx.room.*
import de.lemke.sudoku.domain.model.*
import java.time.ZonedDateTime

@Entity(tableName = "sudoku")
data class SudokuDb(
    @PrimaryKey
    val id: String,
    val size: Int,
    val difficulty: Difficulty,
    var hintsUsed: Int,
    var errorsMade: Int,
    val seconds: Int,
    val created: ZonedDateTime,
    val updated: ZonedDateTime,
)

data class SudokuWithFields(
    @Embedded
    val sudoku: SudokuDb,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
    )
    val fields: List<FieldDb>
)