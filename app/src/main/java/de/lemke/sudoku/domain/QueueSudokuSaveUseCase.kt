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

import android.util.Log
import de.lemke.commonutils.di.ApplicationScope
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async

/** A queued save that has not started yet and writes the newest snapshot of its game when it does. */
private class PendingSave(var snapshot: Sudoku) {
    lateinit var job: Deferred<Unit>
}

/**
 * Saves sudokus one after another in the application scope, so no save overtakes an earlier one or ends with a screen. A save
 * that has not started yet takes the newer snapshot of its game, so a burst of moves writes once.
 */
@Singleton
class QueueSudokuSaveUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    private val lock = Any()
    private var lastSave: Job? = null
    private val pending = mutableMapOf<SudokuId, PendingSave>()

    /**
     * Saves [sudoku] after every earlier save. The returned [Deferred] completes when a save of [sudoku] or of a newer snapshot of
     * its game has ended.
     */
    operator fun invoke(sudoku: Sudoku): Deferred<Unit> =
        synchronized(lock) {
            val id = sudoku.info.id
            pending[id]?.let { waiting ->
                waiting.snapshot = sudoku
                return@synchronized waiting.job
            }
            val slot = PendingSave(sudoku)
            pending[id] = slot
            val previous = lastSave
            applicationScope
                .async(defaultDispatcher) {
                    previous?.join()
                    val snapshot = synchronized(lock) { slot.snapshot.also { pending.remove(id, slot) } }
                    sudokusRepository.saveSudoku(snapshot)
                }.also {
                    slot.job = it
                    lastSave = it
                }
        }

    /**
     * Runs [block] after every earlier save has ended. Saves queued meanwhile wait until [block] and every earlier save
     * have ended, even when [block] fails or its caller is canceled. [block] must not queue a save and await it, or it deadlocks.
     */
    suspend fun <R> exclusive(block: suspend () -> R): R {
        val finished = CompletableDeferred<Unit>()
        val previous =
            synchronized(lock) {
                pending.clear()
                lastSave.also { lastSave = finished }
            }
        try {
            previous?.join()
            return block()
        } finally {
            previous?.invokeOnCompletion { finished.complete(Unit) } ?: finished.complete(Unit)
        }
    }

    /** Queues [sudoku] like [invoke] for a caller that does not await the save, and logs a failed save. */
    fun launch(sudoku: Sudoku) {
        invoke(sudoku).invokeOnCompletion { cause ->
            if (cause != null && cause !is CancellationException) Log.e(TAG, "Saving sudoku failed", cause)
        }
    }

    private companion object {
        private const val TAG = "QueueSudokuSaveUseCase"
    }
}
