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

package de.lemke.sudoku.ui.utils

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.lemke.commonutils.awaitMainLooperUntil
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import java.time.LocalDateTime

private val LIST_SIZE = SudokuSize.FOUR

/** A 4x4 sudoku without givens whose first [filled] fields hold their solution, so progress is `filled * 100 / 16`. */
internal fun listSudoku(
    sudokuId: SudokuId,
    mode: GameMode,
    filled: Int,
    errorsMade: Int,
    seconds: Int,
    created: LocalDateTime,
    updated: LocalDateTime = created,
): Sudoku =
    Sudoku(
        info =
            GameInfo(
                id = sudokuId,
                size = LIST_SIZE,
                difficulty = Difficulty.VERY_EASY,
                mode = mode,
                created = created,
                updated = updated,
                seconds = seconds,
                errorsMade = errorsMade,
            ),
        board =
            Board(
                LIST_SIZE,
                List(LIST_SIZE.cellCount) { index ->
                    val blockSize = LIST_SIZE.blockSize
                    val row = index / LIST_SIZE.value
                    val col = index % LIST_SIZE.value
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % LIST_SIZE.value + 1
                    Cell(solution, value = if (index < filled) solution else null)
                },
            ),
    )

/** ListAdapter diffs every submitList after the first on a background thread, so this polls until [expected] shows. */
internal fun RecyclerView.awaitSmallText(
    position: Int,
    expected: String,
): String? {
    awaitMainLooperUntil { smallTextAt(position) == expected }
    return smallTextAt(position)
}

private fun RecyclerView.smallTextAt(position: Int): String? {
    idleMainLooper()
    return findViewHolderForAdapterPosition(position)
        ?.itemView
        ?.findViewById<TextView>(R.id.item_text_small)
        ?.text
        ?.toString()
}
