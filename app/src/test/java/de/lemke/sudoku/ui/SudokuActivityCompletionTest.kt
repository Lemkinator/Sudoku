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
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Completes a real board with animations left enabled (the default) to drive
 * [SudokuActivity.SudokuGameListener.onCompleted] through `animate(..., animateSudoku = true)` in
 * `SudokuActivityAnimations.kt` and into `onSudokuCompleted` — a path every other `SudokuActivity` test disables via
 * `userSettings.animationsEnabled = false` to keep unrelated assertions simple.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivityCompletionTest {
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
        // SudokuActivity's completion flow syncs Play Games achievements; the SDK is normally
        // auto-initialized by its manifest-merged ContentProvider, which Robolectric does not run.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    /** A size-4 board with every field given except index 0 (solution 1), one move away from completion. */
    private fun almostSolvedSudoku(
        sudokuId: SudokuId,
        modeLevel: Int = MODE_NORMAL,
    ): Sudoku {
        val size = 4
        val blockSize = 2
        return Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = modeLevel,
            fields =
                MutableList(size * size) { index ->
                    val row = index / size
                    val col = index % size
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
                    val given = index != 0
                    Field(
                        position = Position.create(index, size),
                        solution = solution,
                        value = if (given) solution else null,
                        given = given,
                    )
                },
        )
    }

    private fun completeBoard(
        sudokuId: SudokuId,
        block: (SudokuActivity) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.select(0)
                activity.select(activity.sudoku.itemCount) // solution at index 0 is 1 -> number button "1"
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.sudoku.completed.shouldBeTrue()
                block(activity)
            }
        }
    }

    @Test
    fun `completing a normal sudoku with animations enabled shows the completion dialog`() {
        val sudokuId = SudokuId.generate()
        runBlocking { saveSudoku(almostSolvedSudoku(sudokuId)) }
        completeBoard(sudokuId) {
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog?
            dialog.shouldNotBeNull()
        }
    }

    @Test
    fun `clicking new game on a completed normal sudoku's dialog starts a fresh one`() {
        val sudokuId = SudokuId.generate()
        runBlocking { saveSudoku(almostSolvedSudoku(sudokuId)) }
        completeBoard(sudokuId) { activity ->
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            shadowOf(Looper.getMainLooper()).idle()
            activity.sudoku.id shouldNotBe sudokuId
        }
    }

    @Test
    fun `completing the max level of a sudoku level's dialog offers the next level`() {
        val sudokuId = SudokuId.generate()
        runBlocking { saveSudoku(almostSolvedSudoku(sudokuId, modeLevel = 1)) }
        completeBoard(sudokuId) { activity ->
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            shadowOf(Looper.getMainLooper()).idle()
            activity.sudoku.modeLevel shouldBe 2
        }
    }
}
