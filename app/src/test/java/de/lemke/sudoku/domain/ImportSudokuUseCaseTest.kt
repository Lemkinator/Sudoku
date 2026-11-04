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
import de.lemke.sudoku.data.DefaultSudokuDocuments
import de.lemke.sudoku.data.codec.SudokuFileCodec
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.model.GameSource
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.testSudoku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
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
    private val clock = Clock.systemUTC()
    private val useCase =
        ImportSudokuUseCase(DefaultSudokuDocuments(context), SaveSudokuUseCase(sudokusRepository, dispatcher), clock, dispatcher)

    private fun uriFor(content: ByteArray): Uri {
        val file = temporaryFolder.newFile("sudoku-import.sudoku").apply { writeBytes(content) }
        return Uri.fromFile(file)
    }

    private fun fileOf(sudoku: Sudoku): ByteArray = SudokuFileCodec.encode(sudoku, clock.zone)

    @Test
    fun `returns null and never saves for a null uri`() =
        runTest {
            useCase(null).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `imports a shared file, saves it and returns the sudoku`() =
        runTest {
            val sudoku = testSudoku(created = LocalDateTime.of(2026, 11, 3, 12, 0, 5))
            val uri = uriFor(fileOf(sudoku))

            useCase(uri) shouldBe sudoku

            sudokusRepository.saves shouldBe listOf(sudoku)
        }

    @Test
    fun `saves a solved file as an imported game`() =
        runTest {
            val solved = testSudoku(created = LocalDateTime.of(2026, 11, 3, 12, 0, 5), completed = true)
            val uri = uriFor(fileOf(solved))

            useCase(uri) shouldBe solved.copy(info = solved.info.copy(source = GameSource.IMPORTED))

            sudokusRepository.saves.map { it.info.source } shouldBe listOf(GameSource.IMPORTED)
        }

    @Test
    fun `saves an unfinished file as the player's own game`() =
        runTest {
            val uri = uriFor(fileOf(testSudoku()))

            useCase(uri).shouldNotBeNull().info.source shouldBe GameSource.OWN

            sudokusRepository.saves.map { it.info.source } shouldBe listOf(GameSource.OWN)
        }

    @Test
    fun `returns null and never saves for a file in the former JSON format`() =
        runTest {
            val uri =
                uriFor(
                    (
                        """{"id":"old-1","size":4,"difficulty":1,"modeLevel":0,"created":"2026-11-03T12:00:00",""" +
                            """"updated":"2026-11-03T12:00:00","seconds":0,"fields":[{"index":0,"solution":1,"value":1,"given":true}]}"""
                    ).toByteArray(),
                )

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `returns null and never saves for a file without a board`() =
        runTest {
            val uri = uriFor(byteArrayOf(0x08, 0x01, 0x12, 0x03, 0x61, 0x62, 0x63))

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `returns null instead of throwing when the file cannot be opened`() =
        runTest {
            val uri = Uri.fromFile(File(context.cacheDir, "does-not-exist-${System.nanoTime()}.sudoku"))

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `returns null and never saves when the content is no sudoku file`() =
        runTest {
            val uri = uriFor("{not json".toByteArray())

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `returns null when saving the imported sudoku fails in the database`() =
        runTest {
            val uri = uriFor(fileOf(testSudoku()))
            sudokusRepository.saveFailure = SQLiteException("boom")

            useCase(uri).shouldBeNull()
        }

    @Test
    fun `returns null instead of crashing when the content provider throws an unexpected exception`() =
        runTest {
            val file = temporaryFolder.newFile("sudoku-import.sudoku").apply { writeBytes(fileOf(testSudoku())) }
            val provider = FakeDocumentProvider(file, "application/sudoku", true, UnsupportedOperationException("provider bug"))
            provider.attachInfo(context, ProviderInfo().apply { authority = "import.providerbug" })
            ShadowContentResolver.registerProviderInternal("import.providerbug", provider)
            val uri = Uri.parse("content://import.providerbug/document/import")

            useCase(uri).shouldBeNull()

            sudokusRepository.saves.shouldBeEmpty()
        }

    @Test
    fun `returns null and never saves when the content provider opens the uri without content`() =
        runTest {
            val file = temporaryFolder.newFile("sudoku-import.sudoku").apply { writeBytes(fileOf(testSudoku())) }
            val provider = FakeDocumentProvider(file, "application/sudoku", true, null, hasContent = false)
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
            val uri = uriFor(fileOf(testSudoku()))
            sudokusRepository.saveFailure = CancellationException()

            shouldThrow<CancellationException> { useCase(uri) }
        }
}
