package de.lemke.sudoku.domain.model

import android.util.Log
import de.lemke.sudoku.ui.SudokuViewAdapter
import java.time.LocalDateTime
import java.util.*
import kotlin.math.sqrt

@JvmInline
value class SudokuId(val value: String) {
    companion object {
        fun generate(): SudokuId = SudokuId(UUID.randomUUID().toString())
    }
}

class Sudoku(
    val id: SudokuId,
    val size: Int,
    val history: MutableList<HistoryItem>,
    val difficulty: Difficulty,
    var hintsUsed: Int,
    var errorsMade: Int,
    var seconds: Int,
    var resumed: Boolean,
    var timer: Timer?,
    var gameListener: GameListener?,
    val created: LocalDateTime,
    val updated: LocalDateTime,
    val fields: MutableList<Field>,
) {
    companion object {
        fun create(
            sudokuId: SudokuId = SudokuId.generate(),
            size: Int,
            history: MutableList<HistoryItem> = mutableListOf(),
            difficulty: Difficulty,
            hintsUsed: Int = 0,
            errorsMade: Int = 0,
            seconds: Int = 0,
            resumed: Boolean = false,
            timer: Timer? = null,
            gameListener: GameListener? = null,
            created: LocalDateTime = LocalDateTime.now(),
            updated: LocalDateTime = LocalDateTime.now(),
            fields: MutableList<Field>,
        ): Sudoku = Sudoku(
            id = sudokuId,
            size = size,
            history = history,
            difficulty = difficulty,
            hintsUsed = hintsUsed,
            errorsMade = errorsMade,
            seconds = seconds,
            resumed = resumed,
            timer = timer,
            gameListener = gameListener,
            created = created,
            updated = updated,
            fields = fields,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Sudoku

        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int = id.hashCode()

    val errors: Int
        get() = fields.count { it.error }

    val completed: Boolean
        get() = fields.all { !it.error && it.value != null }

    val itemCount: Int
        get() = (this.size * this.size)
    val blockSize: Int
        get() = sqrt(this.size.toDouble()).toInt()

    operator fun get(position: Position): Field = fields[position.index]
    operator fun set(position: Position, field: Field) {
        fields[position.index] = field.clone(position = position)
    }

    operator fun get(index: Int): Field = fields[index]
    operator fun set(index: Int, field: Field) {
        fields[index] = field.clone(position = Position.create(index, size))
    }

    operator fun get(row: Int, column: Int) = fields[Position.create(size = size, row = row, column = column).index]
    operator fun set(row: Int, column: Int, field: Field) {
        fields[Position.create(size = size, row = row, column = column).index] =
            field.clone(position = Position.create(size = size, row = row, column = column))
    }

    fun getRow(row: Int): List<Field> =
        fields.filter { it.position.row == row }

    fun getColumn(column: Int): List<Field> =
        fields.filter { it.position.column == column }

    private fun getBlock(block: Int): List<Field> =
        fields.filter { it.position.block == block }

    fun setRow(row: Int, fields: List<Field>) {
        fields.forEachIndexed { index, field -> set(row = row, column = index, field = field) }
    }

    fun setColumn(column: Int, fields: List<Field>) {
        fields.forEachIndexed { index, field -> set(row = index, column = column, field = field) }
    }

    fun rowContainsValue(row: Int, value: Int): Boolean = getRow(row).any { it.value == value }
    fun columnContainsValue(column: Int, value: Int): Boolean = getColumn(column).any { it.value == value }
    fun blockContainsValue(block: Int, value: Int): Boolean = getBlock(block).any { it.value == value }

    fun getPossibleValues(position: Position): List<Int> {
        val row = getRow(position.row)
        val column = getColumn(position.column)
        val block = getBlock(position.block)
        val values = (1..size).toMutableList()
        row.forEach { values.remove(it.value) }
        column.forEach { values.remove(it.value) }
        block.forEach { values.remove(it.value) }
        return values
    }

    fun copy(
        id: SudokuId = this.id,
        size: Int = this.size,
        history: MutableList<HistoryItem> = this.history,
        difficulty: Difficulty = this.difficulty,
        hints: Int = this.hintsUsed,
        errors: Int = this.errorsMade,
        seconds: Int = this.seconds,
        resumed: Boolean = this.resumed,
        created: LocalDateTime = this.created,
        updated: LocalDateTime = this.updated,
        fields: MutableList<Field> = this.fields,
    ): Sudoku = Sudoku(
        id = id,
        size = size,
        history = history.toMutableList(),
        difficulty = difficulty,
        hintsUsed = hints,
        errorsMade = errors,
        seconds = seconds,
        resumed = resumed,
        timer = null,
        gameListener = null,
        created = created,
        updated = updated,
        fields = fields.toMutableList()
    )

    fun move(position: Position, value: Int?) {
        val field = get(position)
        if (field.value == value) return
        history.add(HistoryItem(position, if (value == null) field.value else null))
        field.value = value
        gameListener?.onHistoryChange(history.size)
    }

    fun revertLastChange(adapter: SudokuViewAdapter) {
        if (history.size != 0) {
            val item = history.removeAt(history.lastIndex)
            get(item.position.index).value = item.deletedNumber
            adapter.updateFieldView(item.position.index)
            gameListener?.onHistoryChange(history.size)
        }
    }

    fun startTimer(delay: Long) {
        if (completed) return
        resumed = true
        timer?.cancel()
        timer = Timer()
        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                seconds++
                gameListener?.onTimeChanged(getTimeString())
            }
        }, delay, 1000)
    }

    fun stopTimer() {
        resumed = false
        timer?.cancel()
        timer = null
    }

    fun getTimeString(): String =
        if (seconds >= 3600) String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
}

interface GameListener {
    fun onHistoryChange(length: Int)
    fun onCompleted()
    fun onTimeChanged(time: String?)
}

