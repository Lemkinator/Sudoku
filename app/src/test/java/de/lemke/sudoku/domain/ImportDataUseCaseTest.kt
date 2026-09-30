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
import android.content.Context
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.data.database.AppDatabase
import de.lemke.sudoku.data.database.FieldDb
import de.lemke.sudoku.data.database.SudokuDao
import de.lemke.sudoku.data.database.SudokuDb
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.domain.model.DataImportResult
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import io.kjson.stringifyJSON
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowDialog
import de.lemke.commonutils.R as commonutilsR

/** `DocumentFile.fromSingleUri` resolves its checks only through a registered ContentProvider, not `file://`. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ImportDataUseCaseTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // An Application context never gets the manifest theme, and AppCompat dialogs refuse to inflate without it.
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
        openFailure: RuntimeException? = null,
        hasContent: Boolean = true,
    ): Uri {
        val file = temporaryFolder.newFile().apply { writeText(content) }
        val provider = FakeDocumentProvider(file, mimeType, exists, openFailure, hasContent)
        provider.attachInfo(context, ProviderInfo().apply { this.authority = authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        return Uri.parse("content://$authority/document/import")
    }

    private fun repositoryFailingInsertWith(failure: Exception): SudokusRepository =
        SudokusRepository(
            object : SudokuDao by database.sudokuDao() {
                override suspend fun insert(
                    sudoku: SudokuDb,
                    fields: List<FieldDb>,
                ) = throw failure
            },
        )

    @Test
    fun `imports and saves every sudoku from a schema-valid export`() {
        val sudoku1 = testSudoku(size = 4)
        val sudoku2 = testSudoku(size = 9, difficulty = Difficulty.EXPERT)
        val json = listOf(sudokuToExport(sudoku1), sudokuToExport(sudoku2)).stringifyJSON()
        val uri = registerDocument("import.valid", json)

        runTest { useCase(uri) shouldBe DataImportResult.Imported(skippedCount = 0) }

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
    }

    @Test
    fun `succeeds without saving anything for an empty export list`() {
        val uri = registerDocument("import.empty", "[]")

        runTest { useCase(uri) shouldBe DataImportResult.Imported(skippedCount = 0) }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
    }

    @Test
    fun `saves the readable sudokus and counts every entry the mapper rejects as skipped`() {
        val valid4 = testSudoku(size = 4)
        val valid9 = testSudoku(size = 9)
        val size5 = sudokuToExport(testSudoku(size = 4)).copy(size = 5)
        val size0 = sudokuToExport(testSudoku(size = 4)).copy(size = 0)
        val json = listOf(sudokuToExport(valid4), size5, sudokuToExport(valid9), size0).stringifyJSON()
        val uri = registerDocument("import.skipped", json)

        runTest { useCase(uri) shouldBe DataImportResult.Imported(skippedCount = 2) }

        runBlocking { sudokusRepository.getAllSudokus() }.map { it.id.value }.toSet() shouldBe setOf(valid4.id.value, valid9.id.value)
    }

    @Test
    fun `counts a single unsupported entry as one skipped and dismisses the progress dialog`() {
        val json = listOf(sudokuToExport(testSudoku(size = 16)), sudokuToExport(testSudoku(size = 4)).copy(size = 25)).stringifyJSON()
        val uri = registerDocument("import.oneskipped", json)

        runTest { useCase(uri) shouldBe DataImportResult.Imported(skippedCount = 1) }

        runBlocking { sudokusRepository.getAllSudokus() }.map { it.size } shouldBe listOf(16)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowDialog.getLatestDialog().isShowing.shouldBeFalse()
    }

    @Test
    fun `returns InvalidJson when the JSON does not match the export schema`() {
        val uri = registerDocument("import.badschema", "{}")

        runTest { useCase(uri) shouldBe DataImportResult.InvalidJson }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
    }

    @Test
    fun `returns InvalidFile when the resolved document has the wrong mime type`() {
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.wrongmime", json, mimeType = "text/plain")

        runTest { useCase(uri) shouldBe DataImportResult.InvalidFile }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
    }

    @Test
    fun `returns InvalidFile when the resolved document has no readable mime type`() {
        // DocumentsContractApi19.canRead() returns false for an empty MIME type before any type comparison.
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.nomime", json, mimeType = null)

        runTest { useCase(uri) shouldBe DataImportResult.InvalidFile }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
    }

    @Test
    fun `logs and reports failure instead of crashing when saving an imported sudoku throws`() {
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.savefails", json)
        val failingRepository = repositoryFailingInsertWith(RuntimeException("boom"))
        val throwingUseCase = ImportDataUseCase(context, failingRepository, UnconfinedTestDispatcher(), UnconfinedTestDispatcher())

        runTest { throwingUseCase(uri) shouldBe DataImportResult.InvalidJson }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
    }

    @Test
    fun `returns InvalidJson instead of crashing when the provider fails with an unexpected exception`() {
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.providerbug", json, openFailure = UnsupportedOperationException("provider bug"))

        runTest { useCase(uri) shouldBe DataImportResult.InvalidJson }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
    }

    @Test
    fun `returns InvalidJson when the document is not parseable JSON`() {
        val uri = registerDocument("import.malformed", "[{not json")

        runTest { useCase(uri) shouldBe DataImportResult.InvalidJson }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
    }

    @Test
    fun `returns InvalidJson when reading the document is denied`() {
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.denied", json, openFailure = SecurityException("Permission Denial"))

        runTest { useCase(uri) shouldBe DataImportResult.InvalidJson }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
    }

    @Test
    fun `returns InvalidJson when the provider opens the document without content`() {
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.nocontent", json, hasContent = false)

        runTest { useCase(uri) shouldBe DataImportResult.InvalidJson }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
    }

    @Test
    fun `rethrows a CancellationException from saving instead of reporting it as a failed import`() {
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.cancelled", json)
        val failingRepository = repositoryFailingInsertWith(CancellationException())
        val throwingUseCase = ImportDataUseCase(context, failingRepository, UnconfinedTestDispatcher(), UnconfinedTestDispatcher())

        shouldThrow<CancellationException> { runTest { throwingUseCase(uri) } }
    }

    @Test
    fun `returns InvalidFile when the document does not exist`() {
        val json = listOf(sudokuToExport(testSudoku())).stringifyJSON()
        val uri = registerDocument("import.missing", json, exists = false)

        runTest { useCase(uri) shouldBe DataImportResult.InvalidFile }

        runBlocking { sudokusRepository.getAllSudokus() }.shouldBeEmpty()
    }
}
