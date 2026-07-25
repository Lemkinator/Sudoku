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

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.AppDatabase
import de.lemke.sudoku.data.database.MIGRATION_1_2
import de.lemke.sudoku.data.database.SudokuDao
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object
PersistenceModule : Application() {
    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideUserSettings(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): UserSettings = UserSettings(PreferenceManager.getDefaultSharedPreferences(context), scope)

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "app")
            .addMigrations(MIGRATION_1_2)
            // .createFromAsset("databases/app-v1.db")
            .build()

    @Provides
    fun provideSudokuDao(database: AppDatabase): SudokuDao = database.sudokuDao()
}

// common-utils' own activities (CommonUtilsAboutActivity, CommonUtilsSettingsActivity.SettingsFragment) inject
// SettingsRepository directly via Hilt, so it must be bound in the graph regardless of app-specific settings access
// (mirrors GetIcon's SettingsBindModule).
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsBindModule {
    @Binds
    abstract fun bindSettingsRepository(userSettings: UserSettings): SettingsRepository
}
