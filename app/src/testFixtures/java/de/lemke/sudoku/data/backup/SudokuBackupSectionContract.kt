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
import de.lemke.sudoku.data.database.toLegacyModeLevel
import de.lemke.sudoku.domain.model.GameMode
import java.time.LocalDate
import java.time.OffsetDateTime

private val MAY_SECOND = LocalDate.parse("2026-05-02")

private const val SUDOKUS_V2 =
    """{"entries":[
{"id":"11111111-aaaa","size":4,"difficulty":"MEDIUM","modeLevel":0,"created":"2026-05-02T08:00:00+02:00","updated":"2026-05-02T08:10:00+02:00","seconds":600,"regionalHighlightingUsed":true,"numberHighlightingUsed":false,"eraserUsed":true,"isChecklist":false,"isReverseChecklist":false,"checklistNumber":0,"hintsUsed":1,"notesMade":3,"errorsMade":2,"board":"CAEQBBoIASMjARIwMBIiECECACQAACEAACNEACQAACMqBAIFBAw="},
{"id":"22222222-bbbb","size":4,"difficulty":"EASY","modeLevel":-1,"created":"2026-05-03T07:30:00+02:00","updated":"2026-05-03T07:30:00+02:00","seconds":0,"board":"CAEQBBoIASMjARIwMBIiECEAACQAACEAACMAACQAACM="}
]}"""

abstract class SudokuBackupSectionContract : BackupSectionContract<SudokuBackupEntry>() {
    override fun sampleEntries(): List<SudokuBackupEntry> =
        listOf(
            backupEntry(id = "normal-game", updated = "2026-05-02T08:10:00+02:00", solvedCells = 3, seconds = 95),
            backupEntry(id = "daily-game", modeLevel = -1, created = "2026-05-03T07:30:00+02:00", dailyDate = MAY_SECOND),
            backupEntry(id = "level-game", modeLevel = 2, created = "2026-01-10T19:00:00+01:00", solvedCells = 10),
        )

    override fun fixtures(): Map<Int, SectionFixture<SudokuBackupEntry>> =
        mapOf(
            2 to
                SectionFixture(
                    text = SUDOKUS_V2,
                    entries =
                        listOf(
                            SudokuBackupEntry(
                                id = "11111111-aaaa",
                                size = 4,
                                difficulty = "MEDIUM",
                                modeLevel = GameMode.Normal.toLegacyModeLevel(),
                                created = OffsetDateTime.parse("2026-05-02T08:00:00+02:00"),
                                updated = OffsetDateTime.parse("2026-05-02T08:10:00+02:00"),
                                seconds = 600,
                                regionalHighlightingUsed = true,
                                eraserUsed = true,
                                hintsUsed = 1,
                                notesMade = 3,
                                errorsMade = 2,
                                board = "CAEQBBoIASMjARIwMBIiECECACQAACEAACNEACQAACMqBAIFBAw=",
                            ),
                            backupEntry(
                                id = "22222222-bbbb",
                                modeLevel = -1,
                                created = "2026-05-03T07:30:00+02:00",
                                dailyDate = LocalDate.parse("2026-05-03"),
                            ),
                        ),
                ),
        )

    override fun brokenEntryJson(): String = """{"id":"broken","size":5}"""

    override fun clashCases(): List<ClashCase<SudokuBackupEntry>> = levelClashCases() + dailyClashCases() + normalClashCases()

    private fun levelClashCases(): List<ClashCase<SudokuBackupEntry>> =
        listOf(
            ClashCase(
                name = "level 1: completed in May beats 0 % today",
                local = backupEntry(id = "may", modeLevel = 1, created = "2026-05-02T08:00:00+02:00", solvedCells = 10),
                incoming = backupEntry(id = "today", modeLevel = 1, created = "2026-10-30T09:00:00+01:00", solvedCells = 0),
                expected = backupEntry(id = "may", modeLevel = 1, created = "2026-05-02T08:00:00+02:00", solvedCells = 10),
            ),
        )

    private fun dailyClashCases(): List<ClashCase<SudokuBackupEntry>> =
        listOf(
            ClashCase(
                name = "daily: 70 % beats 40 % although older",
                local = dailyEntry(id = "seventy", updated = "2026-05-02T09:00:00+02:00", solvedCells = 7),
                incoming = dailyEntry(id = "forty", updated = "2026-05-02T18:00:00+02:00", solvedCells = 4),
                expected = dailyEntry(id = "seventy", updated = "2026-05-02T09:00:00+02:00", solvedCells = 7),
            ),
            ClashCase(
                name = "daily: an entry without a day and one made for that day clash, the higher progress wins",
                local = backupEntry(id = "old", modeLevel = -1, created = "2026-05-02T08:00:00+02:00", solvedCells = 3),
                incoming = dailyEntry(id = "new", created = "2026-05-03T00:30:00+02:00", solvedCells = 6),
                expected = dailyEntry(id = "new", created = "2026-05-03T00:30:00+02:00", solvedCells = 6),
            ),
            ClashCase(
                name = "daily: equal progress, the newer updated wins",
                local = dailyEntry(id = "older", updated = "2026-05-02T09:00:00+02:00", solvedCells = 4),
                incoming = dailyEntry(id = "newer", updated = "2026-05-02T18:00:00+02:00", solvedCells = 4),
                expected = dailyEntry(id = "newer", updated = "2026-05-02T18:00:00+02:00", solvedCells = 4),
            ),
            ClashCase(
                name = "daily: two completed, the newer updated wins",
                local = dailyEntry(id = "first", updated = "2026-05-02T09:00:00+02:00", solvedCells = 10),
                incoming = dailyEntry(id = "second", updated = "2026-05-02T10:00:00+02:00", solvedCells = 10),
                expected = dailyEntry(id = "second", updated = "2026-05-02T10:00:00+02:00", solvedCells = 10),
            ),
            ClashCase(
                name = "daily: equal progress and equal updated, the entry greater by encodedOrder wins",
                local = dailyEntry(id = "a", solvedCells = 4),
                incoming = dailyEntry(id = "b", solvedCells = 4),
                expected = dailyEntry(id = "b", solvedCells = 4),
            ),
        )

    private fun normalClashCases(): List<ClashCase<SudokuBackupEntry>> =
        listOf(
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

    private fun dailyEntry(
        id: String,
        created: String = "2026-05-02T08:00:00+02:00",
        updated: String = created,
        solvedCells: Int,
    ) = backupEntry(id = id, modeLevel = -1, created = created, updated = updated, solvedCells = solvedCells, dailyDate = MAY_SECOND)
}
