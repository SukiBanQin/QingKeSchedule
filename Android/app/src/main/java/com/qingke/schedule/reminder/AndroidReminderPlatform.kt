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
 * [ReminderAlarm.exact], which the coordinator derived from the SCHEDULE_EXACT_ALARM capability (D03).
 */
internal class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    override fun schedule(alarm: ReminderAlarm) {
        val pending = pendingIntent(alarm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // D03: the capability is re-checked here as well, because it can be revoked between planning and
        // scheduling; a revoked capability must degrade to an inexact reminder instead of throwing.
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

/** A08: NotificationManager backend: one "上课提醒" channel plus plain notification posting. */
internal class AndroidNotificationPresenter(private val context: Context) : NotificationPresenter {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    override fun ensureChannel(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (manager.getNotificationChannel(ReminderNotifications.CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ReminderNotifications.CHANNEL_ID,
                    ReminderNotifications.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = ReminderNotifications.CHANNEL_DESCRIPTION },
            )
        }
        return manager.getNotificationChannel(ReminderNotifications.CHANNEL_ID) != null
    }

    override fun areNotificationsPermitted(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun notify(alarm: ReminderAlarm) {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(alarm.title)
            .setContentText(alarm.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alarm.body))
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(notificationId(alarm.uri), notification)
        }
    }

    override fun cancelNotification(uri: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(uri))
    }

    private fun launchIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notificationId(uri: String): Int = uri.hashCode() and 0x7FFFFFFF
}
