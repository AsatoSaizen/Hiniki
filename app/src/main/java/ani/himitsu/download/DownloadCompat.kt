package ani.himitsu.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import ani.himitsu.Himitsu
import ani.himitsu.R
import ani.himitsu.connections.Status
import ani.himitsu.currContext
import ani.himitsu.download.anime.OfflineAnimeModel
import ani.himitsu.download.manga.OfflineMangaModel
import ani.himitsu.media.MediaNameAdapter
import ani.himitsu.media.MediaType
import ani.himitsu.media.cereal.Media
import ani.himitsu.parsers.Episode
import ani.himitsu.parsers.MangaChapter
import ani.himitsu.parsers.MangaImage
import ani.himitsu.parsers.Subtitle
import ani.himitsu.parsers.SubtitleType
import ani.himitsu.util.Logger
import bit.himitsu.nio.Strings.getString
import bit.himitsu.nio.totalEpisodeText
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.SEpisodeImpl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import eu.kanade.tachiyomi.source.model.SManga
import java.io.File
import kotlin.collections.set

fun directory(downloadedType: DownloadedType) = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    "${Himitsu.appName}/${downloadedType.type.text}/${downloadedType.titleName}"
)

fun directory(type: MediaType, path: String) = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    "${Himitsu.appName}/{$type.text}/$path"
)

object DownloadCompat {
    @Deprecated(EXTERNAL_STORAGE_DEPRECATED)
    fun loadMediaCompat(downloadedType: DownloadedType): Media? {
        val directory = directory(downloadedType)
        //load media.json and convert to media class with gson
        return try {
            val gson = GsonBuilder()
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
            val media = File(directory, "media.json")
            val mediaJson = media.readText()
            gson.fromJson(mediaJson, Media::class.java)
        } catch (e: Exception) {
            Logger.log("Error loading media.json: ${e.message}")
            Logger.log(e)
            null
        }
    }

    @Deprecated(EXTERNAL_STORAGE_DEPRECATED)
    fun loadOfflineAnimeModelCompat(downloadedType: DownloadedType): OfflineAnimeModel {
        val directory = directory(downloadedType)
        //load media.json and convert to media class with gson
        try {
            @Suppress("DEPRECATION")
            val mediaModel = loadMediaCompat(downloadedType)!!
            val cover = File(directory, "cover.jpg")
            val coverUri: Uri? = if (cover.exists()) {
                Uri.fromFile(cover)
            } else null
            val banner = File(directory, "banner.jpg")
            val bannerUri: Uri? = if (banner.exists()) {
                Uri.fromFile(banner)
            } else null
            val title = mediaModel.mainName()
            val score = ((if (mediaModel.userScore == 0) (mediaModel.meanScore
                ?: 0) else mediaModel.userScore) / 10.0).toString()
            val isOngoing = mediaModel.status == getString(R.string.status_releasing)
            val isUserScored = mediaModel.userScore != 0
            val watchedEpisodes = (mediaModel.userProgress ?: "~").toString()
            val chapters = " Chapters"
            val totalEpisodesList = "${mediaModel.anime?.totalEpisodes?.takeIf {
                it >= (mediaModel.anime.nextAiringEpisode ?: 0)
            } ?: mediaModel.anime?.nextAiringEpisode ?: "??"}"

            return OfflineAnimeModel(
                title,
                score,
                mediaModel.anime?.totalEpisodeText.toString(),
                totalEpisodesList,
                watchedEpisodes,
                downloadedType.type.text,
                chapters,
                isOngoing,
                isUserScored,
                coverUri,
                bannerUri
            )
        } catch (e: Exception) {
            Logger.log("Error loading media.json: ${e.message}")
            Logger.log(e)
            return OfflineAnimeModel(
                downloadedType.titleName,
                "0",
                "??",
                "??",
                "??",
                downloadedType.type.text,
                downloadedType.chapterName,
                isOngoing = false,
                isUserScored = false,
                null,
                null
            )
        }
    }

    @Deprecated(EXTERNAL_STORAGE_DEPRECATED)
    fun loadOfflineMangaModelCompat(downloadedType: DownloadedType): OfflineMangaModel {
        val directory = directory(downloadedType)
        //load media.json and convert to media class with gson
        try {
            @Suppress("DEPRECATION")
            val mediaModel = loadMediaCompat(downloadedType)!!
            val cover = File(directory, "cover.jpg")
            val coverUri: Uri? = if (cover.exists()) {
                Uri.fromFile(cover)
            } else null
            val banner = File(directory, "banner.jpg")
            val bannerUri: Uri? = if (banner.exists()) {
                Uri.fromFile(banner)
            } else null
            val title = mediaModel.mainName()
            val score = ((if (mediaModel.userScore == 0) (mediaModel.meanScore
                ?: 0) else mediaModel.userScore) / 10.0).toString()
            val isOngoing = mediaModel.status == getString(R.string.status_releasing)
            val isUserScored = mediaModel.userScore != 0
            val readchapter = (mediaModel.userProgress ?: "~").toString()
            val totalchapter = "${mediaModel.manga?.totalChapters ?: "??"}".let {
                if (mediaModel.status == Status.FINISHED) it else "[${it}]"
            }
            val chapters = " Chapters"
            return OfflineMangaModel(
                title,
                score,
                totalchapter,
                readchapter,
                downloadedType.type.text,
                chapters,
                isOngoing,
                isUserScored,
                coverUri,
                bannerUri
            )
        } catch (e: Exception) {
            Logger.log("Error loading media.json: ${e.message}")
            Logger.log(e)
            return OfflineMangaModel(
                downloadedType.titleName,
                "0",
                "??",
                "??",
                downloadedType.type.text,
                downloadedType.chapterName,
                isOngoing = false,
                isUserScored = false,
                null,
                null
            )
        }
    }

    @Deprecated(EXTERNAL_STORAGE_DEPRECATED)
    fun loadEpisodesCompat(
        animeLink: String,
        extra: Map<String, String>?,
        sAnime: SAnime
    ): List<Episode> {

        val directory = directory(MediaType.ANIME, animeLink)
        //get all of the folder names and add them to the list
        val episodes = mutableListOf<Episode>()
        if (directory.exists()) {
            directory.listFiles()?.forEach {
                //put the title and episdode number in the extra data
                val extraData = mutableMapOf<String, String>()
                extraData["title"] = animeLink
                extraData["episode"] = it.name
                if (it.isDirectory) {
                    val episode = Episode(
                        it.name,
                        "$animeLink - ${it.name}",
                        it.name,
                        null,
                        null,
                        extra = extraData,
                        sEpisode = SEpisodeImpl()
                    )
                    episodes.add(episode)
                }
            }
            episodes.sortBy { MediaNameAdapter.findEpisodeNumber(it.number) }
            return episodes
        }
        return emptyList()
    }

    @Deprecated(EXTERNAL_STORAGE_DEPRECATED)
    fun loadChaptersCompat(
        mangaLink: String,
        extra: Map<String, String>?,
        sManga: SManga
    ): List<MangaChapter> {
        val directory = directory(MediaType.MANGA, mangaLink)
        //get all of the folder names and add them to the list
        val chapters = mutableListOf<MangaChapter>()
        if (directory.exists()) {
            directory.listFiles()?.filter { it.isDirectory }?.forEach {
                val chapter = MangaChapter(
                    it.name,
                    "$mangaLink/${it.name}",
                    it.name,
                    null,
                    "??",
                    SChapter.create()
                )
                chapters.add(chapter)
            }
            chapters.sortBy { MediaNameAdapter.findChapterNumber(it.number) }
            return chapters
        }
        return emptyList()
    }

    @Deprecated(EXTERNAL_STORAGE_DEPRECATED)
    fun loadImagesCompat(chapterLink: String, sChapter: SChapter): List<MangaImage> {
        val directory = directory(MediaType.MANGA, chapterLink)
        val images = mutableListOf<MangaImage>()
        val imageNumberRegex = Regex("""(\d+)\.jpg$""")
        if (directory.exists()) {
            directory.listFiles()?.filter { it.isFile }?.forEach {
                val image = MangaImage(it.absolutePath, false, null)
                images.add(image)
            }
            images.sortBy { image ->
                val matchResult = imageNumberRegex.find(image.url.url)
                matchResult?.groups?.get(1)?.value?.toIntOrNull() ?: Int.MAX_VALUE
            }
            images.forEach {
                Logger.log("imageNumber: ${it.url.url}")
            }
            return images
        }
        return emptyList()
    }

    @Deprecated(EXTERNAL_STORAGE_DEPRECATED)
    fun loadSubtitleCompat(title: String, episode: String): List<Subtitle>? {
        currContext().let {
            directory(MediaType.ANIME, "$title/$episode").listFiles()?.filter {
                it.name.contains("subtitle")
            }?.forEach { file ->
                return listOf(
                    Subtitle(
                        "Downloaded Subtitle",
                        Uri.fromFile(file).toString(),
                        determineSubtitletype(file.absolutePath)
                    )
                )
            }
        }
        return null
    }

    private fun determineSubtitletype(url: String): SubtitleType {
        return when {
            url.endsWith("ass", true) -> SubtitleType.ASS
            url.endsWith("vtt", true) -> SubtitleType.VTT
            else -> SubtitleType.SRT
        }
    }

    @Deprecated(EXTERNAL_STORAGE_DEPRECATED)
    fun removeMediaCompat(context: Context, title: String, type: MediaType) {
        val directory = directory(type, title)
        if (directory.exists()) {
            directory.deleteRecursively()
        }
    }

    @Deprecated(EXTERNAL_STORAGE_DEPRECATED)
    fun removeDownloadCompat(context: Context, downloadedType: DownloadedType) {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "${Himitsu.appName}/${downloadedType.type.text}/${downloadedType.titleName}/${downloadedType.chapterName}"
        )

        // Check if the directory exists and delete it recursively
        if (directory.exists()) {
            val deleted = directory.deleteRecursively()
            if (deleted) {
                Toast.makeText(context,
                    context.getString(R.string.successfully_deleted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context,
                    context.getString(R.string.failed_to_delete_directory), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private const val EXTERNAL_STORAGE_DEPRECATED = "External storage is deprecated. Use SAF instead."
}