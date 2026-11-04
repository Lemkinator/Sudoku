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
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject

/** What a picked document reports about itself before it is read. */
data class DocumentInfo(
    val exists: Boolean,
    val readable: Boolean,
    val mimeType: String?,
)

/** Reads picked documents. */
interface SudokuDocuments {
    /** Reads the document at [uri]; throws [FileNotFoundException] when its provider has no content for it. */
    fun readBytes(uri: Uri): ByteArray

    fun describe(uri: Uri): DocumentInfo
}

class DefaultSudokuDocuments @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SudokuDocuments {
    override fun readBytes(uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("No content for $uri")
        return stream.use { it.readBytes() }
    }

    override fun describe(uri: Uri): DocumentInfo {
        val document = DocumentFile.fromSingleUri(context, uri) ?: return DocumentInfo(exists = false, readable = false, mimeType = null)
        return DocumentInfo(exists = document.exists(), readable = document.canRead(), mimeType = document.type)
    }
}
