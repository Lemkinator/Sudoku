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

import de.lemke.commonutils.backup.core.EntryCheck
import de.lemke.commonutils.backup.core.MergeableSection
import de.lemke.commonutils.backup.core.encodedOrder
import de.lemke.commonutils.backup.core.higherRankWins
import de.lemke.commonutils.backup.core.newerWins
import de.lemke.sudoku.data.database.SudokuDao
import de.lemke.sudoku.data.database.SudokuWithFields
import de.lemke.sudoku.data.database.sudokuFromDb
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

/** All saved games. A normal sudoku clashes by id, a daily one by day and a level one by size and level. */
class SudokuBackupSection @Inject constructor(
    private val dao: SudokuDao,
    private val clock: Clock,
) : MergeableSection<SudokuBackupEntry, SudokuBackupKey> {
    override val id: String = ID
    override val version: Int = 1
    override val minReadableVersion: Int = 1
    override val serializer: KSerializer<SudokuBackupEntry> = SudokuBackupEntry.serializer()

    private val newest = newerWins<SudokuBackupEntry, Instant>({ it.updated.toInstant() }, encodedOrder(serializer))
    private val furthest =
        higherRankWins<SudokuBackupEntry, Int, Instant>(
            { progressOf(it, clock.zone) },
            { it.updated.toInstant() },
            encodedOrder(serializer),
        )

    override fun upgrade(
        fromVersion: Int,
        entry: JsonObject,
    ): JsonObject = error("The sudokus section has no format before version $version")

    override fun validate(entry: SudokuBackupEntry): EntryCheck =
        if (sudokuFromBackup(entry, clock.zone) == null) EntryCheck.Invalid("The game is broken") else EntryCheck.Valid

    override suspend fun exportEntries(): List<SudokuBackupEntry> = dao.getAll().mapNotNull(::entryOf)

    override suspend fun clear() = dao.deleteAll()

    override suspend fun insert(entries: List<SudokuBackupEntry>) {
        entries.mapNotNull { sudokuFromBackup(it, clock.zone) }.forEach {
            val rows = sudokuWithFieldsToDb(it)
            dao.insert(rows.sudoku, rows.fields)
        }
    }

    override fun key(entry: SudokuBackupEntry): SudokuBackupKey = entry.key()

    override fun resolve(
        local: SudokuBackupEntry,
        incoming: SudokuBackupEntry,
    ): SudokuBackupEntry =
        when (key(local)) {
            is SudokuBackupKey.Normal -> newest(local, incoming)
            is SudokuBackupKey.Daily, is SudokuBackupKey.Level -> furthest(local, incoming)
        }

    override suspend fun localEntries(keys: Set<SudokuBackupKey>): Map<SudokuBackupKey, SudokuBackupEntry> {
        val normals =
            keys
                .filterIsInstance<SudokuBackupKey.Normal>()
                .map { it.id }
                .chunked(MAX_IDS_PER_QUERY)
                .flatMap { dao.getByIds(it) }
        val dailies = if (keys.any { it is SudokuBackupKey.Daily }) dao.getDailySudokus() else emptyList()
        val levels =
            keys
                .filterIsInstance<SudokuBackupKey.Level>()
                .map { it.size }
                .distinct()
                .flatMap { dao.getAllSudokuLevel(it.value) }
        return (normals + dailies + levels)
            .mapNotNull(::entryOf)
            .groupBy { it.key() }
            .filterKeys { it in keys }
            .mapValues { (_, clashing) -> clashing.first() }
    }

    override suspend fun replace(
        local: SudokuBackupEntry,
        merged: SudokuBackupEntry,
    ) {
        dao.deleteById(local.id)
        insert(listOf(merged))
    }

    private fun entryOf(rows: SudokuWithFields): SudokuBackupEntry? = sudokuFromDb(rows)?.let { sudokuToBackup(it, clock.zone) }

    companion object {
        const val ID = "sudokus"
        private const val MAX_IDS_PER_QUERY = 500
    }
}
