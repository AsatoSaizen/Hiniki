package ani.himitsu.notifications.anilist

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ani.himitsu.util.Logger

class AniListNotificationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (System.currentTimeMillis() - lastCheck < 60000) {
            Logger.log("AniListNotificationWorker: doWork skipped")
            return Result.success()
        }
        Logger.log("AniListNotificationWorker: doWork")
        lastCheck = System.currentTimeMillis()
        return if (AniListNotificationTask().execute(applicationContext)) {
            Result.success()
        } else {
            Logger.log("AniListNotificationWorker: doWork failed")
            Result.retry()
        }
    }

    companion object {
        val checkIntervals = arrayOf(0L, 30, 60, 120, 240, 360, 720, 1440)
        const val WORK_NAME = "ani.himitsu.notifications.anilist.AniListNotificationWorker"
        private var lastCheck = 0L
    }
}