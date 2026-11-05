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

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

data class GameHeader(
    val id: String,
    val mode: Int,
    val level: Int?,
    val dailyDate: Long?,
    val size: Int,
    val difficulty: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val seconds: Int,
    val hintsUsed: Int,
    val notesMade: Int,
    val errorsMade: Int,
    val regionalHighlightingUsed: Boolean,
    val numberHighlightingUsed: Boolean,
    val eraserUsed: Boolean,
    val isChecklist: Boolean,
    val isReverseChecklist: Boolean,
    val checklistNumber: Int,
    val completed: Boolean,
    val progress: Int,
    val imported: Boolean,
)

@Entity(
    tableName = "game",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["size", "level"], unique = true),
        Index(value = ["dailyDate"], unique = true),
        Index(value = ["mode", "updatedAt"]),
    ],
)
class GameEntity(
    @Embedded val header: GameHeader,
    val board: ByteArray,
)
