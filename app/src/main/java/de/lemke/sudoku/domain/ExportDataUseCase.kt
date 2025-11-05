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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR


class ExportDataUseCase @Inject constructor(
    @param:ActivityContext private val context: Context,
    private val getAllSudokus: GetAllSudokusUseCase,
) {
    @SuppressLint("Recycle")
    suspend operator fun invoke(destination: Uri): Unit = withContext(Dispatchers.Main) {
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
            val exportSudokus = sudokus.map { sudoku ->
                withContext(Dispatchers.Main) { dialog.incrementProgressBy(1) }
                sudokuToExport(sudoku)
            }
            withContext(Dispatchers.Main) {
                dialog.isIndeterminate = true
                dialog.max = 1
                dialog.setMessage(context.getString(R.string.export_data_ongoing_writing_file))
            }
            val exportString = exportSudokus.stringifyJSON()
            context.contentResolver.openOutputStream(destination)!!.bufferedWriter()
                .use { bufferedWriter -> bufferedWriter.write(exportString) }
        }
        dialog.dismiss()
        AlertDialog.Builder(context)
            .setTitle(R.string.export_data)
            .setMessage(context.getString(R.string.export_data_success))
            .setPositiveButton(commonutilsR.string.commonutils_ok, null)
            .show()
    }
}


