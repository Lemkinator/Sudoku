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
import android.os.Looper
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
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
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY_ERROR_LIMIT
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.dateFormatShort
import de.lemke.sudoku.resetFileProviderCache
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

/**
 * Covers [SudokuActivity]'s `shareDialog`'s three radio-button share paths (`shareStats`/`shareGame` in
 * `SudokuActivityKt`), a 16x16 board's `initSudokuButtons` branch, and `setTitle`/`setSubtitle`'s daily-mode
 * branches — none of which the existing selection/lifecycle tests reach (they stick to size-4/9 normal boards).
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivityShareTest {
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
    lateinit var userSettings: de.lemke.sudoku.data.UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
        resetFileProviderCache()
    }

    private fun formulaicSudoku(
        size: Int,
        modeLevel: Int = MODE_NORMAL,
        created: LocalDateTime = LocalDateTime.now(),
    ): Sudoku {
        val blockSize = sqrt(size.toDouble()).toInt()
        return Sudoku.create(
            sudokuId = SudokuId.generate(),
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = modeLevel,
            created = created,
            fields =
                MutableList(size * size) { index ->
                    val row = index / size
                    val col = index % size
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
                    val given = index % 3 == 0
                    Field(
                        position = Position.create(index, size),
                        solution = solution,
                        value = if (given) solution else null,
                        given = given,
                    )
                },
        )
    }

    private fun launch(
        sudoku: Sudoku,
        block: (SudokuActivity) -> Unit,
    ) {
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { it.userSettings.animationsEnabled = false }
            scenario.onActivity(block)
        }
    }

    private fun shareVia(
        activity: SudokuActivity,
        radioButtonId: Int,
    ) {
        activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_share))
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.findViewById<RadioGroup>(R.id.shareRadioGroup)?.check(radioButtonId)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        // shareGame's export crosses to the real Dispatchers.IO/Main (bound above), so this needs real
        // wall-clock polling, not a single idle() - see awaitMainLooperIdleUntil elsewhere in this fleet.
        awaitMainLooperIdleUntil { shadowOf(activity).peekNextStartedActivity() != null }
    }

    private fun awaitMainLooperIdleUntil(
        timeoutMillis: Long = 5000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
        check(condition()) { "share chooser was not started within ${timeoutMillis}ms" }
    }

    @Test
    fun `sharing as text starts a plain-text share chooser`() =
        launch(formulaicSudoku(4)) { activity ->
            shareVia(activity, R.id.radioButtonText)
            val started = shadowOf(activity).nextStartedActivity
            started.action shouldBe Intent.ACTION_CHOOSER
        }

    // Stock AndroidX FileProvider.SimplePathStrategy#belongsToRoot hardcodes '/' when comparing
    // canonical paths, so it always fails on a Windows JVM (File.getCanonicalPath() uses '\') —
    // same root cause as ShareSudokuUseCaseTest's assumeUnixPaths. Linux only gets past that far
    // enough to need setup()'s resetFileProviderCache() too.
    @Test
    fun `sharing the initial board exports and starts a file share chooser`() {
        assumeTrue(File.separatorChar == '/')
        launch(formulaicSudoku(4)) { activity ->
            shareVia(activity, R.id.radioButtonInitial)
            val started = shadowOf(activity).nextStartedActivity
            started.action shouldBe Intent.ACTION_CHOOSER
        }
    }

    @Test
    fun `sharing the current board exports and starts a file share chooser`() {
        assumeTrue(File.separatorChar == '/')
        launch(formulaicSudoku(4)) { activity ->
            shareVia(activity, R.id.radioButtonCurrent)
            val started = shadowOf(activity).nextStartedActivity
            started.action shouldBe Intent.ACTION_CHOOSER
        }
    }

    @Test
    fun `a 16x16 board wires up every extended number button`() =
        launch(formulaicSudoku(16)) { activity ->
            activity.sudokuButtons.size shouldBe 16
        }

    @Test
    fun `the title reflects a daily sudoku's date`() =
        launch(formulaicSudoku(4, modeLevel = MODE_DAILY)) { activity ->
            val expected = "${activity.getString(R.string.sudoku)} (${activity.sudoku.created.dateFormatShort})"
            activity.binding.sudokuToolbarLayout.expandedTitle
                .toString() shouldBe expected
        }

    @Test
    fun `the subtitle reflects a daily sudoku's fixed error limit`() =
        launch(formulaicSudoku(4, modeLevel = MODE_DAILY)) { activity ->
            activity.binding.sudokuToolbarLayout.expandedSubtitle
                .toString()
                .shouldContain(
                    activity.getString(
                        R.string.current_errors_with_limit,
                        activity.sudoku.errorsMade,
                        MODE_DAILY_ERROR_LIMIT,
                    ),
                )
        }

    @Test
    fun `the title reflects a sudoku level's number`() =
        launch(formulaicSudoku(4, modeLevel = 3)) { activity ->
            val expected = "${activity.getString(R.string.sudoku)} (${activity.getString(R.string.level)} 3)"
            activity.binding.sudokuToolbarLayout.expandedTitle
                .toString() shouldBe expected
        }

    @Test
    fun `the subtitle reflects a sudoku level's fixed error limit`() =
        launch(formulaicSudoku(4, modeLevel = 3)) { activity ->
            activity.binding.sudokuToolbarLayout.expandedSubtitle
                .toString()
                .shouldContain(
                    activity.getString(
                        R.string.current_errors_with_limit,
                        activity.sudoku.errorsMade,
                        de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_LEVEL_ERROR_LIMIT,
                    ),
                )
        }

    @Test
    fun `the subtitle omits the error limit for a normal sudoku with an unlimited error setting`() {
        userSettings.errorLimit = 0
        launch(formulaicSudoku(4, modeLevel = de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL)) { activity ->
            activity.binding.sudokuToolbarLayout.expandedSubtitle
                .toString()
                .shouldContain(activity.getString(R.string.current_errors, activity.sudoku.errorsMade))
        }
    }
}
