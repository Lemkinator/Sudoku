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

package de.lemke.sudoku.ui

import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.isBlockCompleted
import de.lemke.sudoku.domain.model.isColumnCompleted
import de.lemke.sudoku.domain.model.isRowCompleted
import de.lemke.sudoku.ui.utils.FieldView
import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ROW_COLUMN_BLOCK_ANIMATION_DURATION_MILLIS = 200L
private const val FIELD_ANIMATION_FADE_ALPHA = 0.4f
private const val FIELD_ANIMATION_SCALE = 1.6f
private const val FIELD_ANIMATION_ROTATION_DEGREES = 100f

internal fun checkRowColumnBlockCompleted(
    position: Position,
    sudoku: Sudoku,
    gameAdapter: SudokuViewAdapter,
    lifecycleScope: LifecycleCoroutineScope,
) {
    animate(
        position,
        gameAdapter,
        sudoku,
        lifecycleScope,
        animateRow = sudoku.isRowCompleted(position.row),
        animateColumn = sudoku.isColumnCompleted(position.column),
        animateBlock = sudoku.isBlockCompleted(position.block),
    )
}

internal fun animate(
    position: Position,
    gameAdapter: SudokuViewAdapter,
    sudoku: Sudoku,
    lifecycleScope: LifecycleCoroutineScope,
    animateRow: Boolean = false,
    animateColumn: Boolean = false,
    animateBlock: Boolean = false,
    animateSudoku: Boolean = false,
): Job? {
    if (!animateRow && !animateColumn && !animateBlock && !animateSudoku) return null
    val delay = 60L / sudoku.blockSize
    lifecycleScope.launch {
        gameAdapter.fieldViews
            .filter { matchesAnimation(it, position, animateRow, animateColumn, animateBlock, animateSudoku) { a, b -> a <= b } }
            .reversed()
            .forEach {
                if (animateSudoku) {
                    animateField(it.fieldViewValue, sudoku, ROW_COLUMN_BLOCK_ANIMATION_DURATION_MILLIS, delay)
                } else {
                    animateField(it.fieldViewValue, sudoku)
                }
            }
    }
    return lifecycleScope.launch {
        gameAdapter.fieldViews
            .filter { matchesAnimation(it, position, animateRow, animateColumn, animateBlock, animateSudoku) { a, b -> a > b } }
            .forEach {
                if (animateSudoku) {
                    animateField(it.fieldViewValue, sudoku, ROW_COLUMN_BLOCK_ANIMATION_DURATION_MILLIS, delay)
                } else {
                    animateField(it.fieldViewValue, sudoku)
                }
            }
    }
}

private fun matchesAnimation(
    fieldView: FieldView,
    position: Position,
    animateRow: Boolean,
    animateColumn: Boolean,
    animateBlock: Boolean,
    animateSudoku: Boolean,
    compare: (Int, Int) -> Boolean,
): Boolean =
    (animateRow && fieldView.position.row == position.row && compare(fieldView.position.column, position.column)) ||
        (animateColumn && fieldView.position.column == position.column && compare(fieldView.position.row, position.row)) ||
        (animateBlock && fieldView.position.block == position.block && compare(fieldView.position.index, position.index)) ||
        (animateSudoku && compare(fieldView.position.index, position.index))

private suspend fun animateField(
    fieldTextView: TextView?,
    sudoku: Sudoku,
    duration: Long = 250L,
    delay: Long = 120L,
) {
    fieldTextView?.let {
        it
            .animate()
            .alpha(FIELD_ANIMATION_FADE_ALPHA)
            .scaleX(FIELD_ANIMATION_SCALE)
            .scaleY(FIELD_ANIMATION_SCALE)
            .rotation(FIELD_ANIMATION_ROTATION_DEGREES)
            .setDuration(duration)
            .withEndAction {
                it
                    .animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotation(0f)
                    .setDuration(duration)
                    .start()
            }.start()
    }
    delay((delay / sudoku.blockSize).milliseconds)
}
