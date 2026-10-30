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

import de.lemke.sudoku.domain.model.Sudoku
import java.time.OffsetDateTime

const val FOUR_SOLUTION = "1234341223414123"
private const val GIVEN_STEP = 3
val FOUR_GIVENS = FOUR_SOLUTION.indices.filter { it % GIVEN_STEP == 0 }

/**
 * A 4x4 game over [FOUR_SOLUTION] whose given cells are every third one. The first [solvedCells] of the ten other
 * cells hold their solution, so the progress is `solvedCells * 10` percent.
 */
fun backupEntry(
    id: String = "game",
    modeLevel: Int = Sudoku.MODE_NORMAL,
    created: String = "2026-05-02T08:00:00+02:00",
    updated: String = created,
    solvedCells: Int = 0,
    seconds: Int = 0,
): SudokuBackupEntry {
    val open = FOUR_SOLUTION.indices.filterNot { it in FOUR_GIVENS }
    val filled = FOUR_GIVENS + open.take(solvedCells)
    return SudokuBackupEntry(
        id = id,
        size = 4,
        difficulty = "EASY",
        modeLevel = modeLevel,
        created = OffsetDateTime.parse(created),
        updated = OffsetDateTime.parse(updated),
        seconds = seconds,
        board =
            BoardBackup(
                solution = FOUR_SOLUTION,
                values = FOUR_SOLUTION.indices.map { if (it in filled) FOUR_SOLUTION[it] else '.' }.joinToString(""),
                givens = FOUR_GIVENS,
            ),
    )
}
