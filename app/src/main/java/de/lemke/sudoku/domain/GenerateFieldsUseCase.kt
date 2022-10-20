package de.lemke.sudoku.domain

import android.util.Log
import de.lemke.sudoku.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import kotlin.math.sqrt

class GenerateFieldsUseCase @Inject constructor(

) {
    //TODO cleanup
    private var random: Random = Random()
    private var size: Int = 9
    private var numbersToRemove: Int = -1
    private var tries: Int = 60


    suspend operator fun invoke(size: Int = 9, difficulty: Difficulty, sudokuId: SudokuId): MutableList<Field> = withContext(Dispatchers.Default) {
        this@GenerateFieldsUseCase.size = size
        this@GenerateFieldsUseCase.numbersToRemove = difficulty.numbersToRemove(size)
        this@GenerateFieldsUseCase.tries = 40

        var solutions: MutableList<Array<Array<Int?>>> = mutableListOf()
        while (solutions.isEmpty()) {
            val fields = generateFields()
            solutions = findSolutions(fields)
        }
        val solution = solutions.random()
        val output = removeRandomNumbers(solution)

        return@withContext MutableList(size * size) { index ->
            val position = Position.create(index, size)
            Field(
                sudokuId = sudokuId,
                position = position,
                value = output[position.row][position.column],
                solution = solution[position.row][position.column],
                given = output[position.row][position.column] != null,
            )
        }
    }

    private fun generateFields(): Array<Array<Int?>> {
        val fields = Array(size) { arrayOfNulls<Int>(size) }
        for (i in 0 until (size * size / 3)) addRandomNumberTo(fields)
        return fields
    }

    private fun findSolutions(fields: Array<Array<Int?>>): MutableList<Array<Array<Int?>>> {
        val solutions = mutableListOf<Array<Array<Int?>>>()
        solveFieldForSolutionGame(cloneArray(fields), 0, 0, solutions)
        return solutions
    }


    private fun addRandomNumberTo(input: Array<Array<Int?>>) {
        val row = random.nextInt(size)
        val column = random.nextInt(size)
        val number = random.nextInt(size) + 1
        if (input[row][column] == null && isValid(input, number, row, column)) {
            input[row][column] = number
        } else {
            addRandomNumberTo(input)
        }
    }

    private fun isValid(input: Array<Array<Int?>>, i: Int, row: Int, column: Int): Boolean {
        val size = sqrt(size.toDouble()).toInt()
        for (number in input[row]) if (i == number) return false
        for (rows in input) if (i == rows[column]) return false
        val sRow = row / size * size
        val sColumn = column / size * size
        for (r in sRow until sRow + size) for (c in sColumn until sColumn + size) if (i == input[r][c]) return false
        return true
    }

    private fun cloneArray(input: Array<Array<Int?>>): Array<Array<Int?>> {
        val output = Array(size) { arrayOfNulls<Int>(size) }
        for (i in 0 until size) {
            for (j in 0 until size) {
                output[i][j] = input[i][j]
            }
        }
        return output
    }

    private fun solveFieldForSolutionGame(input: Array<Array<Int?>>, row: Int, column: Int, solutions: MutableList<Array<Array<Int?>>>) {
        var newRow = row
        var newColumn = column
        if (newColumn == size) { //go to next row
            newColumn = 0
            newRow++
        }
        if (newRow == size) { //save the solution once reached the end
            solutions.add(cloneArray(input))
            return
        }
        if (input[newRow][newColumn] == null) {
            for (n in 1..size) {
                if (isValid(input, n, newRow, newColumn)) {
                    input[newRow][newColumn] = n
                    solveFieldForSolutionGame(input, newRow, newColumn + 1, solutions)
                }
            }
            input[newRow][newColumn] = null
        } else solveFieldForSolutionGame(input, newRow, newColumn + 1, solutions)
    }

    private fun removeRandomNumbers(input: Array<Array<Int?>>): Array<Array<Int?>> {
        val newField = cloneArray(input)
        val row = random.nextInt(size)
        val column = random.nextInt(size)
        return if (newField[row][column] != null) {
            newField[row][column] = null
            val solutions = ArrayList<Array<Array<Int?>>>()
            solveFieldForSolutionGame(newField, 0, 0, solutions)
            if (solutions.size == 1) { //continue while solutions.size() is one
                numbersToRemove--
                if (numbersToRemove == 0) {
                    Log.d("removeRandomNumbers", "numbersToRemove: 0, tries: $tries, got one solution, return newField")
                    newField
                } else {
                    Log.d(
                        "removeRandomNumbers",
                        "numbersToRemove: $numbersToRemove, tries: $tries, got one solution, continue to removeRandomNumbers"
                    )
                    removeRandomNumbers(newField)
                }
            } else { //try to remove more numbers
                tries--
                if (tries > 0) {
                    Log.d("removeRandomNumbers", "numbersToRemove: $numbersToRemove, tries: $tries, retry, continue to removeRandomNumbers")
                    removeRandomNumbers(input)
                } else {
                    Log.d("removeRandomNumbers", "numbersToRemove: $numbersToRemove, tries: $tries, no more tries, return input")
                    input
                }
            }
        } else { //skip already removed numbers
            removeRandomNumbers(input)
        }
    }


    /*
    suspend operator fun invoke(size: Int = 9, difficulty: Difficulty): Sudoku = withContext(Dispatchers.Default) {
        this@GenerateSudokuUseCase.difficulty = if (difficulty.value == Difficulty.max) -1
        else (size.toDouble().pow(2.0) * (difficulty.value * 0.35f / Difficulty.max + 0.4f)).toInt()

        val sudokuId = SudokuId.generate()
        val sudoku = Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = difficulty,
            fields = MutableList(size * size) { index ->
                Field(
                    sudokuId = sudokuId,
                    position = Position.create(size, index),
                    value = null,
                    solution = null,
                    given = true,
                )
            })

        Log.d("GenerateSudokuUseCase", "difficulty: $difficulty, tries: $tries")
        //add random numbers
        for (field in 0..size * size / 3) {
            addRandomNumberTo(sudoku)
        }

        Log.d("GenerateSudokuUseCase", "generate solution and retry if there arent")
        //generate solutions and retry if there aren't
        val solutions = mutableListOf<Sudoku>()
        solveFieldForSolutionGame(sudoku.copy(), Position.first(sudoku), solutions)
        if (solutions.isEmpty()) {
            Log.d("GenerateSudokuUseCase", "no solution found, retry")
            return@withContext this@GenerateSudokuUseCase(sudoku.size, difficulty)
        }

        Log.d("GenerateSudokuUseCase", "remove random numbers while there is a solution")
        //remove random numbers while there is one solution
        val solution = solutions[random.nextInt(solutions.size)]
        return@withContext removeRandomNumber(solution)
    }


    private suspend fun addRandomNumberTo(sudoku: Sudoku) {
        val index = random.nextInt(sudoku.itemCount)
        val number = random.nextInt(sudoku.size) + 1
        if (sudoku[index].value == null && validateNumber(sudoku, sudoku[index].position, number)) {
            sudoku[index].value = number
            sudoku[index].solution = number
            sudoku[index].given = true
        } else {
            addRandomNumberTo(sudoku)
        }
    }

    private suspend fun solveFieldForSolutionGame(sudoku: Sudoku, position: Position?, solutions: MutableList<Sudoku>) {
        if (position == null) { //save the solution once reached the end
            solutions.add(sudoku.copy())
            return
        }
        if (sudoku[position].value == null) {
            for (n in 1..sudoku.size) {
                if (validateNumber(sudoku, position, n)) {
                    sudoku[position].value = n
                    sudoku[position].solution = n
                    sudoku[position].given = true
                    solveFieldForSolutionGame(sudoku, position.next(), solutions)
                }
            }
            sudoku[position].value = null
            sudoku[position].solution = null
            sudoku[position].given = false
        } else solveFieldForSolutionGame(sudoku, position.next(), solutions)
    }

    private suspend fun removeRandomNumber(sudoku: Sudoku): Sudoku {
        Log.d("removeRandomNumber", "removeRandomNumber")
        val newSudoku = sudoku.copy()
        val index = random.nextInt(sudoku.itemCount)
        return if (newSudoku[index].value != null) {
            newSudoku[index].value = null
            newSudoku[index].solution = null
            newSudoku[index].given = false
            val solutions = mutableListOf<Sudoku>()
            solveFieldForSolutionGame(newSudoku, Position.first(sudoku), solutions)
            if (solutions.size == 1) { //continue while solutions.size() is one
                difficulty--
                if (difficulty == 0) {
                    Log.d("removeRandomNumber", "solutions.size == 1, difficulty: $difficulty, return sudoku")
                    newSudoku
                } else {
                    Log.d("removeRandomNumber", "solutions.size == 1, difficulty: $difficulty, continue")
                    removeRandomNumber(newSudoku)
                }
            } else { //try to remove more numbers
                tries--
                if (tries > 0) {
                    Log.d("removeRandomNumber", "solutions.size != 1, tries: $tries, continue")
                    removeRandomNumber(sudoku)
                } else {
                    Log.d("removeRandomNumber", "solutions.size != 1, tries: $tries, return sudoku")
                    sudoku
                }
            }
        } else { //skip already removed numbers
            Log.d("removeRandomNumber", "skip already removed numbers")
            removeRandomNumber(sudoku)
        }
    }
    */


    //====================================================================================================


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
                    position = Position.create(size, index),
                    value = initialSudoku[index],
                    solution = initialSudoku[index],
                    given = true,
                )
            })
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
