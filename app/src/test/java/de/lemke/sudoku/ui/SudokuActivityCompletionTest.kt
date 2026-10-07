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

import android.app.Activity
import android.content.Intent
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.commonutils.ui.utils.showInAppReviewIfPossible
import de.lemke.sudoku.HiltTestRule
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.di.ApplicationScope
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_LEVEL_ERROR_LIMIT
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.utils.applyPlayGamesSync
import dev.oneuiproject.oneui.dialog.ProgressDialog
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
import org.robolectric.shadows.ShadowToast

/** sdk = 36: Robolectric's max supported SDK. */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivityCompletionTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

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

    @BindValue
    @JvmField
    val queueSudokuSave: QueueSudokuSaveUseCase = mockk()

    @BindValue
    @JvmField
    val getMaxSudokuLevel: GetMaxSudokuLevelUseCase = mockk()

    @BindValue
    @JvmField
    val calculatePlayGamesSync: CalculatePlayGamesSyncUseCase = mockk()

    private var maxLevelRead = CompletableDeferred(Unit)

    private var syncCalculated = CompletableDeferred(Unit)

    @Inject
    lateinit var sudokusRepository: SudokusRepository

    @Inject
    lateinit var getAllSudokus: GetAllSudokusUseCase

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Before
    fun setup() {
        hiltRule.inject()
        val queue = QueueSudokuSaveUseCase(sudokusRepository, applicationScope, testDefaultDispatcher)
        every { queueSudokuSave(any(), any()) } answers { queue(firstArg(), secondArg()) }
        every { queueSudokuSave.launch(any(), any()) } answers { queue.launch(firstArg(), secondArg()) }
        coEvery { getMaxSudokuLevel(any()) } coAnswers {
            maxLevelRead.await()
            sudokusRepository.getMaxSudokuLevel(firstArg())
        }
        coEvery { calculatePlayGamesSync(any()) } coAnswers {
            syncCalculated.await()
            CalculatePlayGamesSyncUseCase(getAllSudokus, testDefaultDispatcher)(firstArg())
        }
        settings.bypassOobe()
        // Robolectric skips the Play Games SDK's auto-init ContentProvider.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun almostSolvedSudoku(
        sudokuId: SudokuId,
        modeLevel: Int = MODE_NORMAL,
    ): Sudoku {
        val size = SudokuSize.FOUR
        val blockSize = size.blockSize
        return Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = modeLevel,
            fields =
                MutableList(size.cellCount) { index ->
                    val row = index / size.value
                    val col = index % size.value
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size.value + 1
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
                activity.select(activity.sudoku.itemCount)
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
        runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId)) }
        completeBoard(sudokuId) { activity ->
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog?
            dialog.shouldNotBeNull()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).text shouldBe "New Game"
            activity.viewModel.completion.value shouldBe SudokuCompletion.Summary(activity.sudoku, FollowUp.NEW_GAME)
            activity.viewModel.playGamesSync.value shouldBe null
        }
    }

    @Test
    fun `clicking new game on a completed normal sudoku's dialog starts a fresh one`() {
        val sudokuId = SudokuId.generate()
        runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId)) }
        completeBoard(sudokuId) { activity ->
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            shadowOf(Looper.getMainLooper()).idle()
            activity.sudoku.id shouldNotBe sudokuId
            activity.viewModel.completion.value shouldBe SudokuCompletion.Idle
        }
    }

    @Test
    fun `clicking new game twice on a completed normal sudoku's dialog starts one fresh sudoku`() {
        val sudokuId = SudokuId.generate()
        runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId)) }
        completeBoard(sudokuId) { activity ->
            val newGameButton = (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            newGameButton.performClick()
            newGameButton.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            shadowOf(Looper.getMainLooper()).idle()
            activity.sudoku.id shouldNotBe sudokuId
            runBlocking { getAllSudokus() }.size shouldBe 2
        }
    }

    @Test
    fun `completing the max level of a sudoku level's dialog offers the next level`() {
        val sudokuId = SudokuId.generate()
        runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId, modeLevel = 1)) }
        completeBoard(sudokuId) { activity ->
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            shadowOf(Looper.getMainLooper()).idle()
            activity.sudoku.modeLevel shouldBe 2
        }
    }

    @Test
    fun `completing a level below the max level offers no follow-up`() {
        val sudokuId = SudokuId.generate()
        runBlocking {
            sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId, modeLevel = 1))
            sudokusRepository.saveSudoku(almostSolvedSudoku(SudokuId.generate(), modeLevel = 2))
        }
        completeBoard(sudokuId) {
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isShown shouldBe false
        }
    }

    @Test
    fun `a failed save of a completed sudoku shows an error toast instead of the completion dialog`() {
        val sudoku = almostSolvedSudoku(SudokuId.generate()).apply { fields[0].value = fields[0].solution }
        runBlocking { sudokusRepository.saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            every { queueSudokuSave(sudoku, true) } returns
                CompletableDeferred<Unit>().apply { completeExceptionally(IllegalStateException("disk full")) }

            scenario.onActivity { activity -> activity.viewModel.onCompleted() }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

            scenario.onActivity { activity ->
                ShadowToast.getTextOfLatestToast() shouldBe "Could not save the sudoku"
                ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().shouldBeEmpty()
                activity.viewModel.completion.value shouldBe SudokuCompletion.Idle
                activity.viewModel.playGamesSync.value shouldBe null
            }
        }
    }

    @Test
    fun `recreating the activity while the completion runs shows one dialog after one wrap-up`() {
        val sudokuId = SudokuId.generate()
        runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId, modeLevel = 1)) }
        maxLevelRead = CompletableDeferred()
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.select(0)
                activity.select(activity.sudoku.itemCount)
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
            scenario.onActivity { activity -> activity.viewModel.completion.value shouldBe SudokuCompletion.Running }
            coVerify(exactly = 1) { getMaxSudokuLevel(SudokuSize.FOUR) }
            clearMocks(queueSudokuSave, answers = false)

            scenario.recreate()
            maxLevelRead.complete(Unit)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity { activity ->
                val dialogs = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>()
                dialogs.size shouldBe 1
                dialogs.single().getButton(AlertDialog.BUTTON_POSITIVE).text shouldBe "Next level"
                activity.viewModel.completion.value shouldBe SudokuCompletion.Summary(activity.sudoku, FollowUp.NEXT_LEVEL)
            }
            verify(exactly = 0) { queueSudokuSave(any(), any()) }
            coVerify(exactly = 1) { getMaxSudokuLevel(SudokuSize.FOUR) }
        }
    }

    @Test
    fun `recreating the activity while the completion dialog shows reshows it once without a second wrap-up`() {
        mockkStatic(Activity::applyPlayGamesSync, AppCompatActivity::showInAppReviewIfPossible)
        every { any<Activity>().applyPlayGamesSync(any()) } just Runs
        var reviewRequested = false
        every { any<AppCompatActivity>().showInAppReviewIfPossible(any()) } answers { reviewRequested = true }
        try {
            val sudokuId = SudokuId.generate()
            runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId, modeLevel = 1)) }
            val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
            ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    activity.select(0)
                    activity.select(activity.sudoku.itemCount)
                }
                awaitUntil { reviewRequested && ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().isNotEmpty() }
                val dialog = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().single()
                dialog.isShowing shouldBe true

                scenario.recreate()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
                shadowOf(Looper.getMainLooper()).idle()

                dialog.isShowing shouldBe false
                scenario.onActivity { activity ->
                    val reshown = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().filter { it.isShowing }
                    reshown.size shouldBe 1
                    reshown.single().getButton(AlertDialog.BUTTON_POSITIVE).text shouldBe "Next level"
                    activity.sudoku.id shouldBe sudokuId
                    activity.viewModel.completion.value shouldBe SudokuCompletion.Summary(activity.sudoku, FollowUp.NEXT_LEVEL)
                }
                verify(exactly = 1) { any<Activity>().applyPlayGamesSync(any()) }
                verify(exactly = 1) { any<AppCompatActivity>().showInAppReviewIfPossible(any()) }
                coVerify(exactly = 1) { calculatePlayGamesSync(any()) }
                coVerify(exactly = 1) { getMaxSudokuLevel(SudokuSize.FOUR) }
            }
        } finally {
            unmockkStatic(Activity::applyPlayGamesSync, AppCompatActivity::showInAppReviewIfPossible)
        }
    }

    @Test
    fun `OK closes the completion dialog for good, also after a recreate`() {
        val sudokuId = SudokuId.generate()
        runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId)) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.select(0)
                activity.select(activity.sudoku.itemCount)
            }
            awaitUntil { ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().any { it.isShowing } }
            val dialog = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().single()

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

            dialog.isShowing shouldBe false
            ShadowDialog
                .getShownDialogs()
                .filterIsInstance<AlertDialog>()
                .filter { it.isShowing }
                .shouldBeEmpty()
            scenario.onActivity { activity -> activity.viewModel.completion.value shouldBe SudokuCompletion.Idle }
        }
    }

    @Test
    fun `pausing and resuming while the completion dialog shows keeps that one dialog, and OK still closes it`() {
        val sudokuId = SudokuId.generate()
        runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId)) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.select(0)
                activity.select(activity.sudoku.itemCount)
            }
            awaitUntil { ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().any { it.isShowing } }
            val dialog = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().single()

            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            shadowOf(Looper.getMainLooper()).idle()

            ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>() shouldBe listOf(dialog)
            dialog.isShowing shouldBe true
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            dialog.isShowing shouldBe false
            scenario.onActivity { activity -> activity.viewModel.completion.value shouldBe SudokuCompletion.Idle }
        }
    }

    @Test
    fun `back on the completion dialog closes it for good`() {
        val sudokuId = SudokuId.generate()
        runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId)) }
        completeBoard(sudokuId) { activity ->
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog

            dialog.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()

            dialog.isShowing shouldBe false
            activity.viewModel.completion.value shouldBe SudokuCompletion.Idle
            activity.sudoku.id shouldBe sudokuId
        }
    }

    @Test
    fun `recreating the activity while the game-over dialog shows replaces it with one new one without a restart`() {
        val sudokuId = SudokuId.generate()
        runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId, modeLevel = 1).copy(errorsMade = MODE_LEVEL_ERROR_LIMIT)) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            val dialog = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().single()
            dialog.isShowing shouldBe true

            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            shadowOf(Looper.getMainLooper()).idle()

            dialog.isShowing shouldBe false
            val newDialog = (ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>() - dialog).single()
            newDialog.isShowing shouldBe true
            newDialog.findViewById<TextView>(android.R.id.message)?.text?.toString() shouldBe "Error limit reached (3)."
            scenario.onActivity { activity ->
                activity.sudoku.id shouldBe sudokuId
                activity.sudoku.errorsMade shouldBe MODE_LEVEL_ERROR_LIMIT
                activity.viewModel.game.value
                    .shouldBeInstanceOf<SudokuGame.Playing>()
            }
        }
    }

    @Test
    fun `a restart after OK on the completion dialog waits without a loading dialog until the Play Games sync is calculated`() {
        val sudokuId = SudokuId.generate()
        runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId)) }
        syncCalculated = CompletableDeferred()
        completeBoard(sudokuId) { activity ->
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            activity.viewModel.onRestart()
            shadowOf(Looper.getMainLooper()).idle()

            activity.viewModel.game.value shouldBe SudokuGame.Restarting
            activity.sudoku.completed shouldBe true
            ShadowDialog.getShownDialogs().filterIsInstance<ProgressDialog>().shouldBeEmpty()
            syncCalculated.complete(Unit)
            awaitUntil { activity.viewModel.game.value is SudokuGame.Playing }
            ShadowDialog.getShownDialogs().filterIsInstance<ProgressDialog>().shouldBeEmpty()
            activity.sudoku.completed shouldBe false
        }
    }

    @Test
    fun `recreating the activity while the Play Games sync is calculated applies it once, then asks for the in-app review`() {
        mockkStatic(Activity::applyPlayGamesSync, AppCompatActivity::showInAppReviewIfPossible)
        every { any<Activity>().applyPlayGamesSync(any()) } just Runs
        var reviewRequested = false
        every { any<AppCompatActivity>().showInAppReviewIfPossible(any()) } answers { reviewRequested = true }
        try {
            val sudokuId = SudokuId.generate()
            runBlocking { sudokusRepository.saveSudoku(almostSolvedSudoku(sudokuId)) }
            syncCalculated = CompletableDeferred()
            val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
            ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    activity.select(0)
                    activity.select(activity.sudoku.itemCount)
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
                scenario.onActivity { activity ->
                    activity.viewModel.completion.value shouldBe SudokuCompletion.Summary(activity.sudoku, FollowUp.NEW_GAME)
                }
                coVerify(exactly = 1) { calculatePlayGamesSync(any()) }

                scenario.recreate()
                syncCalculated.complete(Unit)
                awaitUntil { reviewRequested }

                scenario.onActivity { activity -> activity.viewModel.playGamesSync.value shouldBe null }
                verify(exactly = 1) { any<Activity>().applyPlayGamesSync(any()) }
                verify(exactly = 1) { any<AppCompatActivity>().showInAppReviewIfPossible(any()) }
                verifyOrder {
                    any<Activity>().applyPlayGamesSync(any())
                    any<AppCompatActivity>().showInAppReviewIfPossible(any())
                }
                coVerify(exactly = 1) { calculatePlayGamesSync(any()) }
            }
        } finally {
            unmockkStatic(Activity::applyPlayGamesSync, AppCompatActivity::showInAppReviewIfPossible)
        }
    }

    // The Play Games sync and the restart save run on a real background dispatcher that idle() does not wait for.
    private fun awaitUntil(condition: () -> Boolean) {
        repeat(AWAIT_ATTEMPTS) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                return
            }
            Thread.sleep(AWAIT_STEP_MS)
        }
        error("condition not met within ${AWAIT_ATTEMPTS * AWAIT_STEP_MS} ms")
    }

    private companion object {
        const val AWAIT_ATTEMPTS = 200
        const val AWAIT_STEP_MS = 25L
    }
}
