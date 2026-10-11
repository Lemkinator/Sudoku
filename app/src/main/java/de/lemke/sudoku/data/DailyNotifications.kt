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

package de.lemke.sudoku.data

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_NONE
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.sudoku.R
import de.lemke.sudoku.receivers.AlarmReceiver
import java.time.Clock
import java.util.Calendar
import javax.inject.Inject

/** Shows the daily sudoku notification and schedules the alarm that triggers it. */
interface DailyNotifications {
    fun isPermissionGranted(): Boolean

    /** Whether the system keeps the daily notification from the user: notifications, its channel or the permission is off. */
    fun isBlocked(): Boolean

    /** Schedules the next daily alarm at [hour]:[minute], on the next day once that time is less than a minute away. */
    fun schedule(
        hour: Int,
        minute: Int,
    )

    fun cancel()

    fun post()
}

class DefaultDailyNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : DailyNotifications {
    private val channelId = context.getString(R.string.daily_sudoku_notification_channel_id)

    override fun isPermissionGranted(): Boolean =
        SDK_INT < TIRAMISU || ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED

    override fun isBlocked(): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        return when {
            !notificationManager.areNotificationsEnabled() -> true
            notificationManager.getNotificationChannel(channelId)?.importance == IMPORTANCE_NONE -> true
            SDK_INT >= TIRAMISU -> ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PERMISSION_GRANTED
            else -> false
        }
    }

    override fun post() {
        createNotificationChannel()
        if (ActivityCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, createNotificationBuilder().build())
        }
    }

    override fun schedule(
        hour: Int,
        minute: Int,
    ) {
        createNotificationChannel()
        val alarmIntent = createAlarmIntent()
        val calendar: Calendar =
            nowCalendar().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
        // If the trigger time you specify is in the past, the alarm triggers immediately. if soo just add one day to required calendar
        // Note: also adding 1 min cuz if user clicks on notification as soon as received it will reschedule the alarm to
        // fire another notification immediately
        if (nowCalendar().apply { add(Calendar.MINUTE, 1) }.timeInMillis - calendar.timeInMillis > 0) {
            calendar.add(Calendar.DATE, 1)
        }
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
    }

    override fun cancel() {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(createAlarmIntent())
    }

    private fun createNotificationChannel() {
        val name = context.getString(R.string.daily_sudoku_notification_channel_name)
        val descriptionText = context.getString(R.string.daily_sudoku_notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel =
            NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
        // Register the channel with the system
        val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val resultPendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(context.getString(R.string.daily_sudoku))
            .setContentText(context.getString(R.string.daily_sudoku_notification_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(resultPendingIntent)
            // Automatically removes the notification when the user taps it.
            .setAutoCancel(true)
    }

    private fun nowCalendar(): Calendar = Calendar.getInstance().apply { timeInMillis = clock.millis() }

    private fun createAlarmIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext,
            DAILY_SUDOKU_NOTIFICATION_REQUEST_CODE,
            Intent(context.applicationContext, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val NOTIFICATION_ID = 5
        const val DAILY_SUDOKU_NOTIFICATION_REQUEST_CODE = 55
    }
}
