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
import android.net.Uri
import android.os.Looper
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.IntentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.PausableDispatcher
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.commonutils.WhileResumed
import de.lemke.commonutils.awaitMainLooperUntil
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.commonutils.returnFromLaunchedScreen
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.sudoku.R
import de.lemke.sudoku.TestDispatchers
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.codec.SudokuFileCodec
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.domain.model.dateFormatShort
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36], shadows = [ShadowFileProvider::class])
class SudokuActivityShareTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private val pausableIoDispatcher = PausableDispatcher(Dispatchers.IO, WhileResumed.RunInPlace)

    @Inject
    lateinit var testDispatchers: TestDispatchers

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var userSettings: de.lemke.sudoku.data.UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        testDispatchers.io.redirectTo(pausableIoDispatcher)
        settings.bypassOobe()
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    @After
    fun resetDispatchers() {
        testDispatchers.reset()
    }

    private fun formulaicSudoku(
        size: SudokuSize,
        mode: GameMode = GameMode.Normal,
        created: LocalDateTime = LocalDateTime.now(),
    ): Sudoku = testSudoku(size = size, mode = mode, difficulty = Difficulty.VERY_EASY, created = created)

    private fun launch(
        sudoku: Sudoku,
        block: (SudokuActivity) -> Unit,
    ) {
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.info.id.value)
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
        awaitMainLooperUntil { shadowOf(activity).peekNextStartedActivity() != null }
    }

    private fun levelSudokuWithAnEntry(): Sudoku =
        formulaicSudoku(SudokuSize.FOUR, mode = GameMode.Level(3)).let { it.copy(board = it.board.with(1, it.board[1].copy(value = 2))) }

    private fun sharedSudoku(activity: SudokuActivity): Sudoku {
        val chooser = shadowOf(activity).nextStartedActivity
        chooser.action shouldBe Intent.ACTION_CHOOSER
        chooser.getStringExtra(Intent.EXTRA_TITLE) shouldBe "Share Sudoku"
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        send.type shouldBe "application/sudoku"
        val uri = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
        val bytes = activity.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        return SudokuFileCodec.decode(bytes, ZoneId.systemDefault())!!
    }

    @Test
    fun `sharing as text starts a plain-text share chooser`() =
        launch(formulaicSudoku(SudokuSize.FOUR)) { activity ->
            shareVia(activity, R.id.radioButtonText)
            val started = shadowOf(activity).nextStartedActivity
            started.action shouldBe Intent.ACTION_CHOOSER
            val send = IntentCompat.getParcelableExtra(started, Intent.EXTRA_INTENT, Intent::class.java)!!
            send.type shouldBe "text/plain"
            send.getStringExtra(Intent.EXTRA_TEXT) shouldBe activity.sudoku.getLocalStatisticsStringShare(activity.resources)
        }

    @Test
    fun `sharing the statistics shares the sudoku the dialog shows, not one that moved on since`() =
        launch(formulaicSudoku(SudokuSize.FOUR)) { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_share))
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            activity.viewModel.onResumed()
            repeat(3) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)) }
            activity.sudoku.info.timeString shouldBe "00:03"
            dialog.findViewById<RadioGroup>(R.id.shareRadioGroup)?.check(R.id.radioButtonText)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            awaitMainLooperUntil { shadowOf(activity).peekNextStartedActivity() != null }

            val send = IntentCompat.getParcelableExtra(shadowOf(activity).nextStartedActivity, Intent.EXTRA_INTENT, Intent::class.java)!!
            val text = send.getStringExtra(Intent.EXTRA_TEXT)!!
            text shouldContain "Time: 00:00 "
            text shouldNotContain "00:03"
        }

    @Test
    fun `selecting share twice shows one share dialog`() =
        launch(formulaicSudoku(SudokuSize.FOUR)) { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_share)) shouldBe true
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_share)) shouldBe true
            ShadowDialog.getShownDialogs().count { it.isShowing } shouldBe 1
        }

    @Test
    fun `tapping the share button twice starts one share chooser`() =
        launch(formulaicSudoku(SudokuSize.FOUR)) { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_share))
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.findViewById<RadioGroup>(R.id.shareRadioGroup)?.check(R.id.radioButtonText)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            awaitMainLooperUntil { shadowOf(activity).peekNextStartedActivity() != null }
            shadowOf(activity).nextStartedActivity.action shouldBe Intent.ACTION_CHOOSER
            shadowOf(activity).nextStartedActivity shouldBe null
        }

    @Test
    fun `tapping the share button twice for the current board shares one sudoku file`() =
        launch(levelSudokuWithAnEntry()) { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_share))
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.findViewById<RadioGroup>(R.id.shareRadioGroup)?.check(R.id.radioButtonCurrent)
            pausableIoDispatcher.pause()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            pausableIoDispatcher.resume()
            awaitMainLooperUntil { shadowOf(activity).peekNextStartedActivity() != null }
            sharedSudoku(activity).board[1].value shouldBe 2
            shadowOf(activity).nextStartedActivity shouldBe null
            dialog.isShowing shouldBe false
        }

    @Test
    fun `a sudoku file written across a recreation is shared from the recreated activity`() {
        val sudoku = levelSudokuWithAnEntry()
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.info.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.userSettings.animationsEnabled = false
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_share))
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                dialog.findViewById<RadioGroup>(R.id.shareRadioGroup)?.check(R.id.radioButtonCurrent)
                pausableIoDispatcher.pause()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            scenario.recreate()
            pausableIoDispatcher.resume()
            scenario.onActivity { activity ->
                awaitMainLooperUntil { shadowOf(activity).peekNextStartedActivity() != null }
                sharedSudoku(activity).board[1].value shouldBe 2
                shadowOf(activity).nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun `a sudoku file written while a launch is pending is shared once the activity resumes again`() {
        val sudoku = levelSudokuWithAnEntry()
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.info.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            lateinit var viewModel: SudokuViewModel
            scenario.onActivity { activity ->
                activity.userSettings.animationsEnabled = false
                viewModel = activity.viewModel
                activity.singleLaunchActivity(Intent(activity, MainActivity::class.java)) shouldBe true
                viewModel.onShare(activity.sudoku.initialCopy())
            }
            awaitMainLooperUntil { viewModel.share.value is SudokuShare.File }
            val file = viewModel.share.value as SudokuShare.File
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity.component?.className shouldBe MainActivity::class.java.name
                shadowOf(activity).nextStartedActivity shouldBe null
            }
            viewModel.share.value shouldBe file

            scenario.returnFromLaunchedScreen()
            idleMainLooper()

            scenario.onActivity { activity ->
                val chooser = shadowOf(activity).nextStartedActivity
                chooser.action shouldBe Intent.ACTION_CHOOSER
                val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
                send.type shouldBe "application/sudoku"
                val uri = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
                uri.path shouldBe "/cache/${file.file.name}"
                shadowOf(activity).nextStartedActivity shouldBe null
            }
            viewModel.share.value shouldBe SudokuShare.Idle
        }
    }

    @Test
    fun `sharing the initial board shares a normal sudoku file without the player's entries`() =
        launch(levelSudokuWithAnEntry()) { activity ->
            shareVia(activity, R.id.radioButtonInitial)
            val shared = sharedSudoku(activity)
            shared.info.mode shouldBe GameMode.Normal
            shared.board[0].value shouldBe 1
            shared.board[1].value shouldBe null
        }

    @Test
    fun `sharing the current board shares a normal sudoku file with the player's entries`() =
        launch(levelSudokuWithAnEntry()) { activity ->
            shareVia(activity, R.id.radioButtonCurrent)
            val shared = sharedSudoku(activity)
            shared.info.mode shouldBe GameMode.Normal
            shared.board[0].value shouldBe 1
            shared.board[1].value shouldBe 2
        }

    @Test
    fun `a 16x16 board wires up every extended number button`() =
        launch(formulaicSudoku(SudokuSize.SIXTEEN)) { activity ->
            activity.sudokuButtons.size shouldBe 16
        }

    @Test
    fun `the title reflects a daily sudoku's date`() =
        launch(formulaicSudoku(SudokuSize.FOUR, mode = GameMode.Daily(LocalDate.now()))) { activity ->
            val expected = "Sudoku (${activity.sudoku.info.created.dateFormatShort})"
            activity.binding.sudokuToolbarLayout.expandedTitle
                .toString() shouldBe expected
        }

    @Test
    fun `the subtitle shows a daily sudoku's fixed error limit instead of the user's`() {
        userSettings.errorLimit = 5
        launch(formulaicSudoku(SudokuSize.FOUR, mode = GameMode.Daily(LocalDate.now()))) { activity ->
            activity.subtitleErrorsSegment() shouldBe "Errors: 0/3"
        }
    }

    @Test
    fun `the title reflects a sudoku level's number`() =
        launch(formulaicSudoku(SudokuSize.FOUR, mode = GameMode.Level(3))) { activity ->
            activity.binding.sudokuToolbarLayout.expandedTitle
                .toString() shouldBe "Sudoku (Level 3)"
        }

    @Test
    fun `the title reflects a normal sudoku's difficulty`() =
        launch(formulaicSudoku(SudokuSize.FOUR, mode = GameMode.Normal)) { activity ->
            activity.binding.sudokuToolbarLayout.expandedTitle
                .toString() shouldBe "Sudoku (Very easy)"
        }

    @Test
    fun `the subtitle shows a sudoku level's fixed error limit instead of the user's`() {
        userSettings.errorLimit = 5
        launch(formulaicSudoku(SudokuSize.FOUR, mode = GameMode.Level(3))) { activity ->
            activity.subtitleErrorsSegment() shouldBe "Errors: 0/3"
        }
    }

    @Test
    fun `the subtitle omits the error limit for a normal sudoku with an unlimited error setting`() {
        userSettings.errorLimit = 0
        launch(formulaicSudoku(SudokuSize.FOUR, mode = GameMode.Normal)) { activity ->
            activity.subtitleErrorsSegment() shouldBe "Errors: 0"
        }
    }

    private fun SudokuActivity.subtitleErrorsSegment(): String =
        binding.sudokuToolbarLayout.expandedSubtitle
            .toString()
            .split(" | ")[2]
}
