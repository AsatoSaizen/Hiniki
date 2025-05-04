package ani.himitsu

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import ani.himitsu.connections.anilist.api.MediaCoverImage
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.BitmapUtil
import bit.himitsu.content.ScaledContext
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.ObjectKey
import jp.wasabeef.glide.transformations.BlurTransformation
import java.io.File

val Context.imageContext get() = ScaledContext(this).apply {
    if (PrefManager.getVal(PrefName.SmallVille))
        screen(PrefManager.getVal(PrefName.LoisLane))
    else
        restore()
}

fun getNonNullUrl(url: String?): String { return url ?: "" }

fun ImageView.toRoundImage(url: String?, size: Int) {
    Glide.with(this.context.imageContext).asBitmap().load(url).override(size)
        .into(object : CustomTarget<Bitmap>(){
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                this@toRoundImage.setImageBitmap(BitmapUtil.circular(resource))
            }
            override fun onLoadCleared(placeholder: Drawable?) {

            }
        })
}

fun ImageView.loadLocalImage(file: File?, size: Int = 0) {
    if (file?.exists() == true) {
        tryWith {
            Glide.with(this.context.imageContext).load(file).transition(DrawableTransitionOptions.withCrossFade()).override(size)
                .into(this)
        }
    }
}

fun ImageView.loadImage(file: FileUrl?, width: Int = 0, height: Int = 0) {
    file?.url = getNonNullUrl(file.url)
    if (file?.url?.isNotEmpty() == true) {
        tryWith {
            if (file.url.startsWith("content://")) {
                Glide.with(this.context.imageContext).load(Uri.parse(file.url)).transition(
                    DrawableTransitionOptions.withCrossFade()
                )
                    .override(width, height).into(this)
            } else {
                val glideUrl = GlideUrl(file.url) { file.headers }
                Glide.with(this.context.imageContext).load(glideUrl).transition(
                    DrawableTransitionOptions.withCrossFade()
                ).override(width, height).into(this)
            }
        }
    }
}

fun ImageView.loadImage(file: FileUrl?, size: Int = 0) {
    loadImage(file, size, size)
}

fun ImageView.loadImage(url: String?, size: Int = 0) {
    if (!url.isNullOrEmpty()) {
        val localFile = File(url)
        if (localFile.exists()) loadLocalImage(localFile, size) else loadImage(FileUrl(url), size)
    }
}

fun ImageView.loadCover(coverImage: MediaCoverImage?) {
    this.loadImage(coverImage?.extraLarge ?: coverImage?.large ?: coverImage?.medium)
}

fun ImageView.blurImage(banner: String?) {
    if (banner != null) {
        if ((this.context as Activity).isDestroyed) return
        val url = getNonNullUrl(banner)
        val radius = PrefManager.getVal<Float>(PrefName.BlurRadius).toInt()
        val sampling = PrefManager.getVal<Float>(PrefName.BlurSampling).toInt()
        if (PrefManager.getVal(PrefName.BlurBanners)) {
            Glide.with(this.context.imageContext)
                .load(
                    when {
                        banner.startsWith("http") -> GlideUrl(url)
                        banner.startsWith("content://") -> Uri.parse(url)
                        else -> File(url)
                    }
                )
                .override(400)
                .apply(RequestOptions.bitmapTransform(BlurTransformation(radius, sampling)))
                .into(this)

        } else {
            Glide.with(this.context.imageContext)
                .load(
                    when {
                        banner.startsWith("http") -> GlideUrl(url)
                        banner.startsWith("content://") -> Uri.parse(url)
                        else -> File(url)
                    }
                )
                .override(400)
                .into(this)
        }
    } else {
        setImageResource(R.drawable.linear_gradient_bg)
    }
}

fun ImageView.blurCover(coverImage: MediaCoverImage?) {
    this.blurImage(coverImage?.extraLarge ?: coverImage?.large ?: coverImage?.medium)
}

fun ImageView.loadGif(url: String, single: Boolean = false) {
    Glide.with(this)
        .load(url)
        .signature(ObjectKey(url))
        .listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable?>,
                isFirstResource: Boolean
            ): Boolean {
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable?>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                if (resource is GifDrawable) {
                    if (single) {
                        resource.setLoopCount(1)
                    }
                    resource.start()
                }
                return false
            }
        })
        .into(this)
}

fun ImageView.toBitmap(): Bitmap? {
    val drawable = this.drawable ?: return null

    // If the drawable is a BitmapDrawable, then just get the bitmap
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }

    return this.drawable.toBitmap()
}
