package ani.himitsu.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.HorizontalScrollView
import kotlin.math.abs

/*
 * https://stackoverflow.com/a/54933073/461982
 */
class PagedHorizontalScrollView : HorizontalScrollView {
    var xOld: Float = 0f
    var yOld: Float = 0f

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            //Start of touch.  Could be tap, could be drag.
            xOld = ev.x
            yOld = ev.y
        } else if (action == MotionEvent.ACTION_MOVE) {
            //Drag movement underway
            val deltaX = ev.x - xOld
            val deltaY = ev.y - yOld

            if (abs(deltaX.toDouble()) > abs(deltaY.toDouble())) {
                //scrolling more left/right than up/down
                if (deltaX > 0 && scrollX == 0) {
                    // dragging left, at left edge of HorizontalScrollView.
                    // Don't handle this touch event, let it bubble up to ViewPager
                    return true // Consume the event
                    // dragging right. Use first child to determine width of content inside HorizontalScrollView
                } else if (deltaX < 0 && (this.scrollX + this.width) >= getChildAt(0).width) {
                    // swiping left, and at right edge of HorizontalScrollView.
                    // Don't handle this touch event, let it bubble up to ViewPager
                    return true // Consume the event
                }
            }
        }
        return super.onTouchEvent(ev)
    }
}