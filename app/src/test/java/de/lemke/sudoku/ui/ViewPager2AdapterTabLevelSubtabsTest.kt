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

package de.lemke.sudoku.ui

import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.sudoku.di.SolvedBoardGeneratorModule
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SolvedBoardGenerator
import de.lemke.sudoku.ui.fragments.SudokuLevelTab
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** sdk = 36: Robolectric's max supported SDK. */
@UninstallModules(SolvedBoardGeneratorModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class ViewPager2AdapterTabLevelSubtabsTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val solvedBoardGenerator: SolvedBoardGenerator = PatternSolvedBoardGenerator()

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    @Test
    fun `createFragment builds a SudokuLevelTab for every declared position and falls back beyond it`() {
        ActivityScenario.launch(SudokuLevelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val adapter = ViewPager2AdapterTabLevelSubtabs(activity)
                val sizes = listOf(0, 1, 2, 3).map { (adapter.createFragment(it) as SudokuLevelTab).arguments?.getInt("size") }
                sizes shouldBe listOf(4, 9, 16, 9)
            }
        }
    }
}
