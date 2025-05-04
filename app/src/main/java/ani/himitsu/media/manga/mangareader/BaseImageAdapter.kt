package ani.himitsu.media.manga.mangareader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.FileUrl
import ani.himitsu.R
import ani.himitsu.media.manga.MangaCache
import ani.himitsu.media.manga.MangaChapter
import ani.himitsu.settings.CurrentReaderSettings
import ani.himitsu.settings.bottomTopPaged
import ani.himitsu.settings.directionHorz
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.tryWithSuspend
import ani.himitsu.view.GestureSlider
import bit.himitsu.content.toPx
import com.alexvasilkov.gestures.views.GestureFrameLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.math.max

abstract class BaseImageAdapter(
    val activity: MangaReaderActivity,
    chapter: MangaChapter
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val settings = activity.defaultSettings
    private val chapterImages = chapter.images()
    var images = chapterImages

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        images =  chapterImages.let {
            if (settings.bottomTopPaged) it.reversed() else it
        }
        super.onAttachedToRecyclerView(recyclerView)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val view = holder.itemView as GestureFrameLayout
        view.controller.also {
            if (settings.layout == CurrentReaderSettings.Layouts.PAGED) {
                it.settings.enableGestures()
            }
            it.settings.isRotationEnabled = settings.rotation
        }
        if (settings.layout != CurrentReaderSettings.Layouts.PAGED) {
            if (settings.padding) {
                view.setPadding(0, 0, 0, 0)
                when (settings.direction) {
                    CurrentReaderSettings.Directions.TOP_TO_BOTTOM ->
                        view.updatePadding(bottom = 16.toPx)

                    CurrentReaderSettings.Directions.LEFT_TO_RIGHT ->
                        view.updatePadding(right = 16.toPx)

                    CurrentReaderSettings.Directions.BOTTOM_TO_TOP ->
                        view.updatePadding(top = 16.toPx)

                    CurrentReaderSettings.Directions.RIGHT_TO_LEFT ->
                        view.updatePadding(left = 16.toPx)
                }
            }
            view.updateLayoutParams {
                if (settings.directionHorz) {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT // 480.toPx
                } else {
                    width = ViewGroup.LayoutParams.MATCH_PARENT // 480.toPx
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
        } else {
            val detector = GestureDetector(view.context, object : GestureSlider() {
                override fun onSingleClick(event: MotionEvent) =
                    activity.handleController(event = event)
            })
            view.findViewById<View>(R.id.imgProgCover).apply {
                setOnTouchListener { _, event ->
                    detector.onTouchEvent(event)
                    false
                }
                setOnLongClickListener {
                    val pos = holder.bindingAdapterPosition
                    val image = images.getOrNull(pos) ?: return@setOnLongClickListener false
                    activity.onImageLongClicked(pos, image, null) { dialog ->
                        activity.lifecycleScope.launch {
                            loadImage(pos, view)
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        dialog.dismiss()
                    }
                }
            }
        }
        activity.lifecycleScope.launch { loadImage(holder.bindingAdapterPosition, view) }
    }

    abstract fun isZoomed(): Boolean
    abstract fun setZoom(zoom: Float)

    abstract suspend fun loadImage(position: Int, parent: View): Boolean

    companion object {
        suspend fun Context.loadBitmapOld(
            link: FileUrl,
            transforms: List<BitmapTransformation>
        ): Bitmap? { // still used in some places
            return tryWithSuspend {
                withIOContext {
                    Glide.with(this@loadBitmapOld)
                        .asBitmap()
                        .let {
                            if (link.url.startsWith("file://")) {
                                it.load(link.url)
                                    .skipMemoryCache(true)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                            } else {
                                it.load(GlideUrl(link.url) { link.headers })
                            }
                        }
                        .let {
                            if (transforms.isNotEmpty()) {
                                it.transform(*transforms.toTypedArray())
                            } else {
                                it
                            }
                        }
                        .submit()
                        .get()
                }
            }
        }

        suspend fun Context.loadBitmap(
            link: FileUrl,
            transforms: List<BitmapTransformation>
        ): Bitmap? {
            return tryWithSuspend {
                val mangaCache = uy.kohesive.injekt.Injekt.get<MangaCache>()
                withIOContext {
                    Glide.with(this@loadBitmap)
                        .asBitmap()
                        .let {
                            val localFile = File(link.url)
                            if (localFile.exists()) {
                                it.load(localFile.absoluteFile)
                                    .skipMemoryCache(true)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                            } else if (link.url.startsWith("content://")) {
                                it.load(Uri.parse(link.url))
                                    .skipMemoryCache(true)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                            } else {
                                mangaCache.get(link.url)?.let { imageData ->
                                    val response = imageData.source.getImage(imageData.page)
                                    if (response.isSuccessful) {
                                        it.load(response.body.bytes())
                                            // .skipMemoryCache(true)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    } else {
                                        it.load(
                                            imageData.fetchAndProcessImage(
                                                imageData.page,
                                                imageData.source
                                            )
                                        )
                                            // .skipMemoryCache(true)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    }
                                }
                            }
                        }
                        ?.let {
                            if (transforms.isNotEmpty()) {
                                it.transform(*transforms.toTypedArray())
                            } else {
                                it
                            }
                        }
                        ?.submit()
                        ?.get()
                }
            }
        }

        fun mergeBitmap(bitmap1: Bitmap, bitmap2: Bitmap, scale: Boolean = false): Bitmap {
            val height = max(bitmap1.height, bitmap2.height)
            val (bit1, bit2) = if (!scale) bitmap1 to bitmap2 else {
                val width1 = bitmap1.width * height * 1f / bitmap1.height
                val width2 = bitmap2.width * height * 1f / bitmap2.height
                (Bitmap.createScaledBitmap(
                    bitmap1, width1.toInt(), height, false
                ) to Bitmap.createScaledBitmap(
                    bitmap2, width2.toInt(), height, false
                ))
            }
            return Bitmap.createBitmap(
                bit1.width + bit2.width,
                height,
                if (PrefManager.getVal(PrefName.HardColors)
                    || PrefManager.getVal(PrefName.TrueColors))
                    Bitmap.Config.ARGB_8888
                else
                    Bitmap.Config.RGB_565
            ).apply {
                with (Canvas(this)) {
                    drawBitmap(bit1, 0f, (height * 1f - bit1.height) / 2, null)
                    drawBitmap(bit2, bit1.width.toFloat(), (height * 1f - bit2.height) / 2, null)
                    bitmap1.recycle()
                    bitmap2.recycle()
                }
            }
        }
    }
}