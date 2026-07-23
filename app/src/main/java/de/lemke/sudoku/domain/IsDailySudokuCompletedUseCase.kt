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

import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IsDailySudokuCompletedUseCase @Inject constructor(
    private val getAllSudokus: GetAllSudokusUseCase,
) {
    suspend operator fun invoke(date: LocalDate = LocalDate.now()): Boolean =
        withContext(Dispatchers.Default) {
            return@withContext getAllSudokus(
                GetAllSudokusUseCase.TYPE_DAILY or GetAllSudokusUseCase.DIFFICULTY_ALL or GetAllSudokusUseCase.SIZE_ALL,
            ).find { it.created.toLocalDate() == date && it.completed } != null
        }
}
