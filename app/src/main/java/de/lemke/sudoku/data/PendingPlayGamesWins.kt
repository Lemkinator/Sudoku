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

package de.lemke.sudoku.data

import de.lemke.sudoku.domain.model.SudokuId
import javax.inject.Inject
import javax.inject.Singleton

/** The won sudokus whose Play Games sync no activity has applied yet; it outlives the process. */
@Singleton
class PendingPlayGamesWins @Inject constructor(
    private val userSettings: UserSettings,
) {
    @Synchronized
    fun all(): Set<SudokuId> = userSettings.pendingPlayGamesWins.mapTo(mutableSetOf(), ::SudokuId)

    @Synchronized
    fun add(id: SudokuId) {
        userSettings.pendingPlayGamesWins += id.value
    }

    @Synchronized
    fun remove(ids: Set<SudokuId>) {
        userSettings.pendingPlayGamesWins -= ids.mapTo(mutableSetOf(), SudokuId::value)
    }
}
