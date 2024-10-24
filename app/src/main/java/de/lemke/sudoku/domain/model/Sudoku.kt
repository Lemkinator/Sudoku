package de.lemke.sudoku.domain.model

import android.annotation.SuppressLint
import android.content.res.Resources
import de.lemke.sudoku.R
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlin.concurrent.timer
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
    val difficulty: Difficulty,
    val modeLevel: Int,
    var regionalHighlightingUsed: Boolean,
    var numberHighlightingUsed: Boolean,
    var eraserUsed: Boolean,
    var isChecklist: Boolean,
    var isReverseChecklist: Boolean,
    var checklistNumber: Int,
    var hintsUsed: Int,
    var notesMade: Int,
    var errorsMade: Int,
    val created: LocalDateTime,
    var updated: LocalDateTime,
    var seconds: Int,
    var timer: Timer?,
    var gameListener: GameListener?,
    val fields: MutableList<Field>,
) {
    companion object {
        const val MODE_NORMAL = 0
        const val MODE_DAILY = -1
        const val MODE_LEVEL_ERROR_LIMIT = 3
        const val MODE_DAILY_ERROR_LIMIT = 3

        fun create(
            sudokuId: SudokuId = SudokuId.generate(),
            size: Int,
            difficulty: Difficulty,
            modeLevel: Int,
            regionalHighlightingUsed: Boolean = false,
            numberHighlightingUsed: Boolean = false,
            eraserUsed: Boolean = false,
            isChecklist: Boolean = false,
            isReverseChecklist: Boolean = false,
            checklistNumber: Int = 0,
            hintsUsed: Int = 0,
            notesMade: Int = 0,
            errorsMade: Int = 0,
            created: LocalDateTime = LocalDateTime.now(),
            updated: LocalDateTime = LocalDateTime.now(),
            seconds: Int = 0,
            timer: Timer? = null,
            gameListener: GameListener? = null,
            fields: MutableList<Field>,
        ): Sudoku = Sudoku(
            id = sudokuId,
            size = size,
            difficulty = difficulty,
            modeLevel = modeLevel,
            regionalHighlightingUsed = regionalHighlightingUsed,
            numberHighlightingUsed = numberHighlightingUsed,
            eraserUsed = eraserUsed,
            isChecklist = isChecklist,
            isReverseChecklist = isReverseChecklist,
            checklistNumber = checklistNumber,
            hintsUsed = hintsUsed,
            notesMade = notesMade,
            errorsMade = errorsMade,
            created = created,
            updated = updated,
            seconds = seconds,
            timer = timer,
            gameListener = gameListener,
            fields = fields,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Sudoku
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    fun copy(
        sudokuId: SudokuId = this.id,
        size: Int = this.size,
        difficulty: Difficulty = this.difficulty,
        modeLevel: Int = this.modeLevel,
        regionalHighlightingUsed: Boolean = this.regionalHighlightingUsed,
        numberHighlightingUsed: Boolean = this.numberHighlightingUsed,
        eraserUsed: Boolean = this.eraserUsed,
        isChecklist: Boolean = this.isChecklist,
        isReverseChecklist: Boolean = this.isReverseChecklist,
        checklistNumber: Int = this.checklistNumber,
        hintsUsed: Int = this.hintsUsed,
        notesMade: Int = this.notesMade,
        errorsMade: Int = this.errorsMade,
        created: LocalDateTime = this.created,
        updated: LocalDateTime = this.updated,
        seconds: Int = this.seconds,
        timer: Timer? = this.timer,
        gameListener: GameListener? = this.gameListener,
        fields: MutableList<Field> = MutableList(itemCount) { this.fields[it].copy() },
    ): Sudoku = Sudoku(
        id = sudokuId,
        size = size,
        difficulty = difficulty,
        modeLevel = modeLevel,
        regionalHighlightingUsed = regionalHighlightingUsed,
        numberHighlightingUsed = numberHighlightingUsed,
        eraserUsed = eraserUsed,
        isChecklist = isChecklist,
        isReverseChecklist = isReverseChecklist,
        checklistNumber = checklistNumber,
        hintsUsed = hintsUsed,
        notesMade = notesMade,
        errorsMade = errorsMade,
        created = created,
        updated = updated,
        seconds = seconds,
        timer = timer,
        gameListener = gameListener,
        fields = fields,
    )

    private val hintLimit: Int
        get() = when (size) {
            4 -> 1
            9 -> 3
            16 -> 8
            else -> 3
        }

    val availableHints: Int
        get() = hintLimit - hintsUsed

    val isHintAvailable: Boolean get() = if (isNormalSudoku) hintsUsed < hintLimit else false

    val isSudokuLevel: Boolean get() = modeLevel > 0

    val isDailySudoku: Boolean get() = modeLevel == MODE_DAILY

    val isNormalSudoku: Boolean get() = modeLevel == MODE_NORMAL

    val completed: Boolean get() = fields.all { !it.error && it.value != null }

    val resumed: Boolean get() = timer != null

    val itemCount: Int get() = (this.size * this.size)

    val blockSize: Int get() = sqrt(this.size.toDouble()).toInt()

    val sizeString: String get() = "$size×$size"

    val progress: Int
        get() {
            val total = fields.count { !it.given }
            return fields.count { !it.given && it.correct } * 100 / total
        }

    val timeString: String
        get() = if (seconds >= 3600) String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

    fun errorLimitReached(errorLimit: Int): Boolean = if (errorLimit == 0) false else errorsMade >= errorLimit

    fun getInitialSudoku() = Sudoku(
        id = SudokuId.generate(),
        size = size,
        difficulty = difficulty,
        modeLevel = MODE_NORMAL,
        regionalHighlightingUsed = false,
        numberHighlightingUsed = false,
        eraserUsed = false,
        isChecklist = false,
        isReverseChecklist = false,
        checklistNumber = 0,
        hintsUsed = 0,
        notesMade = 0,
        errorsMade = 0,
        created = created,
        updated = created,
        seconds = 0,
        timer = null,
        gameListener = null,
        fields = MutableList(itemCount) { fields[it].getInitialField() },
    )

    fun reset() {
        fields.forEach { it.reset() }
        regionalHighlightingUsed = false
        numberHighlightingUsed = false
        eraserUsed = false
        isChecklist = false
        isReverseChecklist = false
        checklistNumber = 0
        hintsUsed = 0
        notesMade = 0
        errorsMade = 0
        seconds = 0
        timer?.cancel()
        timer = null
        gameListener = null
    }

    fun startTimer(delay: Long = 1000L) {
        if (completed) return
        timer?.cancel()
        timer = timer(initialDelay = delay, period = 1000L ) {
            seconds++
            updated = LocalDateTime.now()
            gameListener?.onTimeChanged()
        }
    }

    fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    fun move(index: Int, value: Int?, isNote: Boolean = false) = move(Position.create(index, size), value, isNote)

    fun move(position: Position, value: Int?, isNote: Boolean = false): Boolean {
        val field = get(position)
        return when {
            timer == null || field.given || field.hint -> false
            isNote -> {
                if (field.value != null) return false
                if (value != null) {
                    if (field.toggleNote(value)) notesMade++
                } else field.notes.clear()
                gameListener?.onFieldChanged(position)
                true
            }
            value == null -> {
                if (field.value == null && field.notes.isEmpty()) return false
                eraserUsed = true
                if (field.value == null) field.notes.clear()
                field.value = null
                gameListener?.onFieldChanged(position)
                true
            }
            field.correct || field.value == value -> false
            else -> {
                field.value = value
                gameListener?.onFieldChanged(position)
                checkChecklist(value)
                if (field.error) {
                    errorsMade++
                    gameListener?.onError()
                } else removeNumberNotesFromNeighbors(position, value)
                if (completed) {
                    stopTimer()
                    gameListener?.onCompleted(position)
                }
                true
            }
        }
    }

    private fun checkChecklist(value: Int) {
        when {
            isChecklist -> when {
                value == checklistNumber -> return
                value > checklistNumber -> checklistNumber = value
                else -> isChecklist = false
            }
            isReverseChecklist -> when {
                value == checklistNumber -> return
                value < checklistNumber -> checklistNumber = value
                else -> isReverseChecklist = false
            }
            checklistNumber == 0 -> when (value) {
                1 -> isChecklist = true
                size -> isReverseChecklist = true
            }
        }
        checklistNumber = value
    }

    private fun removeNumberNotesFromNeighbors(position: Position, value: Int?) {
        get(position).notes.clear()
        gameListener?.onFieldChanged(position)
        getNeighbors(position).forEach {
            if (it.notes.remove(value)) gameListener?.onFieldChanged(it.position)
        }
    }

    fun setHint(index: Int) = setHint(Position.create(index, size))

    fun setHint(position: Position) {
        hintsUsed++
        get(position).setHint()
        gameListener?.onFieldChanged(position)
        removeNumberNotesFromNeighbors(position, get(position).value)
        if (completed) {
            stopTimer()
            gameListener?.onCompleted(position)
        }
    }

    operator fun get(position: Position): Field = fields[position.index]
    operator fun set(position: Position, field: Field) {
        fields[position.index] = field.copy(position = position)
    }

    operator fun get(index: Int): Field = fields[index]
    operator fun set(index: Int, field: Field) {
        fields[index] = field.copy(position = Position.create(index, size))
    }

    operator fun get(row: Int, column: Int) = fields[Position.create(size = size, row = row, column = column).index]
    operator fun set(row: Int, column: Int, field: Field) {
        fields[Position.create(size = size, row = row, column = column).index] =
            field.copy(position = Position.create(size = size, row = row, column = column))
    }

    private fun getRow(row: Int): List<Field> = fields.filter { it.position.row == row }
    private fun getColumn(column: Int): List<Field> = fields.filter { it.position.column == column }
    private fun getBlock(block: Int): List<Field> = fields.filter { it.position.block == block }
    private fun getNeighbors(position: Position): List<Field> = getRow(position.row) + getColumn(position.column) + getBlock(position.block)
    fun getNeighbors(index: Int): List<Field> = getNeighbors(Position.create(index, size))

    fun isRowCompleted(row: Int): Boolean = getRow(row).all { it.correct }
    fun isColumnCompleted(column: Int): Boolean = getColumn(column).all { it.correct }
    fun isBlockCompleted(block: Int): Boolean = getBlock(block).all { it.correct }

    @Suppress("unused")
    private fun getPossibleValues(position: Position): List<Int> {
        val values = (1..size).toMutableList()
        getRow(position.row).forEach { values.remove(it.value) }
        getColumn(position.column).forEach { values.remove(it.value) }
        getBlock(position.block).forEach { values.remove(it.value) }
        return values
    }

    fun getCompletedNumbers(): List<Pair<Int, Boolean>> {
        val numbers = MutableList(size) { 0 }
        fields.forEach { field ->
            if (field.correct) numbers[field.value!! - 1]++
        }
        return numbers.mapIndexed { index, i -> Pair(index + 1, i >= size) }
    }

    fun getLocalStatisticsString(resources: Resources): String = resources.getString(
        R.string.sudokuStatisticsString,
        when (modeLevel) {
            MODE_NORMAL -> resources.getString(R.string.normal_sudoku)
            MODE_DAILY -> resources.getString(R.string.daily_sudoku)
            else -> resources.getString(R.string.level) + " $modeLevel"
        },
        size,
        difficulty.getLocalString(resources),
        timeString,
        errorsMade,
        hintsUsed,
        notesMade,
        resources.getString(if (regionalHighlightingUsed) R.string.yes else R.string.no),
        resources.getString(if (numberHighlightingUsed) R.string.yes else R.string.no),
        created.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())),
        updated.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())),
    )

    @SuppressLint("StringFormatInvalid")
    fun getLocalStatisticsStringShare(resources: Resources): String =
        if (completed) {
            resources.getString(R.string.sudoku_completed)
        } else {
            resources.getString(R.string.sudoku_solving, progress)
        } + getLocalStatisticsString(resources)

}

interface GameListener {
    fun onFieldClicked(position: Position)
    fun onFieldChanged(position: Position)
    fun onCompleted(position: Position)
    fun onError()
    fun onTimeChanged()
}

