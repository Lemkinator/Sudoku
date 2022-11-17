package de.lemke.sudoku.domain

import android.util.Log
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.SudokuId
import de.sfuhrm.sudoku.Creator
import de.sfuhrm.sudoku.GameMatrix
import de.sfuhrm.sudoku.GameSchemas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GenerateFieldsUseCase @Inject constructor() {
    suspend operator fun invoke(size: Int, difficulty: Difficulty, sudokuId: SudokuId): MutableList<Field> = withContext(Dispatchers.Default) {
        val schema = when (size) {
            4 -> GameSchemas.SCHEMA_4X4
            9 -> GameSchemas.SCHEMA_9X9
            16 -> GameSchemas.SCHEMA_16X16
            else -> GameSchemas.SCHEMA_9X9
        }
        var gameMatrix: GameMatrix? = null
        while (gameMatrix == null) {
            try {
                gameMatrix = Creator.createFull(schema)
            } catch (e: java.lang.AssertionError) {
                Log.e("GenerateFieldsUseCase", "Error: ${e.message}") //Why???
            }
        }
        val matrix = gameMatrix.array
        val riddle = Creator.createRiddle(gameMatrix, difficulty.numbersToRemove(size)).array
        return@withContext MutableList(size * size) { index ->
            val position = Position.create(index, size)
            val value = riddle[position.row][position.column]
            val solutionValue = matrix[position.row][position.column]
            Field(
                sudokuId = sudokuId,
                position = position,
                value = if (value == schema.unsetValue) null else value.toInt(),
                solution = solutionValue.toInt(),
                given = value == solutionValue,
            )
        }
    }
}
