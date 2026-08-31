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

package de.lemke.sudoku.data

import android.content.SharedPreferences
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.delegates
import de.lemke.commonutils.data.sanitized
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.SudokuFilterFlags.DIFFICULTY_ALL
import de.lemke.sudoku.domain.model.SudokuFilterFlags.SIZE_ALL
import de.lemke.sudoku.domain.model.SudokuFilterFlags.TYPE_ALL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Sudoku-specific settings, layered on top of common-utils [SettingsRepository]. */
class UserSettings(
    preferences: SharedPreferences,
    scope: CoroutineScope,
) : SettingsRepository(preferences) {
    var difficultySliderValue: Int by preferences.delegates.int(2).sanitized { it.coerceIn(0, Difficulty.max) }
    var sizeSliderValue: Int by preferences.delegates.int(1)
    var keepScreenOn: Boolean by preferences.delegates.boolean(true)
    var animationsEnabled: Boolean by preferences.delegates.boolean(true)
    var highlightRegional: Boolean by preferences.delegates.boolean(true)
    var highlightNumber: Boolean by preferences.delegates.boolean(true)
    var errorLimit: Int by preferences.delegates.int(3).sanitized { it.coerceAtLeast(0) }
    var filterFlags: Int by preferences.delegates.int(TYPE_ALL or SIZE_ALL or DIFFICULTY_ALL)
    var dailyShowUncompleted: Boolean by preferences.delegates.boolean(true)
    var dailySudokuNotificationEnabled: Boolean by preferences.delegates.boolean(true)
    var dailySudokuNotificationHour: Int by preferences.delegates.int(9)
    var dailySudokuNotificationMinute: Int by preferences.delegates.int(0)
    var currentLevelTab: Int by preferences.delegates.int(1)

    /**
     * Reactive view of [dailyShowUncompleted] — [ObserveDailySudokusUseCase][de.lemke.sudoku.domain.ObserveDailySudokusUseCase]
     * needs to re-filter when this changes while being observed.
     */
    val dailyShowUncompletedFlow: StateFlow<Boolean> = settingsFlow(scope) { dailyShowUncompleted }

    /**
     * Reactive view of [filterFlags] —
     * [ObserveSudokusAndStatisticsFilterFlagsUseCase][de.lemke.sudoku.domain.ObserveSudokusAndStatisticsFilterFlagsUseCase]
     * needs to re-filter when this changes while being observed.
     */
    val filterFlagsFlow: StateFlow<Int> = settingsFlow(scope) { filterFlags }

    /**
     * Reactive view of [errorLimit] — [TabHistory][de.lemke.sudoku.ui.fragments.TabHistory] needs to update its
     * error-limit highlighting live while visible-but-unfocused (e.g. split-screen alongside Settings), not just
     * on resume.
     */
    val errorLimitFlow: StateFlow<Int> = settingsFlow(scope) { errorLimit }
}
