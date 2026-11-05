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

package de.lemke.sudoku.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.domain.model.Sudoku
import java.time.Clock
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultSudokusRepositoryContractTest : SudokusRepositoryContract() {
    private lateinit var database: AppDatabase
    override lateinit var repository: SudokusRepository

    @Before
    fun setUp() {
        // A same-thread executor keeps Room's invalidation re-query synchronous with the triggering write.
        val directExecutor = Executor { it.run() }
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor(directExecutor)
                .setTransactionExecutor(directExecutor)
                .build()
        repository = DefaultSudokusRepository(database.gameDao(), Clock.systemDefaultZone())
    }

    @After
    fun tearDown() {
        database.close()
    }

    override suspend fun storeUnreadable(sudoku: Sudoku) {
        val game = sudoku.toEntity(Clock.systemDefaultZone().zone)
        database.gameDao().save(GameEntity(game.header, byteArrayOf(1)))
    }

    override suspend fun storedIds(): Set<String> =
        database
            .gameDao()
            .getAll()
            .map { it.header.id }
            .toSet()
}
