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

import android.app.Application
import android.content.pm.ProviderInfo
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.testSudoku
import io.kjson.stringifyJSON
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ImportSudokuUseCaseTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val sudokusRepository = FakeSudokusRepository()
    private val dispatcher = UnconfinedTestDispatcher()
    private val useCase = ImportSudokuUseCase(context, SaveSudokuUseCase(sudokusRepository, dispatcher), dispatcher)

    private fun uriFor(content: String): Uri {
        val file = temporaryFolder.newFile("sudoku-import.json").apply { writeText(content) }
        return Uri.fromFile(file)
    }

    @Test
    fun `returns null and never saves for a null uri`() =
        runTest {
            useCase(null).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `imports a valid export, saves it and returns the mapped sudoku`() =
        runTest {
            val sudoku = testSudoku()
            val uri = uriFor(sudokuToExport(sudoku).stringifyJSON())

            val result = useCase(uri)

            result.shouldNotBeNull()
            result.id shouldBe sudoku.id
            result.fields.size shouldBe sudoku.fields.size
            sudokusRepository.saves.map { it.rows.sudoku.id to it.onlyUpdate } shouldBe listOf(sudoku.id.value to false)
        }

    @Test
    fun `returns null and never saves when content fails schema validation`() =
        runTest {
            val uri = uriFor("""{"foo":"bar"}""")

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `returns null and never saves when the mapped field count does not match size`() =
        runTest {
            val export = sudokuToExport(testSudoku())
            val uri = uriFor(export.copy(fields = export.fields.take(1)).stringifyJSON())

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `returns null instead of throwing when the file cannot be opened`() =
        runTest {
            val uri = Uri.fromFile(File(context.cacheDir, "does-not-exist-${System.nanoTime()}.json"))

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `returns null and never saves when the content is not parseable JSON`() =
        runTest {
            val uri = uriFor("{not json")

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `returns null when saving the imported sudoku fails in the database`() =
        runTest {
            val uri = uriFor(sudokuToExport(testSudoku()).stringifyJSON())
            sudokusRepository.saveFailure = SQLiteException("boom")

            useCase(uri).shouldBeNull()
        }

    @Test
    fun `returns null instead of crashing when the content provider throws an unexpected exception`() =
        runTest {
            val file = temporaryFolder.newFile("sudoku-import.json").apply { writeText(sudokuToExport(testSudoku()).stringifyJSON()) }
            val provider = FakeDocumentProvider(file, "application/json", true, UnsupportedOperationException("provider bug"))
            provider.attachInfo(context, ProviderInfo().apply { authority = "import.providerbug" })
            ShadowContentResolver.registerProviderInternal("import.providerbug", provider)
            val uri = Uri.parse("content://import.providerbug/document/import")

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `returns null and never saves when the content provider opens the uri without content`() =
        runTest {
            val file = temporaryFolder.newFile("sudoku-import.json").apply { writeText(sudokuToExport(testSudoku()).stringifyJSON()) }
            val provider = FakeDocumentProvider(file, "application/json", true, null, hasContent = false)
            provider.attachInfo(context, ProviderInfo().apply { authority = "import.nocontent" })
            ShadowContentResolver.registerProviderInternal("import.nocontent", provider)
            val uri = Uri.parse("content://import.nocontent/document/import")
            context.contentResolver.openInputStream(uri).shouldBeNull()

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `rethrows a CancellationException instead of swallowing it as a failed import`() =
        runTest {
            val sudoku = testSudoku()
            val uri = uriFor(sudokuToExport(sudoku).stringifyJSON())
            sudokusRepository.saveFailure = CancellationException()

            shouldThrow<CancellationException> { useCase(uri) }
        }
}
