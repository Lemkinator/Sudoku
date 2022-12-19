package de.lemke.sudoku.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.data.database.sudokuFromExport
import de.lemke.sudoku.domain.model.Sudoku
import io.kjson.parseJSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.pwall.json.schema.JSONSchema
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

class ImportSudokuUseCase @Inject constructor(
    @ActivityContext private val context: Context,
    private val saveSudoku: SaveSudokuUseCase,
) {
    suspend operator fun invoke(uri: Uri?): Sudoku? = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext null
        try {
            val schema = JSONSchema.parse(context.assets.open("schemas/sudoku.json").bufferedReader().use { it.readText() })
            val json = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri))).readText()
            val output = schema.validateBasic(json)
            output.errors?.forEach { Log.e("ImportSudokuUseCase", "${it.error} - ${it.instanceLocation}") }
            if (output.errors.isNullOrEmpty()) {
                val sudokuExport = json.parseJSON<SudokuExport>()
                if (sudokuExport != null) {
                    val sudoku = sudokuFromExport(sudokuExport)
                    saveSudoku(sudoku)
                    return@withContext sudoku
                }
            } else {
                Log.e("ImportDataUseCase", "JSON Schema validation failed")
            }
        } catch (e: Exception) {
            Log.e("ImportDataUseCase", "Error when reading file:", e)
        }
        return@withContext null
    }
}