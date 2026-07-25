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

import java.time.LocalDateTime

data class SudokuExport(
    val id: String,
    val size: Int,
    val difficulty: Int,
    val modeLevel: Int,
    val created: LocalDateTime,
    val updated: LocalDateTime,
    val seconds: Int,
    var regionalHighlightingUsed: Boolean? = null,
    val numberHighlightingUsed: Boolean? = null,
    val eraserUsed: Boolean? = null,
    val isChecklist: Boolean? = null,
    val isReverseChecklist: Boolean? = null,
    val checklistNumber: Int? = null,
    val hintsUsed: Int? = null,
    val notesMade: Int? = null,
    val errorsMade: Int? = null,
    val fields: List<FieldExport>,
)

data class FieldExport(
    val index: Int,
    val solution: Int?,
    val value: Int? = null,
    val given: Boolean? = null,
    val hint: Boolean? = null,
    val notes: String? = null,
)
