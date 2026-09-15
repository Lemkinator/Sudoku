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
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.R
import de.lemke.sudoku.data.database.AppDatabase
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import io.kjson.stringifyJSON
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowDialog
import de.lemke.commonutils.R as commonutilsR

/**
 * [DocumentFile.fromSingleUri][androidx.documentfile.provider.DocumentFile.fromSingleUri] only resolves
 * `exists()`/`canRead()`/`type` through a real [android.content.ContentProvider] registered for the
 * uri's authority (verified against the androidx.documentfile 1.1.0 sources) - a bare `file://` uri from
 * `Uri.fromFile` never satisfies those checks under Robolectric (no provider backs the "file" scheme), so
 * every fixture here is served by [FakeDocumentProvider], registered per-test via
 * [ShadowContentResolver.registerProviderInternal].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ImportDataUseCaseTest {
    // a bare Application context, unlike an Activity, never gets the manifest's android:theme
    // applied automatically - AppCompat dialogs need it set explicitly or they refuse to inflate.
    private val context: Context =
        ApplicationProvider.getApplicationContext<Context>().apply { setTheme(commonutilsR.style.CommonUtils_AppTheme) }
    private lateinit var database: AppDatabase
    private lateinit var sudokusRepository: SudokusRepository
    private lateinit var useCase: ImportDataUseCase

    @Before
    fun setUp() {
        val directExecutor = Executor { it.run() }
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor(directExecutor)
                .setTransactionExecutor(directExecutor)
                .build()
        sudokusRepository = SudokusRepository(database.sudokuDao())
        useCase = ImportDataUseCase(context, sudokusRepository, UnconfinedTestDispatcher(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun testSudoku(
        id: String = SudokuId.generate().value,
        size: Int = 4,
        difficulty: Difficulty = Difficulty.VERY_EASY,
    ): Sudoku =
        Sudoku.create(
            sudokuId = SudokuId(id),
            size = size,
            difficulty = difficulty,
            modeLevel = Sudoku.MODE_NORMAL,
            created = LocalDateTime.of(2024, 1, 1, 12, 0),
            updated = LocalDateTime.of(2024, 1, 1, 12, 5),
            seconds = 42,
            fields =
                MutableList(size * size) { index ->
                    val value = index % size + 1
                    Field(position = Position.create(index, size), solution = value, value = value, given = true)
                },
        )

    private fun registerDocument(
        authority: String,
        content: String,
        mimeType: String? = "application/json",
        exists: Boolean = true,
    ): Uri {
        val file = File.createTempFile("sudoku_import", ".json").apply { writeText(content) }
        val provider = FakeDocumentProvider(file, mimeType, exists)
        provider.attachInfo(context, ProviderInfo().apply { this.authority = authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        return Uri.parse("content://$authority/document/import")
    }

    private fun resultDialogMessage(): String? {
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        return dialog.findViewById<TextView>(android.R.id.message)?.text?.toString()
    }

    @Test
    fun `imports and saves every sudoku from a schema-valid export`() {
        val sudoku1 = testSudoku(size = 4)
        val sudoku2 = testSudoku(size = 9, difficulty = Difficulty.EXPERT)
        val json = listOf(sudokuToExport(sudoku1), sudokuToExport(sudoku2)).stringifyJSON()
        val uri = registerDocument("import.valid", json)

        runTest { useCase(uri) }

        val saved = runBlocking { sudokusRepository.getAllSudokus() }
        saved.map { it.id.value }.toSet() shouldBe setOf(sudoku1.id.value, sudoku2.id.value)
        val imported1 = saved.single { it.id == sudoku1.id }
        imported1.size shouldBe sudoku1.size
        imported1.difficulty shouldBe sudoku1.difficulty
        imported1.seconds shouldBe sudoku1.seconds
        imported1.fields.map { it.solution to it.value } shouldBe sudoku1.fields.map { it.solution to it.value }
        val imported2 = saved.single { it.id == sudoku2.id }
        imported2.size shouldBe sudoku2.size
        imported2.difficulty shouldBe sudoku2.difficulty
        resultDialogMessage() shouldBe context.getString(R.string.import_data_success)
    }

    @Test
    fun `succeeds without saving anything for an empty export list`() {
        val uri = registerDocument("import.empty", "[]")

        runTest { useCase(uri) }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
        resultDialogMessage() shouldBe context.getString(R.string.import_data_success)
    }

    @Test
    fun `shows the schema-validation error when the JSON does not match the export schema`() {
        val uri = registerDocument("import.badschema", "{}")

        runTest { useCase(uri) }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
        resultDialogMessage() shouldBe context.getString(R.string.import_data_error_no_valid_json)
    }

    @Test
    fun `shows the invalid-file error when the resolved document has the wrong mime type`() {
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.wrongmime", json, mimeType = "text/plain")

        runTest { useCase(uri) }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
        resultDialogMessage() shouldBe context.getString(R.string.import_data_error_no_valid_file)
    }

    @Test
    fun `shows the invalid-file error when the resolved document has no readable mime type`() {
        // DocumentsContractApi19.canRead() (androidx.documentfile 1.1.0) returns false once the raw MIME type is
        // empty, before the compound condition ever reaches the "application/json" comparison - a null mime type
        // from the provider is the only fixture that reaches canRead()'s false branch instead of the type mismatch.
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.nomime", json, mimeType = null)

        runTest { useCase(uri) }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
        resultDialogMessage() shouldBe context.getString(R.string.import_data_error_no_valid_file)
    }

    @Test
    fun `logs and reports failure instead of crashing when saving an imported sudoku throws`() {
        // A real repository has no natural way to make saveSudoku() throw, so this one case keeps a mock
        // repository purely to force the catch branch in ImportDataUseCase.importJson.
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.savefails", json)
        val throwingRepository = mockk<SudokusRepository>()
        coEvery { throwingRepository.saveSudoku(any(), any()) } throws RuntimeException("boom")
        val throwingUseCase = ImportDataUseCase(context, throwingRepository, UnconfinedTestDispatcher(), UnconfinedTestDispatcher())

        runTest { throwingUseCase(uri) }

        resultDialogMessage() shouldBe context.getString(R.string.import_data_error_no_valid_json)
    }

    @Test
    fun `rethrows a CancellationException from saving instead of reporting it as a failed import`() {
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.cancelled", json)
        val throwingRepository = mockk<SudokusRepository>()
        coEvery { throwingRepository.saveSudoku(any(), any()) } throws CancellationException()
        val throwingUseCase = ImportDataUseCase(context, throwingRepository, UnconfinedTestDispatcher(), UnconfinedTestDispatcher())

        shouldThrow<CancellationException> { runTest { throwingUseCase(uri) } }
    }

    @Test
    fun `shows the invalid-file error when the document does not exist`() {
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.missing", json, exists = false)

        runTest { useCase(uri) }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
        resultDialogMessage() shouldBe context.getString(R.string.import_data_error_no_valid_file)
    }
}

private class FakeDocumentProvider(
    private val file: File,
    private val mimeType: String?,
    private val exists: Boolean,
) : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val cursor = MatrixCursor(columns)
        if (exists) {
            cursor.addRow(
                columns.map {
                    when (it) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> "import"
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> mimeType
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> file.name
                        else -> null
                    }
                },
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = mimeType

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0
}
