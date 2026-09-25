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
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.DeleteSudokusUseCase
import de.lemke.sudoku.domain.ObserveSudokuHistoryUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.ui.MainActivity
import de.lemke.sudoku.ui.utils.awaitSmallText
import de.lemke.sudoku.ui.utils.listSudoku
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

/** sdk = 36: Robolectric's max supported SDK. */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class TabHistoryListRefreshTest {
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
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var observeSudokuHistory: ObserveSudokuHistoryUseCase

    @Inject
    lateinit var deleteSudokus: DeleteSudokusUseCase

    private val playedId = SudokuId.generate()

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        // Robolectric skips the Play Games SDK's auto-init ContentProvider under HiltTestApplication.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun olderSudoku(): Sudoku =
        listSudoku(
            sudokuId = SudokuId.generate(),
            modeLevel = MODE_NORMAL,
            filled = 0,
            errorsMade = 0,
            seconds = 0,
            created = LocalDateTime.of(2026, 1, 15, 9, 0),
        )

    private fun playedSudoku(
        filled: Int,
        errorsMade: Int,
        seconds: Int,
        updated: LocalDateTime,
    ): Sudoku =
        listSudoku(
            sudokuId = playedId,
            modeLevel = MODE_NORMAL,
            filled = filled,
            errorsMade = errorsMade,
            seconds = seconds,
            created = LocalDateTime.of(2026, 1, 15, 10, 0),
            updated = updated,
        )

    private fun save(sudoku: Sudoku) = runBlocking { saveSudoku(sudoku) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `the history carries the new stats when the top sudoku is saved again under the same id`() {
        save(olderSudoku())
        save(playedSudoku(filled = 0, errorsMade = 0, seconds = 0, updated = LocalDateTime.of(2026, 1, 15, 10, 0)))
        val viewModel = TabHistoryViewModel(userSettings, observeSudokuHistory, deleteSudokus)
        idle()

        save(playedSudoku(filled = 16, errorsMade = 2, seconds = 75, updated = LocalDateTime.of(2026, 1, 15, 10, 30)))
        idle()

        val sudoku =
            viewModel.sudokuHistory.value
                .filterIsInstance<SudokuItem>()
                .first()
                .sudoku
        sudoku.id shouldBe playedId
        sudoku.errorsMade shouldBe 2
        sudoku.seconds shouldBe 75
        sudoku.progress shouldBe 100
        sudoku.completed shouldBe true
    }

    @Test
    fun `resuming the history shows the stats saved while it was stopped`() {
        save(olderSudoku())
        save(playedSudoku(filled = 0, errorsMade = 0, seconds = 0, updated = LocalDateTime.of(2026, 1, 15, 10, 0)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.onTabItemSelected(0) }
            idle()
            scenario.onActivity { activity ->
                historyList(activity).awaitSmallText(1, "00:00 | 0% | Errors: 0/3 | Hints: 0") shouldBe
                    "00:00 | 0% | Errors: 0/3 | Hints: 0"
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            save(playedSudoku(filled = 16, errorsMade = 2, seconds = 75, updated = LocalDateTime.of(2026, 1, 15, 10, 30)))
            idle()
            scenario.moveToState(Lifecycle.State.RESUMED)
            idle()

            scenario.onActivity { activity ->
                historyList(activity).awaitSmallText(1, "01:15 | Errors: 2/3 | Hints: 0") shouldBe "01:15 | Errors: 2/3 | Hints: 0"
            }
        }
    }

    private fun historyList(activity: MainActivity) =
        activity.supportFragmentManager.fragments
            .filterIsInstance<TabHistory>()
            .first()
            .binding.sudokuHistoryList
}
