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

import de.lemke.sudoku.domain.model.CompletedRegions
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class FieldAnimationsTest : ShouldSpec(
    {
        should("map no completed region to no field region") {
            CompletedRegions.NONE.fieldRegions() shouldBe emptySet()
        }

        should("map a completed row, column and block to their field regions") {
            CompletedRegions(row = true, column = false, block = false, board = false).fieldRegions() shouldBe setOf(FieldRegion.ROW)
            CompletedRegions(row = false, column = true, block = false, board = false).fieldRegions() shouldBe setOf(FieldRegion.COLUMN)
            CompletedRegions(row = false, column = false, block = true, board = false).fieldRegions() shouldBe setOf(FieldRegion.BLOCK)
        }

        should("leave the board out of the wave regions") {
            CompletedRegions(row = true, column = true, block = true, board = true).fieldRegions() shouldBe
                setOf(FieldRegion.ROW, FieldRegion.COLUMN, FieldRegion.BLOCK)
        }
    },
)
