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
import io.kjson.parseJSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.pwall.json.schema.JSONSchema
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

class ImportDataUseCase @Inject constructor(
    @ActivityContext private val context: Context,
    private val sudokusRepository: SudokusRepository,
) {
    suspend operator fun invoke(origin: Uri) = withContext(Dispatchers.Main) {
        val progressDialog = ProgressDialog(context)
        progressDialog.setCancelable(false)
        progressDialog.isIndeterminate = true
        progressDialog.max = 1
        progressDialog.setTitle(R.string.import_data)
        progressDialog.setMessage(context.getString(R.string.import_data_ongoing))
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.show()
        var result: String
        withContext(Dispatchers.IO) {
            val importFile = DocumentFile.fromSingleUri(context, origin)
            result = if (importFile != null && importFile.exists() && importFile.canRead() && importFile.type == "application/json") {
                if (importJson(importFile, progressDialog)) context.getString(R.string.import_data_success)
                else context.getString(R.string.import_data_error_no_valid_json)
            } else context.getString(R.string.import_data_error_no_valid_file)
        }
        progressDialog.dismiss()
        AlertDialog.Builder(context)
            .setTitle(R.string.import_data)
            .setPositiveButton(de.lemke.commonutils.R.string.ok, null)
            .setMessage(result)
            .show()
    }

    private suspend fun importJson(jsonFile: DocumentFile, progressDialog: ProgressDialog): Boolean {
        try {
            val schema = JSONSchema.parse(context.assets.open("schemas/sudoku_list.json").bufferedReader().use { it.readText() })
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
                val sudokus = exportSudokus.mapNotNull { sudokuExport ->
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
            Log.e("ImportDataUseCase", "Error when reading JSON file:", e)
            return false
        }
    }
}