package ani.himitsu.download.manga

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ani.himitsu.R
import ani.himitsu.download.DownloadedType
import ani.himitsu.download.DownloadsManager
import ani.himitsu.download.DownloadsManager.Companion.getSubDirectory
import ani.himitsu.media.MediaType
import ani.himitsu.media.cereal.Media
import ani.himitsu.media.manga.ImageData
import ani.himitsu.media.manga.MangaReadFragment.Companion.ACTION_DOWNLOAD_FAILED
import ani.himitsu.media.manga.MangaReadFragment.Companion.ACTION_DOWNLOAD_FINISHED
import ani.himitsu.media.manga.MangaReadFragment.Companion.ACTION_DOWNLOAD_PROGRESS
import ani.himitsu.media.manga.MangaReadFragment.Companion.ACTION_DOWNLOAD_STARTED
import ani.himitsu.media.manga.MangaReadFragment.Companion.EXTRA_CHAPTER_NUMBER
import ani.himitsu.others.Download
import ani.himitsu.snackString
import ani.himitsu.toast
import ani.himitsu.util.Logger
import bit.himitsu.os.Version
import com.anggrayudi.storage.file.deleteRecursively
import com.anggrayudi.storage.file.forceDelete
import com.anggrayudi.storage.file.openOutputStream
import eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_DOWNLOADER_PROGRESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

class MangaDownloaderService : Service() {

    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var builder: NotificationCompat.Builder
    private val downloadsManager: DownloadsManager = Injekt.get<DownloadsManager>()

    private val downloadJobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()
    private var isCurrentlyProcessing = false

    override fun onBind(intent: Intent?): IBinder? {
        // This is only required for bound services.
        return null
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManagerCompat.from(this)
        builder = NotificationCompat.Builder(this, CHANNEL_DOWNLOADER_PROGRESS).apply {
            setContentTitle(getString(R.string.download_progress, MediaType.MANGA.string))
            setSmallIcon(R.drawable.ic_download_24)
            priority = NotificationCompat.PRIORITY_DEFAULT
            setOnlyAlertOnce(true)
            setProgress(0, 0, false)
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
        MangaServiceDataSingleton.downloadQueue.clear()
        downloadJobs.clear()
        MangaServiceDataSingleton.isServiceRunning = false
        unregisterReceiver(cancelReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        toast("Download started")
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
        CoroutineScope(Dispatchers.Default).launch {
            while (MangaServiceDataSingleton.downloadQueue.isNotEmpty()) {
                val task = MangaServiceDataSingleton.downloadQueue.poll()
                if (task != null) {
                    val job = launch { download(task) }
                    mutex.withLock {
                        downloadJobs[task.chapter] = job
                    }
                    job.join() // Wait for the job to complete before continuing to the next task
                    mutex.withLock {
                        downloadJobs.remove(task.chapter)
                    }
                    updateNotification() // Update the notification after each task is completed
                }
                if (MangaServiceDataSingleton.downloadQueue.isEmpty()) {
                    withUIContext {
                        stopSelf() // Stop the service when the queue is empty
                    }
                }
            }
        }
    }

    fun cancelDownload(chapter: String) {
        CoroutineScope(Dispatchers.Default).launch {
            mutex.withLock {
                downloadJobs[chapter]?.cancel()
                downloadJobs.remove(chapter)
                MangaServiceDataSingleton.downloadQueue.removeAll { it.chapter == chapter }
                updateNotification() // Update the notification after cancellation
            }
        }
    }

    private fun updateNotification() {
        // Update the notification to reflect the current state of the queue
        val pendingDownloads = MangaServiceDataSingleton.downloadQueue.size
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

    suspend fun download(task: DownloadTask) {
        try {
            withUIContext {
                val notifi = if (Version.isTiramisu) {
                    ContextCompat.checkSelfPermission(
                        this@MangaDownloaderService,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                val deferredMap = mutableMapOf<Int, Deferred<Bitmap?>>()
                builder.setContentText("Downloading ${task.title} - ${task.chapter}")
                if (notifi) {
                    notificationManager.notify(NOTIFICATION_ID, builder.build())
                }

                getSubDirectory(
                    this@MangaDownloaderService,
                    MediaType.MANGA,
                    false,
                    task.title,
                    task.chapter
                )?.deleteRecursively(this@MangaDownloaderService)

                // Loop through each ImageData object from the task
                var farthest = 0
                for ((index, image) in task.imageData.withIndex()) {
                    if (deferredMap.size >= task.simultaneousDownloads) {
                        deferredMap.values.awaitAll()
                        deferredMap.clear()
                    }

                    deferredMap[index] = async(Dispatchers.IO) {
                        var bitmap: Bitmap? = null
                        var retryCount = 0

                        while (bitmap == null && retryCount < task.retries) {
                            bitmap = image.fetchAndProcessImage(
                                image.page,
                                image.source
                            )
                            retryCount++
                        }

                        if (bitmap != null) {
                            saveToDisk("$index.jpg", bitmap, task.title, task.chapter)
                        }
                        farthest++
                        builder.setProgress(task.imageData.size, farthest, false)
                        broadcastDownloadProgress(
                            task.chapter,
                            farthest * 100 / task.imageData.size
                        )
                        if (notifi) {
                            notificationManager.notify(NOTIFICATION_ID, builder.build())
                        }

                        bitmap
                    }
                }

                // Wait for any remaining deferred to complete
                deferredMap.values.awaitAll()

                builder.setContentText("${task.title} - ${task.chapter} Download complete")
                    .setProgress(0, 0, false)
                notificationManager.notify(NOTIFICATION_ID, builder.build())

                saveMediaInfo(task)
                downloadsManager.addDownload(
                    DownloadedType(
                        task.title,
                        task.chapter,
                        MediaType.MANGA
                    )
                )
                broadcastDownloadFinished(task.chapter)
                toast("${task.title} - ${task.chapter} Download finished")
            }
        } catch (e: Exception) {
            snackString("Exception while downloading file: ${e.message}")
            Logger.log(e)
            broadcastDownloadFailed(task.chapter)
        }
    }


    private fun saveToDisk(fileName: String, bitmap: Bitmap, title: String, chapter: String) {
        try {
            // Define the directory within the private external storage space
            val directory = getSubDirectory(this, MediaType.MANGA, false, title, chapter)
                ?: throw Exception("Directory not found")
            directory.findFile(fileName)?.forceDelete(this)
            // Create a file reference within that directory for the image
            val file =
                directory.createFile("image/jpeg", fileName) ?: throw Exception("File not created")

            // Use a FileOutputStream to write the bitmap to the file
            file.openOutputStream(this, false).use { outputStream ->
                if (outputStream == null) throw Exception("Output stream is null")
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
        } catch (e: Exception) {
            snackString("Exception while saving image: ${e.message}")
            Logger.log(e)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun saveMediaInfo(task: DownloadTask) {
        Download.saveMediaInfo(
            this@MangaDownloaderService,
            task.sourceMedia,
            MediaType.MANGA,
            task.title
        )
    }

    private fun broadcastDownloadStarted(chapterNumber: String) {
        val intent = Intent(ACTION_DOWNLOAD_STARTED).apply {
            putExtra(EXTRA_CHAPTER_NUMBER, chapterNumber)
        }
        sendBroadcast(intent)
    }

    private fun broadcastDownloadFinished(chapterNumber: String) {
        val intent = Intent(ACTION_DOWNLOAD_FINISHED).apply {
            putExtra(EXTRA_CHAPTER_NUMBER, chapterNumber)
        }
        sendBroadcast(intent)
    }

    private fun broadcastDownloadFailed(chapterNumber: String) {
        val intent = Intent(ACTION_DOWNLOAD_FAILED).apply {
            putExtra(EXTRA_CHAPTER_NUMBER, chapterNumber)
        }
        sendBroadcast(intent)
    }

    private fun broadcastDownloadProgress(chapterNumber: String, progress: Int) {
        val intent = Intent(ACTION_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_CHAPTER_NUMBER, chapterNumber)
            putExtra("progress", progress)
        }
        sendBroadcast(intent)
    }

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CANCEL_DOWNLOAD) {
                val chapter = intent.getStringExtra(EXTRA_CHAPTER)
                chapter?.let {
                    cancelDownload(it)
                }
            }
        }
    }


    data class DownloadTask(
        val title: String,
        val chapter: String,
        val imageData: List<ImageData>,
        val sourceMedia: Media? = null,
        val retries: Int = 2,
        val simultaneousDownloads: Int = 2,
    )

    companion object {
        private const val NOTIFICATION_ID = 1103
        const val ACTION_CANCEL_DOWNLOAD = "action_cancel_download"
        const val EXTRA_CHAPTER = "extra_chapter"
    }
}

object MangaServiceDataSingleton {
    var imageData: List<ImageData> = listOf()
    var sourceMedia: Media? = null
    var downloadQueue: Queue<MangaDownloaderService.DownloadTask> = ConcurrentLinkedQueue()

    @Volatile
    var isServiceRunning: Boolean = false
}