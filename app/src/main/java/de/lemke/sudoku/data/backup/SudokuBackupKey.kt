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
import de.lemke.sudoku.domain.model.SudokuSize
import java.time.LocalDate

/** What makes two games the same game: the id of a normal sudoku, the day of a daily one, the size and level of a level one. */
sealed interface SudokuBackupKey {
    data class Normal(
        val id: String,
    ) : SudokuBackupKey

    data class Daily(
        val date: LocalDate,
    ) : SudokuBackupKey

    data class Level(
        val size: SudokuSize,
        val level: Int,
    ) : SudokuBackupKey
}

/** The entry must be valid, so its size is known. */
fun SudokuBackupEntry.key(): SudokuBackupKey =
    when {
        modeLevel == Sudoku.MODE_DAILY -> SudokuBackupKey.Daily(created.toLocalDate())
        modeLevel > Sudoku.MODE_NORMAL -> SudokuBackupKey.Level(SudokuSize.fromValue(size), modeLevel)
        else -> SudokuBackupKey.Normal(id)
    }
