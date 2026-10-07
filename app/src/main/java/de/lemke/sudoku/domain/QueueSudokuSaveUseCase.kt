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
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import de.lemke.sudoku.di.ApplicationScope
import de.lemke.sudoku.domain.model.Sudoku
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async

/** Saves sudokus one after another in the application scope, so no save overtakes an earlier one or ends with a screen. */
@Singleton
class QueueSudokuSaveUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    private val lock = Any()
    private var lastSave: Job? = null

    /** Maps [sudoku] to its rows right away, so later moves do not reach this save, then saves them after every earlier one. */
    operator fun invoke(
        sudoku: Sudoku,
        onlyUpdate: Boolean = false,
    ): Deferred<Unit> {
        val rows = sudokuWithFieldsToDb(sudoku)
        return synchronized(lock) {
            val previous = lastSave
            applicationScope
                .async(defaultDispatcher) {
                    previous?.join()
                    sudokusRepository.saveSudokuRows(rows, onlyUpdate)
                }.also { lastSave = it }
        }
    }
}
