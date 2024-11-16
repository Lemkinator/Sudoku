package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class SudokusRepository @Inject constructor(
    private val sudokuDao: SudokuDao,
    private val fieldDao: FieldDao,
) {
    suspend fun getAllDailySudokus(): List<Sudoku> = sudokuDao.getAllDaily().mapNotNull { sudokuFromDb(it) }

    suspend fun getAllLevelSudokus(size:Int): List<Sudoku> = sudokuDao.getAllLevelWithSize(size).mapNotNull { sudokuFromDb(it) }

    fun observeAllNormalSudokus() = sudokuDao.observeAllNormal().map { it.mapNotNull { sudokuFromDb(it) } }

    suspend fun getAllSudokus(): List<Sudoku> = sudokuDao.getAll().mapNotNull { sudokuFromDb(it) }

    suspend fun getRecentlyUpdatedNormalSudoku(): Sudoku? = sudokuFromDb(sudokuDao.getRecentlyUpdatedNormalSudoku())

    suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = sudokuFromDb(sudokuDao.getById(sudokuId.value))

    suspend fun deleteSudoku(sudoku: Sudoku) = sudokuDao.delete(sudokuToDb(sudoku))

    suspend fun saveSudoku(sudoku: Sudoku) {
        when {
            sudoku.isDailySudoku -> getDailySudoku(sudoku.created.toLocalDate())?.let { deleteSudoku(it) }
            sudoku.isSudokuLevel -> getSudokuLevel(sudoku.size, sudoku.modeLevel)?.let { deleteSudoku(it) }
        }
        sudokuDao.upsert(sudokuToDb(sudoku))
        fieldDao.upsert(sudoku.fields.map { fieldToDb(it, sudoku.id) })
    }

    private suspend fun getSudokuLevel(size: Int, level: Int): Sudoku? = sudokuFromDb(sudokuDao.getSudokuLevel(size, level))

    suspend fun getMaxSudokuLevel(size: Int): Int = sudokuDao.getMaxSudokuLevel(size) ?: 0

    private suspend fun getDailySudoku(date: LocalDate): Sudoku? = sudokuFromDb(sudokuDao.getAllDaily().firstOrNull { it.sudoku.created.toLocalDate() == date })
}