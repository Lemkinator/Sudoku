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

import android.os.Looper
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import java.time.LocalDateTime
import org.robolectric.Shadows.shadowOf

private const val LIST_SIZE = 4
private const val DIFF_TIMEOUT_MS = 5_000L

/** A 4x4 sudoku without givens whose first [filled] fields hold their solution, so progress is `filled * 100 / 16`. */
internal fun listSudoku(
    sudokuId: SudokuId,
    modeLevel: Int,
    filled: Int,
    errorsMade: Int,
    seconds: Int,
    created: LocalDateTime,
    updated: LocalDateTime = created,
): Sudoku =
    Sudoku.create(
        sudokuId = sudokuId,
        size = LIST_SIZE,
        difficulty = Difficulty.VERY_EASY,
        modeLevel = modeLevel,
        errorsMade = errorsMade,
        seconds = seconds,
        created = created,
        updated = updated,
        fields =
            MutableList(LIST_SIZE * LIST_SIZE) { index ->
                val row = index / LIST_SIZE
                val col = index % LIST_SIZE
                val solution = (2 * (row % 2) + row / 2 + col) % LIST_SIZE + 1
                Field(Position.create(index, LIST_SIZE), solution = solution, value = if (index < filled) solution else null)
            },
    )

/** ListAdapter diffs every submitList after the first on a background thread, so this polls until [expected] shows. */
internal fun RecyclerView.awaitSmallText(
    position: Int,
    expected: String,
): String? {
    val deadline = System.currentTimeMillis() + DIFF_TIMEOUT_MS
    var actual = smallTextAt(position)
    while (actual != expected && System.currentTimeMillis() < deadline) {
        Thread.sleep(5)
        actual = smallTextAt(position)
    }
    return actual
}

private fun RecyclerView.smallTextAt(position: Int): String? {
    shadowOf(Looper.getMainLooper()).idle()
    return findViewHolderForAdapterPosition(position)
        ?.itemView
        ?.findViewById<TextView>(R.id.item_text_small)
        ?.text
        ?.toString()
}
