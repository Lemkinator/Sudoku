package de.lemke.sudoku.domain.model

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
    var timer: Timer?,
    var gameListener: GameListener?,
    val created: LocalDateTime,
    var updated: LocalDateTime,
    val fields: MutableList<Field>,
    var regionalHighlightingUsed: Boolean,
    var numberHighlightingUsed: Boolean,
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
            timer: Timer? = null,
            gameListener: GameListener? = null,
            created: LocalDateTime = LocalDateTime.now(),
            updated: LocalDateTime = LocalDateTime.now(),
            fields: MutableList<Field>,
            regionalHighlightingUsed: Boolean = false,
            numberHighlightingUsed: Boolean = false,
        ): Sudoku = Sudoku(
            id = sudokuId,
            size = size,
            history = history,
            difficulty = difficulty,
            hintsUsed = hintsUsed,
            errorsMade = errorsMade,
            seconds = seconds,
            timer = timer,
            gameListener = gameListener,
            created = created,
            updated = updated,
            fields = fields,
            regionalHighlightingUsed = regionalHighlightingUsed,
            numberHighlightingUsed = numberHighlightingUsed,
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

    val resumed: Boolean
        get() = timer != null

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

    private fun getBlock(block: Int): List<Field> = fields.filter { it.position.block == block }

    fun getNeighbors(index: Int): List<Field> = getNeighbors(Position.create(index, size))

    fun getNeighbors(position: Position): List<Field> = getRow(position.row) + getColumn(position.column) + getBlock(position.block)

    fun setRow(row: Int, fields: List<Field>) {
        fields.forEachIndexed { index, field -> set(row = row, column = index, field = field) }
    }

    fun setColumn(column: Int, fields: List<Field>) {
        fields.forEachIndexed { index, field -> set(row = index, column = column, field = field) }
    }

    fun rowContainsValue(row: Int, value: Int): Boolean = getRow(row).any { it.value == value }
    fun columnContainsValue(column: Int, value: Int): Boolean = getColumn(column).any { it.value == value }
    fun blockContainsValue(block: Int, value: Int): Boolean = getBlock(block).any { it.value == value }

    fun isRowCompleted(row: Int): Boolean = getRow(row).all { it.value != null }
    fun isColumnCompleted(column: Int): Boolean = getColumn(column).all { it.value != null }
    fun isBlockCompleted(block: Int): Boolean = getBlock(block).all { it.value != null }

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

    fun getCompletedNumbers(): List<Pair<Int, Boolean>> {
        val numbers = MutableList(size) { 0 }
        for (field in fields) {
            if (field.value != null && field.value!! == field.solution) {
                numbers[field.value!! - 1]++
            }
        }
        return numbers.mapIndexed { index, i -> Pair(index + 1, i > 8) }

    }

    fun copy(
        id: SudokuId = this.id,
        size: Int = this.size,
        history: MutableList<HistoryItem> = this.history,
        difficulty: Difficulty = this.difficulty,
        hints: Int = this.hintsUsed,
        errors: Int = this.errorsMade,
        seconds: Int = this.seconds,
        created: LocalDateTime = this.created,
        updated: LocalDateTime = this.updated,
        fields: MutableList<Field> = this.fields,
        regionalHighlightingUsed: Boolean = this.regionalHighlightingUsed,
        numberHighlightingUsed: Boolean = this.numberHighlightingUsed,
    ): Sudoku = Sudoku(
        id = id,
        size = size,
        history = history.toMutableList(),
        difficulty = difficulty,
        hintsUsed = hints,
        errorsMade = errors,
        seconds = seconds,
        timer = null,
        gameListener = null,
        created = created,
        updated = updated,
        fields = fields.toMutableList(),
        regionalHighlightingUsed = regionalHighlightingUsed,
        numberHighlightingUsed = numberHighlightingUsed,
    )

    fun reset() {
        fields.forEach { it.reset() }
        history.clear()
        hintsUsed = 0
        errorsMade = 0
        seconds = 0
        timer?.cancel()
        timer = null
        gameListener = null
        updated = LocalDateTime.now()
    }

    fun move(index: Int, value: Int?, isNote: Boolean = false) = move(Position.create(index,size), value, isNote)
    fun move(position: Position, value: Int?, isNote: Boolean = false): Boolean {
        updated = LocalDateTime.now()
        val field = get(position)
        if (field.given || field.hint) return false
        if (isNote) {
            if (value != null) field.toggleNote(value)
            else field.notes.clear()
            gameListener?.onFieldChanged(position)
        } else {
            if (field.value == value) return false
            history.add(HistoryItem(position, if (value == null) field.value else null))
            field.value = value
            field.notes.clear()
            gameListener?.onFieldChanged(position)
            gameListener?.onHistoryChange(history.size)
            if (value != null) {
                if (field.error) {
                    errorsMade++
                    gameListener?.onError()
                }
                if (completed) {
                    stopTimer()
                    gameListener?.onCompleted(position)
                }
            }
        }
        return true
    }

    fun setHint(index: Int) = setHint(Position.create(index, size))

    fun setHint(position: Position) {
        hintsUsed++
        get(position).setHint()
        get(position).notes.clear()
        gameListener?.onFieldChanged(position)
    }

    fun revertLastChange(adapter: SudokuViewAdapter) {
        if (history.size != 0) {
            val item = history.removeAt(history.lastIndex)
            get(item.position.index).value = item.deletedNumber
            adapter.updateFieldView(item.position.index)
            gameListener?.onHistoryChange(history.size)
            gameListener?.onFieldChanged(item.position)
        }
    }

    fun startTimer(delay: Long = 1500) {
        if (completed) return
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
        timer?.cancel()
        timer = null
    }

    fun getTimeString(): String =
        if (seconds >= 3600) String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

}

interface GameListener {
    fun onHistoryChange(length: Int)
    fun onFieldClicked(position: Position)
    fun onFieldChanged(position: Position)
    fun onCompleted(position: Position)
    fun onError()
    fun onTimeChanged(time: String?)
}

