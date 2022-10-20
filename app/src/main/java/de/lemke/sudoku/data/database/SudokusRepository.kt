package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.Difficulty
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

    suspend fun getAllSudokus(includeDaily: Boolean, includeLevel: Boolean): List<Sudoku> =
        if (includeDaily && includeLevel) sudokuDao.getAll().mapNotNull { sudokuFromDb(it) }
        else if (includeDaily) (sudokuDao.getAllNormal() + sudokuDao.getAllDaily()).mapNotNull { sudokuFromDb(it) }
        else if (includeLevel) (sudokuDao.getAllNormal() + sudokuDao.getAllLevel()).mapNotNull { sudokuFromDb(it) }
        else sudokuDao.getAllNormal().mapNotNull { sudokuFromDb(it) }

    suspend fun getAllSudokusWithDifficulty(difficulty: Difficulty, includeDaily: Boolean, includeLevel: Boolean): List<Sudoku> =
        if (includeDaily && includeLevel) sudokuDao.getAllWithDifficulty(difficulty.ordinal).mapNotNull { sudokuFromDb(it) }
        else if (includeDaily) (sudokuDao.getNormalWithDifficulty(difficulty.ordinal) + sudokuDao.getDailyWithDifficulty(difficulty.ordinal)).mapNotNull { sudokuFromDb(it) }
        else if (includeLevel) (sudokuDao.getNormalWithDifficulty(difficulty.ordinal) + sudokuDao.getLevelWithDifficulty(difficulty.ordinal)).mapNotNull { sudokuFromDb(it) }
        else sudokuDao.getNormalWithDifficulty(difficulty.ordinal).mapNotNull { sudokuFromDb(it) }

    suspend fun getRecentlyUpdatedNormalSudoku(): Sudoku? = sudokuFromDb(sudokuDao.getRecentlyUpdatedNormalSudoku())

    suspend fun getSudokuById(sudokuId: SudokuId): Sudoku? = sudokuFromDb(sudokuDao.getById(sudokuId.value))

    suspend fun saveSudoku(sudoku: Sudoku) {
        sudokuDao.upsert(sudokuToDb(sudoku))
        sudoku.fields.forEach { field ->
            fieldDao.upsert(fieldToDb(field))
        }
    }

    fun deleteSudoku(sudoku: Sudoku) {
        sudokuDao.delete(sudokuToDb(sudoku))
    }
}