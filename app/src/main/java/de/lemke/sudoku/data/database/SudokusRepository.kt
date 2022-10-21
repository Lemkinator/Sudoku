package de.lemke.sudoku.data.database

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import javax.inject.Inject

class SudokusRepository @Inject constructor(
    private val sudokuDao: SudokuDao,
    private val fieldDao: FieldDao,
) {
    suspend fun getAllDailySudokus(): List<Sudoku> = sudokuDao.getAllDaily().mapNotNull { sudokuFromDb(it) }

    suspend fun getAllLevelSudokus(): List<Sudoku> = sudokuDao.getAllLevel().mapNotNull { sudokuFromDb(it) }

    suspend fun getAllSudokus(includeNormal: Boolean, includeDaily: Boolean, includeLevel: Boolean): List<Sudoku> = when {
        includeNormal && includeDaily && includeLevel -> sudokuDao.getAll().mapNotNull { sudokuFromDb(it) }
        includeNormal && includeDaily -> (sudokuDao.getAllNormal() + sudokuDao.getAllDaily()).mapNotNull { sudokuFromDb(it) }
        includeNormal && includeLevel -> (sudokuDao.getAllNormal() + sudokuDao.getAllLevel()).mapNotNull { sudokuFromDb(it) }
        includeDaily && includeLevel -> (sudokuDao.getAllDaily() + sudokuDao.getAllLevel()).mapNotNull { sudokuFromDb(it) }
        includeNormal -> sudokuDao.getAllNormal().mapNotNull { sudokuFromDb(it) }
        includeDaily -> sudokuDao.getAllDaily().mapNotNull { sudokuFromDb(it) }
        includeLevel -> sudokuDao.getAllLevel().mapNotNull { sudokuFromDb(it) }
        else -> emptyList()
    }

    suspend fun getAllSudokusWithDifficulty(
        difficulty: Difficulty,
        includeNormal: Boolean,
        includeDaily: Boolean,
        includeLevel: Boolean
    ): List<Sudoku> = when {
        includeNormal && includeDaily && includeLevel -> sudokuDao.getAllWithDifficulty(difficulty.ordinal).mapNotNull { sudokuFromDb(it) }
        includeNormal && includeDaily -> (sudokuDao.getNormalWithDifficulty(difficulty.ordinal) + sudokuDao.getDailyWithDifficulty(
            difficulty.ordinal
        )).mapNotNull { sudokuFromDb(it) }
        includeNormal && includeLevel -> (sudokuDao.getNormalWithDifficulty(difficulty.ordinal) + sudokuDao.getLevelWithDifficulty(
            difficulty.ordinal
        )).mapNotNull { sudokuFromDb(it) }
        includeDaily && includeLevel -> (sudokuDao.getDailyWithDifficulty(difficulty.ordinal) + sudokuDao.getLevelWithDifficulty(difficulty.ordinal)).mapNotNull {
            sudokuFromDb(
                it
            )
        }
        includeNormal -> sudokuDao.getNormalWithDifficulty(difficulty.ordinal).mapNotNull { sudokuFromDb(it) }
        includeDaily -> sudokuDao.getDailyWithDifficulty(difficulty.ordinal).mapNotNull { sudokuFromDb(it) }
        includeLevel -> sudokuDao.getLevelWithDifficulty(difficulty.ordinal).mapNotNull { sudokuFromDb(it) }
        else -> emptyList()
    }

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