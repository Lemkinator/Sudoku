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
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.domain.model.dateFormatShort
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kjson.parseJSON
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
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
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

/** sdk = 36: Robolectric's max supported SDK. */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36], shadows = [ShadowFileProvider::class])
class SudokuActivityShareTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @DefaultDispatcher
    @JvmField
    val testDefaultDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    private val pausableIoDispatcher = PausableDispatcher(Dispatchers.IO)

    @BindValue
    @IoDispatcher
    @JvmField
    val testIoDispatcher: CoroutineDispatcher = pausableIoDispatcher

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
    }

    private fun formulaicSudoku(
        size: SudokuSize,
        modeLevel: Int = MODE_NORMAL,
        created: LocalDateTime = LocalDateTime.now(),
    ): Sudoku {
        val blockSize = size.blockSize
        return Sudoku.create(
            sudokuId = SudokuId.generate(),
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = modeLevel,
            created = created,
            fields =
                MutableList(size.cellCount) { index ->
                    val row = index / size.value
                    val col = index % size.value
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size.value + 1
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

    private fun levelSudokuWithAnEntry(): Sudoku = formulaicSudoku(SudokuSize.FOUR, modeLevel = 3).apply { fields[1].value = 2 }

    private fun sharedSudoku(activity: SudokuActivity): SudokuExport {
        val chooser = shadowOf(activity).nextStartedActivity
        chooser.action shouldBe Intent.ACTION_CHOOSER
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        send.type shouldBe "application/sudoku"
        val uri = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
        return activity.contentResolver
            .openInputStream(uri)!!
            .bufferedReader()
            .use { it.readText() }
            .parseJSON<SudokuExport>()
    }

    @Test
    fun `sharing as text starts a plain-text share chooser`() =
        launch(formulaicSudoku(SudokuSize.FOUR)) { activity ->
            shareVia(activity, R.id.radioButtonText)
            val started = shadowOf(activity).nextStartedActivity
            started.action shouldBe Intent.ACTION_CHOOSER
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
            awaitMainLooperIdleUntil { shadowOf(activity).peekNextStartedActivity() != null }
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
            awaitMainLooperIdleUntil { shadowOf(activity).peekNextStartedActivity() != null }
            sharedSudoku(activity).fields[1].value shouldBe 2
            shadowOf(activity).nextStartedActivity shouldBe null
            dialog.isShowing shouldBe false
        }

    @Test
    fun `a sudoku file written across a recreation is shared from the recreated activity`() {
        val sudoku = levelSudokuWithAnEntry()
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
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
                awaitMainLooperIdleUntil { shadowOf(activity).peekNextStartedActivity() != null }
                sharedSudoku(activity).fields[1].value shouldBe 2
                shadowOf(activity).nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun `a sudoku file written while a launch is pending is shared once the activity resumes again`() {
        val sudoku = levelSudokuWithAnEntry()
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            lateinit var viewModel: SudokuViewModel
            scenario.onActivity { activity ->
                activity.userSettings.animationsEnabled = false
                viewModel = activity.viewModel
                activity.singleLaunchActivity(Intent(activity, MainActivity::class.java)) shouldBe true
                viewModel.onShare(activity.sudoku.getInitialSudoku())
            }
            awaitMainLooperIdleUntil { viewModel.share.value is SudokuShare.File }
            val file = viewModel.share.value as SudokuShare.File
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity.component?.className shouldBe MainActivity::class.java.name
                shadowOf(activity).nextStartedActivity shouldBe null
            }
            viewModel.share.value shouldBe file

            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity { activity ->
                val chooser = shadowOf(activity).nextStartedActivity
                chooser.action shouldBe Intent.ACTION_CHOOSER
                val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
                send.type shouldBe "application/sudoku"
                IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java) shouldBe file.uri
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
            shared.modeLevel shouldBe 0
            shared.fields[0].value shouldBe 1
            shared.fields[1].value shouldBe null
        }

    @Test
    fun `sharing the current board shares a normal sudoku file with the player's entries`() =
        launch(levelSudokuWithAnEntry()) { activity ->
            shareVia(activity, R.id.radioButtonCurrent)
            val shared = sharedSudoku(activity)
            shared.modeLevel shouldBe 0
            shared.fields[0].value shouldBe 1
            shared.fields[1].value shouldBe 2
        }

    @Test
    fun `a 16x16 board wires up every extended number button`() =
        launch(formulaicSudoku(SudokuSize.SIXTEEN)) { activity ->
            activity.sudokuButtons.size shouldBe 16
        }

    @Test
    fun `the title reflects a daily sudoku's date`() =
        launch(formulaicSudoku(SudokuSize.FOUR, modeLevel = MODE_DAILY)) { activity ->
            val expected = "Sudoku (${activity.sudoku.created.dateFormatShort})"
            activity.binding.sudokuToolbarLayout.expandedTitle
                .toString() shouldBe expected
        }

    @Test
    fun `the subtitle shows a daily sudoku's fixed error limit instead of the user's`() {
        userSettings.errorLimit = 5
        launch(formulaicSudoku(SudokuSize.FOUR, modeLevel = MODE_DAILY)) { activity ->
            activity.subtitleErrorsSegment() shouldBe "Errors: 0/3"
        }
    }

    @Test
    fun `the title reflects a sudoku level's number`() =
        launch(formulaicSudoku(SudokuSize.FOUR, modeLevel = 3)) { activity ->
            activity.binding.sudokuToolbarLayout.expandedTitle
                .toString() shouldBe "Sudoku (Level 3)"
        }

    @Test
    fun `the title reflects a normal sudoku's difficulty`() =
        launch(formulaicSudoku(SudokuSize.FOUR, modeLevel = MODE_NORMAL)) { activity ->
            activity.binding.sudokuToolbarLayout.expandedTitle
                .toString() shouldBe "Sudoku (Very easy)"
        }

    @Test
    fun `the title is the bare name for a mode level outside normal, daily and level`() =
        launch(formulaicSudoku(SudokuSize.FOUR, modeLevel = -2)) { activity ->
            activity.binding.sudokuToolbarLayout.expandedTitle
                .toString() shouldBe "Sudoku"
        }

    @Test
    fun `the subtitle shows a sudoku level's fixed error limit instead of the user's`() {
        userSettings.errorLimit = 5
        launch(formulaicSudoku(SudokuSize.FOUR, modeLevel = 3)) { activity ->
            activity.subtitleErrorsSegment() shouldBe "Errors: 0/3"
        }
    }

    @Test
    fun `the subtitle omits the error limit for a normal sudoku with an unlimited error setting`() {
        userSettings.errorLimit = 0
        launch(formulaicSudoku(SudokuSize.FOUR, modeLevel = MODE_NORMAL)) { activity ->
            activity.subtitleErrorsSegment() shouldBe "Errors: 0"
        }
    }

    private fun SudokuActivity.subtitleErrorsSegment(): String =
        binding.sudokuToolbarLayout.expandedSubtitle
            .toString()
            .split(" | ")[2]
}
