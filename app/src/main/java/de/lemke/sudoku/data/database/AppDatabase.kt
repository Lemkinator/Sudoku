package de.lemke.sudoku.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = 1,
    entities = [
        SudokuDb::class,
        FieldDb::class,
    ],
    exportSchema = true,

    )
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sudokuDao(): SudokuDao
    abstract fun fieldDao(): FieldDao
}
