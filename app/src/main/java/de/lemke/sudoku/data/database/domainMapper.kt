package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.*

fun sudokuFromDb(sudokuWithFields: SudokuWithFields?): Sudoku? {
    val fields = sudokuWithFields?.fields?.mapNotNull { fieldFromDb(it) }?.toMutableList() ?: return null
    if (fields.size != sudokuWithFields.sudoku.size * sudokuWithFields.sudoku.size) return null
    return Sudoku(
        id = SudokuId(sudokuWithFields.sudoku.id),
        size = sudokuWithFields.sudoku.size,
        difficulty = Difficulty.fromInt(sudokuWithFields.sudoku.difficulty),
        modeLevel = sudokuWithFields.sudoku.modeLevel,
        created = sudokuWithFields.sudoku.created,
        updated = sudokuWithFields.sudoku.updated,
        seconds = sudokuWithFields.sudoku.seconds,
        regionalHighlightingUsed = sudokuWithFields.sudoku.regionalHighlightingUsed,
        numberHighlightingUsed = sudokuWithFields.sudoku.numberHighlightingUsed,
        eraserUsed = sudokuWithFields.sudoku.eraserUsed,
        isChecklist = sudokuWithFields.sudoku.isChecklist,
        isReverseChecklist = sudokuWithFields.sudoku.isReverseChecklist,
        checklistNumber = sudokuWithFields.sudoku.checklistNumber,
        hintsUsed = sudokuWithFields.sudoku.hintsUsed,
        notesMade = sudokuWithFields.sudoku.notesMade,
        errorsMade = sudokuWithFields.sudoku.errorsMade,
        timer = null,
        gameListener = null,
        fields = fields
    )
}

fun sudokuToDb(sudoku: Sudoku): SudokuDb =
    SudokuDb(
        id = sudoku.id.value,
        size = sudoku.size,
        difficulty = sudoku.difficulty.ordinal,
        modeLevel = sudoku.modeLevel,
        created = sudoku.created,
        updated = sudoku.updated,
        seconds = sudoku.seconds,
        regionalHighlightingUsed = sudoku.regionalHighlightingUsed,
        numberHighlightingUsed = sudoku.numberHighlightingUsed,
        eraserUsed = sudoku.eraserUsed,
        isChecklist = sudoku.isChecklist,
        isReverseChecklist = sudoku.isReverseChecklist,
        checklistNumber = sudoku.checklistNumber,
        hintsUsed = sudoku.hintsUsed,
        notesMade = sudoku.notesMade,
        errorsMade = sudoku.errorsMade,
    )

fun fieldFromDb(fieldDb: FieldDb?): Field? =
    if (fieldDb?.solution == null) null
    else Field(
        position = Position.create(fieldDb.index, fieldDb.gameSize),
        solution = fieldDb.solution,
        value = fieldDb.value,
        given = fieldDb.given,
        hint = fieldDb.hint,
        notes = fieldDb.notes.map { it.digitToInt() }.toMutableList(),
    )

fun fieldToDb(field: Field, sudokuId: SudokuId): FieldDb =
    FieldDb(
        sudokuId = sudokuId.value,
        index = field.position.index,
        gameSize = field.position.size,
        solution = field.solution,
        value = field.value,
        given = field.given,
        hint = field.hint,
        notes = field.notes.joinToString(separator = ""),
    )

fun sudokuFromExport(sudokuExport: SudokuExport): Sudoku? {
    val fields = sudokuExport.fields.mapNotNull { fieldFromExport(it, sudokuExport.size) }.toMutableList()
    if (fields.size != sudokuExport.size * sudokuExport.size) return null
    return Sudoku(
        id = SudokuId(sudokuExport.id),
        size = sudokuExport.size,
        difficulty = Difficulty.fromInt(sudokuExport.difficulty),
        modeLevel = sudokuExport.modeLevel,
        created = sudokuExport.created,
        updated = sudokuExport.updated,
        seconds = sudokuExport.seconds,
        regionalHighlightingUsed = sudokuExport.regionalHighlightingUsed == true,
        numberHighlightingUsed = sudokuExport.numberHighlightingUsed == true,
        eraserUsed = sudokuExport.eraserUsed == true,
        isChecklist = sudokuExport.isChecklist == true,
        isReverseChecklist = sudokuExport.isReverseChecklist == true,
        checklistNumber = sudokuExport.checklistNumber ?: 0,
        hintsUsed = sudokuExport.hintsUsed ?: 0,
        notesMade = sudokuExport.notesMade ?: 0,
        errorsMade = sudokuExport.errorsMade ?: 0,
        timer = null,
        gameListener = null,
        fields = fields,
    )
}

fun sudokuToExport(sudoku: Sudoku): SudokuExport =
    SudokuExport(
        id = sudoku.id.value,
        size = sudoku.size,
        difficulty = sudoku.difficulty.ordinal,
        modeLevel = sudoku.modeLevel,
        created = sudoku.created,
        updated = sudoku.updated,
        seconds = sudoku.seconds,
        regionalHighlightingUsed = sudoku.regionalHighlightingUsed.takeIf { it },
        numberHighlightingUsed = sudoku.numberHighlightingUsed.takeIf { it },
        eraserUsed = sudoku.eraserUsed.takeIf { it },
        isChecklist = sudoku.isChecklist.takeIf { it },
        isReverseChecklist = sudoku.isReverseChecklist.takeIf { it },
        checklistNumber = sudoku.checklistNumber.takeIf { it > 0 },
        hintsUsed = sudoku.hintsUsed.takeIf { it > 0 },
        notesMade = sudoku.notesMade.takeIf { it > 0 },
        errorsMade = sudoku.errorsMade.takeIf { it > 0 },
        fields = sudoku.fields.map { fieldToExport(it) },
    )

fun fieldFromExport(fieldExport: FieldExport, size: Int): Field? =
    if (fieldExport.solution == null) null
    else Field(
        position = Position.create(fieldExport.index, size),
        value = fieldExport.value,
        solution = fieldExport.solution,
        given = fieldExport.given == true,
        hint = fieldExport.hint == true,
        notes = fieldExport.notes?.map { it.digitToInt() }?.toMutableList() ?: mutableListOf(),
    )

fun fieldToExport(field: Field): FieldExport =
    FieldExport(
        index = field.position.index,
        value = field.value,
        solution = field.solution,
        given = field.given.takeIf { it },
        hint = field.hint.takeIf { it },
        notes = field.notes.joinToString(separator = "").ifBlank { null },
    )
