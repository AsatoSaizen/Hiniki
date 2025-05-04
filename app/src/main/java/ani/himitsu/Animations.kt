package ani.himitsu

import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import androidx.core.animation.doOnEnd
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext
import kotlin.math.max

fun setAnimation(
    context: Context,
    viewToAnimate: View,
    duration: Long = 150,
    list: FloatArray = floatArrayOf(0f, 1f, 0f, 1f),
    pivot: Pair<Float, Float> = 0.5f to 0.5f
) {
    if (PrefManager.getVal(PrefName.LayoutAnimations)) {
        val anim = ScaleAnimation(
            list[0],
            list[1],
            list[2],
            list[3],
            Animation.RELATIVE_TO_SELF,
            pivot.first,
            Animation.RELATIVE_TO_SELF,
            pivot.second
        )
        anim.duration = (duration * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong()
        anim.setInterpolator(context, R.anim.over_shoot)
        viewToAnimate.startAnimation(anim)
    }
}

fun View.circularReveal(ex: Int, ey: Int, subX: Boolean, time: Long) {
    ViewAnimationUtils.createCircularReveal(
        this,
        if (subX) (ex - x.toInt()) else ex,
        ey - y.toInt(),
        0f,
        max(height, width).toFloat()
    ).setDuration(time).start()
}

fun setSlideIn() = AnimationSet(false).apply {
    if (PrefManager.getVal(PrefName.LayoutAnimations)) {
        var animation: Animation = AlphaAnimation(0f, 1f)
        val animationSpeed: Float = PrefManager.getVal(PrefName.AnimationSpeed)
        animation.duration = (500 * animationSpeed).toLong()
        animation.interpolator = AccelerateDecelerateInterpolator()
        addAnimation(animation)

        animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f
        )

        animation.duration = (750 * animationSpeed).toLong()
        animation.interpolator = OvershootInterpolator(1.1f)
        addAnimation(animation)
    }
}

fun setSlideUp() = AnimationSet(false).apply {
    if (PrefManager.getVal(PrefName.LayoutAnimations)) {
        var animation: Animation = AlphaAnimation(0f, 1f)
        val animationSpeed: Float = PrefManager.getVal(PrefName.AnimationSpeed)
        animation.duration = (500 * animationSpeed).toLong()
        animation.interpolator = AccelerateDecelerateInterpolator()
        addAnimation(animation)

        animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 0f
        )

        animation.duration = (750 * animationSpeed).toLong()
        animation.interpolator = OvershootInterpolator(1.1f)
        addAnimation(animation)
    }
}

suspend fun View.pop() {
    withUIContext {
        ObjectAnimator.ofFloat(this@pop, "scaleX", 1f, 1.25f).setDuration(120).apply {
            doOnEnd {
                ObjectAnimator.ofFloat(this@pop, "scaleX", 1.25f, 1f).setDuration(100).start()
            }
        }.start()
        ObjectAnimator.ofFloat(this@pop, "scaleY", 1f, 1.25f).setDuration(120).apply {
            doOnEnd {
                ObjectAnimator.ofFloat(this@pop, "scaleY", 1.25f, 1f).setDuration(100).start()
            }
        }.start()
    }
}