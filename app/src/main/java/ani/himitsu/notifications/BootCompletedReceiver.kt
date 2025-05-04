package ani.himitsu.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ani.dantotsu.notifications.comment.CommentNotificationWorker
import ani.himitsu.notifications.TaskScheduler.TaskType
import ani.himitsu.notifications.anilist.AniListNotificationWorker
import ani.himitsu.notifications.subscription.SubscriptionNotificationWorker
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.Logger
import bit.himitsu.os.Version

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val scheduler = AlarmManagerScheduler(context)
            PrefManager.init(context)
            Logger.init(context)
            Logger.log("Starting Himitsu Subscription Service on Boot")
            if (PrefManager.getVal(PrefName.UseAlarmManager)) {
                scheduler.scheduleRepeatingTask(
                    TaskType.ANILIST_NOTIFICATION,
                    AniListNotificationWorker.checkIntervals[PrefManager.getVal(PrefName.AnilistNotificationInterval)]
                )
                scheduler.scheduleRepeatingTask(
                    TaskType.SUBSCRIPTION_NOTIFICATION,
                    SubscriptionNotificationWorker.checkIntervals[PrefManager.getVal(PrefName.SubscriptionNotificationInterval)]
                )
                scheduler.scheduleRepeatingTask(
                    TaskType.COMMENT_NOTIFICATION,
                    CommentNotificationWorker.checkIntervals[PrefManager.getVal(PrefName.CommentNotificationInterval)]
                )
            }
        }
    }
}

class AlarmPermissionStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            PrefManager.init(context)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canScheduleExactAlarms = if (Version.isSnowCone) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            if (canScheduleExactAlarms) {
                TaskScheduler.create(context, false).cancelAllTasks()
                TaskScheduler.create(context, true).scheduleAllTasks(context)
            } else {
                TaskScheduler.create(context, true).cancelAllTasks()
                TaskScheduler.create(context, false).scheduleAllTasks(context)
            }
            PrefManager.setVal(PrefName.UseAlarmManager, canScheduleExactAlarms)
        }
    }
}
