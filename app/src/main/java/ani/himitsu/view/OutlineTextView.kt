package ani.himitsu.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import ani.himitsu.R
import bit.himitsu.content.toPx

class OutlineTextView : AppCompatTextView {

    private val defaultStrokeWidth = 0F
    private var isDrawing: Boolean = false

    private var strokeColor: Int = 0
    private var strokeWidth: Float = 0.toFloat()

    constructor(context: Context) : super(context) {
        initResources(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initResources(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initResources(context, attrs)
    }

    private fun initResources(context: Context?, attrs: AttributeSet?) {
        strokeColor = currentTextColor
        strokeWidth = defaultStrokeWidth
        if (attrs != null) {
            context?.obtainStyledAttributes(attrs, R.styleable.OutlineTextView)?.let {
                try {
                    strokeColor = it.getColor(
                        R.styleable.OutlineTextView_outlineColor,
                        currentTextColor
                    )
                    strokeWidth = it.getFloat(
                        R.styleable.OutlineTextView_outlineWidth,
                        defaultStrokeWidth
                    )
                } finally {
                    it.recycle()
                }
            }
        }
        setStrokeWidth(strokeWidth)
    }

    private fun setStrokeWidth(width: Float) {
        strokeWidth = width.toPx.toFloat()
    }

    override fun invalidate() {
        if (isDrawing) return
        super.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (strokeWidth > 0) {
            isDrawing = true
            super.onDraw(canvas)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            val colorTmp = paint.color
            setTextColor(strokeColor)
            super.onDraw(canvas)

            setTextColor(colorTmp)
            paint.style = Paint.Style.FILL

            isDrawing = false
        } else {
            super.onDraw(canvas)
        }
    }
}
