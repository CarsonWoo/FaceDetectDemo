package com.example.facedetectdemo.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.facedetectdemo.R
import com.example.facedetectdemo.util.ScreenUtils

class CircleTextureBorderView : View {

    private val mPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val mAnimatePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var mTextureViewWidth: Int = measuredWidth /*- ScreenUtils.dip2px(context, 73F)*/

    private var mColor = Color.CYAN

    private val mTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var mTipsText: String = "请放入人脸"

    private var mTextHeight = 0F

    constructor(context: Context): this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?): super(context, attributeSet) {
        mPaint.strokeWidth = ScreenUtils.dip2px(context, 3F).toFloat()
        mPaint.style = Paint.Style.STROKE
        mAnimatePaint.style = Paint.Style.FILL
        mAnimatePaint.color = Color.parseColor("#7F000000")
        mTextPaint.color = Color.WHITE
        mTextPaint.style = Paint.Style.FILL
        mTextPaint.textSize = ScreenUtils.dip2px(context, 12F).toFloat()
        mTextPaint.strokeWidth = 1F
        mTextHeight = mTextPaint.fontMetrics.descent - mTextPaint.fontMetrics.ascent
        attributeSet?.apply {
            val a = context.obtainStyledAttributes(this, R.styleable.CircleTextureBorderView)
//            mTextureViewWidth = a.getDimensionPixelSize(R.styleable.CircleTextureBorderView_circleTextureWidth,
//                measuredWidth /*- ScreenUtils.dip2px(context, 73F)*/)
            mColor = a.getColor(
                R.styleable.CircleTextureBorderView_circleTextureBorderColor,
                Color.CYAN)
            a.recycle()
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        mPaint.color = mColor
        val radius = mTextureViewWidth.toFloat() / 2
        canvas?.drawCircle(measuredWidth.toFloat() / 2, measuredWidth.toFloat() / 2, radius, mPaint)
        // 外边框比内部大10-12dp 边框的厚度为1
        mPaint.strokeWidth = 1F
        canvas?.drawCircle(measuredWidth.toFloat() / 2, measuredWidth.toFloat() / 2,
            radius + ScreenUtils.dip2px_20(context), mPaint)
        mPaint.strokeWidth = ScreenUtils.dip2px(context, 3F).toFloat()

        val left = (measuredWidth - mTextureViewWidth.toFloat()) / 2
        val right = (measuredWidth + mTextureViewWidth.toFloat()) / 2
        val top = (measuredHeight - mTextureViewWidth.toFloat()) / 2
        val bottom = (measuredHeight + mTextureViewWidth.toFloat()) / 2

        // x (measuredWidth.toFloat() - mTextPaint.measureText(mTipsText)) / 2
        // y (measuredHeight.toFloat() + mTextureViewWidth / 2) +
        //                    (mTextPaint.fontMetrics.descent - mTextPaint.fontMetrics.ascent) / 2
        canvas?.drawArc(left, top, right, bottom, 150F, -120F, false, mAnimatePaint)
        canvas?.drawText(mTipsText, (measuredWidth.toFloat() - mTextPaint.measureText(mTipsText)) / 2,
            (measuredHeight.toFloat() + mTextureViewWidth / 2) / 2 + mTextHeight * 1.5F, mTextPaint)
    }

    fun setTipsText(str: String) {
        this.mTipsText = str
        postInvalidate()
    }

    fun setCircleTextureWidth(width: Int) {
        this.mTextureViewWidth = width
        postInvalidate()
    }

}