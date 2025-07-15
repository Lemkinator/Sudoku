package de.lemke.sudoku.domain

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.sudoku.BuildConfig
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.domain.model.Sudoku
import io.kjson.JSONConfig
import io.kjson.stringifyJSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ShareSudokuUseCase @Inject constructor(
    @param:ActivityContext private val context: Context,
) {
    @SuppressLint("Recycle")
    suspend operator fun invoke(sudoku: Sudoku): Uri = withContext(Dispatchers.IO) {
        val fileName = "Sudoku (${sudoku.sizeString} ${sudoku.difficulty.getLocalString(context.resources)}).sudoku"
        val file = File(context.cacheDir, fileName)
        val uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file)
        val json = sudokuToExport(sudoku).stringifyJSON(JSONConfig { includeNulls = true })
        context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { bufferedWriter -> bufferedWriter.write(json) }
        return@withContext uri
    }
}


