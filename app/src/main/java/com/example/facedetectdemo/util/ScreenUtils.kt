package com.example.facedetectdemo.util

import android.content.Context
import kotlin.math.roundToInt

object ScreenUtils {

    fun dip2px_2(context: Context) = dip2px(context, 2F)
    fun dip2px_5(context: Context) = dip2px(context, 5F)
    fun dip2px_10(context: Context) = dip2px(context, 10F)
    fun dip2px_20(context: Context) = dip2px(context, 20F)

    fun dip2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5F).roundToInt()
    }

    fun px2dip(context: Context, pxValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5F).roundToInt()
    }

    fun getScreenWidth(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    fun getScreenHeight(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }
}