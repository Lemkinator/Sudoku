package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.*

fun sudokuFromDb(sudokuWithFields: SudokuWithFields?): Sudoku? =
    if (sudokuWithFields == null) null
    else Sudoku(
        id = SudokuId(sudokuWithFields.sudoku.id),
        size = sudokuWithFields.sudoku.size,
        history = mutableListOf(),
        difficulty = Difficulty.fromInt(sudokuWithFields.sudoku.difficulty),
        hintsUsed = sudokuWithFields.sudoku.hintsUsed,
        notesMade = sudokuWithFields.sudoku.notesMade,
        errorsMade = sudokuWithFields.sudoku.errorsMade,
        seconds = sudokuWithFields.sudoku.seconds,
        timer = null,
        gameListener = null,
        created = sudokuWithFields.sudoku.created,
        updated = sudokuWithFields.sudoku.updated,
        fields = sudokuWithFields.fields.mapNotNull{ fieldFromDb(it) }.toMutableList(),
        regionalHighlightingUsed = sudokuWithFields.sudoku.neighborHighlightingUsed,
        numberHighlightingUsed = sudokuWithFields.sudoku.numberHighlightingUsed,
        modeLevel = sudokuWithFields.sudoku.modeLevel,
    )

fun sudokuToDb(sudoku: Sudoku): SudokuDb =
    SudokuDb(
        id = sudoku.id.value,
        size = sudoku.size,
        difficulty = sudoku.difficulty.ordinal,
        hintsUsed = sudoku.hintsUsed,
        notesMade = sudoku.notesMade,
        errorsMade = sudoku.errorsMade,
        seconds = sudoku.seconds,
        created = sudoku.created,
        updated = sudoku.updated,
        neighborHighlightingUsed = sudoku.regionalHighlightingUsed,
        numberHighlightingUsed = sudoku.numberHighlightingUsed,
        autoNotesUsed = false,
        modeLevel = sudoku.modeLevel,
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
        history = mutableListOf(),
        difficulty = Difficulty.fromInt(sudokuExport.difficulty),
        hintsUsed = sudokuExport.hintsUsed,
        notesMade = sudokuExport.notesMade,
        errorsMade = sudokuExport.errorsMade,
        seconds = sudokuExport.seconds,
        timer = null,
        gameListener = null,
        created = sudokuExport.created,
        updated = sudokuExport.updated,
        fields = sudokuExport.fields,
        regionalHighlightingUsed = sudokuExport.regionalHighlightingUsed,
        numberHighlightingUsed = sudokuExport.numberHighlightingUsed,
        modeLevel = sudokuExport.modeLevel,
    )

fun sudokuToExport(sudoku: Sudoku): SudokuExport =
    SudokuExport(
        id = sudoku.id.value,
        size = sudoku.size,
        difficulty = sudoku.difficulty.ordinal,
        hintsUsed = sudoku.hintsUsed,
        notesMade = sudoku.notesMade,
        errorsMade = sudoku.errorsMade,
        seconds = sudoku.seconds,
        created = sudoku.created,
        updated = sudoku.updated,
        regionalHighlightingUsed = sudoku.regionalHighlightingUsed,
        numberHighlightingUsed = sudoku.numberHighlightingUsed,
        modeLevel = sudoku.modeLevel,
        fields = sudoku.fields,
    )
