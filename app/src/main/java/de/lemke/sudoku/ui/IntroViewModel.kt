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

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface IntroEvent {
    data object AdvanceOnboarding : IntroEvent

    data object RequestNotificationPermission : IntroEvent
}

@HiltViewModel
class IntroViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userSettings: UserSettings,
    private val sendDailyNotification: SendDailyNotificationUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val openedFromSettings: Boolean = savedStateHandle["openedFromSettings"] ?: false

    private val _events = Channel<IntroEvent>(BUFFERED)
    val events: Flow<IntroEvent> = _events.receiveAsFlow()

    fun onNotificationsDeclined() = setNotificationsEnabledAndAdvance(false)

    fun onNotificationsAccepted() {
        if (SDK_INT < TIRAMISU || ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
            setNotificationsEnabledAndAdvance(true)
        } else {
            viewModelScope.launch { _events.send(IntroEvent.RequestNotificationPermission) }
        }
    }

    fun onNotificationPermissionResult(isGranted: Boolean) = setNotificationsEnabledAndAdvance(isGranted)

    private fun setNotificationsEnabledAndAdvance(enabled: Boolean) {
        userSettings.dailySudokuNotificationEnabled = enabled
        viewModelScope.launch {
            sendDailyNotification.setDailySudokuNotification(enable = enabled)
            _events.send(IntroEvent.AdvanceOnboarding)
        }
    }
}
