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
import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupExport
import de.lemke.commonutils.backup.core.BackupFileExtension
import de.lemke.commonutils.backup.core.BackupReadResult
import de.lemke.commonutils.backup.core.BackupRefusal
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.backup.core.EntryCheck
import de.lemke.commonutils.backup.core.MergeableSection
import de.lemke.sudoku.data.database.AppDatabase
import de.lemke.sudoku.data.database.DefaultSudokusRepository
import de.lemke.sudoku.data.database.GameDao
import de.lemke.sudoku.data.database.GameEntity
import de.lemke.sudoku.data.database.toEntity
import de.lemke.sudoku.data.database.toLegacyModeLevel
import de.lemke.sudoku.di.BackupModule
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
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
        section = SudokuBackupSection(database.gameDao())
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultZone)
        database.close()
    }

    override fun section(): BackupSection<SudokuBackupEntry> = section

    override fun transaction(): BackupTransaction = transaction

    private fun storedGameCount(): Int =
        database.query("SELECT COUNT(*) FROM game", null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    private suspend fun store(vararg sudokus: Sudoku) = sudokus.forEach { database.gameDao().save(it.toEntity(ZoneId.systemDefault())) }

    @Test
    fun `clear removes every game`(): Unit =
        runBlocking {
            store(testSudoku(), testSudoku())

            section.clear()

            section.exportEntries().shouldBeEmpty()
            storedGameCount() shouldBe 0
        }

    @Test
    fun `an export leaves out a game whose header cannot be read`(): Unit =
        runBlocking {
            val unreadable = testSudoku().toEntity(ZoneId.systemDefault())
            database.gameDao().save(GameEntity(unreadable.header.copy(size = 6), unreadable.board))
            val readable = testSudoku()
            store(readable)

            section.exportEntries().map { it.id } shouldBe listOf(readable.info.id.value)
        }

    @Test
    fun `local entries find a game by id, day and size with level`(): Unit =
        runBlocking {
            val normal = testSudoku()
            val daily = testSudoku(mode = GameMode.Daily(LocalDate.of(2026, 5, 3)), created = LocalDateTime.of(2026, 5, 3, 7, 30))
            val level = testSudoku(mode = GameMode.Level(3))
            store(normal, daily, level, testSudoku(), testSudoku(mode = GameMode.Level(4)))
            val keys =
                setOf(
                    SudokuBackupKey.Normal(normal.info.id.value),
                    SudokuBackupKey.Daily(LocalDate.of(2026, 5, 3)),
                    SudokuBackupKey.Level(SudokuSize.FOUR, 3),
                    SudokuBackupKey.Normal("missing"),
                    SudokuBackupKey.Daily(LocalDate.of(2026, 5, 4)),
                    SudokuBackupKey.Level(SudokuSize.NINE, 3),
                )

            val local = section.localEntries(keys)

            local.mapValues { it.value.id } shouldBe
                mapOf(
                    SudokuBackupKey.Normal(normal.info.id.value) to normal.info.id.value,
                    SudokuBackupKey.Daily(LocalDate.of(2026, 5, 3)) to daily.info.id.value,
                    SudokuBackupKey.Level(SudokuSize.FOUR, 3) to level.info.id.value,
                )
        }

    @Test
    fun `local entries ignore daily and level games when no key asks for them`(): Unit =
        runBlocking {
            val normal = testSudoku()
            store(normal, testSudoku(mode = GameMode.Daily(LocalDate.of(2026, 5, 3))), testSudoku(mode = GameMode.Level(1)))

            val local = section.localEntries(setOf(SudokuBackupKey.Normal(normal.info.id.value)))

            local.keys shouldBe setOf(SudokuBackupKey.Normal(normal.info.id.value))
        }

    @Test
    fun `local entries answer more ids than one query holds`(): Unit =
        runBlocking {
            val stored = List(3) { testSudoku() }
            store(*stored.toTypedArray())
            val keys = stored.map { SudokuBackupKey.Normal(it.info.id.value) } + List(1200) { SudokuBackupKey.Normal("missing-$it") }

            section.localEntries(keys.toSet()).keys shouldBe stored.map { SudokuBackupKey.Normal(it.info.id.value) }.toSet()
        }

    @Test
    fun `replace swaps the local game for the merged one`(): Unit =
        runBlocking {
            val local = backupEntry(id = "local", modeLevel = -1, solvedCells = 2, dailyDate = LocalDate.parse("2026-05-02"))
            val merged = backupEntry(id = "merged", modeLevel = -1, solvedCells = 6, dailyDate = LocalDate.parse("2026-05-02"))
            section.insert(listOf(local))

            section.replace(local, merged)

            section.exportEntries() shouldBe listOf(merged)
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
            val dao = database.gameDao()
            val dispatcher = UnconfinedTestDispatcher()
            val queue =
                QueueSudokuSaveUseCase(
                    DefaultSudokusRepository(dao, Clock.systemDefaultZone()),
                    CoroutineScope(SupervisorJob() + dispatcher),
                    dispatcher,
                )
            var inserts = 0
            val failingDao =
                object : GameDao by dao {
                    override suspend fun save(game: GameEntity) {
                        check(++inserts < 2) { "disk full" }
                        dao.save(game)
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
            after.map { it.id }.toSet() shouldBe (before.map { it.id } + queued.info.id.value).toSet()
            after.filter { it.id != queued.info.id.value }.toSet() shouldBe before.toSet()
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
    fun `the section reads no format before its first readable version`() {
        shouldThrow<IllegalStateException> { section.upgrade(0, JsonObject(emptyMap())) }.message shouldBe
            "The sudokus section has no format before version 2"
    }

    @Test
    fun `an exported entry carries the stored board bytes`(): Unit =
        runBlocking {
            val sudoku = testSudoku()
            store(sudoku)

            val stored = database.gameDao().getById(sudoku.info.id.value).shouldNotBeNull()

            section.exportEntries().single().board shouldBe Base64.getEncoder().encodeToString(stored.board)
        }

    @Test
    fun `a restored entry stores the bytes of its board`(): Unit =
        runBlocking {
            val entry = backupEntry(id = "restored", solvedCells = 4)

            section.insert(listOf(entry))

            val stored = database.gameDao().getById("restored").shouldNotBeNull()
            Base64.getEncoder().encodeToString(stored.board) shouldBe entry.board
            section.exportEntries() shouldBe listOf(entry)
        }

    @Test
    fun `an entry with a broken board is invalid`() {
        section.validate(backupEntry().copy(board = "AAA=")) shouldBe EntryCheck.Invalid("The game is broken")
    }

    @Test
    fun `a version 1 section is refused`(): Unit =
        runBlocking {
            val former =
                object : MergeableSection<SudokuBackupEntry, SudokuBackupKey> by section {
                    override val version: Int = 1
                    override val minReadableVersion: Int = 1
                }
            val app = BackupApp("sudoku", 1, BackupFileExtension(".sudokubackup"))
            val written = BackupEngine(setOf(former), transaction, app, { "2026-10-30T12:00:00Z" }).export()
            val files = (written as BackupExport.Files).files.toMutableMap()

            BackupEngine(setOf(section), transaction, app, { "2026-10-30T12:00:00Z" }).read(files) shouldBe
                BackupReadResult.Refused(BackupRefusal.TooOldSection("sudokus", 1))
        }
}
