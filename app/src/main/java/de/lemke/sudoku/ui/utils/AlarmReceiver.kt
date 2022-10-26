package de.lemke.sudoku.ui.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {


    @Inject
    lateinit var sendDailyNotification: SendDailyNotificationUseCase
    /**
     * sends notification when receives alarm
     * and then reschedule the reminder again
     * */
    override fun onReceive(context: Context, intent: Intent) {
        sendDailyNotification()
        sendDailyNotification.enableDailySudokuNotification() //reschedule the reminder
    }
}
