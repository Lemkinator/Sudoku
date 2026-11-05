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

import de.lemke.sudoku.domain.model.CompletedRegions
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.SudokuSize

/**
 * Cells of the completed [regions] around [from], grouped into steps that pulse together. The cells reached are ordered by
 * index; step n holds the n-th cell at or before [from], counted backward, and the n-th cell after it, counted forward.
 */
fun animationSteps(
    size: SudokuSize,
    from: Int,
    regions: CompletedRegions,
): List<List<Int>> {
    val origin = Position.create(from, size)
    val reached =
        (0 until size.cellCount).filter { index ->
            val position = Position.create(index, size)
            regions.board ||
                (regions.row && position.row == origin.row) ||
                (regions.column && position.column == origin.column) ||
                (regions.block && position.block == origin.block)
        }
    val backward = reached.filter { it <= from }.asReversed()
    val forward = reached.filter { it > from }
    return List(maxOf(backward.size, forward.size)) { step ->
        listOfNotNull(backward.getOrNull(step), forward.getOrNull(step))
    }
}
