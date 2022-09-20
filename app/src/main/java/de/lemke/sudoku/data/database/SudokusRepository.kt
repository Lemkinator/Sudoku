package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import javax.inject.Inject

class SudokusRepository @Inject constructor(
    private val sudokuDao: SudokuDao,
    private val fieldDao: FieldDao,
) {
    suspend fun getAllSudokus(): List<Sudoku> = sudokuDao.getAll().mapNotNull { sudokuFromDb(it) }

    suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = sudokuFromDb(sudokuDao.getById(sudokuId.value))

    suspend fun addSudoku(sudoku: Sudoku) = sudokuDao.insert(sudokuToDb(sudoku))
}