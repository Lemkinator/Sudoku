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
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.awaitMainLooperUntil
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.commonutils.returnFromLaunchedScreen
import de.lemke.commonutils.ui.utils.FakeInAppReview
import de.lemke.commonutils.ui.utils.FakeInAppReview.Call.ShowIfPossible
import de.lemke.sudoku.R
import de.lemke.sudoku.data.PendingPlayGamesWins
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.di.GamesSignInModule
import de.lemke.sudoku.di.SudokusRepositoryModule
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_LEVEL_ERROR_LIMIT
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.utils.DefaultGamesSignIn
import de.lemke.sudoku.ui.utils.DefaultPlayGamesIntentProvider
import de.lemke.sudoku.ui.utils.FakePlayGamesSyncApplier
import de.lemke.sudoku.ui.utils.GamesSignIn
import de.lemke.sudoku.ui.utils.PlayGamesIntentProvider
import de.lemke.sudoku.ui.utils.PlayGamesSyncApplier
import dev.oneuiproject.oneui.dialog.ProgressDialog
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenu
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(GamesSignInModule::class, SudokusRepositoryModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivityCompletionTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var pendingWins: PendingPlayGamesWins

    private val fakeSudokusRepository = FakeSudokusRepository()

    @BindValue
    @JvmField
    val sudokusRepository: SudokusRepository = fakeSudokusRepository

    @Inject
    lateinit var fakeInAppReview: FakeInAppReview

    private val fakePlayGamesSyncApplier = FakePlayGamesSyncApplier()

    private val reviewCallsWhenApplied = mutableListOf<Int>()

    @BindValue
    @JvmField
    val playGamesSyncApplier: PlayGamesSyncApplier =
        object : PlayGamesSyncApplier by fakePlayGamesSyncApplier {
            override fun apply(
                activity: Activity,
                sync: PlayGamesSync,
            ) {
                reviewCallsWhenApplied += fakeInAppReview.calls.size
                fakePlayGamesSyncApplier.apply(activity, sync)
            }
        }

    @BindValue
    @JvmField
    val gamesSignIn: GamesSignIn = DefaultGamesSignIn()

    @BindValue
    @JvmField
    val playGamesIntentProvider: PlayGamesIntentProvider = DefaultPlayGamesIntentProvider()

    private var maxLevelRead = CompletableDeferred(Unit)

    private var syncCalculated = CompletableDeferred(Unit)

    private val maxLevelReads = AtomicInteger()

    private val syncCalculations = AtomicInteger()

    @Before
    fun setup() {
        hiltRule.inject()
        fakeSudokusRepository.beforeGetMaxSudokuLevel = {
            maxLevelReads.incrementAndGet()
            maxLevelRead.await()
        }
        fakeSudokusRepository.beforeGetCompletedSummaries = {
            syncCalculations.incrementAndGet()
            syncCalculated.await()
        }
        settings.bypassOobe()
        // Robolectric skips the Play Games SDK's auto-init ContentProvider.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun almostSolvedSudoku(
        sudokuId: SudokuId,
        mode: GameMode = GameMode.Normal,
    ): Sudoku {
        val solved =
            testSudoku(size = SudokuSize.FOUR, mode = mode, difficulty = Difficulty.VERY_EASY, sudokuId = sudokuId, completed = true)
        return solved.copy(board = solved.board.with(0, Cell(solved.board[0].solution)))
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
                activity.select(activity.sudoku.info.size.cellCount)
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.sudoku.completed.shouldBeTrue()
                block(activity)
            }
        }
    }

    @Test
    fun `completing a normal sudoku with animations enabled shows the completion dialog`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId))
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
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId))
        completeBoard(sudokuId) { activity ->
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            idleMainLooper()
            activity.sudoku.info.id shouldNotBe sudokuId
            activity.viewModel.completion.value shouldBe SudokuCompletion.Idle
        }
    }

    @Test
    fun `clicking new game twice on a completed normal sudoku's dialog starts one fresh sudoku`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId))
        completeBoard(sudokuId) { activity ->
            val newGameButton = (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            newGameButton.performClick()
            newGameButton.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            idleMainLooper()
            activity.sudoku.info.id shouldNotBe sudokuId
            runBlocking { sudokusRepository.observeAllSummaries().first() }.size shouldBe 2
        }
    }

    @Test
    fun `completing the max level of a sudoku level's dialog offers the next level`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId, mode = GameMode.Level(1)))
        completeBoard(sudokuId) { activity ->
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            idleMainLooper()
            activity.sudoku.info.mode shouldBe GameMode.Level(2)
        }
    }

    @Test
    fun `completing a level below the max level offers no follow-up`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(
            almostSolvedSudoku(sudokuId, mode = GameMode.Level(1)),
            almostSolvedSudoku(SudokuId.generate(), mode = GameMode.Level(2)),
        )
        completeBoard(sudokuId) {
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isShown shouldBe false
        }
    }

    @Test
    fun `a failed save of a completed sudoku shows an error toast instead of the completion dialog`() {
        val sudoku = almostSolvedSudoku(SudokuId.generate())
        fakeSudokusRepository.store(sudoku)
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.info.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            fakeSudokusRepository.saveFailure = IllegalStateException("disk full")

            scenario.onActivity { activity ->
                activity.select(0)
                activity.select(activity.sudoku.info.size.cellCount)
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2500))

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
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId, mode = GameMode.Level(1)))
        maxLevelRead = CompletableDeferred()
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.select(0)
                activity.select(activity.sudoku.info.size.cellCount)
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
            scenario.onActivity { activity -> activity.viewModel.completion.value shouldBe SudokuCompletion.Running }
            maxLevelReads.get() shouldBe 1
            val savesBeforeRecreate = fakeSudokusRepository.saves

            scenario.recreate()
            maxLevelRead.complete(Unit)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            idleMainLooper()

            scenario.onActivity { activity ->
                val dialogs = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>()
                dialogs.size shouldBe 1
                dialogs.single().getButton(AlertDialog.BUTTON_POSITIVE).text shouldBe "Next level"
                activity.viewModel.completion.value shouldBe SudokuCompletion.Summary(activity.sudoku, FollowUp.NEXT_LEVEL)
            }
            fakeSudokusRepository.saves shouldBe savesBeforeRecreate
            maxLevelReads.get() shouldBe 1
        }
    }

    @Test
    fun `leaving the activity while the winning move animates saves the win and the next activity applies the sync once`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId))
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        val saved = CompletableDeferred<Unit>()
        fakeSudokusRepository.beforeSave = { saved.await() }
        Robolectric.buildActivity(SudokuActivity::class.java, intent).use { controller ->
            controller.setup()
            idleMainLooper()
            val animating = controller.get()
            animating.select(0)
            animating.select(animating.sudoku.info.size.cellCount)

            animating.onBackPressedDispatcher.onBackPressed()
            controller.pause()
            controller.destroy()
            idleMainLooper()
            pendingWins.all() shouldBe setOf(sudokuId)
            fakePlayGamesSyncApplier.applied.size shouldBe 0

            saved.complete(Unit)
            idleMainLooper()
            fakeSudokusRepository.saves.count { it.completed } shouldBe 1
            fakePlayGamesSyncApplier.applied.size shouldBe 0
            pendingWins.all() shouldBe setOf(sudokuId)
        }

        ActivityScenario.launch<SudokuActivity>(Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)).use {
            idleMainLooper()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            idleMainLooper()

            fakePlayGamesSyncApplier.applied.size shouldBe 1
            fakePlayGamesSyncApplier.applied
                .single()
                .achievementUnlocks
                .first() shouldBe R.string.achievement_first_win
            pendingWins.all() shouldBe emptySet()
        }
        ActivityScenario.launch<SudokuActivity>(Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)).use {
            idleMainLooper()
            fakePlayGamesSyncApplier.applied.size shouldBe 1
        }
    }

    private fun SudokuActivity.resetVisible(): Boolean {
        val menu = RoboMenu(this)
        val reset = menu.add(0, R.id.menu_reset, 0, "Restart")
        onPrepareOptionsMenu(menu)
        return reset.isVisible
    }

    @Test
    fun `recreating the activity while the winning move animates wraps up once and keeps reset hidden until the dialog is dismissed`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId))
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        Robolectric.buildActivity(SudokuActivity::class.java, intent).use { controller ->
            controller.setup()
            idleMainLooper()
            val animating = controller.get()
            animating.select(0)
            animating.select(animating.sudoku.info.size.cellCount)
            animating.viewModel.completion.value shouldNotBe SudokuCompletion.Idle
            animating.resetVisible() shouldBe false

            controller.recreate()
            repeat(3) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)) }
            idleMainLooper()

            val activity = controller.get()
            val dialogs = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().filter { it.isShowing }
            dialogs.size shouldBe 1
            activity.viewModel.completion.value shouldBe SudokuCompletion.Summary(activity.sudoku, FollowUp.NEW_GAME)
            activity.resetVisible() shouldBe false
            dialogs.single().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            idleMainLooper()
            activity.resetVisible() shouldBe true
            fakeSudokusRepository.saves.count { it.completed } shouldBe 1
            fakePlayGamesSyncApplier.applied.size shouldBe 1
            syncCalculations.get() shouldBe 1
        }
    }

    @Test
    fun `recreating the activity while the completion dialog shows reshows it once without a second wrap-up`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId, mode = GameMode.Level(1)))
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.select(0)
                activity.select(activity.sudoku.info.size.cellCount)
            }
            awaitMainLooperUntil {
                fakeInAppReview.calls.isNotEmpty() &&
                    ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().isNotEmpty()
            }
            val dialog = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().single()
            dialog.isShowing shouldBe true

            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            idleMainLooper()

            dialog.isShowing shouldBe false
            scenario.onActivity { activity ->
                val reshown = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().filter { it.isShowing }
                reshown.size shouldBe 1
                reshown.single().getButton(AlertDialog.BUTTON_POSITIVE).text shouldBe "Next level"
                activity.sudoku.info.id shouldBe sudokuId
                activity.viewModel.completion.value shouldBe SudokuCompletion.Summary(activity.sudoku, FollowUp.NEXT_LEVEL)
            }
            fakePlayGamesSyncApplier.applied.size shouldBe 1
            fakeInAppReview.calls.single().shouldBeInstanceOf<ShowIfPossible>()
            syncCalculations.get() shouldBe 1
            maxLevelReads.get() shouldBe 1
        }
    }

    @Test
    fun `OK closes the completion dialog for good, also after a recreate`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId))
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.select(0)
                activity.select(activity.sudoku.info.size.cellCount)
            }
            awaitMainLooperUntil { ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().any { it.isShowing } }
            val dialog = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().single()

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            idleMainLooper()
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
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId))
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.select(0)
                activity.select(activity.sudoku.info.size.cellCount)
            }
            awaitMainLooperUntil { ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().any { it.isShowing } }
            val dialog = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().single()

            scenario.returnFromLaunchedScreen()
            idleMainLooper()

            ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>() shouldBe listOf(dialog)
            dialog.isShowing shouldBe true
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            idleMainLooper()
            dialog.isShowing shouldBe false
            scenario.onActivity { activity -> activity.viewModel.completion.value shouldBe SudokuCompletion.Idle }
        }
    }

    @Test
    fun `back on the completion dialog closes it for good`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId))
        completeBoard(sudokuId) { activity ->
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog

            dialog.onBackPressedDispatcher.onBackPressed()
            idleMainLooper()

            dialog.isShowing shouldBe false
            activity.viewModel.completion.value shouldBe SudokuCompletion.Idle
            activity.sudoku.info.id shouldBe sudokuId
        }
    }

    @Test
    fun `recreating the activity while the game-over dialog shows dismisses it and keeps the lost game without a new dialog`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(
            almostSolvedSudoku(sudokuId, mode = GameMode.Level(1)).let {
                it.copy(info = it.info.copy(errorsMade = MODE_LEVEL_ERROR_LIMIT))
            },
        )
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            idleMainLooper()

            ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().filter { it.isShowing } shouldBe emptyList()
            scenario.onActivity { activity ->
                activity.sudoku.info.id shouldBe sudokuId
                activity.sudoku.info.errorsMade shouldBe MODE_LEVEL_ERROR_LIMIT
                activity.viewModel.game.value
                    .shouldBeInstanceOf<SudokuGame.Playing>()
                    .phase shouldBe Phase.Lost
            }
        }
    }

    @Test
    fun `a restart after OK on the completion dialog waits without a loading dialog until the Play Games sync is calculated`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId))
        syncCalculated = CompletableDeferred()
        completeBoard(sudokuId) { activity ->
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            idleMainLooper()

            activity.viewModel.onRestart()
            idleMainLooper()

            activity.viewModel.game.value shouldBe SudokuGame.Restarting
            fakeSudokusRepository.saves.last().completed shouldBe true
            ShadowDialog.getShownDialogs().filterIsInstance<ProgressDialog>().shouldBeEmpty()
            syncCalculated.complete(Unit)
            awaitMainLooperUntil { activity.viewModel.game.value is SudokuGame.Playing }
            ShadowDialog.getShownDialogs().filterIsInstance<ProgressDialog>().shouldBeEmpty()
            activity.sudoku.completed shouldBe false
            fakeSudokusRepository.saves.last().completed shouldBe false
        }
    }

    @Test
    fun `recreating the activity while the Play Games sync is calculated applies it once, then asks for the in-app review`() {
        val sudokuId = SudokuId.generate()
        fakeSudokusRepository.store(almostSolvedSudoku(sudokuId))
        syncCalculated = CompletableDeferred()
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuId.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.select(0)
                activity.select(activity.sudoku.info.size.cellCount)
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
            scenario.onActivity { activity ->
                activity.viewModel.completion.value shouldBe SudokuCompletion.Summary(activity.sudoku, FollowUp.NEW_GAME)
            }
            syncCalculations.get() shouldBe 1

            scenario.recreate()
            syncCalculated.complete(Unit)
            awaitMainLooperUntil { fakeInAppReview.calls.isNotEmpty() }

            scenario.onActivity { activity -> activity.viewModel.playGamesSync.value shouldBe null }
            fakePlayGamesSyncApplier.applied.size shouldBe 1
            reviewCallsWhenApplied shouldBe listOf(0)
            fakeInAppReview.calls.single().shouldBeInstanceOf<ShowIfPossible>()
            syncCalculations.get() shouldBe 1
        }
    }
}
