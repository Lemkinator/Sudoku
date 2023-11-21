package de.lemke.sudoku.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 2,
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

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        //delete column autoNotesUsed and rename column neighborHighlightingUsed to regionalHighlightingUsed
        //Drop column isn't supported by SQLite, so the data must manually be moved
        with(db) {
            execSQL("CREATE TABLE sudoku_backup (id TEXT NOT NULL, size INTEGER NOT NULL, difficulty INTEGER NOT NULL, modeLevel INTEGER NOT NULL, regionalHighlightingUsed INTEGER NOT NULL, numberHighlightingUsed INTEGER NOT NULL, hintsUsed INTEGER NOT NULL, notesMade INTEGER NOT NULL, errorsMade INTEGER NOT NULL, created TEXT NOT NULL, updated TEXT NOT NULL, seconds INTEGER NOT NULL, PRIMARY KEY(id))")
            execSQL("INSERT INTO sudoku_backup SELECT id, size, difficulty, modeLevel, neighborHighlightingUsed, numberHighlightingUsed, hintsUsed, notesMade, errorsMade, created, updated, seconds FROM sudoku")
            execSQL("DROP TABLE sudoku")
            execSQL("ALTER TABLE sudoku_backup RENAME to sudoku")

            execSQL("ALTER TABLE sudoku ADD COLUMN eraserUsed INTEGER NOT NULL DEFAULT 0")
            execSQL("ALTER TABLE sudoku ADD COLUMN isChecklist INTEGER NOT NULL DEFAULT 0")
            execSQL("ALTER TABLE sudoku ADD COLUMN isReverseChecklist INTEGER NOT NULL DEFAULT 0")
            execSQL("ALTER TABLE sudoku ADD COLUMN checklistNumber INTEGER NOT NULL DEFAULT 0")
        }
    }
}
