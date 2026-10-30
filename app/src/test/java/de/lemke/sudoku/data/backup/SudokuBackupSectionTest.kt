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

package de.lemke.sudoku.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.backup.DirectBackupTransaction
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.backup.core.EntryCheck
import de.lemke.sudoku.data.database.AppDatabase
import de.lemke.sudoku.data.database.DefaultSudokusRepository
import de.lemke.sudoku.data.database.FieldDb
import de.lemke.sudoku.data.database.SudokuDao
import de.lemke.sudoku.data.database.SudokuDb
import de.lemke.sudoku.data.database.fieldToDb
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import de.lemke.sudoku.di.BackupModule
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val BERLIN = ZoneId.of("Europe/Berlin")

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SudokuBackupSectionTest : SudokuBackupSectionContract() {
    private lateinit var database: AppDatabase
    private lateinit var section: SudokuBackupSection
    private val transaction = DirectBackupTransaction()
    private lateinit var defaultZone: TimeZone

    @Before
    fun setUp() {
        val directExecutor = Executor { it.run() }
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor(directExecutor)
                .setTransactionExecutor(directExecutor)
                .build()
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(BERLIN))
        section = SudokuBackupSection(database.sudokuDao())
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultZone)
        database.close()
    }

    override fun section(): BackupSection<SudokuBackupEntry> = section

    override fun transaction(): BackupTransaction = transaction

    private fun storedFieldCount(): Int =
        database.query("SELECT COUNT(*) FROM field", null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    private suspend fun store(vararg sudokus: Sudoku) =
        sudokus.forEach {
            val rows = sudokuWithFieldsToDb(it)
            database.sudokuDao().insert(rows.sudoku, rows.fields)
        }

    @Test
    fun `clear removes every game and its fields`(): Unit =
        runBlocking {
            store(testSudoku(), testSudoku())

            section.clear()

            section.exportEntries().shouldBeEmpty()
            storedFieldCount() shouldBe 0
        }

    @Test
    fun `an export leaves out a game that cannot be read`(): Unit =
        runBlocking {
            val unreadable = sudokuWithFieldsToDb(testSudoku())
            database.sudokuDao().insert(unreadable.sudoku, unreadable.fields.drop(1))
            val readable = testSudoku()
            store(readable)

            section.exportEntries().map { it.id } shouldBe listOf(readable.id.value)
        }

    @Test
    fun `an export leaves out a game with a solution that has no digit`(): Unit =
        runBlocking {
            val sudoku = testSudoku()
            val rows = sudokuWithFieldsToDb(sudoku)
            database.sudokuDao().insert(rows.sudoku, listOf(fieldToDb(sudoku[0], sudoku.id).copy(solution = 0)) + rows.fields.drop(1))
            val readable = testSudoku()
            store(readable)

            section.exportEntries().map { it.id } shouldBe listOf(readable.id.value)
        }

    @Test
    fun `local entries find a game by id, day and size with level`(): Unit =
        runBlocking {
            val normal = testSudoku()
            val daily = testSudoku(modeLevel = Sudoku.MODE_DAILY, created = LocalDateTime.of(2026, 5, 3, 7, 30))
            val level = testSudoku(modeLevel = 3)
            store(normal, daily, level, testSudoku(), testSudoku(modeLevel = 4))
            val keys =
                setOf(
                    SudokuBackupKey.Normal(normal.id.value),
                    SudokuBackupKey.Daily(LocalDate.of(2026, 5, 3)),
                    SudokuBackupKey.Level(SudokuSize.FOUR, 3),
                    SudokuBackupKey.Normal("missing"),
                    SudokuBackupKey.Daily(LocalDate.of(2026, 5, 4)),
                    SudokuBackupKey.Level(SudokuSize.NINE, 3),
                )

            val local = section.localEntries(keys)

            local.mapValues { it.value.id } shouldBe
                mapOf(
                    SudokuBackupKey.Normal(normal.id.value) to normal.id.value,
                    SudokuBackupKey.Daily(LocalDate.of(2026, 5, 3)) to daily.id.value,
                    SudokuBackupKey.Level(SudokuSize.FOUR, 3) to level.id.value,
                )
        }

    @Test
    fun `local entries ignore daily and level games when no key asks for them`(): Unit =
        runBlocking {
            val normal = testSudoku()
            store(normal, testSudoku(modeLevel = Sudoku.MODE_DAILY), testSudoku(modeLevel = 1))

            val local = section.localEntries(setOf(SudokuBackupKey.Normal(normal.id.value)))

            local.keys shouldBe setOf(SudokuBackupKey.Normal(normal.id.value))
        }

    @Test
    fun `local entries answer more ids than one query holds`(): Unit =
        runBlocking {
            val stored = List(3) { testSudoku() }
            store(*stored.toTypedArray())
            val keys = stored.map { SudokuBackupKey.Normal(it.id.value) } + List(1200) { SudokuBackupKey.Normal("missing-$it") }

            section.localEntries(keys.toSet()).keys shouldBe stored.map { SudokuBackupKey.Normal(it.id.value) }.toSet()
        }

    @Test
    fun `replace swaps the local game for the merged one`(): Unit =
        runBlocking {
            val local = backupEntry(id = "local", modeLevel = Sudoku.MODE_DAILY, solvedCells = 2)
            val merged = backupEntry(id = "merged", modeLevel = Sudoku.MODE_DAILY, solvedCells = 6)
            section.insert(listOf(local))

            section.replace(local, merged)

            section.exportEntries() shouldBe listOf(merged)
        }

    @Test
    fun `a game with a given cell off its solution survives an export and an import`(): Unit =
        runBlocking {
            val sudoku = testSudoku()
            sudoku[0].value = if (sudoku[0].solution == 1) 2 else 1
            store(sudoku)
            val exported = section.exportEntries()

            section.clear()
            section.insert(exported)

            exported.map { it.id } shouldBe listOf(sudoku.id.value)
            section.exportEntries() shouldBe exported
            database
                .sudokuDao()
                .getById(sudoku.id.value)
                .shouldNotBeNull()
                .fields
                .first { it.index == 0 }
                .value shouldBe sudoku[0].value
        }

    @Test
    fun `an export stamps a game with the zone of the device at the time of the call`(): Unit =
        runBlocking {
            store(testSudoku(created = LocalDateTime.of(2026, 5, 2, 8, 0)))

            section
                .exportEntries()
                .single()
                .created.offset shouldBe ZoneOffset.ofHours(2)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            section
                .exportEntries()
                .single()
                .created.offset shouldBe ZoneOffset.ofHours(-4)
        }

    @Test
    fun `a replace that fails partway rolls back and lets a queued save run`(): Unit =
        runBlocking {
            val kept = listOf(testSudoku(), testSudoku())
            store(*kept.toTypedArray())
            val before = section.exportEntries()
            val dao = database.sudokuDao()
            val dispatcher = UnconfinedTestDispatcher()
            val queue = QueueSudokuSaveUseCase(DefaultSudokusRepository(dao), CoroutineScope(SupervisorJob() + dispatcher), dispatcher)
            var inserts = 0
            val failingDao =
                object : SudokuDao by dao {
                    override suspend fun insert(
                        sudoku: SudokuDb,
                        fields: List<FieldDb>,
                    ) {
                        check(++inserts < 2) { "disk full" }
                        dao.insert(sudoku, fields)
                    }
                }
            val failing = SudokuBackupSection(failingDao)
            val queued = testSudoku()
            lateinit var queuedSave: Deferred<Unit>

            shouldThrow<IllegalStateException> {
                BackupModule.provideBackupTransaction(database, queue).run {
                    failing.clear()
                    queuedSave = queue(queued)
                    failing.insert(listOf(backupEntry(id = "a"), backupEntry(id = "b"), backupEntry(id = "c")))
                }
            }.message shouldBe "disk full"
            queuedSave.await()

            val after = section.exportEntries()
            after.map { it.id }.toSet() shouldBe (before.map { it.id } + queued.id.value).toSet()
            after.filter { it.id != queued.id.value }.toSet() shouldBe before.toSet()
        }

    @Test
    fun `a valid entry passes validation`() {
        section.validate(backupEntry()) shouldBe EntryCheck.Valid
    }

    @Test
    fun `a broken entry fails validation`() {
        section.validate(backupEntry().copy(size = 5)) shouldBe EntryCheck.Invalid("The game is broken")
    }

    @Test
    fun `the section reads no format before its first version`() {
        shouldThrow<IllegalStateException> { section.upgrade(0, JsonObject(emptyMap())) }.message shouldBe
            "The sudokus section has no format before version 1"
    }
}
