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
import de.lemke.sudoku.domain.model.CompletedRegions
import de.lemke.sudoku.domain.model.Position
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SUDOKU_COMPLETED_ANIMATION_DURATION_MILLIS = 200L
private const val FIELD_ANIMATION_FADE_ALPHA = 0.4f
private const val FIELD_ANIMATION_SCALE = 1.6f
private const val FIELD_ANIMATION_ROTATION_DEGREES = 100f
private const val FIELD_ANIMATION_DURATION_MILLIS = 250L
private const val FIELD_ANIMATION_DELAY_MILLIS = 120L
private const val WAVE_DELAY_MILLIS = 60L

/** A part of the board whose fields pulse together. */
enum class FieldRegion {
    ROW,
    COLUMN,
    BLOCK,
    BOARD,
    ;

    fun reaches(
        fieldView: FieldView,
        position: Position,
        compare: (Int, Int) -> Boolean,
    ): Boolean =
        when (this) {
            ROW -> fieldView.position.row == position.row && compare(fieldView.position.column, position.column)
            COLUMN -> fieldView.position.column == position.column && compare(fieldView.position.row, position.row)
            BLOCK -> fieldView.position.block == position.block && compare(fieldView.position.index, position.index)
            BOARD -> compare(fieldView.position.index, position.index)
        }
}

fun CompletedRegions.fieldRegions(): Set<FieldRegion> =
    setOfNotNull(
        FieldRegion.ROW.takeIf { row },
        FieldRegion.COLUMN.takeIf { column },
        FieldRegion.BLOCK.takeIf { block },
    )

/** Pulses the fields of [regions] around [position], outward in both directions. */
fun CoroutineScope.animateFields(
    fieldViews: List<FieldView>,
    position: Position,
    regions: Set<FieldRegion>,
): Job {
    val blockSize = position.size.blockSize
    val waveDelay = WAVE_DELAY_MILLIS / blockSize

    fun reached(compare: (Int, Int) -> Boolean) =
        fieldViews.filter { fieldView -> regions.any { it.reaches(fieldView, position, compare) } }

    suspend fun pulse(fieldView: FieldView) {
        if (FieldRegion.BOARD in regions) {
            animateField(fieldView.fieldViewValue, SUDOKU_COMPLETED_ANIMATION_DURATION_MILLIS, waveDelay, blockSize)
        } else {
            animateField(fieldView.fieldViewValue, blockSize = blockSize)
        }
    }
    launch { reached { a, b -> a <= b }.reversed().forEach { pulse(it) } }
    return launch { reached { a, b -> a > b }.forEach { pulse(it) } }
}

private suspend fun animateField(
    fieldTextView: TextView?,
    duration: Long = FIELD_ANIMATION_DURATION_MILLIS,
    delay: Long = FIELD_ANIMATION_DELAY_MILLIS,
    blockSize: Int,
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
    delay((delay / blockSize).milliseconds)
}
