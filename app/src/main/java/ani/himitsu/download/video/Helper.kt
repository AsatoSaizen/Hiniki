package ani.himitsu.download.video

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.OptIn
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import ani.himitsu.R
import ani.himitsu.defaultHeaders
import ani.himitsu.download.DownloadedType
import ani.himitsu.download.DownloadsManager
import ani.himitsu.download.anime.AnimeDownloaderService
import ani.himitsu.download.anime.AnimeServiceDataSingleton
import ani.himitsu.media.MediaType
import ani.himitsu.media.cereal.Media
import ani.himitsu.parsers.Video
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.util.Logger
import bit.himitsu.os.Version
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@Suppress("DEPRECATION")
object Helper {
    @OptIn(UnstableApi::class)
    fun startAnimeDownloadService(
        context: Context,
        title: String,
        episode: String,
        video: Video,
        subtitle: List<Pair<String, String>> = emptyList(),
        audio: List<Pair<String, String>> = emptyList(),
        sourceMedia: Media? = null,
        episodeImage: String? = null
    ) {
        if (!isNotificationPermissionGranted(context)) {
            if (Version.isTiramisu) {
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }

        val animeDownloadTask = AnimeDownloaderService.AnimeDownloadTask(
            title,
            episode,
            video,
            subtitle,
            audio,
            sourceMedia,
            episodeImage
        )

        val downloadsManger = Injekt.get<DownloadsManager>()
        val downloadCheck = downloadsManger
            .queryDownload(title, episode, MediaType.ANIME)

        if (downloadCheck) {
            AlertDialog.Builder(context, R.style.MyDialog)
                .setMessage(R.string.download_exists_overwrite)
                .setPositiveButton(R.string.yes) { _, _ ->
                    PrefManager.getAnimeDownloadPreferences().edit()
                        .remove(animeDownloadTask.getTaskName())
                        .apply()
                    downloadsManger.removeDownload(
                        DownloadedType(
                            title,
                            episode,
                            MediaType.ANIME
                        )
                    ) {
                        AnimeServiceDataSingleton.downloadQueue.offer(animeDownloadTask)
                        if (!AnimeServiceDataSingleton.isServiceRunning) {
                            val intent = Intent(context, AnimeDownloaderService::class.java)
                            ContextCompat.startForegroundService(context, intent)
                            AnimeServiceDataSingleton.isServiceRunning = true
                        }
                    }
                }
                .setNegativeButton(R.string.no) { _, _ -> }
                .show()
        } else {
            AnimeServiceDataSingleton.downloadQueue.offer(animeDownloadTask)
            if (!AnimeServiceDataSingleton.isServiceRunning) {
                val intent = Intent(context, AnimeDownloaderService::class.java)
                ContextCompat.startForegroundService(context, intent)
                AnimeServiceDataSingleton.isServiceRunning = true
            }
        }
    }

    private fun isNotificationPermissionGranted(context: Context): Boolean {
        if (Version.isTiramisu) {
            return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    @Deprecated(DOWNLOAD_MANAGER_OBSOLETE)
    @Synchronized
    @UnstableApi
    fun downloadManager(context: Context): DownloadManager {
        return download ?: let {
            val database = Injekt.get<StandaloneDatabaseProvider>()
            val downloadDirectory = File(getDownloadDirectory(context), DOWNLOAD_CONTENT_DIRECTORY)
            val dataSourceFactory = DataSource.Factory {
                //val dataSource: HttpDataSource = OkHttpDataSource.Factory(okHttpClient).createDataSource()
                val networkHelper = Injekt.get<NetworkHelper>()
                val okHttpClient = networkHelper.client
                val dataSource: HttpDataSource =
                    OkHttpDataSource.Factory(okHttpClient).createDataSource()
                defaultHeaders.forEach {
                    dataSource.setRequestProperty(it.key, it.value)
                }
                dataSource
            }
            val threadPoolSize = Runtime.getRuntime().availableProcessors()
            val executorService = Executors.newFixedThreadPool(threadPoolSize)
            val downloadManager = DownloadManager(
                context,
                database,
                getSimpleCache(context),
                dataSourceFactory,
                executorService
            ).apply {
                requirements =
                    Requirements(Requirements.NETWORK or Requirements.DEVICE_STORAGE_NOT_LOW)
                maxParallelDownloads = 3
            }
            downloadManager.addListener(  //for testing
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?
                    ) {
                        when (download.state) {
                            Download.STATE_COMPLETED -> {
                                Logger.log("Download Completed")
                            }
                            Download.STATE_FAILED -> {
                                Logger.log("Download Failed")
                            }
                            Download.STATE_STOPPED -> {
                                Logger.log("Download Stopped")
                            }
                            Download.STATE_QUEUED -> {
                                Logger.log("Download Queued")
                            }
                            Download.STATE_DOWNLOADING -> {
                                Logger.log("Download Downloading")
                            }
                            Download.STATE_REMOVING -> {
                                Logger.log("Download Removing")
                            }
                            Download.STATE_RESTARTING -> {
                                Logger.log("Download Restarting")
                            }
                        }
                    }
                }
            )

            downloadManager
        }
    }
    @Deprecated(DOWNLOAD_MANAGER_OBSOLETE)
    @OptIn(UnstableApi::class)
    fun getSimpleCache(context: Context): SimpleCache {
        return if (simpleCache == null) {
            val downloadDirectory = File(getDownloadDirectory(context), DOWNLOAD_CONTENT_DIRECTORY)
            val database = Injekt.get<StandaloneDatabaseProvider>()
            simpleCache = SimpleCache(downloadDirectory, NoOpCacheEvictor(), database)
            simpleCache!!
        } else {
            simpleCache!!
        }
    }
    @Deprecated(DOWNLOAD_MANAGER_OBSOLETE)
    @Synchronized
    private fun getDownloadDirectory(context: Context): File {
        if (downloadDirectory == null) {
            downloadDirectory = context.getExternalFilesDir(null)
            if (downloadDirectory == null) {
                downloadDirectory = context.filesDir
            }
        }
        return downloadDirectory!!
    }
    @Deprecated(DOWNLOAD_MANAGER_OBSOLETE)
    private var download: DownloadManager? = null
    @Deprecated(DOWNLOAD_MANAGER_OBSOLETE)
    private const val DOWNLOAD_CONTENT_DIRECTORY = "Anime_Downloads"
    @Deprecated(DOWNLOAD_MANAGER_OBSOLETE)
    private var simpleCache: SimpleCache? = null
    @Deprecated(DOWNLOAD_MANAGER_OBSOLETE)
    private var downloadDirectory: File? = null

    private const val DOWNLOAD_MANAGER_OBSOLETE = "ExoPlayer download manager is obsolete"
}