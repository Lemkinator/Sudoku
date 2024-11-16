package de.lemke.sudoku.data.database

import java.time.LocalDateTime

data class SudokuExport(
    val id: String,
    val size: Int,
    val difficulty: Int,
    val modeLevel: Int,
    var regionalHighlightingUsed: Boolean? = null,
    val numberHighlightingUsed: Boolean? = null,
    val eraserUsed: Boolean? = null,
    val isChecklist: Boolean? = null,
    val isReverseChecklist: Boolean? = null,
    val checklistNumber: Int? = null,
    var hintsUsed: Int? = null,
    var notesMade: Int? = null,
    var errorsMade: Int? = null,
    val created: LocalDateTime,
    val updated: LocalDateTime,
    val seconds: Int,
    val fields: List<FieldExport>,
)

data class FieldExport(
    val index: Int,
    val notes: List<Int>? = null,
    var given: Boolean? = null,
    var hint: Boolean? = null,
    var solution: Int? = null,
    var value: Int? = null,
)