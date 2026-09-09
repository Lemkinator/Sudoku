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

import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.matchesSudokuFilterFlags
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class ObserveSudokusAndStatisticsFilterFlagsUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    private val userSettings: UserSettings,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<List<Sudoku>> =
        combine(userSettings.filterFlagsFlow, sudokusRepository.observeAllSudokus()) { flags, sudokus ->
            sudokus.filter { flags.matchesSudokuFilterFlags(it) }
        }.flowOn(defaultDispatcher)
}
