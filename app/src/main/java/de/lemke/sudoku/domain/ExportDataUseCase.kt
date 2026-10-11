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
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.domain.model.DataExportResult
import de.lemke.sudoku.domain.model.ExportProgress
import io.kjson.stringifyJSON
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class ExportDataUseCase @Inject constructor(
    private val sudokuDocuments: SudokuDocuments,
    private val getAllSudokus: GetAllSudokusUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(
        destination: Uri,
        onProgress: (ExportProgress) -> Unit,
    ): DataExportResult =
        withContext(ioDispatcher) {
            onProgress(ExportProgress.Reading)
            val sudokus = getAllSudokus()
            onProgress(ExportProgress.Converting(done = 0, total = sudokus.size))
            val exportSudokus =
                sudokus.mapIndexed { index, sudoku ->
                    sudokuToExport(sudoku).also { onProgress(ExportProgress.Converting(done = index + 1, total = sudokus.size)) }
                }
            onProgress(ExportProgress.Writing)
            write(exportSudokus.stringifyJSON(), destination)
        }

    private fun write(
        content: String,
        destination: Uri,
    ): DataExportResult =
        runCatching {
            if (sudokuDocuments.writeText(destination, content)) DataExportResult.Written else DataExportResult.WriteFailed
        }.getOrElse { e ->
            Log.e("ExportDataUseCase", "Error when writing the export file:", e)
            DataExportResult.WriteFailed
        }
}
