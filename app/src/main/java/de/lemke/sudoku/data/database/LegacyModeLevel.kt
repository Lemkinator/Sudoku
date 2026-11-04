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

import de.lemke.sudoku.domain.model.GameMode

private const val LEGACY_MODE_NORMAL = 0
private const val LEGACY_MODE_DAILY = -1

/** The mode level the version 2 tables and the backup format keep for this mode: 0 normal, -1 daily, the number for a level. */
fun GameMode.toLegacyModeLevel(): Int =
    when (this) {
        GameMode.Normal -> LEGACY_MODE_NORMAL
        GameMode.Daily -> LEGACY_MODE_DAILY
        is GameMode.Level -> number
    }

/** The mode of a stored mode level, or null if no mode has that level. */
fun legacyGameModeOrNull(modeLevel: Int): GameMode? =
    when {
        modeLevel == LEGACY_MODE_NORMAL -> GameMode.Normal
        modeLevel == LEGACY_MODE_DAILY -> GameMode.Daily
        modeLevel > LEGACY_MODE_NORMAL -> GameMode.Level(modeLevel)
        else -> null
    }
