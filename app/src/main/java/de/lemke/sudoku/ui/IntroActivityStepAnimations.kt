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
import de.lemke.sudoku.ui.utils.FieldView
import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEMO_CELL_INDEX_21 = 21
private const val DEMO_NUMBER_BUTTON_INDEX_7 = 7
private const val DEMO_ROW_INDEX_3 = 3
private const val DEMO_ROW_INDEX_4 = 4
private const val DEMO_FIELD_HIGHLIGHT_SCALE = 1.5f

internal fun startAnimation(
    currentIntroStep: Int,
    lifecycleScope: LifecycleCoroutineScope,
    gameAdapter: SudokuViewAdapter,
    introStepNow: () -> Int,
    selectButton: (Int?) -> Unit,
): Job =
    lifecycleScope.launch {
        when (currentIntroStep) {
            0 -> {
                animateIntroStepRowColumnBlock(gameAdapter, introStepNow)
            }

            2 -> {
                animateIntroStepField(gameAdapter, rows = listOf(0), fieldIndex = DEMO_CELL_INDEX_4, introStep = 2, introStepNow)
            }

            INTRO_STEP_5 -> {
                animateIntroStepField(
                    gameAdapter,
                    rows = listOf(DEMO_ROW_INDEX_3, DEMO_ROW_INDEX_4),
                    fieldIndex = DEMO_CELL_INDEX_49,
                    introStep = INTRO_STEP_5,
                    introStepNow = introStepNow,
                )
            }

            INTRO_STEP_6 -> {
                animateIntroStepBlock(gameAdapter, introStepNow)
            }

            INTRO_STEP_8 -> {
                animateIntroStepNumberEntry(gameAdapter, introStepNow, selectButton)
            }
        }
    }

private suspend fun animateIntroStepRowColumnBlock(
    gameAdapter: SudokuViewAdapter,
    introStepNow: () -> Int,
) {
    while (introStepNow() == 0) {
        delay(900.milliseconds)
        val block = gameAdapter.fieldViews.filter { it?.position?.block == 0 }
        val row = gameAdapter.fieldViews.filter { it?.position?.row == 1 }
        val column = gameAdapter.fieldViews.filter { it?.position?.column == 5 }
        column.forEach {
            it?.isHighlighted = false
            it?.setBackground()
        }
        block.forEach {
            it?.isHighlighted = true
            it?.setBackground()
        }
        block.forEach { animateIntroFieldText(it?.fieldViewValue) }
        delay(900.milliseconds)
        block.forEach {
            it?.isHighlighted = false
            it?.setBackground()
        }
        row.forEach {
            it?.isHighlighted = true
            it?.setBackground()
        }
        row.forEach { animateIntroFieldText(it?.fieldViewValue) }
        delay(900.milliseconds)
        row.forEach {
            it?.isHighlighted = false
            it?.setBackground()
        }
        column.forEach {
            it?.isHighlighted = true
            it?.setBackground()
        }
        column.forEach { animateIntroFieldText(it?.fieldViewValue) }
    }
}

private suspend fun animateIntroStepField(
    gameAdapter: SudokuViewAdapter,
    rows: List<Int>,
    fieldIndex: Int,
    introStep: Int,
    introStepNow: () -> Int,
) {
    gameAdapter.fieldViews.filter { it?.position?.row in rows }.forEach {
        it?.isHighlighted = true
        it?.setBackground()
    }
    while (introStepNow() == introStep) animateIntroFieldView(gameAdapter.fieldViews[fieldIndex])
}

private suspend fun animateIntroStepBlock(
    gameAdapter: SudokuViewAdapter,
    introStepNow: () -> Int,
) {
    gameAdapter.fieldViews.filter { it?.position?.block == 2 }.forEach {
        it?.isHighlighted = true
        it?.setBackground()
    }
    while (introStepNow() == INTRO_STEP_6) animateIntroFieldView(gameAdapter.fieldViews[DEMO_CELL_INDEX_24])
}

private suspend fun animateIntroStepNumberEntry(
    gameAdapter: SudokuViewAdapter,
    introStepNow: () -> Int,
    selectButton: (Int?) -> Unit,
) {
    val delayMillis = 1200L
    while (introStepNow() == INTRO_STEP_8) {
        delay(delayMillis.milliseconds)
        selectButton(null)
        gameAdapter.selectFieldView(DEMO_CELL_INDEX_4, highlightNeighbors = true, highlightNumber = true)
        delay(delayMillis.milliseconds)
        gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
        selectButton(DEMO_NUMBER_BUTTON_INDEX_7)
        delay(delayMillis.milliseconds)
        selectButton(DEMO_NUMBER_BUTTON_INDEX_4)
        delay(delayMillis.milliseconds)
        selectButton(null)
        gameAdapter.selectFieldView(DEMO_CELL_INDEX_21, highlightNeighbors = true, highlightNumber = true)
        delay(delayMillis.milliseconds)
        gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
        selectButton(1)
    }
}

internal fun stopAnimation(
    currentIntroStep: Int,
    animation: Job?,
    gameAdapter: SudokuViewAdapter,
    selectButton: (Int?) -> Unit,
) {
    animation?.cancel()
    when (currentIntroStep) {
        0, INTRO_STEP_5, INTRO_STEP_6 -> {
            gameAdapter.fieldViews.forEach {
                it?.isHighlighted = false
                it?.setBackground()
            }
        }

        INTRO_STEP_8 -> {
            selectButton(null)
            gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
        }
    }
}

private suspend fun animateIntroFieldText(
    fieldTextView: TextView?,
    duration: Long = 450,
    delay: Long = 180L,
) {
    fieldTextView
        ?.animate()
        ?.scaleX(2f)
        ?.scaleY(2f)
        ?.setDuration(duration)
        ?.withEndAction {
            fieldTextView
                .animate()
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(duration)
                ?.start()
        }?.start()
    delay(delay.milliseconds)
}

private suspend fun animateIntroFieldView(
    fieldView: FieldView?,
    duration: Long = 600,
    delay: Long = 2000,
) {
    fieldView
        ?.animate()
        ?.scaleX(DEMO_FIELD_HIGHLIGHT_SCALE)
        ?.scaleY(DEMO_FIELD_HIGHLIGHT_SCALE)
        ?.setDuration(duration)
        ?.withEndAction {
            fieldView
                .animate()
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(duration)
                ?.start()
        }?.start()
    delay(delay.milliseconds)
}
