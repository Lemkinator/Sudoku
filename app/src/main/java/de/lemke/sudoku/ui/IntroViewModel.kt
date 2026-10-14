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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.sudoku.domain.IsNotificationPermissionGrantedUseCase
import de.lemke.sudoku.domain.SetDailyNotificationEnabledUseCase
import java.io.Serializable
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The answer to the notifications question. The activity acts on a [Result] and then reports it handled.
 * An unhandled [Result] stays in the saved state, so the intro resumes it after process death.
 */
sealed interface NotificationChoice : Serializable {
    sealed interface Result : NotificationChoice

    data object Pending : NotificationChoice

    data object RequestPermission : Result

    data object Saved : Result
}

private const val KEY_NOTIFICATION_CHOICE = "notification_choice"

@HiltViewModel
class IntroViewModel @Inject constructor(
    private val setDailyNotificationEnabled: SetDailyNotificationEnabledUseCase,
    private val isNotificationPermissionGranted: IsNotificationPermissionGrantedUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val openedFromSettings: Boolean = savedStateHandle[IntroActivity.KEY_OPENED_FROM_SETTINGS] ?: false

    val notificationChoice: StateFlow<NotificationChoice>
        field = savedStateHandle.getMutableStateFlow<NotificationChoice>(KEY_NOTIFICATION_CHOICE, NotificationChoice.Pending)

    fun onNotificationsDeclined() = setNotificationsEnabledAndAdvance(false)

    fun onNotificationsAccepted() {
        if (isNotificationPermissionGranted()) {
            setNotificationsEnabledAndAdvance(true)
        } else {
            notificationChoice.value = NotificationChoice.RequestPermission
        }
    }

    fun onNotificationPermissionResult(isGranted: Boolean) = setNotificationsEnabledAndAdvance(isGranted)

    fun onNotificationChoiceHandled(result: NotificationChoice.Result) {
        notificationChoice.update { if (it == result) NotificationChoice.Pending else it }
    }

    private fun setNotificationsEnabledAndAdvance(enabled: Boolean) {
        viewModelScope.launch {
            setDailyNotificationEnabled(enabled)
            notificationChoice.value = NotificationChoice.Saved
        }
    }
}
