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

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.sudoku.R
import de.lemke.sudoku.data.database.sudokuToExport
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.ProgressStyle.HORIZONTAL
import io.kjson.stringifyJSON
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.lemke.commonutils.R as commonutilsR

class ExportDataUseCase @Inject constructor(
    @param:ActivityContext private val context: Context,
    private val getAllSudokus: GetAllSudokusUseCase,
) {
    @SuppressLint("Recycle")
    suspend operator fun invoke(destination: Uri): Unit =
        withContext(Dispatchers.Main) {
            val dialog = ProgressDialog(context)
            dialog.setCancelable(false)
            dialog.isIndeterminate = true
            dialog.max = 1
            dialog.setProgressStyle(HORIZONTAL)
            dialog.setTitle(R.string.export_data)
            dialog.setMessage(context.getString(R.string.export_data_ongoing))
            dialog.show()
            withContext(Dispatchers.IO) {
                val sudokus = getAllSudokus()
                withContext(Dispatchers.Main) {
                    dialog.isIndeterminate = false
                    dialog.max = sudokus.size
                    dialog.progress = 0
                }
                val exportSudokus =
                    sudokus.map { sudoku ->
                        withContext(Dispatchers.Main) { dialog.incrementProgressBy(1) }
                        sudokuToExport(sudoku)
                    }
                withContext(Dispatchers.Main) {
                    dialog.isIndeterminate = true
                    dialog.max = 1
                    dialog.setMessage(context.getString(R.string.export_data_ongoing_writing_file))
                }
                val exportString = exportSudokus.stringifyJSON()
                context.contentResolver
                    .openOutputStream(destination)!!
                    .bufferedWriter()
                    .use { bufferedWriter -> bufferedWriter.write(exportString) }
            }
            dialog.dismiss()
            AlertDialog
                .Builder(context)
                .setTitle(R.string.export_data)
                .setMessage(context.getString(R.string.export_data_success))
                .setPositiveButton(commonutilsR.string.commonutils_ok, null)
                .show()
        }
}
