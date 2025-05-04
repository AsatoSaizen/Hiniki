package ani.himitsu.others

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import ani.himitsu.navBarHeight

// https://issuetracker.google.com/issues/36911528#comment88
// https://issuetracker.google.com/issues/36911528#comment100
class AndroidBug5497Workaround private constructor(
    activity: Activity,
    private val callback: (Boolean) -> Unit
) {
    private val mChildOfContent: View
    private var usableHeightPrevious = 0
    private val frameLayoutParams: FrameLayout.LayoutParams

    init {
        val content = activity.findViewById<View>(android.R.id.content) as FrameLayout
        mChildOfContent = content.getChildAt(0)
        mChildOfContent.viewTreeObserver.addOnGlobalLayoutListener { possiblyResizeChildOfContent() }
        frameLayoutParams = mChildOfContent.layoutParams as FrameLayout.LayoutParams
    }

    private fun possiblyResizeChildOfContent() {
        val usableHeightNow = computeUsableHeight()
        if (usableHeightNow != usableHeightPrevious) {
            val usableHeightSansKeyboard = mChildOfContent.rootView.height
            val heightDifference = usableHeightSansKeyboard - usableHeightNow
            if (heightDifference > usableHeightSansKeyboard / 4) {
                // keyboard probably just became visible
                callback.invoke(true)
                frameLayoutParams.height = usableHeightSansKeyboard - heightDifference
            } else {
                // keyboard probably just became hidden
                callback.invoke(false)
                frameLayoutParams.height = usableHeightSansKeyboard // - getNavigationBarHeight(mChildOfContent.context)
            }
            mChildOfContent.requestLayout()
            usableHeightPrevious = usableHeightNow
        }
    }

    private fun computeUsableHeight(): Int {
        val r = Rect()
        mChildOfContent.getWindowVisibleDisplayFrame(r)
        return r.bottom
    }

    private fun getNavigationBarHeight(context: Context): Int {
        return if (!ViewConfiguration.get(context).hasPermanentMenuKey()) return navBarHeight else 0
    }
    /**
     * Fixes windowSoftInputMode adjustResize when used with setDecorFitsSystemWindows(false)
     *
     * @see <a href="https://issuetracker.google.com/issues/36911528">adjustResize breaks when activity is fullscreen </a>
     */
    companion object {
        /**
         * Called on an Activity after the content view has been set.
         */
        fun assistActivity(activity: Activity, callback: (Boolean) -> Unit) {
            AndroidBug5497Workaround(activity, callback)
        }
    }
}
