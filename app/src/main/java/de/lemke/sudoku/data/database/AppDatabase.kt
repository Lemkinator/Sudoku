package de.lemke.sudoku.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = 2,
    entities = [
        SudokuDb::class,
    ],
    exportSchema = true,

    )
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): SudokuDao
}
