package ani.himitsu.notifications

import android.content.Context
import ani.dantotsu.notifications.comment.CommentNotificationWorker
import ani.himitsu.notifications.anilist.AniListNotificationWorker
import ani.himitsu.notifications.subscription.SubscriptionNotificationWorker
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName

interface TaskScheduler {
    fun scheduleRepeatingTask(taskType: TaskType, interval: Long)
    fun cancelTask(taskType: TaskType)

    fun cancelAllTasks() {
        for (taskType in TaskType.entries) {
            cancelTask(taskType)
        }
    }

    fun scheduleAllTasks(context: Context) {
        for (taskType in TaskType.entries) {
            val interval = when (taskType) {
                TaskType.ANILIST_NOTIFICATION -> AniListNotificationWorker.checkIntervals[PrefManager.getVal(
                    PrefName.AnilistNotificationInterval
                )]

                TaskType.SUBSCRIPTION_NOTIFICATION -> SubscriptionNotificationWorker.checkIntervals[PrefManager.getVal(
                    PrefName.SubscriptionNotificationInterval
                )]

                TaskType.COMMENT_NOTIFICATION -> CommentNotificationWorker.checkIntervals[PrefManager.getVal(
                    PrefName.CommentNotificationInterval
                )]
            }
            scheduleRepeatingTask(taskType, interval)
        }
    }

    companion object {
        fun create(context: Context, useAlarmManager: Boolean): TaskScheduler {
            return if (useAlarmManager) {
                AlarmManagerScheduler(context)
            } else {
                WorkManagerScheduler(context)
            }
        }

        fun scheduleSingleWork(context: Context) {
            val workManager = androidx.work.WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                AniListNotificationWorker.WORK_NAME + "_single",
                androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequest.Builder(AniListNotificationWorker::class.java)
                    .build()
            )
            workManager.enqueueUniqueWork(
                SubscriptionNotificationWorker.WORK_NAME + "_single",
                androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequest.Builder(SubscriptionNotificationWorker::class.java)
                    .build()
            )
            workManager.enqueueUniqueWork(
                CommentNotificationWorker.WORK_NAME + "_single",
                androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequest.Builder(CommentNotificationWorker::class.java)
                    .build()
            )
        }
    }

    enum class TaskType {
        ANILIST_NOTIFICATION,
        SUBSCRIPTION_NOTIFICATION,
        COMMENT_NOTIFICATION
    }
}

interface Task {
    suspend fun execute(context: Context, withNotification: Boolean = true): Boolean
}
