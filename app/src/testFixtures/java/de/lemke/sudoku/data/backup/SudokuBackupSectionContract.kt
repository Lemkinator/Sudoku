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

package de.lemke.sudoku.data.backup

import de.lemke.commonutils.backup.BackupSectionContract
import de.lemke.commonutils.backup.ClashCase
import de.lemke.commonutils.backup.SectionFixture
import de.lemke.sudoku.domain.model.Sudoku
import java.time.OffsetDateTime

private const val HINT_CELL = 10
private const val NOTE_CELL = 4
private const val SUDOKUS_V1 =
    """{"entries":[
{"id":"11111111-aaaa","size":4,"difficulty":"MEDIUM","modeLevel":0,"created":"2026-05-02T08:00:00+02:00","updated":"2026-05-02T08:10:00+02:00","seconds":600,"regionalHighlightingUsed":true,"numberHighlightingUsed":false,"eraserUsed":true,"isChecklist":false,"isReverseChecklist":false,"checklistNumber":0,"hintsUsed":1,"notesMade":3,"errorsMade":2,"board":{"solution":"1234341223414123","values":"12.4..1..34.4..3","givens":[0,3,6,9,12,15],"hints":[10],"notes":{"2":"13","4":"34"}}},
{"id":"22222222-bbbb","size":4,"difficulty":"EASY","modeLevel":-1,"created":"2026-05-03T07:30:00+02:00","updated":"2026-05-03T07:30:00+02:00","seconds":0,"board":{"solution":"1234341223414123","values":"1..4..1..3..4..3","givens":[0,3,6,9,12,15]}}
]}"""

abstract class SudokuBackupSectionContract : BackupSectionContract<SudokuBackupEntry>() {
    override fun sampleEntries(): List<SudokuBackupEntry> =
        listOf(
            backupEntry(id = "normal-game", updated = "2026-05-02T08:10:00+02:00", solvedCells = 3, seconds = 95),
            backupEntry(id = "daily-game", modeLevel = Sudoku.MODE_DAILY, created = "2026-05-03T07:30:00+02:00"),
            backupEntry(id = "level-game", modeLevel = 2, created = "2026-01-10T19:00:00+01:00", solvedCells = 10),
        )

    override fun fixtures(): Map<Int, SectionFixture<SudokuBackupEntry>> =
        mapOf(
            1 to
                SectionFixture(
                    text = SUDOKUS_V1,
                    entries =
                        listOf(
                            SudokuBackupEntry(
                                id = "11111111-aaaa",
                                size = 4,
                                difficulty = "MEDIUM",
                                modeLevel = Sudoku.MODE_NORMAL,
                                created = OffsetDateTime.parse("2026-05-02T08:00:00+02:00"),
                                updated = OffsetDateTime.parse("2026-05-02T08:10:00+02:00"),
                                seconds = 600,
                                regionalHighlightingUsed = true,
                                eraserUsed = true,
                                hintsUsed = 1,
                                notesMade = 3,
                                errorsMade = 2,
                                board =
                                    BoardBackup(
                                        solution = FOUR_SOLUTION,
                                        values = "12.4..1..34.4..3",
                                        givens = FOUR_GIVENS,
                                        hints = listOf(HINT_CELL),
                                        notes = mapOf(2 to "13", NOTE_CELL to "34"),
                                    ),
                            ),
                            backupEntry(id = "22222222-bbbb", modeLevel = Sudoku.MODE_DAILY, created = "2026-05-03T07:30:00+02:00"),
                        ),
                ),
        )

    override fun brokenEntryJson(): String = """{"id":"broken","size":5}"""

    override fun clashCases(): List<ClashCase<SudokuBackupEntry>> =
        listOf(
            ClashCase(
                name = "level 1: completed in May beats 0 % today",
                local = backupEntry(id = "may", modeLevel = 1, created = "2026-05-02T08:00:00+02:00", solvedCells = 10),
                incoming = backupEntry(id = "today", modeLevel = 1, created = "2026-10-30T09:00:00+01:00", solvedCells = 0),
                expected = backupEntry(id = "may", modeLevel = 1, created = "2026-05-02T08:00:00+02:00", solvedCells = 10),
            ),
            ClashCase(
                name = "daily: 70 % beats 40 % although older",
                local = backupEntry(id = "seventy", modeLevel = -1, updated = "2026-05-02T09:00:00+02:00", solvedCells = 7),
                incoming = backupEntry(id = "forty", modeLevel = -1, updated = "2026-05-02T18:00:00+02:00", solvedCells = 4),
                expected = backupEntry(id = "seventy", modeLevel = -1, updated = "2026-05-02T09:00:00+02:00", solvedCells = 7),
            ),
            ClashCase(
                name = "daily: equal progress, the newer updated wins",
                local = backupEntry(id = "older", modeLevel = -1, updated = "2026-05-02T09:00:00+02:00", solvedCells = 4),
                incoming = backupEntry(id = "newer", modeLevel = -1, updated = "2026-05-02T18:00:00+02:00", solvedCells = 4),
                expected = backupEntry(id = "newer", modeLevel = -1, updated = "2026-05-02T18:00:00+02:00", solvedCells = 4),
            ),
            ClashCase(
                name = "daily: two completed, the newer updated wins",
                local = backupEntry(id = "first", modeLevel = -1, updated = "2026-05-02T09:00:00+02:00", solvedCells = 10),
                incoming = backupEntry(id = "second", modeLevel = -1, updated = "2026-05-02T10:00:00+02:00", solvedCells = 10),
                expected = backupEntry(id = "second", modeLevel = -1, updated = "2026-05-02T10:00:00+02:00", solvedCells = 10),
            ),
            ClashCase(
                name = "daily: equal progress and equal updated, the entry greater by encodedOrder wins",
                local = backupEntry(id = "a", modeLevel = -1, solvedCells = 4),
                incoming = backupEntry(id = "b", modeLevel = -1, solvedCells = 4),
                expected = backupEntry(id = "b", modeLevel = -1, solvedCells = 4),
            ),
            ClashCase(
                name = "normal: same id, the newer updated wins",
                local = backupEntry(id = "same", updated = "2026-05-02T09:00:00+02:00", solvedCells = 8),
                incoming = backupEntry(id = "same", updated = "2026-05-02T10:00:00+02:00", solvedCells = 2),
                expected = backupEntry(id = "same", updated = "2026-05-02T10:00:00+02:00", solvedCells = 2),
            ),
            ClashCase(
                name = "normal: updated 09:30+01:00 beats 10:00+02:00",
                local = backupEntry(id = "same", updated = "2026-01-02T10:00:00+02:00"),
                incoming = backupEntry(id = "same", updated = "2026-01-02T09:30:00+01:00"),
                expected = backupEntry(id = "same", updated = "2026-01-02T09:30:00+01:00"),
            ),
            ClashCase(
                name = "normal: equal updated, the entry greater by encodedOrder wins",
                local = backupEntry(id = "same", seconds = 5),
                incoming = backupEntry(id = "same", seconds = 7),
                expected = backupEntry(id = "same", seconds = 7),
            ),
        )
}
