package ani.himitsu.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.collection.LruCache
import bit.himitsu.content.toPx
import bit.himitsu.io.Memory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

object BitmapUtil {
    private fun roundCorners(bitmap: Bitmap, cornerRadius: Int = 16): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius.toPx.toFloat(), cornerRadius.toPx.toFloat(), paint)

        return output
    }

    fun circular(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawCircle(rect.centerX(), rect.centerY(), bitmap.width.toFloat() / 2, paint)

        return output
    }

    private val cacheSize = (Memory.maxMemory() / 1048576).toInt() // KB
    private val bitmapCache = LruCache<String, Bitmap>(cacheSize)

    fun downloadBitmap(imageUrl: String): Bitmap? {
        var bitmap: Bitmap? = null
        runBlocking(Dispatchers.IO) {
            var urlConnection: HttpURLConnection? = null
            try {
                urlConnection = URL(imageUrl).openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.connect()

                if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    bitmap = BitmapFactory.decodeStream(urlConnection.inputStream)
                }
            } catch (e: Exception) {
                Logger.log(e)
            } finally {
                urlConnection?.disconnect()
            }
        }
        return bitmap
    }

    fun downloadImageAsBitmap(imageUrl: String): Bitmap? {
        var bitmap: Bitmap?
        runBlocking(Dispatchers.IO) {
            val cacheName = imageUrl.substringAfterLast("/")
            bitmap = bitmapCache[cacheName]
            if (bitmap != null) return@runBlocking

            bitmap = downloadBitmap(imageUrl)
            bitmap?.let { bitmapCache.put(cacheName, it) }
        }
        return bitmap?.let { roundCorners(it) }
    }

    fun Bitmap.scaleTo(maxWidth: Int, maxHeight: Int): Bitmap {
        val ratioBitmap = this.width.toFloat() / this.height.toFloat()
        var finalWidth = maxWidth
        var finalHeight = maxHeight
        if (maxWidth.toFloat() / maxHeight.toFloat() > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }
        return Bitmap.createScaledBitmap(this, finalWidth, finalHeight, true)
    }

    fun Bitmap.toSquare(): Bitmap {
        val side = min(width, height)
        val xOffset = (width - side) / 2
        // Slight offset for the y, since a lil bit under the top is usually the focus of covers
        val yOffset = ((height - side) / 2 * 0.25).toInt()
        return Bitmap.createBitmap(this, xOffset, yOffset, side, side)
    }
}
