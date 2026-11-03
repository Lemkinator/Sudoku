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

package de.lemke.sudoku.domain.model

/** A set of pencil-mark digits; digit `d` occupies bit `d - 1`. */
@JvmInline
value class NoteMask(val bits: Int) {
    operator fun contains(digit: Int): Boolean = digit in 1..Int.SIZE_BITS && bits and bitOf(digit) != 0

    fun toggled(digit: Int): NoteMask = NoteMask(bits xor bitOf(digit))

    operator fun minus(digit: Int): NoteMask = NoteMask(bits and bitOf(digit).inv())

    val count: Int get() = Integer.bitCount(bits)

    val digits: List<Int> get() = (1..Int.SIZE_BITS).filter { it in this }

    fun isEmpty(): Boolean = bits == 0

    companion object {
        val EMPTY = NoteMask(0)

        fun of(vararg digits: Int): NoteMask = NoteMask(digits.fold(0) { bits, digit -> bits or bitOf(digit) })

        private fun bitOf(digit: Int): Int = 1 shl (digit - 1)
    }
}
