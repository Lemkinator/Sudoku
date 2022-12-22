package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import java.time.LocalDate
import javax.inject.Inject

class SudokusRepository @Inject constructor(
    private val sudokuDao: SudokuDao,
    private val fieldDao: FieldDao,
) {
    suspend fun getAllDailySudokus(): List<Sudoku> = sudokuDao.getAllDaily().mapNotNull { sudokuFromDb(it) }

    suspend fun getAllLevelSudokus(): List<Sudoku> = sudokuDao.getAllLevel().mapNotNull { sudokuFromDb(it) }

    suspend fun getAllSudokus(): List<Sudoku> = sudokuDao.getAll().mapNotNull { sudokuFromDb(it) }

    suspend fun getRecentlyUpdatedNormalSudoku(): Sudoku? = sudokuFromDb(sudokuDao.getRecentlyUpdatedNormalSudoku())

    suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = sudokuFromDb(sudokuDao.getById(sudokuId.value))

    suspend fun deleteSudoku(sudoku: Sudoku) = sudokuDao.delete(sudokuToDb(sudoku))

    suspend fun saveSudoku(sudoku: Sudoku) {
        sudokuDao.upsert(sudokuToDb(sudoku))
        sudoku.fields.forEach { field -> fieldDao.upsert(fieldToDb(field, sudoku.id)) }
    }

    private suspend fun getSudokuLevel(level: Int): Sudoku? = sudokuFromDb(sudokuDao.getSudokuLevel(level))

    private suspend fun getDailySudoku(date: LocalDate): Sudoku? = sudokuFromDb(sudokuDao.getAllDaily().firstOrNull { it.sudoku.created.toLocalDate() == date })

    suspend fun saveSudokus(sudokus: List<Sudoku>) {
        sudokus.forEach { sudoku ->
            when {
                sudoku.isDailySudoku -> getDailySudoku(sudoku.created.toLocalDate())?.let { deleteSudoku(it) }
                sudoku.isSudokuLevel -> getSudokuLevel(sudoku.modeLevel)?.let { deleteSudoku(it) }
            }
            saveSudoku(sudoku)
        }
    }
}