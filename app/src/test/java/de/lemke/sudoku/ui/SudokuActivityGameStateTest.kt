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
import androidx.lifecycle.Lifecycle
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
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import dev.oneuiproject.oneui.dialog.ProgressDialog
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

/** sdk = 36: Robolectric's max supported SDK. */
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivityGameStateTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    private val pausableDefaultDispatcher = PausableDispatcher(Dispatchers.Default)

    @BindValue
    @DefaultDispatcher
    @JvmField
    val testDefaultDispatcher: CoroutineDispatcher = pausableDefaultDispatcher

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
        // Robolectric skips the Play Games SDK's auto-init ContentProvider under HiltTestApplication.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun formulaicSudoku(solved: Boolean): Sudoku {
        val size = SudokuSize.FOUR
        val blockSize = size.blockSize
        return Sudoku.create(
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = MODE_NORMAL,
            fields =
                MutableList(size.cellCount) { index ->
                    val row = index / size.value
                    val col = index % size.value
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size.value + 1
                    val given = index % 3 == 0
                    Field(
                        position = Position.create(index, size),
                        solution = solution,
                        value = if (given || solved) solution else null,
                        given = given,
                    )
                },
        )
    }

    private fun launch(
        sudoku: Sudoku,
        block: (ActivityScenario<SudokuActivity>) -> Unit,
    ) {
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { it.userSettings.animationsEnabled = false }
            idleUntil(scenario) { it.viewModel.game.value is SudokuGame.Playing }
            block(scenario)
        }
    }

    private fun idleUntil(
        scenario: ActivityScenario<SudokuActivity>,
        condition: (SudokuActivity) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        var met = false
        while (!met && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { met = condition(it) }
            if (!met) Thread.sleep(POLL_MILLIS)
        }
        check(met) { "condition not met within ${TIMEOUT_MILLIS}ms" }
    }

    private fun loadingDialog(): ProgressDialog? = ShadowDialog.getShownDialogs().filterIsInstance<ProgressDialog>().lastOrNull()

    @Test
    fun `a sudoku that loads after the activity resumed fills the empty board once it is loaded`() {
        val sudoku = formulaicSudoku(solved = false)
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
        pausableDefaultDispatcher.pause()
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.state shouldBe Lifecycle.State.RESUMED
            scenario.onActivity { activity ->
                activity.viewModel.game.value shouldBe SudokuGame.Loading
                activity.binding.gameRecycler.adapter
                    .shouldBeNull()
            }

            pausableDefaultDispatcher.resume()
            idleUntil(scenario) { it.viewModel.game.value is SudokuGame.Playing }
            scenario.onActivity { activity ->
                activity.sudoku.id shouldBe sudoku.id
                activity.binding.gameRecycler.adapter
                    .shouldNotBeNull()
            }
        }
    }

    @Test
    fun `a sudoku id found missing after the activity resumed finishes the activity`() {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, "does-not-exist")
        pausableDefaultDispatcher.pause()
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.state shouldBe Lifecycle.State.RESUMED

            pausableDefaultDispatcher.resume()
            idleUntil(scenario) { it.viewModel.game.value == SudokuGame.NotFound }
            scenario.onActivity { activity -> activity.isFinishing.shouldBeTrue() }
        }
    }

    @Test
    fun `a restart keeps the reset board without a loading dialog until it is saved, then plays it`() {
        val sudoku = formulaicSudoku(solved = false)
        launch(sudoku) { scenario ->
            scenario.onActivity { activity ->
                activity.select(1)
                activity.select(activity.sudoku.itemCount)
                activity.sudoku.errorsMade shouldBe 1
                pausableDefaultDispatcher.pause()

                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_reset)).shouldBeTrue()
            }
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity { activity ->
                activity.viewModel.game.value shouldBe SudokuGame.Restarting
                activity.sudoku.errorsMade shouldBe 0
                (loadingDialog()?.isShowing == true).shouldBeFalse()
            }

            pausableDefaultDispatcher.resume()
            idleUntil(scenario) { it.viewModel.game.value is SudokuGame.Playing }
            scenario.onActivity { activity ->
                activity.sudoku.id shouldBe sudoku.id
                activity.sudoku[1].value.shouldBeNull()
            }
        }
    }

    @Test
    fun `a new game after a completed one shows the loading dialog while it is prepared, then plays the new game`() {
        val completed = formulaicSudoku(solved = true)
        launch(completed) { scenario ->
            scenario.onActivity { activity ->
                activity.sudoku.completed.shouldBeTrue()
                pausableDefaultDispatcher.pause()

                activity.viewModel.onFollowUp(FollowUp.NEW_GAME)
            }
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity { activity ->
                activity.viewModel.game.value shouldBe SudokuGame.Generating
                loadingDialog()?.isShowing shouldBe true
            }

            pausableDefaultDispatcher.resume()
            idleUntil(scenario) { it.viewModel.game.value is SudokuGame.Playing }
            scenario.onActivity { activity ->
                activity.viewModel.game.value
                    .shouldBeInstanceOf<SudokuGame.Playing>()
                activity.sudoku.id shouldNotBe completed.id
                loadingDialog()?.isShowing shouldBe false
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
