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

package de.lemke.sudoku.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.sudoku.BuildConfig
import de.lemke.sudoku.domain.model.Sudoku
import java.io.File
import javax.inject.Inject

/** Writes a shared sudoku to a file other apps can read. */
interface SudokuShareFiles {
    /** Writes [json] to a file named after [sudoku] and returns its content [Uri]. */
    suspend fun write(
        sudoku: Sudoku,
        json: String,
    ): Uri
}

class DefaultSudokuShareFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SudokuShareFiles {
    override suspend fun write(
        sudoku: Sudoku,
        json: String,
    ): Uri {
        val size = sudoku.size.getLocalString(context.resources)
        val fileName = "Sudoku ($size ${sudoku.difficulty.getLocalString(context.resources)}).sudoku"
        val file = File(context.cacheDir, fileName)
        val uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file)
        context.contentResolver
            .openOutputStream(uri)!!
            .bufferedWriter()
            .use { bufferedWriter -> bufferedWriter.write(json) }
        return uri
    }
}
