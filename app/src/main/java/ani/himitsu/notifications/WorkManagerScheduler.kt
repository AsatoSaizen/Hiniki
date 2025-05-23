package ani.himitsu.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequest
import ani.dantotsu.notifications.comment.CommentNotificationWorker
import ani.himitsu.notifications.TaskScheduler.TaskType
import ani.himitsu.notifications.anilist.AniListNotificationWorker
import ani.himitsu.notifications.subscription.SubscriptionNotificationWorker

class WorkManagerScheduler(private val context: Context) : TaskScheduler {
    override fun scheduleRepeatingTask(taskType: TaskType, interval: Long) {
        if (interval * 1000 < PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS) {
            cancelTask(taskType)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        when (taskType) {
            TaskType.ANILIST_NOTIFICATION -> {
                val recurringWork = PeriodicWorkRequest.Builder(
                    AniListNotificationWorker::class.java,
                    interval,
                    java.util.concurrent.TimeUnit.MINUTES,
                    PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
                    java.util.concurrent.TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    AniListNotificationWorker.WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    recurringWork
                )
            }

            TaskType.SUBSCRIPTION_NOTIFICATION -> {
                val recurringWork = PeriodicWorkRequest.Builder(
                    SubscriptionNotificationWorker::class.java,
                    interval,
                    java.util.concurrent.TimeUnit.MINUTES,
                    PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
                    java.util.concurrent.TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    SubscriptionNotificationWorker.WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    recurringWork
                )
            }

            TaskType.COMMENT_NOTIFICATION -> {
                val recurringWork = PeriodicWorkRequest.Builder(
                    CommentNotificationWorker::class.java,
                    interval,
                    java.util.concurrent.TimeUnit.MINUTES,
                    PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
                    java.util.concurrent.TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    CommentNotificationWorker.WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    recurringWork
                )
            }
        }
    }

    override fun cancelTask(taskType: TaskType) {
        when (taskType) {
            TaskType.ANILIST_NOTIFICATION -> {
                androidx.work.WorkManager.getInstance(context)
                    .cancelUniqueWork(AniListNotificationWorker.WORK_NAME)
            }

            TaskType.SUBSCRIPTION_NOTIFICATION -> {
                androidx.work.WorkManager.getInstance(context)
                    .cancelUniqueWork(SubscriptionNotificationWorker.WORK_NAME)
            }

            TaskType.COMMENT_NOTIFICATION -> {
                androidx.work.WorkManager.getInstance(context)
                    .cancelUniqueWork(CommentNotificationWorker.WORK_NAME)
            }
        }
    }
}