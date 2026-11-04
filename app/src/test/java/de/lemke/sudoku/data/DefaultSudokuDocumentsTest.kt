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
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultSudokuDocumentsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val documents = DefaultSudokuDocuments(context)
    private val uri = Uri.parse("content://de.lemke.sudoku.test/document")

    private fun serve(content: ByteArray) = shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(content))

    @Test
    fun `reads a small document completely`() {
        val content = ByteArray(20_000) { (it % 251).toByte() }
        serve(content)

        documents.readBytes(uri).contentEquals(content) shouldBe true
    }

    @Test
    fun `reads a document of exactly the size limit`() {
        val content = ByteArray(MAX_DOCUMENT_BYTES) { 7 }
        serve(content)

        documents.readBytes(uri).size shouldBe 1_048_576
    }

    @Test
    fun `rejects a document one byte beyond the size limit`() {
        serve(ByteArray(MAX_DOCUMENT_BYTES + 1))

        shouldThrow<IOException> { documents.readBytes(uri) }.message shouldContain "1048576"
    }

    @Test
    fun `reads an empty document as no bytes`() {
        serve(ByteArray(0))

        documents.readBytes(uri).size shouldBe 0
    }
}
