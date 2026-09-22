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

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.testLevelSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Seeds a fixed, hand-formulaic board (not solver-generated, see [testLevelSudoku]) under a known [SudokuId] so the
 * activity loads deterministic data instead of hitting the unseeded `de.sfuhrm:sudoku` generator.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SudokuActivityScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @DefaultDispatcher
    @JvmField
    val testDefaultDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    @BindValue
    @IoDispatcher
    @JvmField
    val testIoDispatcher: CoroutineDispatcher = Dispatchers.IO

    @BindValue
    @MainDispatcher
    @JvmField
    val testMainDispatcher: CoroutineDispatcher = Dispatchers.Main

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        runBlocking { saveSudoku(testLevelSudoku(size = 9, level = MODE_NORMAL, sudokuId = SUDOKU_ID)) }
    }

    private fun captureSudokuScreenshot(fileName: String) {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, SUDOKU_ID.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            // Sudoku.startTimer() schedules a real java.util.Timer thread (unrelated to Robolectric's looper), so
            // under a slow/loaded test run it can tick — and queue a "00:0x" subtitle update on the real Main
            // dispatcher — before this callback even runs; canceling it here only stops *further* ticks. Resetting
            // `seconds` and re-firing the listener enqueues one more, correct update after any stray one, which
            // wins because Espresso drains the paused main looper in FIFO order right before the capture.
            scenario.onActivity { activity ->
                activity.sudoku.stopTimer()
                activity.sudoku.seconds = 0
                activity.sudoku.gameListener?.onTimeChanged()
            }
            onView(isRoot()).captureRoboImage(fileName)
        }
    }

    @Test
    fun sudokuActivity_default() {
        captureSudokuScreenshot("src/test/screenshots/sudoku_default.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun sudokuActivity_default_dark() {
        captureSudokuScreenshot("src/test/screenshots/sudoku_default_dark.png")
    }

    companion object {
        private val SUDOKU_ID = SudokuId("test-sudoku-id")
    }
}
