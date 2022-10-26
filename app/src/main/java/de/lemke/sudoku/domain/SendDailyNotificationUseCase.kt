package de.lemke.sudoku.domain

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.sudoku.R
import de.lemke.sudoku.ui.DailySudokuActivity
import de.lemke.sudoku.ui.utils.AlarmReceiver
import java.util.*
import javax.inject.Inject

class SendDailyNotificationUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private val channelId = context.getString(R.string.daily_sudoku_notification_channel_id)
    private val notificationId = 5
    private val dailySudokuNotificationRequestCode = 55
    private val hour = 21
    private val minute = 50

    operator fun invoke() {
        createNotificationChannel()
        initNotificationBuilder()
        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            NotificationManagerCompat.from(context).notify(notificationId, notificationBuilder.build())
        }
    }

    private fun createNotificationChannel() {
        val name = context.getString(R.string.daily_sudoku_notification_channel_name)
        val descriptionText = context.getString(R.string.daily_sudoku_notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }


    private fun initNotificationBuilder() {
        // Create an explicit intent for an Activity in your app
        // Create an Intent for the activity you want to start
        val resultIntent = Intent(context, DailySudokuActivity::class.java)
        // Create the TaskStackBuilder
        val resultPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            // Add the intent, which inflates the back stack
            addNextIntentWithParentStack(resultIntent)
            // Get the PendingIntent containing the entire back stack
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(context.getString(R.string.daily_sudoku))
            .setContentText(context.getString(R.string.daily_sudoku_notification_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(resultPendingIntent)
            // Automatically removes the notification when the user taps it.
            .setAutoCancel(true)
    }

    fun setDailySudokuNotification(enable: Boolean) =
        if (enable) enableDailySudokuNotification() else disableDailySudokuNotification()

    fun enableDailySudokuNotification(skipToday: Boolean = false) {
        createNotificationChannel()
        initNotificationBuilder()
        val alarmIntent = Intent(context.applicationContext, AlarmReceiver::class.java).let { intent ->
            PendingIntent.getBroadcast(
                context.applicationContext,
                dailySudokuNotificationRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val calendar: Calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        // If the trigger time you specify is in the past, the alarm triggers immediately. if soo just add one day to required calendar
        // Note: also adding 1 min cuz if user clicks on notification as soon as received it it will reschedule the alarm to
        // fire another notification immediately
        if (Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }.timeInMillis - calendar.timeInMillis > 0 || skipToday) {
            calendar.add(Calendar.DATE, 1)
        }
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
    }

    private fun disableDailySudokuNotification() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).let { intent ->
            PendingIntent.getBroadcast(context, dailySudokuNotificationRequestCode, intent, 0 or PendingIntent.FLAG_IMMUTABLE)
        }
        alarmManager.cancel(intent)
    }
}