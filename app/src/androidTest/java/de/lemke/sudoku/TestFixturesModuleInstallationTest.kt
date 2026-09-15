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

package de.lemke.sudoku

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.lemke.sudoku.data.UserSettings
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards against Hilt's KSP module aggregation silently dropping a @TestInstallIn module
 * declared in src/testFixtures for the instrumented (src/androidTest) side, leaving the
 * production settings module active undetected on a real device. Keep this test even
 * though it currently passes - a silent regression here produces no other failing test, and
 * would leak test settings into the real device's production SharedPreferences on a reused
 * GMD device.
 */
@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class TestFixturesModuleInstallationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settings: UserSettings

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun injectedSettingsDoNotWriteThroughToProductionSharedPreferences() {
        val productionPrefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        val keepScreenOnBefore = productionPrefs.all["keepScreenOn"]
        settings.keepScreenOn = !(keepScreenOnBefore as? Boolean ?: false)
        assertEquals(
            "keepScreenOn changed in production SharedPreferences - TestSettingsModule (src/testFixtures) was " +
                "NOT installed; production settings module won instead",
            keepScreenOnBefore,
            productionPrefs.all["keepScreenOn"],
        )
    }
}
