package ani.himitsu.notifications.anilist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ani.himitsu.notifications.AlarmManagerScheduler
import ani.himitsu.notifications.TaskScheduler
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.Logger
import kotlinx.coroutines.runBlocking

class AniListNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Logger.log("AniListNotificationReceiver: onReceive")
        runBlocking {
            AniListNotificationTask().execute(context)
        }
        val anilistInterval =
            AniListNotificationWorker.checkIntervals[PrefManager.getVal(PrefName.AnilistNotificationInterval)]
        AlarmManagerScheduler(context).scheduleRepeatingTask(
            TaskScheduler.TaskType.ANILIST_NOTIFICATION,
            anilistInterval
        )
    }
}