package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class SudokusRepository @Inject constructor(
    private val sudokuDao: SudokuDao,
) {
    fun observeAllNormalSudokus() = sudokuDao.observeAllNormal().map { it.mapNotNull { sudokuFromDb(it) } }

    fun observeSudokuLevel(size: Int) = sudokuDao.observeSudokuLevel(size).map { it.mapNotNull { sudokuFromDb(it) } }

    fun observeDailySudokus() = sudokuDao.observeDailySudokus().map { it.mapNotNull { sudokuFromDb(it) } }

    suspend fun getAllSudokus(): List<Sudoku> = sudokuDao.getAll().mapNotNull { sudokuFromDb(it) }

    suspend fun getRecentlyUpdatedNormalSudoku(): Sudoku? = sudokuFromDb(sudokuDao.getRecentlyUpdatedNormalSudoku())

    suspend fun getSudokuLevel(size: Int): List<Sudoku> = sudokuDao.getAllSudokuLevel(size).mapNotNull { sudokuFromDb(it) }

    suspend fun getDailySudokus(): List<Sudoku> = sudokuDao.getDailySudokus().mapNotNull { sudokuFromDb(it) }

    suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = sudokuFromDb(sudokuDao.getById(sudokuId.value))

    suspend fun deleteSudoku(sudoku: Sudoku) = sudokuDao.delete(sudokuToDb(sudoku))

    suspend fun saveSudoku(sudoku: Sudoku, onlyUpdate: Boolean = false) {
        if (!onlyUpdate) when {
            sudoku.isDailySudoku -> getDailySudoku(sudoku.created.toLocalDate())?.let { if (it.id != sudoku.id) deleteSudoku(it) }
            sudoku.isSudokuLevel -> getSudokuLevel(sudoku.size, sudoku.modeLevel)?.let { if (it.id != sudoku.id) deleteSudoku(it) }
        }
        sudokuDao.insert(sudokuToDb(sudoku), sudoku.fields.map { fieldToDb(it, sudoku.id) })
    }

    suspend fun getMaxSudokuLevel(size: Int): Int = sudokuDao.getMaxSudokuLevel(size) ?: 0

    suspend fun deleteInvalidSudokus() = getAllSudokus().filter { it.fields.size != it.size * it.size }.forEach { deleteSudoku(it) }

    private suspend fun getSudokuLevel(size: Int, level: Int): Sudoku? = sudokuFromDb(sudokuDao.getSudokuLevel(size, level))

    private suspend fun getDailySudoku(date: LocalDate): Sudoku? =
        sudokuFromDb(sudokuDao.getDailySudokus().firstOrNull { it.sudoku.created.toLocalDate() == date })
}