package de.lemke.sudoku.domain

import android.util.Log
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.SudokuId
import de.sfuhrm.sudoku.Creator
import de.sfuhrm.sudoku.GameSchemas
import java.util.*
import javax.inject.Inject
import kotlin.math.sqrt

class GenerateFieldsUseCase @Inject constructor() {
    private val random: Random = Random()
    private var tries: Int = 40
    private var sudokuSize: Int = 9
    private var sqrtSize: Int = 3
    private var numbersToRemove: Int = -1


    operator fun invoke(size: Int, difficulty: Difficulty, sudokuId: SudokuId): MutableList<Field> {
        this@GenerateFieldsUseCase.sudokuSize = size
        this@GenerateFieldsUseCase.sqrtSize = sqrt(size.toDouble()).toInt()
        this@GenerateFieldsUseCase.numbersToRemove = difficulty.numbersToRemove(size)

        val schema = when (size) {
            4 -> GameSchemas.SCHEMA_4X4
            9 -> GameSchemas.SCHEMA_9X9
            16 -> GameSchemas.SCHEMA_16X16
            else -> GameSchemas.SCHEMA_9X9
        }
        val gameMatrix = Creator.createFull(schema)
        val matrix = gameMatrix.array
        val riddle = Creator.createRiddle(gameMatrix, difficulty.numbersToRemove(size)).array


        val fields = MutableList(size * size) { index ->
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
        Log.d("fields", fields.map { it.value to it.given }.toString())

        return fields


        /*var solutions: MutableList<Array<Array<Int?>>>
        //Generate fields until there is at least one solution
        do {
            solutions = generateFields().getSolutions()
        } while (solutions.isEmpty())
        val solution = solutions.random()
        val output = solution.removeRandomNumbers()

        return MutableList(size * size) { index ->
            val position = Position.create(index, size)
            Field(
                sudokuId = sudokuId,
                position = position,
                value = output[position.row][position.column],
                solution = solution[position.row][position.column],
                given = output[position.row][position.column] != null,
            )
        }*/
    }

    private fun generateFields(): Array<Array<Int?>> {
        val fields = Array(sudokuSize) { arrayOfNulls<Int>(sudokuSize) }
        for (i in 0 until (sudokuSize * sudokuSize / 3)) fields.addRandomNumber()
        return fields
    }

    private fun Array<Array<Int?>>.addRandomNumber() {
        val (row, column) = getRandomFieldCoordinates(empty = true)
        if (row == -1) return //no empty field found
        val possibleValues = getPossibleValues(row, column)
        if (possibleValues.isNotEmpty()) {
            this[row][column] = possibleValues[random.nextInt(possibleValues.size)]
        } else {
            addRandomNumber()
        }
    }

    private fun Array<Array<Int?>>.isValid(value: Int, row: Int, column: Int): Boolean {
        for (number in this[row]) if (number == value) return false //check row
        for (rows in this) if (value == rows[column]) return false //check column
        //check square
        val sRow = row / sqrtSize * sqrtSize
        val sColumn = column / sqrtSize * sqrtSize
        for (r in sRow until sRow + sqrtSize) for (c in sColumn until sColumn + sqrtSize) if (value == this[r][c]) return false
        return true
    }

    private fun Array<Array<Int?>>.getSolutions(): MutableList<Array<Array<Int?>>> {
        val solutions = mutableListOf<Array<Array<Int?>>>()
        solveFieldsForSolutions(solutions)
        return solutions
    }

    private fun Array<Array<Int?>>.solveFieldsForSolutions(solutions: MutableList<Array<Array<Int?>>>, row: Int = 0, column: Int = 0) {
        when {
            row == sudokuSize -> { //save the solution once reached the end
                solutions.add(copy())
                return
            }
            column == sudokuSize -> { //go to next row
                solveFieldsForSolutions(solutions, row + 1, 0)
            }
            this[row][column] == null -> {
                for (n in 1..sudokuSize) {
                    if (isValid(n, row, column)) {
                        this[row][column] = n
                        solveFieldsForSolutions(solutions, row, column + 1)
                    }
                }
                this[row][column] = null
            }
            else -> solveFieldsForSolutions(solutions, row, column + 1)
        }
    }

    /*
    tailrec approach, that didn't work :/

    private tailrec fun solveFieldsForSolutions(
        solutions: MutableList<Array<Array<Int?>>>,
        fields: Array<Array<Int?>>,
        row: Int = 0,
        column: Int = 0
    ) {
        when {
            row == sudokuSize -> { //save the solution once reached the end
                if (fields.none { ints -> ints.none { it == null } }) solutions.add(fields.copy())
                return
            }
            column == sudokuSize -> { //go to next row
                solveFieldsForSolutions(solutions, fields, row + 1, 0)
            }
            fields[row][column] == null -> {
                solveFieldsForSolutions(
                    solutions,
                    fields.with(row, column, fields.getPossibleValues(row, column).firstOrNull()),
                    row,
                    column + 1
                )
            }
            else -> solveFieldsForSolutions(solutions, fields, row, column + 1)
        }
    }

    private fun Array<Array<Int?>>.with(row: Int, column: Int, value: Int?): Array<Array<Int?>> {
        val copy = copy()
        copy[row][column] = value
        return copy
    }*/

    private fun Array<Array<Int?>>.removeRandomNumbers(): Array<Array<Int?>> {
        val newFields = copy()
        val (row, column) = getRandomFieldCoordinates(false)
        newFields[row][column] = null
        return if (newFields.getSolutions().size == 1) { //continue while solutions.size() is one
            numbersToRemove--
            if (numbersToRemove == 0) { //done, enough numbers removed
                newFields
            } else { //continue removing numbers
                newFields.removeRandomNumbers()
            }
        } else { //multiple or no solutions
            tries--
            if (tries > 0) { //try to remove other numbers
                removeRandomNumbers()
            } else { //give up and return fields
                this
            }
        }
    }

    private fun Array<Array<Int?>>.sudokuString(): String {
        val sb = StringBuilder()
        for (i in 0 until sudokuSize) {
            sb.append("\n")
            for (j in 0 until sudokuSize) {
                sb.append(this[i][j] ?: "0")
                sb.append(" ")
            }
        }
        return sb.toString()
    }

    private fun Array<Int?>.copy(): Array<Int?> = Array(sudokuSize) { this[it] }
    private fun Array<Array<Int?>>.copy(): Array<Array<Int?>> = Array(sudokuSize) { this[it].copy() }
    private fun Array<Array<Int?>>.getRow(row: Int): Array<Int?> = this[row].copy()
    private fun Array<Array<Int?>>.getColumn(column: Int): Array<Int?> = Array(sudokuSize) { this[it][column] }
    private fun Array<Array<Int?>>.getBlock(block: Int): Array<Int?> = Array(sudokuSize) {
        val row = block / sqrtSize * sqrtSize + it / sqrtSize
        val col = block % sqrtSize * sqrtSize + it % sqrtSize
        this[row][col]
    }

    private fun Array<Array<Int?>>.getPossibleValues(row: Int, column: Int): List<Int> =
        (1..sudokuSize).filter { it !in getRow(row) && it !in getColumn(column) && it !in getBlock(row / sqrtSize * sqrtSize + column / sqrtSize) }

    private fun Array<Array<Int?>>.getRandomFieldCoordinates(empty: Boolean): Pair<Int, Int> {
        val emptyCells = mutableListOf<Pair<Int, Int>>()
        for (row in 0 until sudokuSize) {
            for (column in 0 until sudokuSize) {
                if (empty && this[row][column] == null) {
                    emptyCells.add(Pair(row, column))
                } else if (!empty && this[row][column] != null) {
                    emptyCells.add(Pair(row, column))
                }
            }
        }
        return if (emptyCells.isNotEmpty()) emptyCells[random.nextInt(emptyCells.size)]
        else -1 to -1
    }


    /*
    private val initialSudoku9x9 = listOf(
        1, 2, 3, 4, 5, 6, 7, 8, 9,
        4, 5, 6, 7, 8, 9, 1, 2, 3,
        7, 8, 9, 1, 2, 3, 4, 5, 6,
        2, 3, 1, 5, 6, 4, 8, 9, 7,
        5, 6, 4, 8, 9, 7, 2, 3, 1,
        8, 9, 7, 2, 3, 1, 5, 6, 4,
        3, 1, 2, 6, 4, 5, 9, 7, 8,
        6, 4, 5, 9, 7, 8, 3, 1, 2,
        9, 7, 8, 3, 1, 2, 6, 4, 5
    )
    private val initialSudoku4x4 = listOf(
        1, 2, 3, 4,
        3, 4, 1, 2,
        2, 3, 4, 1,
        4, 1, 2, 3
    )

    suspend operator fun invoke(size: Int = 9, difficulty: Difficulty): Sudoku = withContext(Dispatchers.Default) {
        //1.    Find any filled sudoku of sudoku. (use trivial ones will not affect final result)
        val sudoku = generateSudoku(size, difficulty)
        //2.    for each number from 1 to 9 (say num), (i.e 1, 2, 3, 5, 6, 7, 8, 9) take a random number from range [1 to 9],
        //      traverse the sudoku, swap num with your random number.
        shuffleNumbers(sudoku)
        //3.    Now shuffle rows. Take the first group of 3 rows , shuffle them , and do it for all rows. (in 9 X 9 sudoku),
        //      do it for second group and as well as third.
        shuffleRows(sudoku)
        //4.    Swap columns, again take block of 3 columns , shuffle them, and do it for all 3 blocks
        shuffleColumns(sudoku)
        //5.    swap the row blocks itself (ie 3X9 blocks)
        shuffleBlockRows(sudoku)
        //6.    do the same for columns, swap blockwise
        shuffleBlockColumns(sudoku)
        //7.    remove numbers and check if it is solvable.
        removeNumbers(sudoku, difficulty)
        Log.d("result", sudoku.fields.map { it.value }.joinToString())
        sudoku
    }

    private fun generateSudoku(size: Int, difficulty: Difficulty): Sudoku {
        val sudokuId = SudokuId.generate()
        val initialSudoku = getInitialSudoku(size)
        return Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = difficulty,
            fields = MutableList(initialSudoku.size) { index ->
                Field(
                    sudokuId = sudokuId,
                    position = Position.create(index, size),
                    value = initialSudoku[index],
                    solution = initialSudoku[index],
                    given = true,
                )
            },
            modeLevel = Sudoku.MODE_NORMAL
        )
    }

    private fun getInitialSudoku(size: Int): List<Int> =
        when (size) {
            4 -> initialSudoku4x4
            9 -> initialSudoku9x9
            else -> initialSudoku9x9
        }

    private fun shuffleNumbers(sudoku: Sudoku) {
        for (i in 0 until sudoku.size) {
            val ranNum: Int = random.nextInt(sudoku.size)
            swapNumbers(sudoku, i, ranNum)
        }
    }

    private fun swapNumbers(sudoku: Sudoku, n1: Int, n2: Int) {
        for (i in 0 until sudoku.size) {
            if (sudoku[i].value == n1) {
                sudoku[i].value = n2
                sudoku[i].solution = n2
            } else if (sudoku[i].value == n2) {
                sudoku[i].value = n1
                sudoku[i].solution = n1
            }
        }
    }

    private fun shuffleRows(sudoku: Sudoku) {
        var blockNumber: Int
        for (i in 0 until sudoku.size) {
            blockNumber = i / sudoku.blockSize
            swapRows(sudoku, i, blockNumber * sudoku.blockSize + random.nextInt(sudoku.blockSize))
        }
    }

    private fun swapRows(sudoku: Sudoku, r1: Int, r2: Int) {
        val row: List<Field> = sudoku.getRow(r1)
        sudoku.setRow(r1, sudoku.getRow(r2))
        sudoku.setRow(r2, row)
    }

    private fun shuffleColumns(sudoku: Sudoku) {
        var blockNumber: Int
        for (i in 0 until sudoku.size) {
            blockNumber = i / sudoku.blockSize
            swapColumns(sudoku, i, blockNumber * sudoku.blockSize + random.nextInt(sudoku.blockSize))
        }
    }

    private fun swapColumns(sudoku: Sudoku, c1: Int, c2: Int) {
        val column: List<Field> = sudoku.getColumn(c1)
        sudoku.setColumn(c1, sudoku.getColumn(c2))
        sudoku.setColumn(c2, column)
    }

    private suspend fun shuffleBlockRows(sudoku: Sudoku) {
        for (i in 0 until sudoku.blockSize) swapBlockRows(sudoku, i, random.nextInt(sudoku.blockSize))
    }

    private suspend fun swapBlockRows(sudoku: Sudoku, r1: Int, r2: Int) {
        for (i in 0 until sudoku.blockSize) swapRows(sudoku, r1 * sudoku.blockSize + i, r2 * sudoku.blockSize + i)
    }

    private suspend fun shuffleBlockColumns(sudoku: Sudoku) {
        for (i in 0 until sudoku.blockSize) swapBlockColumns(sudoku, i, random.nextInt(sudoku.blockSize))
    }

    private suspend fun swapBlockColumns(sudoku: Sudoku, c1: Int, c2: Int) {
        for (i in 0 until sudoku.blockSize) swapColumns(sudoku, c1 * sudoku.blockSize + i, c2 * sudoku.blockSize + i)
    }

    private suspend fun removeNumbers(sudoku: Sudoku, difficulty: Difficulty) {
        //val diff = if (difficulty.value == Difficulty.max) -1
        //else (sudoku.size.toDouble().pow(2.0) * (difficulty.value * 0.35f / Difficulty.max + 0.4f)).toInt()
        Log.d("removeNumbers", "difficulty: ${difficulty.name} numbersToRemove: ${difficulty.numbersToRemove(sudoku.size)}")
        removedRandomNumber(sudoku, difficulty.numbersToRemove(sudoku.size))
    }

    private suspend fun removedRandomNumber(sudoku: Sudoku, numbersToRemove: Int, tries: Int = 200): Sudoku {
        val index = random.nextInt(sudoku.itemCount)
        val newSudoku = sudoku.copy()
        return if (newSudoku[index].value != null) {
            newSudoku[index].value = null
            newSudoku[index].given = false
            val solutions = mutableListOf<Sudoku>()
            solveFieldForSolutionGame(newSudoku, Position.first(sudoku), solutions)
            if (solutions.size == 1) { //continue while solutions.size() is one
                if (numbersToRemove - 1 == 0) newSudoku else removedRandomNumber(newSudoku, numbersToRemove - 1, tries)
            } else { //try to remove more numbers
                if (tries - 1 > 0) removedRandomNumber(sudoku, numbersToRemove, tries - 1) else sudoku
            }
        } else { //skip already removed numbers
            removedRandomNumber(sudoku, numbersToRemove, tries)
        }
    }

    private suspend fun solveFieldForSolutionGame(sudoku: Sudoku, position: Position, solutions: MutableList<Sudoku>) {
        if (position == Position.last(position.size)) { //reached the end
            if (sudoku[position].value == null) {
                for (n in 1..sudoku.size) {
                    if (validateNumber(sudoku, position, n)) {
                        sudoku[position].value = n
                        sudoku[position].solution = n
                        solutions.add(sudoku) //no errors: add solution
                        return
                    }
                }
            } else {
                solutions.add(sudoku) //no errors: add solution
                return
            }
        }
        else if (sudoku[position].value == null) {
            for (n in 1..sudoku.size) {
                if (validateNumber(sudoku, position, n)) {
                    sudoku[position].value = n
                    sudoku[position].solution = n
                    solveFieldForSolutionGame(sudoku.copy(), position.next(), solutions)
                }
            }
        } else solveFieldForSolutionGame(sudoku, position.next(), solutions)
    }
    */
}
