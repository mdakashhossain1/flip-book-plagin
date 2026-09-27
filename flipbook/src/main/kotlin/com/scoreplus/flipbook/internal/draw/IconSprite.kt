package com.scoreplus.flipbook.internal.draw

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.scoreplus.flipbook.R

/** `iconset2_6.png`: 23 icons of 45×45 px, shown as `background-size` scaled cells. */
internal class IconSprite(resources: Resources) {
    private val bitmap: Bitmap = BitmapFactory.decodeResource(
        resources,
        R.drawable.flipbook_iconset,
        BitmapFactory.Options().apply { inScaled = false },
    )
    private val cell = bitmap.height
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()

    fun draw(canvas: Canvas, index: Int, dst: RectF, mirror: Boolean = false, alpha: Int = 255) {
        src.set(index * cell, 0, index * cell + cell, cell)
        paint.alpha = alpha
        if (mirror) {
            canvas.save()
            canvas.scale(-1f, 1f, dst.centerX(), dst.centerY())
            canvas.drawBitmap(bitmap, src, dst, paint)
            canvas.restore()
        } else canvas.drawBitmap(bitmap, src, dst, paint)
    }

    companion object {
        const val FULLSCREEN_ON = 1
        const val FULLSCREEN_OFF = 2
        const val ZOOM_IN = 3
        const val ZOOM_OUT = 4
        const val CORNER_ARROW = 6
        const val ZOOM_MORE = 8
        const val ZOOM_LESS = 9
        const val SOUND_ON = 10
        const val SOUND_OFF = 11
    }
}
