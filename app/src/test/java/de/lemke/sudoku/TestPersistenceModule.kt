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

package de.lemke.sudoku

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import de.lemke.sudoku.data.database.AppDatabase
import de.lemke.sudoku.data.database.MIGRATION_1_2
import de.lemke.sudoku.data.database.SudokuDao
import de.lemke.sudoku.di.PersistenceModule
import java.util.concurrent.Executor
import javax.inject.Singleton

/**
 * Production Room runs queries on a real background thread pool. Under Robolectric's single-threaded, paused-looper
 * model that races the test's own execution: a suspend DAO call or `Flow` emission can still be in flight on that
 * pool when `captureRoboImage` runs, producing a screenshot that randomly differs (empty list vs. loaded, stale
 * elapsed count, ...) between recordings. A same-thread executor makes every Room operation finish synchronously
 * before the calling coroutine resumes, which removes the race entirely instead of papering over it with idle()/
 * sleep() polling in each screenshot test.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PersistenceModule::class])
object TestPersistenceModule {
    private val directExecutor = Executor { it.run() }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "app")
            .addMigrations(MIGRATION_1_2)
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

    @Provides
    fun provideSudokuDao(database: AppDatabase): SudokuDao = database.sudokuDao()
}
