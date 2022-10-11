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
    )

fun fieldFromDb(fieldDb: FieldDb?): Field? =
    if (fieldDb == null) null
    else Field(
        sudokuId = SudokuId(fieldDb.sudokuId),
        position = Position.create(fieldDb.index, fieldDb.gameSize),
        value = fieldDb.value,
        solution = fieldDb.solution,
        notes = fieldDb.notes.map { it.digitToInt() }.toMutableList(),
        given = fieldDb.given,
        hint = fieldDb.hint,
    )

fun fieldToDb(field: Field): FieldDb =
    FieldDb(
        sudokuId = field.sudokuId.value,
        index = field.position.index,
        gameSize = field.position.size,
        value = field.value,
        solution = field.solution,
        notes = field.notes.joinToString(separator = ""),
        given = field.given,
        hint = field.hint,
    )