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

package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.sfuhrm.sudoku.Creator
import de.sfuhrm.sudoku.GameSchemas
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateFieldsUseCase @Inject constructor() {
    suspend operator fun invoke(
        size: Int,
        difficulty: Difficulty,
    ): MutableList<Field> =
        withContext(Dispatchers.Default) {
            val schema =
                when (size) {
                    4 -> GameSchemas.SCHEMA_4X4
                    9 -> GameSchemas.SCHEMA_9X9
                    16 -> GameSchemas.SCHEMA_16X16
                    else -> GameSchemas.SCHEMA_9X9
                }
            val gameMatrix = Creator.createFull(schema)
            val matrix = gameMatrix.array
            val riddle = Creator.createRiddle(gameMatrix, difficulty.numbersToRemove(size)).array
            return@withContext MutableList(size * size) { index ->
                val position = Position.create(index, size)
                val value = riddle[position.row][position.column]
                val solutionValue = matrix[position.row][position.column]
                Field(
                    position = position,
                    value = if (value == schema.unsetValue) null else value.toInt(),
                    solution = solutionValue.toInt(),
                    given = value == solutionValue,
                )
            }
        }
}
