/**
 * Copyright © 2016 Evander Palacios
 * Copyright © 2024 AbandonedCart
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ani.himitsu.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.TextView
import bit.himitsu.content.toPx

@SuppressLint("AppCompatCustomView")
class StrokedTextView(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int
) : TextView(context, attrs, defStyleAttr, defStyleRes) {

    // fields
    private var _strokeColor = 0
    private var _strokeWidth = 0f
    private var _shadowColor = 0
    private var _shadowWidth = 0f
    private var isDrawing = false

    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : this(context, attrs, defStyleAttr, 0)

    override fun invalidate() {
        // Ignore invalidate() calls when isDrawing == true
        // (setTextColor(color) calls will trigger them,
        // creating an infinite loop)
        if (isDrawing) return
        super.invalidate()
    }

    fun setStrokeColor(color: Int) {
        _strokeColor = color
        invalidate()
    }

    fun setStrokeWidth(width: Float) {
        //convert values specified in dp in XML layout to
        //px, otherwise stroke width would appear different
        //on different screens
        _strokeWidth = width.toPx.toFloat()
        invalidate()
    }

    fun setStrokeWidth(unit: Int, width: Float) {
        _strokeWidth = TypedValue.applyDimension(
            unit, width, context.resources.displayMetrics
        )
    }

    fun setShadowColor(color: Int) {
        _shadowColor = color
        invalidate()
    }

    fun setShadowWidth(width: Float) {
        //convert values specified in dp in XML layout to
        //px, otherwise stroke width would appear different
        //on different screens
        _shadowWidth = width.toPx.toFloat()
        invalidate()
    }

    fun setShadowWidth(unit: Int, width: Float) {
        _shadowWidth = TypedValue.applyDimension(
            unit, width, context.resources.displayMetrics
        )
    }

    // overridden methods
    override fun onDraw(canvas: Canvas) {
        if (_strokeWidth > 0 || _shadowWidth > 0) {
            isDrawing = true
            //set paint to fill mode
            val p: Paint = paint
            p.style = Paint.Style.FILL
            //draw the fill part of text
            if (_shadowWidth > 0) {
                p.setShadowLayer(_shadowWidth, _shadowWidth, _shadowWidth, _shadowColor)
            }
            super.onDraw(canvas)
            //save the text color
            val currentTextColor = currentTextColor
            //set paint to stroke mode and specify
            //stroke color and width
            if (_strokeWidth > 0) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = _strokeWidth
                setTextColor(_strokeColor)
                //draw text stroke
                super.onDraw(canvas)
            }
            //revert the color back to the one
            //initially specified
            setTextColor(currentTextColor)
            isDrawing = false
        } else {
            super.onDraw(canvas)
        }
    }
}