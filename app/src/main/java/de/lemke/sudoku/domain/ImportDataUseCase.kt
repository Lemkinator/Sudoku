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
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.sudoku.R
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.data.database.sudokuFromExport
import de.lemke.sudoku.domain.model.DataImportResult
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.ProgressStyle.HORIZONTAL
import io.kjson.parseJSON
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.pwall.json.schema.JSONSchema

class ImportDataUseCase @Inject constructor(
    @param:ActivityContext private val context: Context,
    private val sudokusRepository: SudokusRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(origin: Uri): DataImportResult =
        withContext(mainDispatcher) {
            val progressDialog = ProgressDialog(context)
            progressDialog.setCancelable(false)
            progressDialog.isIndeterminate = true
            progressDialog.max = 1
            progressDialog.setTitle(R.string.import_data)
            progressDialog.setMessage(context.getString(R.string.import_data_ongoing))
            progressDialog.setProgressStyle(HORIZONTAL)
            progressDialog.show()
            val result =
                withContext(ioDispatcher) {
                    val importFile = DocumentFile.fromSingleUri(context, origin)
                    if (importFile != null && importFile.exists() && importFile.canRead() && importFile.type == "application/json") {
                        importJson(importFile, progressDialog)
                    } else {
                        DataImportResult.InvalidFile
                    }
                }
            progressDialog.dismiss()
            result
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun importJson(
        jsonFile: DocumentFile,
        progressDialog: ProgressDialog,
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
                withContext(mainDispatcher) {
                    progressDialog.isIndeterminate = false
                    progressDialog.max = exportSudokus.size
                    progressDialog.progress = 0
                }
                val sudokus =
                    exportSudokus.mapNotNull { sudokuExport ->
                        withContext(mainDispatcher) { progressDialog.incrementProgressBy(1) }
                        sudokuFromExport(sudokuExport)
                    }
                withContext(mainDispatcher) {
                    progressDialog.progress = 0
                    progressDialog.setMessage(context.getString(R.string.import_data_ongoing_processing))
                }
                sudokus.forEach { sudoku ->
                    sudokusRepository.saveSudoku(sudoku)
                    withContext(mainDispatcher) { progressDialog.incrementProgressBy(1) }
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
