package de.lemke.sudoku.domain

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.sudoku.R
import de.lemke.sudoku.data.database.sudokuToExport
import dev.oneuiproject.oneui.dialog.ProgressDialog
import io.kjson.stringifyJSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


class ExportDataUseCase @Inject constructor(
    @ActivityContext private val context: Context,
    private val getAllSudokus: GetAllSudokusUseCase
) {
    @SuppressLint("Recycle")
    suspend operator fun invoke(destination: Uri) = withContext(Dispatchers.Main) {
        val progressDialog = ProgressDialog(context)
        progressDialog.setCancelable(false)
        progressDialog.isIndeterminate = true
        progressDialog.max = 1
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.setTitle(R.string.export_data)
        progressDialog.setMessage(context.getString(R.string.export_data_ongoing))
        progressDialog.show()
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.GERMANY).format(Date())
            val jsonFile = DocumentFile.fromTreeUri(context, destination)!!
                .createFile("application/json", "sudoku_export_$timestamp")
            val sudokus = getAllSudokus()
            withContext(Dispatchers.Main) {
                progressDialog.isIndeterminate = false
                progressDialog.max = sudokus.size
                progressDialog.progress = 0
            }
            val exportSudokus = sudokus.map { sudoku ->
                withContext(Dispatchers.Main) { progressDialog.incrementProgressBy(1) }
                sudokuToExport(sudoku)
            }
            withContext(Dispatchers.Main) {
                progressDialog.isIndeterminate = true
                progressDialog.max = 1
                progressDialog.setMessage(context.getString(R.string.export_data_ongoing_writing_file))
            }
            val exportString = exportSudokus.stringifyJSON()
            context.contentResolver.openOutputStream(jsonFile!!.uri)!!.bufferedWriter()
                .use { bufferedWriter -> bufferedWriter.write(exportString) }
        }
        progressDialog.dismiss()
        AlertDialog.Builder(context)
            .setTitle(R.string.export_data)
            .setMessage(context.getString(R.string.export_data_success))
            .setPositiveButton(de.lemke.commonutils.R.string.ok, null)
            .show()
    }
}


