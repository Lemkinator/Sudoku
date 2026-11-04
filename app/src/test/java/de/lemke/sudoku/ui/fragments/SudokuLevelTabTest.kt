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

package de.lemke.sudoku.ui.fragments

import android.content.Intent
import android.database.sqlite.SQLiteFullException
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.idleMainLooper
import de.lemke.commonutils.returnFromLaunchedScreen
import de.lemke.commonutils.ui.utils.singleLaunch
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.sudoku.TestPersistenceModule
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.DefaultSudokusRepository
import de.lemke.sudoku.data.database.GameDao
import de.lemke.sudoku.data.database.GameEntity
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.di.SolvedBoardGeneratorModule
import de.lemke.sudoku.di.SudokusRepositoryModule
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SolvedBoardGenerator
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.GameSummary
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuListItem.SeparatorItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.MainActivity
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.SudokuLevelActivity
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
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
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(SolvedBoardGeneratorModule::class, SudokusRepositoryModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuLevelTabTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @BindValue
    @JvmField
    val solvedBoardGenerator: SolvedBoardGenerator = PatternSolvedBoardGenerator()

    private var insertGate = CompletableDeferred(Unit)
    private var insertCalls = 0
    private var insertFailure: Exception? = null
    private val database = TestPersistenceModule.provideTestAppDatabase(ApplicationProvider.getApplicationContext())
    private val gameDao = database.gameDao()

    @BindValue
    @JvmField
    val sudokusRepository: SudokusRepository =
        DefaultSudokusRepository(
            object : GameDao by gameDao {
                override suspend fun save(game: GameEntity) {
                    insertCalls++
                    insertFailure?.let { throw it }
                    insertGate.await()
                    gameDao.save(game)
                }
            },
            Clock.systemDefaultZone(),
        )

    @Inject
    lateinit var settings: UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var getMaxSudokuLevel: GetMaxSudokuLevelUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun launch(
        currentLevelTab: Int = 0,
        block: (SudokuLevelTab) -> Unit,
    ) {
        settings.currentLevelTab = currentLevelTab
        ActivityScenario.launch(SudokuLevelActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<SudokuLevelTab>()
                        .first { it.arguments?.getInt(SudokuLevelTab.KEY_SIZE) == 4 }
                block(fragment)
            }
        }
    }

    private fun clickItem(
        fragment: SudokuLevelTab,
        position: Int,
        sudoku: GameSummary,
    ) {
        val holder = fragment.sudokuListAdapter.onCreateViewHolder(fragment.binding.sudokuLevelsRecycler, SudokuItem.VIEW_TYPE)
        fragment.singleLaunch {
            fragment.sudokuListAdapter.onClickItem?.invoke(
                position,
                SudokuItem(sudoku, (sudoku.info.mode as GameMode.Level).number.toString()),
                holder,
            )
        }
        idleMainLooper()
    }

    private fun levelSudoku(
        level: Int,
        completed: Boolean,
    ): Sudoku = testSudoku(size = SudokuSize.FOUR, mode = GameMode.Level(level), difficulty = Difficulty.VERY_EASY, completed = completed)

    @Test
    fun `an offscreen tab shows its loaded level list instead of the progress bar`() =
        launch(currentLevelTab = 1) { fragment ->
            fragment.isResumed.shouldBeFalse()

            fragment.binding.tabLevelProgressBar.isVisible
                .shouldBeFalse()
            fragment.binding.sudokuLevelsRecycler.isVisible
                .shouldBeTrue()
            fragment.sudokuListAdapter.itemCount shouldBe 1
        }

    @Test
    fun `clicking the auto-generated next level at position 0 confirms it and starts the game`() =
        launch { fragment ->
            idleMainLooper()
            fragment.viewModel.state.value.hasNextLevelToStart
                .shouldBeTrue()
            val nextLevel =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).summary

            clickItem(fragment, 0, nextLevel)

            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
        }

    @Test
    fun `confirming the auto-generated next level keeps the level list in place until the game starts`() {
        runBlocking { saveSudoku(levelSudoku(level = 1, completed = true)) }
        launch { fragment ->
            idleMainLooper()
            val nextLevel =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).summary
            nextLevel.info.mode shouldBe GameMode.Level(2)
            fragment.binding.sudokuLevelsRecycler.top shouldBe 0
            insertGate = CompletableDeferred()

            clickItem(fragment, 0, nextLevel)

            fragment.binding.sudokuLevelsRecycler.top shouldBe 0
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)

            insertGate.complete(Unit)
            idleMainLooper()

            runBlocking { getMaxSudokuLevel(SudokuSize.FOUR) } shouldBe 2
            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
        }
    }

    @Test
    fun `tapping the auto-generated next level twice while it saves starts one game and saves it once`() {
        runBlocking { saveSudoku(levelSudoku(level = 1, completed = true)) }
        launch { fragment ->
            idleMainLooper()
            val nextLevel =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).summary
            insertGate = CompletableDeferred()
            insertCalls = 0

            clickItem(fragment, 0, nextLevel)
            clickItem(fragment, 0, nextLevel)
            insertGate.complete(Unit)
            idleMainLooper()

            val startedActivities = generateSequence { shadowOf(fragment.requireActivity()).nextStartedActivity }.toList()
            startedActivities.size shouldBe 1
            startedActivities.single().component?.className shouldBe SudokuActivity::class.java.name
            insertCalls shouldBe 1
        }
    }

    @Test
    fun `tapping the auto-generated next level again after it saved starts one game and saves it once`() {
        runBlocking { saveSudoku(levelSudoku(level = 1, completed = true)) }
        launch { fragment ->
            idleMainLooper()
            val nextLevel =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).summary
            insertCalls = 0

            clickItem(fragment, 0, nextLevel)
            clickItem(fragment, 0, nextLevel)

            val startedActivities = generateSequence { shadowOf(fragment.requireActivity()).nextStartedActivity }.toList()
            startedActivities.size shouldBe 1
            insertCalls shouldBe 1
        }
    }

    @Test
    fun `a failed next-level save starts no game and shows the start error`() {
        runBlocking { saveSudoku(levelSudoku(level = 1, completed = true)) }
        launch { fragment ->
            idleMainLooper()
            val nextLevel =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).summary
            insertFailure = SQLiteFullException("database or disk is full")

            clickItem(fragment, 0, nextLevel)

            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
            runBlocking { getMaxSudokuLevel(SudokuSize.FOUR) } shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Could not start the level"
            fragment.viewModel.levelStart.value shouldBe LevelStart.Idle
        }
    }

    @Test
    fun `a failing query shows the level load error once and marks it handled`() {
        runBlocking { saveSudoku(levelSudoku(level = 1, completed = false)) }
        database.openHelper.writableDatabase.execSQL("DROP TABLE game")
        settings.currentLevelTab = 0
        ActivityScenario.launch(SudokuLevelActivity::class.java).use { scenario ->
            idleMainLooper()

            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Failed to load sudoku levels"
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<SudokuLevelTab>()
                        .first { it.arguments?.getInt(SudokuLevelTab.KEY_SIZE) == 4 }
                fragment.viewModel.loadFailed.value shouldBe false
            }

            scenario.returnFromLaunchedScreen()
            idleMainLooper()

            ShadowToast.shownToastCount() shouldBe 1
        }
    }

    @Test
    fun `tapping a level row twice opens SudokuActivity once`() {
        runBlocking { saveSudoku(levelSudoku(level = 1, completed = false)) }
        launch { fragment ->
            idleMainLooper()
            val row =
                fragment.binding.sudokuLevelsRecycler
                    .findViewHolderForAdapterPosition(0)
                    .shouldNotBeNull()
                    .itemView

            row.performClick()
            row.performClick()
            idleMainLooper()

            val activity = fragment.requireActivity()
            shadowOf(activity)
                .nextStartedActivity
                .shouldNotBeNull()
                .component
                ?.className shouldBe SudokuActivity::class.java.name
            shadowOf(activity).nextStartedActivity shouldBe null
            fragment.viewModel.levelStart.value shouldBe LevelStart.Idle
        }
    }

    @Test
    fun `tapping an existing level twice starts one game`() {
        runBlocking { saveSudoku(levelSudoku(level = 1, completed = false)) }
        launch { fragment ->
            idleMainLooper()
            val level =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).summary

            clickItem(fragment, 0, level)
            clickItem(fragment, 0, level)

            generateSequence { shadowOf(fragment.requireActivity()).nextStartedActivity }.toList().size shouldBe 1
        }
    }

    @Test
    fun `a level tap after returning to the tab starts a game again`() {
        ActivityScenario.launch(SudokuLevelActivity::class.java).use { scenario ->
            idleMainLooper()
            lateinit var fragment: SudokuLevelTab
            scenario.onActivity { activity ->
                fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<SudokuLevelTab>()
                        .single { it.isResumed }
            }
            val firstLevel =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).summary
            clickItem(fragment, 0, firstLevel)
            clickItem(fragment, 0, firstLevel)
            generateSequence { shadowOf(fragment.requireActivity()).nextStartedActivity }.toList().size shouldBe 1

            scenario.returnFromLaunchedScreen()
            idleMainLooper()
            clickItem(fragment, 0, firstLevel)

            generateSequence { shadowOf(fragment.requireActivity()).nextStartedActivity }.toList().size shouldBe 1
        }
    }

    @Test
    fun `a level opened while a launch is pending starts once the activity resumes again`() {
        val level = levelSudoku(level = 1, completed = false)
        runBlocking { saveSudoku(level) }
        settings.currentLevelTab = 0
        ActivityScenario.launch(SudokuLevelActivity::class.java).use { scenario ->
            idleMainLooper()
            lateinit var fragment: SudokuLevelTab
            scenario.onActivity { activity ->
                fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<SudokuLevelTab>()
                        .first { it.arguments?.getInt(SudokuLevelTab.KEY_SIZE) == 4 }
                activity.singleLaunchActivity(Intent(activity, MainActivity::class.java)) shouldBe true
                fragment.viewModel.confirmSudokuStart(0, level.info.id)
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity.component?.className shouldBe MainActivity::class.java.name
                shadowOf(activity).nextStartedActivity shouldBe null
            }
            fragment.viewModel.levelStart.value shouldBe LevelStart.Open(level.info.id)

            scenario.returnFromLaunchedScreen()
            idleMainLooper()

            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                started.component?.className shouldBe SudokuActivity::class.java.name
                started.getStringExtra(KEY_SUDOKU_ID) shouldBe level.info.id.value
                shadowOf(activity).nextStartedActivity shouldBe null
            }
            fragment.viewModel.levelStart.value shouldBe LevelStart.Idle
        }
    }

    @Test
    fun `clicking a level at a non-zero position starts the game without confirming a new one`() {
        runBlocking { saveSudoku(levelSudoku(level = 1, completed = false)) }
        launch { fragment ->
            idleMainLooper()
            val level =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).summary

            clickItem(fragment, 1, level)

            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
        }
    }

    @Test
    fun `clicking an existing level at position 0 starts it without confirming a next level`() {
        runBlocking { saveSudoku(levelSudoku(level = 1, completed = false)) }
        launch { fragment ->
            idleMainLooper()
            fragment.viewModel.state.value.hasNextLevelToStart
                .shouldBeFalse()
            val level =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).summary

            clickItem(fragment, 0, level)

            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
        }
    }

    @Test
    fun `clicking a separator in the level list does not start anything`() =
        launch { fragment ->
            val holder = fragment.sudokuListAdapter.onCreateViewHolder(fragment.binding.sudokuLevelsRecycler, SeparatorItem.VIEW_TYPE)
            fragment.sudokuListAdapter.onClickItem?.invoke(0, SeparatorItem("label"), holder)
            idleMainLooper()

            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
        }
}
