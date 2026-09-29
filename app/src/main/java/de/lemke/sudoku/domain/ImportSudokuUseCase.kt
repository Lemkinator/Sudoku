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
import android.database.SQLException
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.sudoku.data.database.sudokuFromExport
import de.lemke.sudoku.domain.model.Sudoku
import io.kjson.JSONException
import io.kjson.parseJSON
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.pwall.json.schema.JSONSchema

class ImportSudokuUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val saveSudoku: SaveSudokuUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(uri: Uri?): Sudoku? =
        withContext(ioDispatcher) {
            if (uri == null) return@withContext null
            try {
                val schema =
                    JSONSchema.parse(
                        context.assets
                            .open("schemas/sudoku.json")
                            .bufferedReader()
                            .use { it.readText() },
                    )
                val json =
                    context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() } ?: throw FileNotFoundException("No content for $uri")
                val output = schema.validateBasic(json)
                output.errors?.forEach { Log.e("ImportSudokuUseCase", "${it.error} - ${it.instanceLocation}") }
                if (output.errors.isNullOrEmpty()) {
                    val sudoku = sudokuFromExport(json.parseJSON())
                    if (sudoku == null) {
                        Log.e("ImportDataUseCase", "Invalid Sudoku")
                        return@withContext null
                    }
                    saveSudoku(sudoku)
                    return@withContext sudoku
                } else {
                    Log.e("ImportDataUseCase", "JSON Schema validation failed")
                }
            } catch (e: IOException) {
                Log.e("ImportDataUseCase", "Error when reading file:", e)
            } catch (e: SecurityException) {
                Log.e("ImportDataUseCase", "No permission to read file:", e)
            } catch (e: JSONException) {
                Log.e("ImportDataUseCase", "Error when parsing file:", e)
            } catch (e: SQLException) {
                Log.e("ImportDataUseCase", "Error when saving imported sudoku:", e)
            }
            return@withContext null
        }
}
