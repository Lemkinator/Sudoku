package de.lemke.sudoku.domain.model

import kotlin.math.sqrt

class Position private constructor(
    val size: Int,
    val index: Int,
    val row: Int,
    val column: Int,
    val block: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Position

        if (size != other.size) return false
        if (index != other.index) return false
        return true
    }

    override fun hashCode(): Int = 31 * size + index

    fun next(): Position? = if (index < size * size - 1) create(size, index + 1) else null

    fun previous(): Position? = if (index > 0) create(size, index - 1) else null

    companion object {
        fun create(size: Int, index: Int): Position {
            val blockSize = sqrt(size.toDouble()).toInt()
            return Position(
                size = size,
                index = index,
                row = index / size,
                column = index % size,
                block = index / (size * size / blockSize) * blockSize + index % size / (size / blockSize)
            )
        }

        fun create(size: Int, row: Int, column: Int): Position {
            val sqrtSize = sqrt(size.toDouble()).toInt()
            return Position(
                size = size,
                index = row * size + column,
                row = row,
                column = column,
                block = row / (size / sqrtSize) * sqrtSize + column / (size / sqrtSize)
            )
        }

        fun first(sudoku: Sudoku): Position = first(sudoku.size)
        fun last(sudoku: Sudoku): Position = last(sudoku.size)
        fun first(size: Int): Position = create(size, 0)
        fun last(size: Int): Position = create(size, size * size - 1)
    }
}