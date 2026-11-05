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

package de.lemke.sudoku.ui.board

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

private const val VALUE_TEXT_RATIO = 0.62f
private const val NOTE_SLOT_RATIO = 0.7f
private const val NOTE_VALUE_RATIO = 0.6f

/** Side length of the square note grid for [count] notes: 0, 1, 2 (2-4), 3 (5-9), 4 (10-16). */
fun noteGridSide(count: Int): Int = ceil(sqrt(count.toDouble())).toInt()

fun valueTextSize(cellSide: Float): Float = VALUE_TEXT_RATIO * cellSide

fun noteTextSize(
    content: Box,
    count: Int,
    valueTextSize: Float,
): Float {
    val side = noteGridSide(count)
    if (side == 0) return 0f
    return min(NOTE_SLOT_RATIO * slotSide(content, side), NOTE_VALUE_RATIO * valueTextSize)
}

/** The first [count] slots of the centered square grid, in reading order. */
fun noteSlots(
    content: Box,
    count: Int,
): List<Box> {
    val side = noteGridSide(count)
    if (side == 0) return emptyList()
    val slot = slotSide(content, side)
    val left = content.left + (content.width - slot * side) / 2
    val top = content.top + (content.height - slot * side) / 2
    return List(count) { index ->
        val slotLeft = left + index % side * slot
        val slotTop = top + index / side * slot
        Box(slotLeft, slotTop, slotLeft + slot, slotTop + slot)
    }
}

private fun slotSide(
    content: Box,
    side: Int,
): Float = min(content.width, content.height) / side
