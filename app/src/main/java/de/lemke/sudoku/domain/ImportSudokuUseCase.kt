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

import android.net.Uri
import android.util.Log
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.sudoku.data.SudokuDocuments
import de.lemke.sudoku.data.codec.SudokuFileCodec
import de.lemke.sudoku.domain.model.Sudoku
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "ImportSudokuUseCase"

class ImportSudokuUseCase @Inject constructor(
    private val sudokuDocuments: SudokuDocuments,
    private val saveSudoku: SaveSudokuUseCase,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(uri: Uri?): Sudoku? =
        withContext(ioDispatcher) {
            if (uri == null) return@withContext null
            try {
                val sudoku = SudokuFileCodec.decode(sudokuDocuments.readBytes(uri), clock.zone)
                if (sudoku == null) Log.e(TAG, "Invalid sudoku file") else saveSudoku(sudoku)
                sudoku
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Error when reading file:", e)
                null
            }
        }
}
