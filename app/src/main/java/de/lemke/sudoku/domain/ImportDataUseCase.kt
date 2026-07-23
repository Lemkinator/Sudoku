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
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.sudoku.R
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.data.database.sudokuFromExport
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.ProgressStyle.HORIZONTAL
import io.kjson.parseJSON
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.pwall.json.schema.JSONSchema
import de.lemke.commonutils.R as commonutilsR

class ImportDataUseCase @Inject constructor(
    @param:ActivityContext private val context: Context,
    private val sudokusRepository: SudokusRepository,
) {
    suspend operator fun invoke(origin: Uri): Unit =
        withContext(Dispatchers.Main) {
            val progressDialog = ProgressDialog(context)
            progressDialog.setCancelable(false)
            progressDialog.isIndeterminate = true
            progressDialog.max = 1
            progressDialog.setTitle(R.string.import_data)
            progressDialog.setMessage(context.getString(R.string.import_data_ongoing))
            progressDialog.setProgressStyle(HORIZONTAL)
            progressDialog.show()
            var result: String
            withContext(Dispatchers.IO) {
                val importFile = DocumentFile.fromSingleUri(context, origin)
                result =
                    if (importFile != null && importFile.exists() && importFile.canRead() && importFile.type == "application/json") {
                        if (importJson(importFile, progressDialog)) {
                            context.getString(R.string.import_data_success)
                        } else {
                            context.getString(R.string.import_data_error_no_valid_json)
                        }
                    } else {
                        context.getString(R.string.import_data_error_no_valid_file)
                    }
            }
            progressDialog.dismiss()
            AlertDialog
                .Builder(context)
                .setTitle(R.string.import_data)
                .setPositiveButton(commonutilsR.string.commonutils_ok, null)
                .setMessage(result)
                .show()
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun importJson(
        jsonFile: DocumentFile,
        progressDialog: ProgressDialog,
    ): Boolean {
        try {
            val schema =
                JSONSchema.parse(
                    context.assets
                        .open("schemas/sudoku_list.json")
                        .bufferedReader()
                        .use { it.readText() },
                )
            val json = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(jsonFile.uri))).readText()
            val output = schema.validateBasic(json)
            output.errors?.forEach { Log.e("ImportDataUseCase", "${it.error} - ${it.instanceLocation}") }
            return if (output.errors.isNullOrEmpty()) {
                val exportSudokus = json.parseJSON<List<SudokuExport>>()
                withContext(Dispatchers.Main) {
                    progressDialog.isIndeterminate = false
                    progressDialog.max = exportSudokus.size
                    progressDialog.progress = 0
                }
                val sudokus =
                    exportSudokus.mapNotNull { sudokuExport ->
                        withContext(Dispatchers.Main) { progressDialog.incrementProgressBy(1) }
                        sudokuFromExport(sudokuExport)
                    }
                withContext(Dispatchers.Main) {
                    progressDialog.progress = 0
                    progressDialog.setMessage(context.getString(R.string.import_data_ongoing_processing))
                }
                sudokus.forEach { sudoku ->
                    sudokusRepository.saveSudoku(sudoku)
                    withContext(Dispatchers.Main) { progressDialog.incrementProgressBy(1) }
                }
                true
            } else {
                Log.e("ImportDataUseCase", "JSON Schema validation failed")
                false
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("ImportDataUseCase", "Error when reading JSON file:", e)
            return false
        }
    }
}
