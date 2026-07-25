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

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
}

@Suppress("MaxLineLength")
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // delete column autoNotesUsed and rename column neighborHighlightingUsed to regionalHighlightingUsed
            // Drop column isn't supported by SQLite, so the data must manually be moved
            with(db) {
                execSQL(
                    "CREATE TABLE sudoku_backup (id TEXT NOT NULL, size INTEGER NOT NULL, difficulty INTEGER NOT NULL, modeLevel INTEGER NOT NULL, regionalHighlightingUsed INTEGER NOT NULL, numberHighlightingUsed INTEGER NOT NULL, hintsUsed INTEGER NOT NULL, notesMade INTEGER NOT NULL, errorsMade INTEGER NOT NULL, created TEXT NOT NULL, updated TEXT NOT NULL, seconds INTEGER NOT NULL, PRIMARY KEY(id))",
                )
                execSQL(
                    "INSERT INTO sudoku_backup SELECT id, size, difficulty, modeLevel, neighborHighlightingUsed, numberHighlightingUsed, hintsUsed, notesMade, errorsMade, created, updated, seconds FROM sudoku",
                )
                execSQL("DROP TABLE sudoku")
                execSQL("ALTER TABLE sudoku_backup RENAME to sudoku")

                execSQL("ALTER TABLE sudoku ADD COLUMN eraserUsed INTEGER NOT NULL DEFAULT 0")
                execSQL("ALTER TABLE sudoku ADD COLUMN isChecklist INTEGER NOT NULL DEFAULT 0")
                execSQL("ALTER TABLE sudoku ADD COLUMN isReverseChecklist INTEGER NOT NULL DEFAULT 0")
                execSQL("ALTER TABLE sudoku ADD COLUMN checklistNumber INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
