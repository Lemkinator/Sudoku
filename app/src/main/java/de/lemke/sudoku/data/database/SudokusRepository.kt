package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import javax.inject.Inject

class SudokusRepository @Inject constructor(
    private val sudokuDao: SudokuDao,
    private val fieldDao: FieldDao,
) {
    suspend fun getAllSudokus(): List<Sudoku> = sudokuDao.getAll().map { sudokuFromDb(it) }

    suspend fun getSudokuById(sudokuId: SudokuId): Sudoku = sudokuFromDb(sudokuDao.getById(sudokuId.value))

    suspend fun saveSudoku(sudoku: Sudoku) {
        sudokuDao.upsert(sudokuToDb(sudoku))
        sudoku.fields.forEach { field ->
            fieldDao.upsert(fieldToDb(field))
        }
    }

    suspend fun saveSudokus(sudokus: List<Sudoku>) {
        sudokuDao.upsert(sudokus.map { sudokuToDb(it) })
        sudokus.forEach { sudoku ->
            sudoku.fields.forEach { field ->
                fieldDao.upsert(fieldToDb(field))
            }
        }
    }

}