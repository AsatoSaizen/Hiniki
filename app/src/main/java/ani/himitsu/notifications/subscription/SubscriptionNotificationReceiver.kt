package ani.himitsu.notifications.subscription

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ani.himitsu.notifications.AlarmManagerScheduler
import ani.himitsu.notifications.TaskScheduler
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.Logger
import kotlinx.coroutines.runBlocking

class SubscriptionNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Logger.log("SubscriptionNotificationReceiver: onReceive")
        runBlocking { SubscriptionNotificationTask().execute(context) }
        val subscriptionInterval =
            SubscriptionNotificationWorker.checkIntervals[PrefManager.getVal(PrefName.SubscriptionNotificationInterval)]
        AlarmManagerScheduler(context).scheduleRepeatingTask(
            TaskScheduler.TaskType.SUBSCRIPTION_NOTIFICATION,
            subscriptionInterval
        )
    }
}