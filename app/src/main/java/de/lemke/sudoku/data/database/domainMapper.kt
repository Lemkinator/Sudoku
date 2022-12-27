package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.*

fun sudokuFromDb(sudokuWithFields: SudokuWithFields?): Sudoku? =
    if (sudokuWithFields == null) null
    else Sudoku(
        id = SudokuId(sudokuWithFields.sudoku.id),
        size = sudokuWithFields.sudoku.size,
        difficulty = Difficulty.fromInt(sudokuWithFields.sudoku.difficulty),
        modeLevel = sudokuWithFields.sudoku.modeLevel,
        regionalHighlightingUsed = sudokuWithFields.sudoku.regionalHighlightingUsed,
        numberHighlightingUsed = sudokuWithFields.sudoku.numberHighlightingUsed,
        eraserUsed = sudokuWithFields.sudoku.eraserUsed,
        isChecklist = sudokuWithFields.sudoku.isChecklist,
        isReverseChecklist = sudokuWithFields.sudoku.isReverseChecklist,
        checklistNumber = sudokuWithFields.sudoku.checklistNumber,
        hintsUsed = sudokuWithFields.sudoku.hintsUsed,
        notesMade = sudokuWithFields.sudoku.notesMade,
        errorsMade = sudokuWithFields.sudoku.errorsMade,
        created = sudokuWithFields.sudoku.created,
        updated = sudokuWithFields.sudoku.updated,
        seconds = sudokuWithFields.sudoku.seconds,
        timer = null,
        gameListener = null,
        fields = sudokuWithFields.fields.mapNotNull{ fieldFromDb(it) }.toMutableList(),
    )

fun sudokuToDb(sudoku: Sudoku): SudokuDb =
    SudokuDb(
        id = sudoku.id.value,
        size = sudoku.size,
        difficulty = sudoku.difficulty.ordinal,
        modeLevel = sudoku.modeLevel,
        regionalHighlightingUsed = sudoku.regionalHighlightingUsed,
        numberHighlightingUsed = sudoku.numberHighlightingUsed,
        eraserUsed = sudoku.eraserUsed,
        isChecklist = sudoku.isChecklist,
        isReverseChecklist = sudoku.isReverseChecklist,
        checklistNumber = sudoku.checklistNumber,
        hintsUsed = sudoku.hintsUsed,
        notesMade = sudoku.notesMade,
        errorsMade = sudoku.errorsMade,
        created = sudoku.created,
        updated = sudoku.updated,
        seconds = sudoku.seconds,
    )

fun fieldFromDb(fieldDb: FieldDb?): Field? =
    if (fieldDb == null) null
    else Field(
        position = Position.create(fieldDb.index, fieldDb.gameSize),
        value = fieldDb.value,
        solution = fieldDb.solution,
        notes = fieldDb.notes.map { it.digitToInt() }.toMutableList(),
        given = fieldDb.given,
        hint = fieldDb.hint,
    )

fun fieldToDb(field: Field, sudokuId: SudokuId): FieldDb =
    FieldDb(
        sudokuId = sudokuId.value,
        index = field.position.index,
        gameSize = field.position.size,
        value = field.value,
        solution = field.solution,
        notes = field.notes.joinToString(separator = ""),
        given = field.given,
        hint = field.hint,
    )

fun sudokuFromExport(sudokuExport: SudokuExport): Sudoku =
    Sudoku(
        id = SudokuId(sudokuExport.id),
        size = sudokuExport.size,
        difficulty = Difficulty.fromInt(sudokuExport.difficulty),
        modeLevel = sudokuExport.modeLevel,
        regionalHighlightingUsed = sudokuExport.regionalHighlightingUsed,
        numberHighlightingUsed = sudokuExport.numberHighlightingUsed,
        eraserUsed = sudokuExport.eraserUsed,
        isChecklist = sudokuExport.isChecklist,
        isReverseChecklist = sudokuExport.isReverseChecklist,
        checklistNumber = sudokuExport.checklistNumber,
        hintsUsed = sudokuExport.hintsUsed,
        notesMade = sudokuExport.notesMade,
        errorsMade = sudokuExport.errorsMade,
        created = sudokuExport.created,
        updated = sudokuExport.updated,
        seconds = sudokuExport.seconds,
        timer = null,
        gameListener = null,
        fields = sudokuExport.fields,
    )

fun sudokuToExport(sudoku: Sudoku): SudokuExport =
    SudokuExport(
        id = sudoku.id.value,
        size = sudoku.size,
        difficulty = sudoku.difficulty.ordinal,
        modeLevel = sudoku.modeLevel,
        regionalHighlightingUsed = sudoku.regionalHighlightingUsed,
        numberHighlightingUsed = sudoku.numberHighlightingUsed,
        eraserUsed = sudoku.eraserUsed,
        isChecklist = sudoku.isChecklist,
        isReverseChecklist = sudoku.isReverseChecklist,
        checklistNumber = sudoku.checklistNumber,
        hintsUsed = sudoku.hintsUsed,
        notesMade = sudoku.notesMade,
        errorsMade = sudoku.errorsMade,
        seconds = sudoku.seconds,
        created = sudoku.created,
        updated = sudoku.updated,
        fields = sudoku.fields,
    )
