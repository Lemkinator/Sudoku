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

package de.lemke.sudoku.data

import android.net.Uri
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId

/** Returns [uri] for every write and records each written sudoku id with its JSON in [writes]. */
class FakeSudokuShareFiles(
    private val uri: Uri,
) : SudokuShareFiles {
    data class Write(
        val sudokuId: SudokuId,
        val json: String,
    )

    val writes = mutableListOf<Write>()

    override suspend fun write(
        sudoku: Sudoku,
        json: String,
    ): Uri {
        writes += Write(sudoku.id, json)
        return uri
    }
}
