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

package de.lemke.sudoku.data.codec

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private const val FIELD_FORMAT = 1
private const val FIELD_ID = 2
private const val FIELD_MODE = 3
private const val FIELD_LEVEL = 4
private const val FIELD_DIFFICULTY = 5
private const val FIELD_CREATED = 6
private const val FIELD_UPDATED = 7
private const val FIELD_SECONDS = 8
private const val FIELD_HINTS_USED = 9
private const val FIELD_NOTES_MADE = 10
private const val FIELD_ERRORS_MADE = 11
private const val FIELD_USAGE = 12
private const val FIELD_CHECKLIST_NUMBER = 13
private const val FIELD_BOARD = 14
private const val FIELD_DAILY_DATE = 15
private val COUNT_FIELDS =
    listOf(FIELD_SECONDS, FIELD_HINTS_USED, FIELD_NOTES_MADE, FIELD_ERRORS_MADE, FIELD_USAGE, FIELD_CHECKLIST_NUMBER)

/** The usage flags of a game, one bit each in field 12. */
private enum class Usage(val bit: Int, val isUsedIn: (GameInfo) -> Boolean) {
    REGIONAL_HIGHLIGHTING(bit = 1 shl 0, isUsedIn = GameInfo::regionalHighlightingUsed),
    NUMBER_HIGHLIGHTING(bit = 1 shl 1, isUsedIn = GameInfo::numberHighlightingUsed),
    ERASER(bit = 1 shl 2, isUsedIn = GameInfo::eraserUsed),
    CHECKLIST(bit = 1 shl 3, isUsedIn = GameInfo::isChecklist),
    REVERSE_CHECKLIST(bit = 1 shl 4, isUsedIn = GameInfo::isReverseChecklist),
    ;

    fun isSetIn(bits: Int): Boolean = bits and bit != 0
}

/** The `.sudoku` share file: one protobuf wire message with the game's header fields and its board blob. */
object SudokuFileCodec {
    const val FORMAT = 1

    /** The file of [sudoku]; [zone] turns its local stamps into epoch milliseconds. */
    fun encode(
        sudoku: Sudoku,
        zone: ZoneId,
    ): ByteArray {
        val info = sudoku.info
        return ProtoWriter()
            .apply {
                varint(FIELD_FORMAT, FORMAT.toLong())
                string(FIELD_ID, info.id.value)
                optionalVarint(FIELD_MODE, info.mode.code.toLong())
                (info.mode as? GameMode.Level)?.let { varint(FIELD_LEVEL, it.number.toLong()) }
                optionalVarint(FIELD_DIFFICULTY, info.difficulty.code.toLong())
                optionalVarint(
                    FIELD_CREATED,
                    info.created
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli(),
                )
                optionalVarint(
                    FIELD_UPDATED,
                    info.updated
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli(),
                )
                optionalVarint(FIELD_SECONDS, info.seconds.toLong())
                optionalVarint(FIELD_HINTS_USED, info.hintsUsed.toLong())
                optionalVarint(FIELD_NOTES_MADE, info.notesMade.toLong())
                optionalVarint(FIELD_ERRORS_MADE, info.errorsMade.toLong())
                optionalVarint(
                    FIELD_USAGE,
                    Usage.entries
                        .filter { it.isUsedIn(info) }
                        .sumOf { it.bit }
                        .toLong(),
                )
                optionalVarint(FIELD_CHECKLIST_NUMBER, info.checklistNumber.toLong())
                (info.mode as? GameMode.Daily)?.let { varint(FIELD_DAILY_DATE, it.date.toEpochDay()) }
                bytes(FIELD_BOARD, BoardCodec.encode(sudoku.board))
            }.toByteArray()
    }

    /** The game of [bytes], or null if they are no `.sudoku` file of this format; [zone] places its stamps on the device clock. */
    fun decode(
        bytes: ByteArray,
        zone: ZoneId,
    ): Sudoku? {
        val fields = ProtoReader.fields(bytes).orEmpty().associateBy { it.number }
        val board = (fields[FIELD_BOARD] as? BytesField)?.let { BoardCodec.decode(it.value) }
        val info = board?.takeIf { fields.long(FIELD_FORMAT) == FORMAT.toLong() }?.let { fields.infoOrNull(it.size, zone) }
        return if (board != null && info != null) Sudoku(info, board) else null
    }

    private fun Map<Int, ProtoField>.infoOrNull(
        size: SudokuSize,
        zone: ZoneId,
    ): GameInfo? {
        val id = (this[FIELD_ID] as? BytesField)?.let { it.value.toString(Charsets.UTF_8) }?.takeIf { it.isNotEmpty() }
        val created = stamp(FIELD_CREATED, zone)
        val mode = int(FIELD_MODE)?.let { GameMode.fromCode(it, int(FIELD_LEVEL), dailyDate(created)) }
        val difficulty = int(FIELD_DIFFICULTY)?.let(Difficulty::fromCodeOrNull)
        val countsValid = COUNT_FIELDS.all { int(it) != null }
        return if (id != null && mode != null && difficulty != null && countsValid) {
            val usage = long(FIELD_USAGE).toInt()
            GameInfo(
                id = SudokuId(id),
                size = size,
                difficulty = difficulty,
                mode = mode,
                created = created,
                updated = stamp(FIELD_UPDATED, zone),
                seconds = long(FIELD_SECONDS).toInt(),
                hintsUsed = long(FIELD_HINTS_USED).toInt(),
                notesMade = long(FIELD_NOTES_MADE).toInt(),
                errorsMade = long(FIELD_ERRORS_MADE).toInt(),
                regionalHighlightingUsed = Usage.REGIONAL_HIGHLIGHTING.isSetIn(usage),
                numberHighlightingUsed = Usage.NUMBER_HIGHLIGHTING.isSetIn(usage),
                eraserUsed = Usage.ERASER.isSetIn(usage),
                isChecklist = Usage.CHECKLIST.isSetIn(usage),
                isReverseChecklist = Usage.REVERSE_CHECKLIST.isSetIn(usage),
                checklistNumber = long(FIELD_CHECKLIST_NUMBER).toInt(),
            )
        } else {
            null
        }
    }

    /** The day a daily game was made for: field 15, or the day of [created] for a file without it; null for an unreadable day. */
    private fun Map<Int, ProtoField>.dailyDate(created: LocalDateTime): LocalDate? =
        when (val field = this[FIELD_DAILY_DATE]) {
            null -> {
                created.toLocalDate()
            }

            is VarintField -> {
                field.value.takeIf { it in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay() }?.let(
                    LocalDate::ofEpochDay,
                )
            }

            else -> {
                null
            }
        }

    private fun ProtoWriter.optionalVarint(
        number: Int,
        value: Long,
    ) {
        if (value != 0L) varint(number, value)
    }

    private fun Map<Int, ProtoField>.long(number: Int): Long = (this[number] as? VarintField)?.value ?: 0L

    /** The non-negative Int of a varint field; an absent field is 0 and a value beyond Int is null. */
    private fun Map<Int, ProtoField>.int(number: Int): Int? = long(number).takeIf { it in 0..Int.MAX_VALUE }?.toInt()

    private fun Map<Int, ProtoField>.stamp(
        number: Int,
        zone: ZoneId,
    ): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(long(number)), zone)
}
