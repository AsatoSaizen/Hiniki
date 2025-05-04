package ani.himitsu.view

import android.animation.ObjectAnimator
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import ani.himitsu.setAnimation
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName

class ZoomOutPageTransformer :
    ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        if (position == 0f && PrefManager.getVal(PrefName.LayoutAnimations)) {
            setAnimation(
                view.context,
                view,
                300,
                floatArrayOf(1.3f, 1f, 1.3f, 1f),
                0.5f to 0f
            )
            ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
                .setDuration((200 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong())
                .start()
        }
    }
}