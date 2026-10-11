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
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.FakeDailyNotifications
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.IsNotificationPermissionGrantedUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.SetDailyNotificationEnabledUseCase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class IntroViewModelSavedStateTest {
    private val dailyNotifications = FakeDailyNotifications()
    private val userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(Dispatchers.Main))

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        IntroViewModel(
            SetDailyNotificationEnabledUseCase(userSettings, SendDailyNotificationUseCase(dailyNotifications, userSettings)),
            IsNotificationPermissionGrantedUseCase(dailyNotifications),
            savedStateHandle,
        )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The saved state of [savedStateHandle] after a round trip through a parcel, as the system restores it after process death. */
    private fun restoredHandle(savedStateHandle: SavedStateHandle): SavedStateHandle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(savedStateHandle.savedStateProvider().saveState())
            parcel.setDataPosition(0)
            val savedState: Bundle = requireNotNull(parcel.readBundle(IntroViewModel::class.java.classLoader))
            return SavedStateHandle.createHandle(savedState, null)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `an unhandled saved choice resumes after process death`() {
        val savedStateHandle = SavedStateHandle()
        viewModel(savedStateHandle).onNotificationsDeclined()
        idle()

        viewModel(restoredHandle(savedStateHandle)).notificationChoice.value shouldBe NotificationChoice.Saved
    }

    @Test
    fun `an unhandled permission request resumes after process death`() {
        dailyNotifications.permissionGranted = false
        val savedStateHandle = SavedStateHandle()
        viewModel(savedStateHandle).onNotificationsAccepted()

        viewModel(restoredHandle(savedStateHandle)).notificationChoice.value shouldBe NotificationChoice.RequestPermission
    }

    @Test
    fun `a handled choice restores as pending after process death`() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = viewModel(savedStateHandle)
        viewModel.onNotificationsDeclined()
        idle()
        viewModel.onNotificationChoiceHandled(NotificationChoice.Saved)

        viewModel(restoredHandle(savedStateHandle)).notificationChoice.value shouldBe NotificationChoice.Pending
    }
}
