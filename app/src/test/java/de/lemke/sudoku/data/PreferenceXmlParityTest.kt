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

import de.lemke.commonutils.data.assertPreferenceXmlBoundToSettings
import de.lemke.sudoku.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.lemke.commonutils.R as commonutilsR

/**
 * Pins every preference XML Sudoku composes in `SettingsActivity.onCreatePreferences` against [UserSettings]
 * — the collision check must run against this subclass, not only against common-utils' own `SettingsRepository`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreferenceXmlParityTest {
    @Test
    fun `composed preference XMLs are bound to UserSettings`() {
        assertPreferenceXmlBoundToSettings(
            commonutilsR.xml.preferences_design,
            R.xml.preferences,
            commonutilsR.xml.preferences_more_info,
            factory = { prefs -> UserSettings(prefs, CoroutineScope(Dispatchers.Unconfined)) },
        )
    }
}
