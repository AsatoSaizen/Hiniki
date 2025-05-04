package ani.himitsu.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ani.dantotsu.notifications.comment.CommentNotificationReceiver
import ani.himitsu.notifications.TaskScheduler.TaskType
import ani.himitsu.notifications.anilist.AniListNotificationReceiver
import ani.himitsu.notifications.subscription.SubscriptionNotificationReceiver
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import bit.himitsu.os.Version
import java.util.concurrent.TimeUnit

class AlarmManagerScheduler(private val context: Context) : TaskScheduler {
    override fun scheduleRepeatingTask(taskType: TaskType, interval: Long) {
        if (interval * 1000 < TimeUnit.MINUTES.toMillis(15)) {
            cancelTask(taskType)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = when (taskType) {
            TaskType.ANILIST_NOTIFICATION -> Intent(
                context,
                AniListNotificationReceiver::class.java
            )

            TaskType.SUBSCRIPTION_NOTIFICATION -> Intent(
                context,
                SubscriptionNotificationReceiver::class.java
            )

            TaskType.COMMENT_NOTIFICATION -> Intent(
                context,
                CommentNotificationReceiver::class.java
            )
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskType.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(interval)
        try {
            if (Version.isMarshmallow) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            PrefManager.setVal(PrefName.UseAlarmManager, false)
            TaskScheduler.create(context, true).cancelAllTasks()
            TaskScheduler.create(context, false).scheduleAllTasks(context)
        }
    }

    override fun cancelTask(taskType: TaskType) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = when (taskType) {
            TaskType.ANILIST_NOTIFICATION -> Intent(
                context,
                AniListNotificationReceiver::class.java
            )

            TaskType.SUBSCRIPTION_NOTIFICATION -> Intent(
                context,
                SubscriptionNotificationReceiver::class.java
            )

            TaskType.COMMENT_NOTIFICATION -> Intent(
                context,
                CommentNotificationReceiver::class.java
            )
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskType.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}