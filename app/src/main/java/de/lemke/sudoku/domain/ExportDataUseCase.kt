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
        val dialog = ProgressDialog(context)
        dialog.setCancelable(false)
        dialog.setTitle(R.string.export_data)
        dialog.setMessage(context.getString(R.string.export_data_ongoing))
        dialog.show()
        val resultDialog = AlertDialog.Builder(context)
            .setTitle(R.string.export_data)
            .setPositiveButton(R.string.ok, null)
            .create()
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.GERMANY).format(Date())
            val jsonFile = DocumentFile.fromTreeUri(context, destination)!!
                .createFile("application/json", "sudoku_export_$timestamp")
            context.contentResolver.openOutputStream(jsonFile!!.uri)!!.bufferedWriter()
                .use { bufferedWriter -> bufferedWriter.write(getAllSudokus().map { sudokuToExport(it) }.stringifyJSON()) }
            resultDialog.setMessage(context.getString(R.string.export_data_success))
        }
        dialog.dismiss()
        resultDialog.show()
    }
}


