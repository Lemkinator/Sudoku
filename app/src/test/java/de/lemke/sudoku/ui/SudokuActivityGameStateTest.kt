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
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.PausableDispatcher
import de.lemke.commonutils.WhileResumed
import de.lemke.commonutils.awaitMainLooperUntil
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.commonutils.read
import de.lemke.sudoku.R
import de.lemke.sudoku.TestDispatchers
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.GameListener
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivityGameStateTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private val pausableDefaultDispatcher = PausableDispatcher(Dispatchers.Default, WhileResumed.RunInPlace)

    @Inject
    lateinit var testDispatchers: TestDispatchers

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        testDispatchers.default.redirectTo(pausableDefaultDispatcher)
        settings.bypassOobe()
        // Robolectric skips the Play Games SDK's auto-init ContentProvider under HiltTestApplication.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    @After
    fun resetDispatchers() {
        testDispatchers.reset()
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
            awaitMainLooperUntil { scenario.read { it.viewModel.game.value is SudokuGame.Playing } }
            block(scenario)
        }
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
            idleMainLooper()
            scenario.state shouldBe Lifecycle.State.RESUMED
            scenario.onActivity { activity ->
                activity.viewModel.game.value shouldBe SudokuGame.Loading
                activity.binding.gameRecycler.adapter
                    .shouldBeNull()
            }

            pausableDefaultDispatcher.resume()
            awaitMainLooperUntil { scenario.read { it.viewModel.game.value is SudokuGame.Playing } }
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
            idleMainLooper()
            scenario.state shouldBe Lifecycle.State.RESUMED

            pausableDefaultDispatcher.resume()
            awaitMainLooperUntil { scenario.read { it.viewModel.game.value == SudokuGame.NotFound } }
            scenario.onActivity { activity -> activity.isFinishing.shouldBeTrue() }
        }
    }

    @Test
    fun `a restart keeps the reset board without a loading dialog until it is saved, then plays it`() {
        val sudoku = formulaicSudoku(solved = false)
        launch(sudoku) { scenario ->
            scenario.onActivity { activity ->
                activity.userSettings.errorLimit = 1
                activity.select(1)
                activity.select(activity.sudoku.itemCount)
                activity.sudoku.errorsMade shouldBe 1
                pausableDefaultDispatcher.pause()

                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_reset)).shouldBeTrue()
            }
            idleMainLooper()

            scenario.onActivity { activity ->
                activity.viewModel.game.value shouldBe SudokuGame.Restarting
                activity.sudoku.errorsMade shouldBe 0
                (loadingDialog()?.isShowing == true).shouldBeFalse()
            }

            pausableDefaultDispatcher.resume()
            awaitMainLooperUntil { scenario.read { it.viewModel.game.value is SudokuGame.Playing } }
            scenario.onActivity { activity ->
                activity.sudoku.id shouldBe sudoku.id
                activity.sudoku[1].value.shouldBeNull()
            }
        }
    }

    @Test
    fun `an error, a tap and a tick reported while the game restarts change nothing`() {
        launch(formulaicSudoku(solved = false)) { scenario ->
            lateinit var listener: GameListener
            scenario.onActivity { activity ->
                activity.userSettings.errorLimit = 1
                activity.select(1)
                activity.select(activity.sudoku.itemCount)
                listener = checkNotNull(activity.sudoku.gameListener)
                pausableDefaultDispatcher.pause()
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_reset)).shouldBeTrue()
            }
            idleMainLooper()

            scenario.onActivity { activity ->
                listener.onError()
                listener.onFieldClicked(Position.create(1, activity.sudoku.size))
                listener.onTimeChanged()
                idleMainLooper()

                activity.viewModel.game.value shouldBe SudokuGame.Restarting
                activity.selected.shouldBeNull()
            }
            pausableDefaultDispatcher.resume()
            awaitMainLooperUntil { scenario.read { it.viewModel.game.value is SudokuGame.Playing } }
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
            idleMainLooper()

            scenario.onActivity { activity ->
                activity.viewModel.game.value shouldBe SudokuGame.Generating
                loadingDialog()?.isShowing shouldBe true
            }

            pausableDefaultDispatcher.resume()
            awaitMainLooperUntil { scenario.read { it.viewModel.game.value is SudokuGame.Playing } }
            scenario.onActivity { activity ->
                activity.viewModel.game.value
                    .shouldBeInstanceOf<SudokuGame.Playing>()
                activity.sudoku.id shouldNotBe completed.id
                loadingDialog()?.isShowing shouldBe false
            }
        }
    }
}
