package com.scoreplus.flipbook.internal.draw

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import com.scoreplus.flipbook.internal.cssBlurToAndroidRadius
import kotlin.math.ceil

/**
 * Draws a CSS `box-shadow: 0 0 <blur> <color>` around a rectangle using a pre-blurred
 * nine-slice bitmap, so it works on every hardware-accelerated canvas.
 */
internal class ShadowPainter(cssBlurPx: Float) {
    private val radius = cssBlurToAndroidRadius(cssBlurPx)
    private val pad = ceil(radius * 3f).toInt().coerceAtLeast(2)
    private val core = 2
    private val bitmap: Bitmap
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()

    init {
        val size = pad * 2 + core
        bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRect(pad.toFloat(), pad.toFloat(), (pad + core).toFloat(), (pad + core).toFloat(), p)
    }

    fun draw(canvas: Canvas, rect: RectF, color: Int) {
        if (Color.alpha(color) == 0 || rect.width() <= 0f || rect.height() <= 0f) return
        paint.colorFilter = PorterDuffColorFilter(color or (0xFF shl 24), PorterDuff.Mode.SRC_IN)
        paint.alpha = Color.alpha(color)
        val p = pad.toFloat()
        val l = rect.left
        val t = rect.top
        val r = rect.right
        val b = rect.bottom
        val xs = floatArrayOf(l - p, l, r, r + p)
        val ys = floatArrayOf(t - p, t, b, b + p)
        val sx = intArrayOf(0, pad, pad + core, pad * 2 + core)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                if (row == 1 && col == 1) continue
                src.set(sx[col], sx[row], sx[col + 1], sx[row + 1])
                dst.set(xs[col], ys[row], xs[col + 1], ys[row + 1])
                canvas.drawBitmap(bitmap, src, dst, paint)
            }
        }
    }
}
