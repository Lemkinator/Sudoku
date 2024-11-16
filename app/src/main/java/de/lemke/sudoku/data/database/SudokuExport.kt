package de.lemke.sudoku.data.database

import java.time.LocalDateTime

data class SudokuExport(
    val id: String,
    val size: Int,
    val difficulty: Int,
    val modeLevel: Int,
    val created: LocalDateTime,
    val updated: LocalDateTime,
    val seconds: Int,
    var regionalHighlightingUsed: Boolean? = null,
    val numberHighlightingUsed: Boolean? = null,
    val eraserUsed: Boolean? = null,
    val isChecklist: Boolean? = null,
    val isReverseChecklist: Boolean? = null,
    val checklistNumber: Int? = null,
    val hintsUsed: Int? = null,
    val notesMade: Int? = null,
    val errorsMade: Int? = null,
    val fields: List<FieldExport>,
)

data class FieldExport(
    val index: Int,
    val solution: Int?,
    val value: Int? = null,
    val given: Boolean? = null,
    val hint: Boolean? = null,
    val notes: String? = null,
)