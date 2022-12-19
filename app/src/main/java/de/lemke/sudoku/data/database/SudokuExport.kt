package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.Field
import java.time.LocalDateTime

data class SudokuExport(
    val id: String,
    val size: Int,
    val difficulty: Int,
    var hintsUsed: Int,
    var notesMade: Int,
    var errorsMade: Int,
    val seconds: Int,
    val created: LocalDateTime,
    val updated: LocalDateTime,
    var regionalHighlightingUsed: Boolean,
    val numberHighlightingUsed: Boolean,
    val modeLevel: Int,
    val fields: MutableList<Field>,
)