package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.Field
import java.time.LocalDateTime

data class SudokuExport(
    val id: String,
    val size: Int,
    val difficulty: Int,
    val modeLevel: Int,
    var regionalHighlightingUsed: Boolean,
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
    val fields: MutableList<Field>,
)