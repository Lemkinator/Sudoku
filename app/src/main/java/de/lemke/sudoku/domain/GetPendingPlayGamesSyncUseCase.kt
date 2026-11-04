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
import de.lemke.sudoku.data.PendingPlayGamesWins
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.PendingPlayGamesSync
import de.lemke.sudoku.domain.model.PlayGamesSync
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class GetPendingPlayGamesSyncUseCase @Inject constructor(
    private val pendingWins: PendingPlayGamesWins,
    private val sudokusRepository: SudokusRepository,
    private val calculatePlayGamesSync: CalculatePlayGamesSyncUseCase,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    /** The sync of every queued win that is still a completed sudoku, or null if none is left. A queued win that is not is dropped. */
    suspend operator fun invoke(): PendingPlayGamesSync? =
        withContext(defaultDispatcher) {
            val queued = pendingWins.all()
            val wins = queued.mapNotNull { id -> sudokusRepository.getSudokuById(id)?.takeIf { it.completed } }
            val winIds = wins.mapTo(mutableSetOf()) { it.info.id }
            pendingWins.remove(queued - winIds)
            if (wins.isEmpty()) null else PendingPlayGamesSync(winIds, wins.map { calculatePlayGamesSync(it) }.reduce(PlayGamesSync::plus))
        }
}
