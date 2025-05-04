package ani.himitsu.download.anime

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.himitsu.R
import ani.himitsu.defaultHeaders
import ani.himitsu.download.DownloadedType
import ani.himitsu.download.DownloadsManager
import ani.himitsu.download.DownloadsManager.Companion.getSubDirectory
import ani.himitsu.download.anime.AnimeDownloaderService.AnimeDownloadTask.Companion.getTaskName
import ani.himitsu.download.findValidName
import ani.himitsu.media.MediaType
import ani.himitsu.media.anime.AnimeWatchFragment
import ani.himitsu.media.cereal.Media
import ani.himitsu.others.Download
import ani.himitsu.parsers.Video
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.snackString
import ani.himitsu.toast
import ani.himitsu.util.Logger
import bit.himitsu.os.Version
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue


class AnimeDownloaderService : Service() {

    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var builder: NotificationCompat.Builder
    private val downloadsManager: DownloadsManager = Injekt.get<DownloadsManager>()

    private val downloadJobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()
    private var isCurrentlyProcessing = false
    private var currentTasks: MutableList<AnimeDownloadTask> = mutableListOf()
    private val ffExtension = Injekt.get<DownloadAddonManager>().extension?.extension

    override fun onBind(intent: Intent?): IBinder? {
        // This is only required for bound services.
        return null
    }

    override fun onCreate() {
        super.onCreate()
        if (ffExtension == null) {
            toast(getString(R.string.download_addon_not_found))
            stopSelf()
            return
        }
        notificationManager = NotificationManagerCompat.from(this)
        builder =
            NotificationCompat.Builder(this, Notifications.CHANNEL_DOWNLOADER_PROGRESS).apply {
                setContentTitle(getString(R.string.download_progress, MediaType.ANIME.string))
                setSmallIcon(R.drawable.ic_download_24)
                priority = NotificationCompat.PRIORITY_DEFAULT
                setOnlyAlertOnce(true)
                setProgress(100, 0, false)
            }
        if (Version.isQuinceTart) {
            startForeground(
                NOTIFICATION_ID,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
        ContextCompat.registerReceiver(
            this,
            cancelReceiver,
            IntentFilter(ACTION_CANCEL_DOWNLOAD),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        AnimeServiceDataSingleton.downloadQueue.clear()
        downloadJobs.clear()
        AnimeServiceDataSingleton.isServiceRunning = false
        unregisterReceiver(cancelReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        toast("Download started")
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serviceScope.launch {
            mutex.withLock {
                if (!isCurrentlyProcessing) {
                    isCurrentlyProcessing = true
                    processQueue()
                    isCurrentlyProcessing = false
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun processQueue() {
        CoroutineScope(Dispatchers.IO).launch {
            while (AnimeServiceDataSingleton.downloadQueue.isNotEmpty()) {
                val task = AnimeServiceDataSingleton.downloadQueue.poll()
                if (task != null) {
                    val job = launch { download(task) }
                    currentTasks.add(task)
                    mutex.withLock {
                        downloadJobs[task.getTaskName()] = job
                    }
                    job.join() // Wait for the job to complete before continuing to the next task
                    mutex.withLock {
                        downloadJobs.remove(task.getTaskName())
                    }
                    updateNotification() // Update the notification after each task is completed
                }
                if (AnimeServiceDataSingleton.downloadQueue.isEmpty()) {
                    withUIContext {
                        stopSelf() // Stop the service when the queue is empty
                    }
                }
            }
        }
    }

    @UnstableApi
    fun cancelDownload(taskName: String) {
        val sessionIds =
            AnimeServiceDataSingleton.downloadQueue.filter { it.getTaskName() == taskName }
                .map { it.sessionId }.toMutableList()
        sessionIds.addAll(currentTasks.filter { it.getTaskName() == taskName }.map { it.sessionId })
        sessionIds.forEach {
            ffExtension!!.cancelDownload(it)
        }
        currentTasks.removeAll { it.getTaskName() == taskName }
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                downloadJobs[taskName]?.cancel()
                downloadJobs.remove(taskName)
                AnimeServiceDataSingleton.downloadQueue.removeAll { it.getTaskName() == taskName }
                updateNotification() // Update the notification after cancellation
            }
        }
    }

    private fun updateNotification() {
        // Update the notification to reflect the current state of the queue
        val pendingDownloads = AnimeServiceDataSingleton.downloadQueue.size
        val text = if (pendingDownloads > 0) {
            "Pending downloads: $pendingDownloads"
        } else {
            "All downloads completed"
        }
        builder.setContentText(text)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun download(task: AnimeDownloadTask) = withIOContext {
        try {
            val notifi = if (Version.isTiramisu) {
                ContextCompat.checkSelfPermission(
                    this@AnimeDownloaderService,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            builder.setContentText("Downloading ${getTaskName(task.title, task.episode)}")
            if (notifi) {
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            }

            val outputDir = getSubDirectory(
                this@AnimeDownloaderService,
                MediaType.ANIME,
                false,
                task.title,
                task.episode
            ) ?: throw Exception("Failed to create output directory")

            outputDir.findFile("${task.getTaskName().findValidName()}.mkv")?.delete()
            val outputFile =
                outputDir.createFile("video/x-matroska", "${task.getTaskName()}.mkv")
                    ?: throw Exception("Failed to create output file")

            var percent = 0
            var totalLength = 0.0
            val path = ffExtension!!.setDownloadPath(
                this@AnimeDownloaderService,
                outputFile.uri
            )
            if (!task.video.file.headers.containsKey("User-Agent")
                && !task.video.file.headers.containsKey("user-agent")
            ) {
                val newHeaders = task.video.file.headers.toMutableMap()
                newHeaders["User-Agent"] = defaultHeaders["User-Agent"]!!
                task.video.file.headers = newHeaders
            }

            ffExtension.executeFFProbe(
                task.video.file.url,
                task.video.file.headers
            ) {
                if (it.toDoubleOrNull() != null) {
                    totalLength = it.toDouble()
                }
            }
            val ffTask =
                ffExtension.executeFFMpeg(
                    task.video.file.url,
                    path,
                    task.video.file.headers,
                    task.subtitle,
                    task.audio,
                ) {
                    // CALLED WHEN SESSION GENERATES STATISTICS
                    val timeInMilliseconds = it
                    if (timeInMilliseconds > 0 && totalLength > 0) {
                        percent = ((it / 1000) / totalLength * 100).toInt()
                    }
                    Logger.log("Statistics: $it")
                }
            task.sessionId = ffTask
            currentTasks.find { it.getTaskName() == task.getTaskName() }?.sessionId = ffTask

            Download.saveMediaInfo(
                this@AnimeDownloaderService,
                task.sourceMedia,
                MediaType.ANIME,
                task.title,
                task.episode,
                task.episodeImage
            )

            // periodically check if the download is complete
            while (ffExtension.getState(ffTask) != "COMPLETED") {
                if (ffExtension.getState(ffTask) == "FAILED") {
                    Logger.log("Download failed")
                    builder.setContentText(
                        "${
                            getTaskName(
                                task.title,
                                task.episode
                            )
                        } Download failed"
                    )
                    withUIContext {
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                        toast("${getTaskName(task.title, task.episode)} Download failed")
                    }
                    downloadsManager.removeDownload(
                        DownloadedType(
                            task.title,
                            task.episode,
                            MediaType.ANIME,
                        ),
                        false
                    ) {}
                    Logger.log(
                        Exception(
                            "Anime Download failed:" +
                                    " ${getTaskName(task.title, task.episode)}" +
                                    " url: ${task.video.file.url}" +
                                    " title: ${task.title}" +
                                    " episode: ${task.episode}"
                        )
                    )
                    currentTasks.removeAll { it.getTaskName() == task.getTaskName() }
                    broadcastDownloadFailed(task.episode)
                    break
                }
                builder.setProgress(
                    100, percent.coerceAtMost(99),
                    false
                )
                broadcastDownloadProgress(
                    task.episode,
                    percent.coerceAtMost(99)
                )
                if (notifi) {
                    withUIContext {
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                    }
                }
                kotlinx.coroutines.delay(2000)
            }
            if (ffExtension.getState(ffTask) == "COMPLETED") {
                if (ffExtension.hadError(ffTask)) {
                    Logger.log("Download failed")
                    builder.setContentText(
                        "${
                            getTaskName(
                                task.title,
                                task.episode
                            )
                        } Download failed"
                    )
                    withUIContext {
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                        snackString("${getTaskName(task.title, task.episode)} Download failed")
                    }
                    downloadsManager.removeDownload(
                        DownloadedType(
                            task.title,
                            task.episode,
                            MediaType.ANIME,
                        )
                    ) {}
                    Logger.log(
                        Exception(
                            "Anime Download failed:" +
                                    " ${getTaskName(task.title, task.episode)}" +
                                    " url: ${task.video.file.url}" +
                                    " title: ${task.title}" +
                                    " episode: ${task.episode}"
                        )
                    )
                    currentTasks.removeAll { it.getTaskName() == task.getTaskName() }
                    broadcastDownloadFailed(task.episode)
                    return@withIOContext
                }
                Logger.log("Download completed")
                builder.setContentText(
                    "${
                        getTaskName(
                            task.title,
                            task.episode
                        )
                    } Download completed"
                )
                withUIContext {
                    notificationManager.notify(NOTIFICATION_ID, builder.build())
                    snackString("${getTaskName(task.title, task.episode)} Download completed")
                }
                PrefManager.getAnimeDownloadPreferences().edit().putString(
                    task.getTaskName(),
                    task.video.file.url
                ).apply()
                downloadsManager.addDownload(
                    DownloadedType(
                        task.title,
                        task.episode,
                        MediaType.ANIME,
                    )
                )

                currentTasks.removeAll { it.getTaskName() == task.getTaskName() }
                broadcastDownloadFinished(task.episode)
            } else throw Exception("Download failed")
        } catch (e: Exception) {
            if (e.message?.contains("Coroutine was cancelled") == false) {  //wut
                Logger.log("Exception while downloading file: ${e.message}")
                snackString("Exception while downloading file: ${e.message}")
                Logger.log(e)
            }
            broadcastDownloadFailed(task.episode)
        }
    }

    private fun broadcastDownloadStarted(episodeNumber: String) {
        val intent = Intent(AnimeWatchFragment.ACTION_DOWNLOAD_STARTED).apply {
            putExtra(AnimeWatchFragment.EXTRA_EPISODE_NUMBER, episodeNumber)
        }
        sendBroadcast(intent)
    }

    private fun broadcastDownloadFinished(episodeNumber: String) {
        val intent = Intent(AnimeWatchFragment.ACTION_DOWNLOAD_FINISHED).apply {
            putExtra(AnimeWatchFragment.EXTRA_EPISODE_NUMBER, episodeNumber)
        }
        sendBroadcast(intent)
    }

    private fun broadcastDownloadFailed(episodeNumber: String) {
        val intent = Intent(AnimeWatchFragment.ACTION_DOWNLOAD_FAILED).apply {
            putExtra(AnimeWatchFragment.EXTRA_EPISODE_NUMBER, episodeNumber)
        }
        sendBroadcast(intent)
    }

    private fun broadcastDownloadProgress(episodeNumber: String, progress: Int) {
        val intent = Intent(AnimeWatchFragment.ACTION_DOWNLOAD_PROGRESS).apply {
            putExtra(AnimeWatchFragment.EXTRA_EPISODE_NUMBER, episodeNumber)
            putExtra("progress", progress)
        }
        sendBroadcast(intent)
    }

    private val cancelReceiver = object : BroadcastReceiver() {
        @androidx.annotation.OptIn(UnstableApi::class)
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CANCEL_DOWNLOAD) {
                val taskName = intent.getStringExtra(EXTRA_TASK_NAME)
                taskName?.let {
                    cancelDownload(it)
                }
            }
        }
    }


    data class AnimeDownloadTask(
        val title: String,
        val episode: String,
        val video: Video,
        val subtitle: List<Pair<String, String>> = emptyList(),
        val audio: List<Pair<String, String>> = emptyList(),
        val sourceMedia: Media? = null,
        val episodeImage: String? = null,
        val retries: Int = 2,
        val simultaneousDownloads: Int = 2,
        var sessionId: Long = -1
    ) {
        fun getTaskName(): String {
            return "${title.replace("/", "")}/${episode.replace("/", "")}"
        }

        companion object {
            fun getTaskName(title: String, episode: String): String {
                return "${title.replace("/", "")}/${episode.replace("/", "")}"
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1103
        const val ACTION_CANCEL_DOWNLOAD = "action_cancel_download"
        const val EXTRA_TASK_NAME = "extra_task_name"
    }
}

object AnimeServiceDataSingleton {
    var video: Video? = null
    var downloadQueue: Queue<AnimeDownloaderService.AnimeDownloadTask> = ConcurrentLinkedQueue()

    @Volatile
    var isServiceRunning: Boolean = false
}