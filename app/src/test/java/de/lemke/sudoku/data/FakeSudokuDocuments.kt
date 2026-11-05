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

import android.net.Uri
import java.io.FileNotFoundException

/** Serves the documents added for a [Uri]. */
class FakeSudokuDocuments : SudokuDocuments {
    private val documents = mutableMapOf<Uri, Pair<DocumentInfo, ByteArray?>>()

    fun addDocument(
        uri: Uri,
        content: ByteArray?,
        info: DocumentInfo = DocumentInfo(exists = true, readable = true, mimeType = "application/octet-stream"),
    ) {
        documents[uri] = info to content
    }

    override fun readBytes(uri: Uri): ByteArray = documents[uri]?.second ?: throw FileNotFoundException("No content for $uri")

    override fun describe(uri: Uri): DocumentInfo = documents[uri]?.first ?: DocumentInfo(exists = false, readable = false, mimeType = null)
}
