package ani.himitsu.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.core.view.updateLayoutParams
import ani.himitsu.navBarHeight
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.internal.ViewUtils

@SuppressLint("RestrictedApi")
class CustomBottomNavBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BottomNavigationView(context, attrs) {
    init {
        ViewUtils.doOnApplyWindowInsets(
            this
        ) { view, insets, initialPadding ->
            initialPadding.bottom = 0
            updateLayoutParams<MarginLayoutParams> { bottomMargin = navBarHeight }
            initialPadding.applyToView(view)
            insets
        }
    }
}