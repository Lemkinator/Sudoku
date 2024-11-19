package de.lemke.sudoku

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.lemke.sudoku.data.database.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object
PersistenceModule : Application() {
    private val Context.userSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "userSettings")

    @Provides
    @Singleton
    fun provideUserSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.userSettingsStore

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "app")
        .addMigrations(MIGRATION_1_2)
        //.createFromAsset("databases/app-v1.db")
        .build()

    @Provides
    fun provideSudokuDao(database: AppDatabase): SudokuDao = database.sudokuDao()
}


