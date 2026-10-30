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

import android.app.Application
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.backup.BackupSectionLabel
import de.lemke.commonutils.backup.DirectBackupTransaction
import de.lemke.commonutils.backup.FakeBackupDocuments
import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupFileExtension
import de.lemke.commonutils.freshTestPreferences
import de.lemke.sudoku.R
import de.lemke.sudoku.data.FakeDailyNotifications
import de.lemke.sudoku.data.FakeDailyNotifications.AlarmTime
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.backup.SudokuBackupSection
import de.lemke.sudoku.data.database.AppDatabase
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import de.lemke.sudoku.domain.DeleteInvalidSudokusUseCase
import de.lemke.sudoku.domain.IsNotificationPermissionGrantedUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.SetDailyNotificationEnabledUseCase
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.testSudoku
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SettingsViewModelTest {
    private val userSettings = UserSettings(freshTestPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
    private val dailyNotifications = FakeDailyNotifications()
    private val sudokusRepository = FakeSudokusRepository()
    private val database =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val sectionLabels = setOf(BackupSectionLabel("sudokus", R.string.backup_section_sudokus))
    private val viewModel by lazy { newViewModel() }

    private fun newViewModel(dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()): SettingsViewModel {
        val sendDailyNotification = SendDailyNotificationUseCase(dailyNotifications, userSettings)
        return SettingsViewModel(
            userSettings,
            dailyNotifications,
            SetDailyNotificationEnabledUseCase(userSettings, sendDailyNotification),
            IsNotificationPermissionGrantedUseCase(dailyNotifications),
            DeleteInvalidSudokusUseCase(sudokusRepository, dispatcher),
            sectionLabels,
            BackupEngine(
                setOf(SudokuBackupSection(database.sudokuDao(), Clock.systemUTC())),
                DirectBackupTransaction(),
                BackupApp("sudoku", 1, BackupFileExtension(".sudokubackup")),
                { "2026-10-30T12:00:00Z" },
            ),
            FakeBackupDocuments(),
            dispatcher,
        )
    }

    @After
    fun closeDatabase() = database.close()

    private fun storeInvalidSudoku(): SudokuId =
        testSudoku().also { sudokusRepository.insertRows(sudokuWithFieldsToDb(it).copy(fields = emptyList())) }.id

    private fun idleFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun `dailySudokuNotificationHour reads through to userSettings`() {
        userSettings.dailySudokuNotificationHour = 7
        viewModel.dailySudokuNotificationHour shouldBe 7
    }

    @Test
    fun `dailySudokuNotificationMinute reads through to userSettings`() {
        userSettings.dailySudokuNotificationMinute = 45
        viewModel.dailySudokuNotificationMinute shouldBe 45
    }

    @Test
    fun `isDailyNotificationChecked is false when dailySudokuNotificationEnabled is false although system notifications are on`() {
        userSettings.dailySudokuNotificationEnabled = false
        dailyNotifications.blocked = false
        viewModel.isDailyNotificationChecked.shouldBeFalse()
    }

    @Test
    fun `isDailyNotificationChecked is false when system notifications are disabled`() {
        userSettings.dailySudokuNotificationEnabled = true
        dailyNotifications.blocked = true
        viewModel.isDailyNotificationChecked.shouldBeFalse()
    }

    @Test
    fun `isDailyNotificationChecked is true when enabled, notifications are on, and the channel is not silenced`() {
        userSettings.dailySudokuNotificationEnabled = true
        viewModel.isDailyNotificationChecked.shouldBeTrue()
    }

    @Test
    fun `returns Applied and disables the notification when enabled is false`() {
        dailyNotifications.schedule(9, 0)
        val result = viewModel.onDailyNotificationToggleRequested(false)
        result shouldBe DailyNotificationToggleResult.Applied
        userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
        dailyNotifications.scheduled.shouldBeNull()
    }

    @Test
    fun `returns NeedsPermission when enabled is true and permission is not granted`() {
        userSettings.dailySudokuNotificationEnabled = false
        dailyNotifications.permissionGranted = false
        val result = viewModel.onDailyNotificationToggleRequested(true)
        result shouldBe DailyNotificationToggleResult.NeedsPermission
        userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
        dailyNotifications.scheduled.shouldBeNull()
    }

    @Test
    fun `returns SystemNotificationsDisabled when permission is granted but system notifications are off`() {
        userSettings.dailySudokuNotificationEnabled = false
        dailyNotifications.blocked = true
        val result = viewModel.onDailyNotificationToggleRequested(true)
        result shouldBe DailyNotificationToggleResult.SystemNotificationsDisabled
        userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
        dailyNotifications.scheduled.shouldBeNull()
    }

    @Test
    fun `returns Applied and enables the notification when permission and system notifications allow it`() {
        userSettings.dailySudokuNotificationEnabled = false
        val result = viewModel.onDailyNotificationToggleRequested(true)
        result shouldBe DailyNotificationToggleResult.Applied
        userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
        dailyNotifications.scheduled shouldBe AlarmTime(hour = 9, minute = 0)
    }

    @Test
    fun `onNotificationPermissionResult enables the notification when granted`() {
        userSettings.dailySudokuNotificationEnabled = false
        viewModel.onNotificationPermissionResult(true)
        userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
        dailyNotifications.scheduled shouldBe AlarmTime(hour = 9, minute = 0)
    }

    @Test
    fun `onNotificationPermissionResult disables the notification when not granted`() {
        dailyNotifications.schedule(9, 0)
        viewModel.onNotificationPermissionResult(false)
        userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
        dailyNotifications.scheduled.shouldBeNull()
    }

    @Test
    fun `onDailyNotificationTimeSelected writes hour and minute to userSettings and enables the notification`() {
        userSettings.dailySudokuNotificationEnabled = false
        viewModel.onDailyNotificationTimeSelected(14, 30)
        userSettings.dailySudokuNotificationHour shouldBe 14
        userSettings.dailySudokuNotificationMinute shouldBe 30
        userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
        dailyNotifications.scheduled shouldBe AlarmTime(hour = 14, minute = 30)
    }

    @Test
    fun `invalidSudokuDeletion starts Idle`() {
        newViewModel().invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Idle
    }

    @Test
    fun `invalidSudokuDeletion runs the deletion and stays Running until 500 ms have passed, then is Finished`() {
        val invalid = storeInvalidSudoku()
        val settingsViewModel = newViewModel()

        settingsViewModel.onDeleteInvalidSudokusConfirmed()

        sudokusRepository.deletedIds shouldBe listOf(invalid)
        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Running
        idleFor(499)
        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Running
        idleFor(1)
        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Finished
    }

    @Test
    fun `invalidSudokuDeletion deletes once when confirmed a second time while Running`() {
        val invalid = storeInvalidSudoku()
        val settingsViewModel = newViewModel()

        settingsViewModel.onDeleteInvalidSudokusConfirmed()
        storeInvalidSudoku()
        settingsViewModel.onDeleteInvalidSudokusConfirmed()
        idleFor(500)

        sudokusRepository.deletedIds shouldBe listOf(invalid)
        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Finished
    }

    @Test
    fun `invalidSudokuDeletion resets to Idle once the Finished deletion is handled`() {
        val settingsViewModel = newViewModel()
        settingsViewModel.onDeleteInvalidSudokusConfirmed()
        idleFor(500)

        settingsViewModel.onInvalidSudokuDeletionHandled(InvalidSudokuDeletion.Finished)

        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Idle
    }

    @Test
    fun `invalidSudokuDeletion keeps a new deletion Running when a stale handled call arrives`() {
        val first = storeInvalidSudoku()
        val settingsViewModel = newViewModel()
        settingsViewModel.onDeleteInvalidSudokusConfirmed()
        idleFor(500)
        settingsViewModel.onInvalidSudokuDeletionHandled(InvalidSudokuDeletion.Finished)
        val second = storeInvalidSudoku()
        settingsViewModel.onDeleteInvalidSudokusConfirmed()

        settingsViewModel.onInvalidSudokuDeletionHandled(InvalidSudokuDeletion.Finished)

        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Running
        sudokusRepository.deletedIds shouldBe listOf(first, second)
    }
}
