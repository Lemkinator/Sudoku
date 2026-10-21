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
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testLevelSudoku
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Seeds one incomplete level-1 board per size so [de.lemke.sudoku.ui.fragments.SudokuLevelTabViewModel]'s init never
 * falls into [de.lemke.sudoku.domain.GenerateSudokuLevelUseCase] — the real `de.sfuhrm:sudoku` generator is
 * unseeded/non-deterministic, which would make the golden un-verifiable across recordings.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
// sdk = [36]: Robolectric's SDK 37 shadows lack InputManager.getInstance().
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SudokuLevelActivityScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        runBlocking {
            SudokuSize.entries.forEach { size -> saveSudoku(testLevelSudoku(size)) }
        }
    }

    @Test
    fun sudokuLevelActivity_default() {
        ActivityScenario.launch(SudokuLevelActivity::class.java).use {
            onView(isRoot()).captureRoboImage("src/test/screenshots/sudoku_level_default.png")
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun sudokuLevelActivity_default_dark() {
        ActivityScenario.launch(SudokuLevelActivity::class.java).use {
            onView(isRoot()).captureRoboImage("src/test/screenshots/sudoku_level_default_dark.png")
        }
    }
}
