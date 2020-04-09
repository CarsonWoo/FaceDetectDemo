package com.example.facedetectdemo.widget

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import com.example.facedetectdemo.R
import kotlin.math.min

class RoundTextureView: TextureView {

    private var mRadius = 0F

    constructor(context: Context): this(context, null)

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RoundTextureView)
        a.apply {
            mRadius = if (hasValue(R.styleable.RoundTextureView_textureRadius)) {
                // 默认为圆形
                a.getFloat(R.styleable.RoundTextureView_textureRadius, measuredWidth.toFloat() / 2)
            } else {
                min(measuredWidth, measuredHeight).toFloat() / 2
            }
        }
        a.recycle()

        outlineProvider = object: ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: Outline?) {
                val rect = if (measuredWidth <= measuredHeight) {
                    Rect(0, (measuredHeight - measuredWidth) / 2, measuredWidth, (measuredHeight + measuredWidth) / 2)
                } else {
                    Rect((measuredWidth - measuredHeight) / 2, 0, measuredHeight, (measuredHeight + measuredWidth) / 2)
                }
//                Log.e("RoundTextureView", "rect left = ${rect.left} top = ${rect.top} width = ${rect.width()} height = ${rect.height()}")
//                outline?.setRoundRect(rect, mRadius)
                outline?.setOval(rect)

            }
        }
        clipToOutline = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        mRadius = min(measuredWidth, measuredHeight).toFloat() / 2
    }

    fun setRadius(radius: Float) {
        this.mRadius = radius

    }

    fun turnRound() {
        invalidateOutline()
    }

}