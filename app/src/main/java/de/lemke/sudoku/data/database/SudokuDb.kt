package de.lemke.sudoku.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDateTime

@Entity(tableName = "sudoku")
data class SudokuDb(
    @PrimaryKey
    val id: String,
    val size: Int,
    val difficulty: Int,
    val modeLevel: Int,
    val regionalHighlightingUsed: Boolean,
    val numberHighlightingUsed: Boolean,
    val eraserUsed: Boolean,
    val isChecklist: Boolean,
    val isReverseChecklist: Boolean,
    val checklistNumber: Int,
    var hintsUsed: Int,
    var notesMade: Int,
    var errorsMade: Int,
    val created: LocalDateTime,
    val updated: LocalDateTime,
    val seconds: Int,
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