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
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the documents added for a [Uri] and reads assets from the app's asset sources.
 * Records every successful write in [written].
 */
class FakeSudokuDocuments(
    private val assetsDir: File = File("src/main/assets"),
) : SudokuDocuments {
    private val documents = mutableMapOf<Uri, Pair<DocumentInfo, String?>>()
    private val unwritable = mutableSetOf<Uri>()
    val written = mutableMapOf<Uri, String>()

    fun addDocument(
        uri: Uri,
        content: String?,
        info: DocumentInfo = DocumentInfo(exists = true, readable = true, mimeType = "application/json"),
    ) {
        documents[uri] = info to content
    }

    fun refuseWrites(uri: Uri) {
        unwritable += uri
    }

    override fun readText(uri: Uri): String = documents[uri]?.second ?: throw FileNotFoundException("No content for $uri")

    override fun writeText(
        uri: Uri,
        text: String,
    ): Boolean {
        if (uri in unwritable) return false
        written[uri] = text
        return true
    }

    override fun readAsset(path: String): String = File(assetsDir, path).readText()

    override fun describe(uri: Uri): DocumentInfo = documents[uri]?.first ?: DocumentInfo(exists = false, readable = false, mimeType = null)
}
