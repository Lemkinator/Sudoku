/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.sudoku.domain.model

import android.content.res.Resources
import de.lemke.sudoku.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId.systemDefault
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofLocalizedDate
import java.time.format.DateTimeFormatter.ofLocalizedDateTime
import java.time.format.FormatStyle
import java.time.format.FormatStyle.FULL
import java.time.format.FormatStyle.MEDIUM
import java.util.Locale
import java.util.Timer
import java.util.UUID
import kotlin.math.sqrt

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
private const val PERCENT_SCALE = 100

// Sizes this app supports; a 16x16 board's digits 10-16 render as letters A-G (see toSudokuString/toSudokuChar).
private const val MAX_STANDARD_DIGIT = 9
private const val MAX_LARGE_DIGIT = 16
private const val LARGE_DIGIT_OFFSET = 10

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
    private val hintLimit: Int
        get() = hintLimitBySize[size] ?: DEFAULT_HINT_LIMIT

    val availableHints: Int
        get() = hintLimit - hintsUsed

    val isHintAvailable: Boolean get() = if (isNormalSudoku) hintsUsed < hintLimit else false

    val isSudokuLevel: Boolean get() = modeLevel > 0

    val isDailySudoku: Boolean get() = modeLevel == MODE_DAILY

    val isNormalSudoku: Boolean get() = modeLevel == MODE_NORMAL

    val completed: Boolean get() = fields.all { !it.error && it.value != null }

    val resumed: Boolean get() = timer != null

    val itemCount: Int get() = this.size * this.size

    val blockSize: Int get() = sqrt(this.size.toDouble()).toInt()

    val sizeString: String get() = "$size×$size"

    val progress: Int
        get() {
            val total = fields.count { !it.given }
            return if (total == 0) PERCENT_SCALE else fields.count { !it.given && it.correct } * PERCENT_SCALE / total
        }

    val timeString: String
        get() =
            if (seconds >= SECONDS_PER_HOUR) {
                String.format(
                    Locale.getDefault(),
                    "%02d:%02d:%02d",
                    seconds / SECONDS_PER_HOUR,
                    seconds / SECONDS_PER_MINUTE % SECONDS_PER_MINUTE,
                    seconds % SECONDS_PER_MINUTE,
                )
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
            }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Sudoku
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    operator fun get(position: Position): Field = fields[position.index]

    operator fun set(
        position: Position,
        field: Field,
    ) {
        fields[position.index] = field.copy(position = position)
    }

    operator fun get(index: Int): Field = fields[index]

    operator fun set(
        index: Int,
        field: Field,
    ) {
        fields[index] = field.copy(position = Position.create(index, size))
    }

    operator fun get(
        row: Int,
        column: Int,
    ) = fields[Position.create(size = size, row = row, column = column).index]

    operator fun set(
        row: Int,
        column: Int,
        field: Field,
    ) {
        fields[Position.create(size = size, row = row, column = column).index] =
            field.copy(position = Position.create(size = size, row = row, column = column))
    }

    companion object {
        const val MODE_NORMAL = 0
        const val MODE_DAILY = -1
        const val MODE_LEVEL_ERROR_LIMIT = 3
        const val MODE_DAILY_ERROR_LIMIT = 3

        // Board sizes this app supports.
        const val SIZE_4X4 = 4
        const val SIZE_9X9 = 9
        const val SIZE_16X16 = 16

        private const val DEFAULT_HINT_LIMIT = 3
        private val hintLimitBySize: Map<Int, Int> = mapOf(SIZE_4X4 to 1, SIZE_9X9 to 3, SIZE_16X16 to 8)

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
        ): Sudoku =
            Sudoku(
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
}

fun Sudoku.contentEquals(other: Sudoku): Boolean =
    id == other.id &&
        regionalHighlightingUsed == other.regionalHighlightingUsed &&
        numberHighlightingUsed == other.numberHighlightingUsed &&
        eraserUsed == other.eraserUsed &&
        isChecklist == other.isChecklist &&
        isReverseChecklist == other.isReverseChecklist &&
        checklistNumber == other.checklistNumber &&
        hintsUsed == other.hintsUsed &&
        notesMade == other.notesMade &&
        errorsMade == other.errorsMade &&
        created == other.created &&
        updated == other.updated &&
        seconds == other.seconds &&
        fields == other.fields

fun Sudoku.copy(
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
): Sudoku =
    Sudoku(
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

fun Sudoku.errorLimitReached(errorLimit: Int): Boolean = if (errorLimit == 0) false else errorsMade >= errorLimit

fun Sudoku.getInitialSudoku() =
    Sudoku(
        id = SudokuId.generate(),
        size = size,
        difficulty = difficulty,
        modeLevel = Sudoku.MODE_NORMAL,
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

fun Sudoku.reset() {
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

fun Sudoku.getLocalStatisticsString(resources: Resources): String =
    resources.getString(
        R.string.sudokuStatisticsString,
        when (modeLevel) {
            Sudoku.MODE_NORMAL -> resources.getString(R.string.normal_sudoku)
            Sudoku.MODE_DAILY -> resources.getString(R.string.daily_sudoku)
            else -> resources.getString(R.string.level) + " $modeLevel"
        },
        size,
        difficulty.getLocalString(resources),
        timeString,
        errorsMade,
        hintsUsed,
        notesMade,
        resources.getString(if (regionalHighlightingUsed) R.string.commonutils_yes else R.string.commonutils_no),
        resources.getString(if (numberHighlightingUsed) R.string.commonutils_yes else R.string.commonutils_no),
        created.formatFull,
        updated.formatFull,
    )

fun Sudoku.getLocalStatisticsStringShare(resources: Resources): String =
    if (completed) {
        resources.getString(R.string.sudoku_completed)
    } else {
        resources.getString(R.string.sudoku_solving, progress)
    } + getLocalStatisticsString(resources)

interface GameListener {
    fun onFieldClicked(position: Position)

    fun onFieldChanged(position: Position)

    fun onCompleted(position: Position)

    fun onError()

    fun onTimeChanged()
}

fun Int?.toSudokuString(): CharSequence? =
    when (this) {
        null -> null
        in 1..MAX_STANDARD_DIGIT -> this.toString()
        in LARGE_DIGIT_OFFSET..MAX_LARGE_DIGIT -> ('A' + (this - LARGE_DIGIT_OFFSET)).toString()
        else -> null
    }

fun Int?.toSudokuChar(): Char? =
    when (this) {
        null -> null
        in 1..MAX_STANDARD_DIGIT -> '0' + this
        in LARGE_DIGIT_OFFSET..MAX_LARGE_DIGIT -> 'A' + (this - LARGE_DIGIT_OFFSET)
        else -> null
    }

val LocalDateTime.monthAndYear: String get() = format(DateTimeFormatter.ofPattern("MMMM yyyy"))
val LocalDateTime.dateFormatShort: String get() = format(ofLocalizedDate(FormatStyle.SHORT))
val LocalDate.formatFull: String get() = format(ofLocalizedDate(FULL))
val LocalDateTime.formatFull: String get() = format(ofLocalizedDateTime(FULL, MEDIUM).withZone(systemDefault()))
