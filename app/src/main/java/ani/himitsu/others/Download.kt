package ani.himitsu.others

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import ani.himitsu.FileUrl
import ani.himitsu.Himitsu
import ani.himitsu.R
import ani.himitsu.defaultHeaders
import ani.himitsu.download.DownloadManager
import ani.himitsu.download.DownloadsManager.Companion.getSubDirectory
import ani.himitsu.media.MediaType
import ani.himitsu.media.anime.Episode
import ani.himitsu.media.cereal.Media
import ani.himitsu.openInGooglePlay
import ani.dantotsu.parsers.Book
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.toast
import ani.himitsu.util.Logger
import com.anggrayudi.storage.file.forceDelete
import com.anggrayudi.storage.file.openOutputStream
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.SEpisodeImpl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Download {
    private fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getDownloadDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val direct = File(downloads, Himitsu.appName)
        if (!direct.exists()) direct.mkdirs()
        return direct
    }

    fun download(
        context: Context,
        episode: Episode,
        animeTitle: String,
        subsFiles: ArrayList<String>,
        subsNames: ArrayList<String>
    ) {
        toast(context.getString(R.string.downloading))
        val extractor =
            episode.extractors?.find { it.server.name == episode.selectedExtractor } ?: return
        val video =
            if (extractor.videos.size > episode.selectedVideo) extractor.videos[episode.selectedVideo] else return
        val regex = "[\\\\/:*?\"<>|]".toRegex()
        val aTitle = animeTitle.replace(regex, "")
        val title =
            "Episode ${episode.number}${if (episode.title != null) " - ${episode.title}" else ""}".replace(
                regex,
                ""
            )

        val notif = "$title : $aTitle"
        val folder = "/${MediaType.ANIME.text}/${aTitle}/"
        val fileName = "$title${if (video.size != null) "(${video.size}p)" else ""}.mp4"
        val file = video.file
        download(context, file, fileName, subsFiles, subsNames, folder, notif)
    }

    fun download(context: Context, book: Book, pos: Int, novelTitle: String) {
        toast(R.string.downloading)
        val regex = "[\\\\/:*?\"<>|]".toRegex()
        val nTitle = novelTitle.replace(regex, "")
        val title = book.name.replace(regex, "")

        val notif = "$title : $nTitle"
        val folder = "/${MediaType.NOVEL.text}/${nTitle}/"
        val fileName = "$title.epub"
        val file = book.links[pos]
        download(context, file, fileName, arrayListOf(), arrayListOf(), folder, notif)
    }

    fun download(
        context: Context,
        file: FileUrl,
        fileName: String,
        subsFiles: ArrayList<String>,
        subsNames: ArrayList<String>,
        folder: String,
        notif: String? = null
    ) {
        if (!file.url.startsWith("http"))
            toast(context.getString(R.string.invalid_url))
        else try {
            when (PrefManager.getVal<Int>(PrefName.DownloadManager)) {
                DownloadManager.OneDM.ordinal ->
                    oneDM(context, file, notif ?: fileName, subsFiles, subsNames)
                DownloadManager.ADM.ordinal -> adm(context, file, fileName, folder)
                else -> {}
            }
        } catch (_: ActivityNotFoundException) {
            toast(R.string.manager_not_found)
        }
    }

    fun download(
        context: Context,
        files: ArrayList<FileUrl>,
        fileNames: ArrayList<String>,
        subsFiles: ArrayList<String>,
        subsNames: ArrayList<String>,
        folder: String
    ) {
        if (!files[0].url.startsWith("http"))
            toast(context.getString(R.string.invalid_url))
        else try {
            when (PrefManager.getVal<Int>(PrefName.DownloadManager)) {
                DownloadManager.OneDM.ordinal ->
                    oneDM(context, files, fileNames, subsFiles, subsNames)
                DownloadManager.ADM.ordinal -> adm(context, files, fileNames, folder)
                else -> {}
            }
        } catch (_: ActivityNotFoundException) {
            toast(R.string.manager_not_found)
        }
    }

    // documentation: https://www.apps2sd.info/idmp/faq?id=35
    private fun oneDM(
        context: Context,
        files: ArrayList<FileUrl>,
        fileNames: ArrayList<String>,
        subsFiles: ArrayList<String>,
        subsNames: ArrayList<String>
    ) {
        val appName =
            when {
                isPackageInstalled(
                    "idm.internet.download.manager.plus",
                    context.packageManager
                ) -> {
                    "idm.internet.download.manager.plus"
                }

                isPackageInstalled(
                    "idm.internet.download.manager",
                    context.packageManager
                ) -> {
                    "idm.internet.download.manager"
                }

                isPackageInstalled(
                    "idm.internet.download.manager.adm.lite",
                    context.packageManager
                ) -> {
                    "idm.internet.download.manager.adm.lite"
                }

                else -> {
                    ""
                }
            }
        if (appName.isNotBlank()) {
            val bundle = Bundle()
            defaultHeaders.forEach { a -> bundle.putString(a.key, a.value) }
            val fileString = arrayListOf<String>()
            files.forEach { file ->
                file.headers.forEach { a -> bundle.putString(a.key, a.value) }
                fileString.add(file.url)
            }

            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    component = ComponentName(appName, "idm.internet.download.manager.Downloader")
                    data = Uri.parse(files[0].url)
                    putExtra("extra_headers", bundle)
                    putExtra("url_list", fileString.toTypedArray())
                    putExtra("url_list.filename", fileNames.toTypedArray())
                    if (subsFiles.isNotEmpty()) {
                        putExtra("subs", subsFiles)
                        putExtra("subs.name", subsNames)
                    }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } else {
            openInGooglePlay("idm.internet.download.manager")
            toast(R.string.install_1dm)
        }
    }

    private fun oneDM(
        context: Context,
        file: FileUrl,
        fileName: String,
        subsFiles: ArrayList<String>,
        subsNames: ArrayList<String>
    ) {
        oneDM(context, arrayListOf(file), arrayListOf(fileName), subsFiles, subsNames)
    }

    // unofficial documentation: https://pastebin.com/ScDNr2if (there is no official documentation)
    private fun adm(context: Context, file: FileUrl, fileName: String, folder: String) {
        if (isPackageInstalled("com.dv.adm", context.packageManager)) {
            val bundle = Bundle()
            defaultHeaders.forEach { a -> bundle.putString(a.key, a.value) }
            file.headers.forEach { a -> bundle.putString(a.key, a.value) }
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    component = ComponentName("com.dv.adm", "com.dv.adm.AEditor")
                    putExtra("com.dv.get.ACTION_LIST_ADD", "${file.url}<info>$fileName")
                    putExtra("com.dv.get.ACTION_LIST_PATH", "${getDownloadDir()}${File.pathSeparator}$folder")
                    putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } else {
            openInGooglePlay("com.dv.adm")
            toast(R.string.install_adm)
        }
    }

    private fun adm(
        context: Context,
        files: ArrayList<FileUrl>,
        fileNames: ArrayList<String>,
        folder: String
    ) {
        if (isPackageInstalled("com.dv.adm", context.packageManager)) {
            val bundle = Bundle()
            defaultHeaders.forEach { a -> bundle.putString(a.key, a.value) }
            val fileString = StringBuilder()
            files.forEachIndexed { index, file ->
                file.headers.forEach { a -> bundle.putString(a.key, a.value) }
                fileString.append("${file.url}<info>${fileNames[index]}")
                if (index < files.size - 1) fileString.append("<line>")
            }
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    component = ComponentName("com.dv.adm", "com.dv.adm.AEditor")
                    putExtra("com.dv.get.ACTION_LIST_ADD", fileString.toString())
                    putExtra("com.dv.get.ACTION_LIST_PATH", "${getDownloadDir()}${File.pathSeparator}$folder")
                    putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } else {
            openInGooglePlay("com.dv.adm")
            toast(R.string.install_adm)
        }
    }

    fun saveMediaInfo(
        context: Context, media: Media?, type: MediaType,
        title: String, episode: String? = null, episodeImage: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val directory = getSubDirectory(context, type, false, title)
                ?: throw Exception(context.getString(R.string.directory_not_found))
            directory.findFile("media.json")?.forceDelete(context)
            val file = directory.createFile("application/json", "media.json")
                ?: throw Exception(context.getString(R.string.file_not_create))

            val gson = if (type == MediaType.MANGA) {
                GsonBuilder()
                    .registerTypeAdapter(SChapter::class.java, InstanceCreator<SChapter> {
                        SChapterImpl() // Provide an instance of SChapterImpl
                    })
                    .create()
            } else {
                GsonBuilder()
                    .registerTypeAdapter(SChapter::class.java, InstanceCreator<SChapter> {
                        SChapterImpl() // Provide an instance of SChapterImpl
                    })
                    .registerTypeAdapter(SAnime::class.java, InstanceCreator<SAnime> {
                        SAnimeImpl() // Provide an instance of SAnimeImpl
                    })
                    .registerTypeAdapter(SEpisode::class.java, InstanceCreator<SEpisode> {
                        SEpisodeImpl() // Provide an instance of SEpisodeImpl
                    })
                    .create()
            }

            val mediaJson = gson.toJson(media)
            val media = gson.fromJson(mediaJson, Media::class.java)
            if (media != null) {
                media.cover = media.cover?.let { downloadImage(context, it, directory, "cover.jpg") }
                media.banner = media.banner?.let { downloadImage(context, it, directory, "banner.jpg") }

                if (type == MediaType.ANIME) {
                    val episodeDirectory =
                        getSubDirectory(
                            context,
                            MediaType.ANIME,
                            false,
                            title,
                            episode
                        ) ?: throw Exception("Directory not found")
                    if (episodeImage != null) {
                        media.anime?.episodes?.get(episode)?.let { episode ->
                            episode.thumb = downloadImage(
                                context,
                                episodeImage,
                                episodeDirectory,
                                "episodeImage.jpg"
                            )?.let { FileUrl(it) }
                        }
                        downloadImage(context, episodeImage, episodeDirectory, "episodeImage.jpg")
                    }
                }

                val jsonString = gson.toJson(media)
                try {
                    file.openOutputStream(context, false).use { output ->
                        if (output == null) throw Exception("Output stream is null")
                        output.write(jsonString.toByteArray())
                    }
                } catch (e: android.system.ErrnoException) {
                    Logger.log(e)
                    withUIContext {
                        Toast.makeText(
                            context,
                            "Error while saving: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private suspend fun downloadImage(context: Context, url: String, directory: DocumentFile, name: String): String? =
        withIOContext {
            var connection: HttpURLConnection? = null
            println("Downloading url $url")
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
                }
                directory.findFile(name)?.forceDelete(context)
                val file =
                    directory.createFile("image/jpeg", name) ?: throw Exception("File not created")
                file.openOutputStream(context, false).use { output ->
                    if (output == null) throw Exception("Output stream is null")
                    connection.inputStream.use { input ->
                        input.copyTo(output)
                    }
                }
                return@withIOContext file.uri.toString()
            } catch (e: Exception) {
                Logger.log(e)
                withUIContext {
                    Toast.makeText(
                        context,
                        "Exception while saving ${name}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                null
            } finally {
                connection?.disconnect()
            }
        }
}