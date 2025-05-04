package ani.himitsu.media.manga

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import ani.himitsu.Himitsu
import ani.himitsu.snackString
import ani.himitsu.util.Logger
import bit.himitsu.io.Memory
import bit.himitsu.os.Version
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext
import java.io.File
import java.io.FileOutputStream

data class ImageData(
    val page: Page,
    val source: HttpSource
) {
    suspend fun fetchAndProcessImage(
        page: Page,
        httpSource: HttpSource
    ): Bitmap? {
        return withIOContext {
            try {
                // Fetch the image
                val response = httpSource.getImage(page)
                Logger.log("Response: ${response.code} - ${response.message}")
                return@withIOContext response.body.byteStream().use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) {
                // Handle any exceptions
                Logger.log("An error occurred: ${e.message}")
                snackString("An error occurred: ${e.message}")
                return@withIOContext null
            }
        }
    }
}

fun saveImage(
    bitmap: Bitmap,
    contentResolver: ContentResolver,
    filename: String,
    format: Bitmap.CompressFormat,
    quality: Int
) {
    try {
        if (Version.isQuinceTart) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/${format.name.lowercase()}")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}${File.separator}${Himitsu.appName}${File.separator}Manga"
                )
            }

            val uri: Uri? =
                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                contentResolver.openOutputStream(it)?.use { os ->
                    bitmap.compress(format, quality, os)
                }
            }
        } else {
            val directory =
                File("${Environment.getExternalStorageDirectory()}${File.separator}${Himitsu.appName}${File.separator}Manga")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, filename)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(format, quality, outputStream)
            }
        }
    } catch (e: Exception) {
        // Handle exception here
        println("Exception while saving image: ${e.message}")
    }
}

class MangaCache {
    private val maxMemory = (Memory.maxMemory() / 1024).toInt() // MB
    private val cache = LruCache<String, ImageData>(maxMemory)

    @Synchronized
    fun put(key: String, imageDate: ImageData) {
        cache.put(key, imageDate)
    }

    @Synchronized
    fun get(key: String): ImageData? = cache.get(key)

    @Synchronized
    fun remove(key: String) {
        cache.remove(key)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }

    fun size(): Int = cache.size()


}
