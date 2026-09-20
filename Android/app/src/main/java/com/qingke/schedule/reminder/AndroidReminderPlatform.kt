package com.qingke.schedule.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.qingke.schedule.MainActivity
import com.qingke.schedule.R

/**
 * A08: AlarmManager backend. Every alarm is an explicit [CourseReminderReceiver] PendingIntent whose identity is
 * the reminder URI (alarm data), never a string-hash request code, and it is immutable. Exactness comes from
 * [ReminderAlarm.exact], which the coordinator derived from the SCHEDULE_EXACT_ALARM capability (D03), and is
 * re-checked here because the capability can be revoked at any time.
 */
internal class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    override fun schedule(alarm: ReminderAlarm) {
        val pending = pendingIntent(alarm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (alarm.exact && canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.fireAt.toEpochMilli(), pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.fireAt.toEpochMilli(), pending)
        }
    }

    override fun cancel(uri: String) {
        val intent = baseIntent().setData(Uri.parse(uri))
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    override fun isRegistered(uri: String): Boolean {
        val intent = baseIntent().setData(Uri.parse(uri))
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null
    }

    private fun pendingIntent(alarm: ReminderAlarm, flags: Int): PendingIntent =
        PendingIntent.getBroadcast(context, REQUEST_CODE, baseIntent().setData(Uri.parse(alarm.uri)).putExtras(alarm.toExtras()), flags)

    private fun baseIntent(): Intent = Intent(context, CourseReminderReceiver::class.java).setAction(CourseReminderReceiver.ACTION_COURSE_REMINDER)

    companion object {
        /** Alarm identity lives in the intent data, so one constant request code is enough. */
        const val REQUEST_CODE = 0
    }
}

/**
 * A08: NotificationManager backend. Notifications are addressed by their full reminder URI as the notification
 * tag (id 0), so two different reminders can never overwrite or cancel each other through a hashCode collision.
 *
 * The channel id defaults to the shared "上课提醒" channel; device tests inject a private probe id because the
 * platform preserves a switched-off channel across an app-side delete/recreate, so pushing the shared channel to
 * IMPORTANCE_NONE would break every later reminder in the same run.
 */
internal class AndroidNotificationPresenter(
    private val context: Context,
    private val channelId: String = ReminderNotifications.CHANNEL_ID,
) : NotificationPresenter {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    override fun ensureChannel(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    ReminderNotifications.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = ReminderNotifications.CHANNEL_DESCRIPTION },
            )
        }
        val channel = manager.getNotificationChannel(channelId) ?: return false
        // A channel the user switched off reports IMPORTANCE_NONE and can never deliver a reminder.
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    override fun isChannelReady(): Boolean =
        manager.getNotificationChannel(channelId)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } ?: false

    /** The runtime POST_NOTIFICATIONS permission plus the app level notification switch. */
    override fun areNotificationsPermitted(): Boolean =
        notificationPermissionGranted() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun notificationPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun notify(alarm: ReminderAlarm): Boolean {
        if (!ensureChannel()) return false
        // The permission is re-checked here (not only through the helper) so the platform call is guarded.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(alarm.title)
            .setContentText(alarm.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alarm.body))
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .build()
        NotificationManagerCompat.from(context).notify(alarm.uri, NOTIFICATION_ID, notification)
        return true
    }

    override fun cancelNotification(uri: String) {
        NotificationManagerCompat.from(context).cancel(uri, NOTIFICATION_ID)
    }

    private fun launchIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        /** One id for every reminder: the URI tag distinguishes them. */
        const val NOTIFICATION_ID = 0
    }
}
