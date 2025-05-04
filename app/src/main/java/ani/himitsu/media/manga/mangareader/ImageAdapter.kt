package ani.himitsu.media.manga.mangareader

import android.animation.ObjectAnimator
import android.content.res.Resources.getSystem
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.databinding.ItemImageBinding
import ani.himitsu.media.manga.MangaChapter
import ani.himitsu.settings.CurrentReaderSettings.Layouts.PAGED
import ani.himitsu.settings.directionVert
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import bit.himitsu.content.isNightMode
import bit.himitsu.content.negative
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

open class ImageAdapter(
    activity: MangaReaderActivity,
    chapter: MangaChapter
) : BaseImageAdapter(activity, chapter) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    inner class ImageViewHolder(binding: ItemImageBinding)
        : RecyclerView.ViewHolder(binding.root as View)

    open suspend fun loadBitmap(position: Int, parent: View): Bitmap? {
        val link = images.getOrNull(position)?.url ?: return null
        if (link.url.isEmpty()) return null

        val transforms = mutableListOf<BitmapTransformation>()
        val parserTransformation = activity.getTransformation(images[position])

        if (parserTransformation != null) transforms.add(parserTransformation)
        if (settings.cropBorders) {
            transforms.add(RemoveBordersTransformation(true, settings.cropBorderThreshold))
            transforms.add(RemoveBordersTransformation(false, settings.cropBorderThreshold))
        }

        return activity.loadBitmap(link, transforms)
    }

    override suspend fun loadImage(position: Int, parent: View): Boolean {
        val imageView = parent.findViewById<SubsamplingScaleImageView>(R.id.imgProgImageNoGestures)?.apply {
            setHardwareConfig(settings.hardColors)
        }
            ?: return false
        val progress = parent.findViewById<View>(R.id.imgProgProgress) ?: return false
        imageView.recycle()
        imageView.visibility = View.GONE

        var bitmap = loadBitmap(position, parent) ?: return false

        if (settings.autoNegative && PrefManager.getVal<Int>(PrefName.DarkMode) == 0) {
            if (activity.isNightMode) bitmap.negative()
        } else {
            if (settings.photoNegative) bitmap.negative()
        }

        var sWidth = getSystem().displayMetrics.widthPixels
        var sHeight = getSystem().displayMetrics.heightPixels

        if (settings.layout != PAGED)
            parent.updateLayoutParams {
                if (settings.directionVert) {
                    sHeight =
                        if (settings.wrapImages) bitmap.height else (sWidth * bitmap.height * 1f / bitmap.width).toInt()
                    height = sHeight
                } else {
                    sWidth =
                        if (settings.wrapImages) bitmap.width else (sHeight * bitmap.width * 1f / bitmap.height).toInt()
                    width = sWidth
                }
            }

        imageView.visibility = View.VISIBLE
        imageView.setImage(ImageSource.cachedBitmap(bitmap))

        val parentArea = sWidth * sHeight * 1f
        val bitmapArea = bitmap.width * bitmap.height * 1f
        val scale =
            if (parentArea < bitmapArea) (bitmapArea / parentArea) else (parentArea / bitmapArea)

        imageView.maxScale = scale * 1.1f
        imageView.minScale = scale

        ObjectAnimator.ofFloat(parent, "alpha", 0f, 1f)
            .setDuration((400 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong())
            .start()
        progress.visibility = View.GONE

        return true
    }

    override fun getItemCount(): Int = images.size

    override fun isZoomed(): Boolean {
        val imageView =
            activity.findViewById<SubsamplingScaleImageView>(R.id.imgProgImageNoGestures)
        return imageView.scale > imageView.minScale
    }

    override fun setZoom(zoom: Float) {
        val imageView =
            activity.findViewById<SubsamplingScaleImageView>(R.id.imgProgImageNoGestures)
        imageView.setScaleAndCenter(zoom, imageView.center)
    }
}
