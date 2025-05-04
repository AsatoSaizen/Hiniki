package ani.himitsu.media

import android.content.Context
import androidx.media3.common.MimeTypes
import ani.himitsu.R
import ani.himitsu.download.DownloadedType
import ani.himitsu.download.DownloadsManager
import ani.himitsu.parsers.SubtitleType
import ani.himitsu.toast
import ani.himitsu.util.Logger
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SubtitleDownloader {
    suspend fun loadSubtitleType(url: String): SubtitleType = withIOContext {
        return@withIOContext try {
            // Initialize the NetworkHelper instance. Replace this line based on how you usually initialize it
            val networkHelper = Injekt.get<NetworkHelper>()
            val request = Request.Builder()
                .url(url)
                .build()

            val response = networkHelper.client.newCall(request).execute()

            // Check if response is successful
            if (response.isSuccessful) {
                val responseBody = response.body.string()

                val formats = arrayOf(
                    MimeTypes.TEXT_VTT,
                    MimeTypes.APPLICATION_TTML,
                    MimeTypes.APPLICATION_SUBRIP,
                    MimeTypes.TEXT_SSA
                )

                response.headers.find {
                    it.first == "Content-Type" && formats.contains(it.second)
                }?.let {
                    SubtitleType.fromMimeType(it.second)
                } ?: when {
                    responseBody.contains("[Script Info]") -> SubtitleType.ASS
                    responseBody.contains("WEBVTT") -> SubtitleType.VTT
                    else -> SubtitleType.SRT
                }
            } else {
                SubtitleType.UNKNOWN
            }
        } catch (e: Exception) {
            Logger.log(e)
            SubtitleType.UNKNOWN
        }
    }

    suspend fun downloadSubtitle(
        context: Context,
        url: String,
        downloadedType: DownloadedType
    ) = withIOContext {
        try {
            val directory = DownloadsManager.getSubDirectory(
                context,
                downloadedType.type,
                false,
                downloadedType.titleName,
                downloadedType.chapterName
            ) ?: throw Exception("Could not create directory")
            val type = loadSubtitleType(url)
            directory.findFile("subtitle.${type}")?.delete()
            val subtitleFile = directory.createFile("*/*", "subtitle.${type}")
                ?: throw Exception("Could not create subtitle file")

            val client = Injekt.get<NetworkHelper>().client
            val request = Request.Builder().url(url).build()
            val reponse = client.newCall(request).execute()

            if (!reponse.isSuccessful) {
                toast(R.string.sub_download_failed)
                return@withIOContext
            }

            context.contentResolver.openOutputStream(subtitleFile.uri).use { output ->
                output?.write(reponse.body.bytes())
                    ?: throw Exception("Could not open output stream")
            }
        } catch (e: Exception) {
            toast(R.string.sub_download_failed)
            Logger.log(e)
            return@withIOContext
        }
    }
}