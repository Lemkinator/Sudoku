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
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.sudoku.BuildConfig
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.domain.model.Sudoku
import io.kjson.JSONConfig
import io.kjson.stringifyJSON
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShareSudokuUseCase @Inject constructor(
    @param:ActivityContext private val context: Context,
) {
    @SuppressLint("Recycle")
    suspend operator fun invoke(sudoku: Sudoku): Uri =
        withContext(Dispatchers.IO) {
            val fileName = "Sudoku (${sudoku.sizeString} ${sudoku.difficulty.getLocalString(context.resources)}).sudoku"
            val file = File(context.cacheDir, fileName)
            val uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file)
            val json = sudokuToExport(sudoku).stringifyJSON(JSONConfig { includeNulls = true })
            context.contentResolver
                .openOutputStream(uri)!!
                .bufferedWriter()
                .use { bufferedWriter -> bufferedWriter.write(json) }
            return@withContext uri
        }
}
