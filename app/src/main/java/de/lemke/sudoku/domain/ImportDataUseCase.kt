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

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.data.database.sudokuFromExport
import de.lemke.sudoku.domain.model.DataImportResult
import de.lemke.sudoku.domain.model.ImportProgress
import io.kjson.parseJSON
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.pwall.json.schema.JSONSchema

class ImportDataUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sudokusRepository: SudokusRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(
        origin: Uri,
        onProgress: (ImportProgress) -> Unit,
    ): DataImportResult =
        withContext(ioDispatcher) {
            onProgress(ImportProgress.Reading)
            val importFile = DocumentFile.fromSingleUri(context, origin)
            if (importFile != null && importFile.exists() && importFile.canRead() && importFile.type == "application/json") {
                importJson(importFile, onProgress)
            } else {
                DataImportResult.InvalidFile
            }
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun importJson(
        jsonFile: DocumentFile,
        onProgress: (ImportProgress) -> Unit,
    ): DataImportResult =
        try {
            val schema =
                JSONSchema.parse(
                    context.assets
                        .open("schemas/sudoku_list.json")
                        .bufferedReader()
                        .use { it.readText() },
                )
            val json =
                context.contentResolver
                    .openInputStream(jsonFile.uri)
                    ?.bufferedReader()
                    ?.use { it.readText() } ?: throw FileNotFoundException("No content for ${jsonFile.uri}")
            val output = schema.validateBasic(json)
            output.errors?.forEach { Log.e("ImportDataUseCase", "${it.error} - ${it.instanceLocation}") }
            if (output.errors.isNullOrEmpty()) {
                val exportSudokus = json.parseJSON<List<SudokuExport>>()
                onProgress(ImportProgress.Parsing(done = 0, total = exportSudokus.size))
                val sudokus =
                    exportSudokus.mapIndexedNotNull { index, sudokuExport ->
                        sudokuFromExport(sudokuExport).also {
                            onProgress(ImportProgress.Parsing(done = index + 1, total = exportSudokus.size))
                        }
                    }
                onProgress(ImportProgress.Saving(done = 0, total = sudokus.size))
                sudokus.forEachIndexed { index, sudoku ->
                    sudokusRepository.saveSudoku(sudoku)
                    onProgress(ImportProgress.Saving(done = index + 1, total = sudokus.size))
                }
                DataImportResult.Imported(skippedCount = exportSudokus.size - sudokus.size)
            } else {
                Log.e("ImportDataUseCase", "JSON Schema validation failed")
                DataImportResult.InvalidJson
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("ImportDataUseCase", "Error when reading JSON file:", e)
            DataImportResult.InvalidJson
        }
}
