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

private const val FADE_ALPHA = 0.4f
private const val WAVE_SCALE = 1.6f
private const val WAVE_ROTATION_DEGREES = 100f
private const val WAVE_DURATION_MILLIS = 250L
private const val WAVE_STAGGER_MILLIS = 120L
private const val BOARD_WAVE_DURATION_MILLIS = 200L
private const val BOARD_WAVE_STAGGER_MILLIS = 60L
private const val PULSE_SCALE = 2f
private const val PULSE_DURATION_MILLIS = 450L
private const val PULSE_STAGGER_MILLIS = 180L
private const val LEGS = 2

/** How the value of a cell moves while its step runs: out to the peak and back, each leg taking [legMillis]. */
enum class CellAnimation(
    val legMillis: Long,
    private val staggerMillis: Long,
    val scale: Float,
    val alpha: Float,
    val rotationDegrees: Float,
) {
    WAVE(WAVE_DURATION_MILLIS, WAVE_STAGGER_MILLIS, WAVE_SCALE, FADE_ALPHA, WAVE_ROTATION_DEGREES),
    BOARD_WAVE(BOARD_WAVE_DURATION_MILLIS, BOARD_WAVE_STAGGER_MILLIS, WAVE_SCALE, FADE_ALPHA, WAVE_ROTATION_DEGREES),
    PULSE(PULSE_DURATION_MILLIS, PULSE_STAGGER_MILLIS, PULSE_SCALE, alpha = 1f, rotationDegrees = 0f),
    ;

    /** The time between the starts of two consecutive steps on a board whose blocks have [blockSize] cells per side. */
    fun stepMillis(blockSize: Int): Long =
        when (this) {
            WAVE -> staggerMillis / blockSize
            BOARD_WAVE -> staggerMillis / blockSize / blockSize
            PULSE -> staggerMillis
        }

    fun totalMillis(
        steps: Int,
        blockSize: Int,
    ): Long = (steps - 1).coerceAtLeast(0) * stepMillis(blockSize) + LEGS * legMillis

    /** How far a step that started [elapsedMillis] ago is from rest: 0 before, at and after the animation, 1 at the peak. */
    fun peakAt(elapsedMillis: Float): Float {
        val leg = elapsedMillis / legMillis
        return when {
            leg <= 0f || leg >= LEGS -> 0f
            leg <= 1f -> leg
            else -> LEGS - leg
        }
    }
}
