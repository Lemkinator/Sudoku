package de.lemke.sudoku.ui.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.IsDailySudokuCompletedUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sendDailyNotification: SendDailyNotificationUseCase

    @Inject
    lateinit var isDailySudokuCompleted: IsDailySudokuCompletedUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    /**
     * sends notification when receives alarm
     * and then reschedule the reminder again
     * */
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        GlobalScope.launch {
            val notificationEnabled = getUserSettings().dailySudokuNotificationEnabled
            if (
                !intent.action.equals(Intent.ACTION_BOOT_COMPLETED) &&
                !intent.action.equals(Intent.ACTION_MY_PACKAGE_REPLACED) &&
                notificationEnabled &&
                !isDailySudokuCompleted()
            ) {
                sendDailyNotification()
            }
            sendDailyNotification.setDailySudokuNotification(enable = notificationEnabled)
        }
    }
}
