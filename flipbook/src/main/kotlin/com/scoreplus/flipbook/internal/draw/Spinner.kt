package com.scoreplus.flipbook.internal.draw

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.scoreplus.flipbook.internal.rgba

/** `.loader-1-circle`: twelve dots bouncing with `loader-1-circleBounceDelay 1.2s ease-in-out`. */
internal class Spinner(private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgba(0x3e, 0x54, 0x5e, 0.7) }
    private val oval = RectF()

    fun draw(canvas: Canvas, cx: Float, cy: Float, timeMs: Long) {
        val size = 50f * density
        val dotW = size * 0.08f
        val dotH = size * 0.15f
        val left = cx - size / 2
        val top = cy - size / 2
        for (i in 0 until 12) {
            val delay = if (i == 0) 0.0 else -(1.2 - (i + 1 - 1) * 0.1)
            val t = (((timeMs / 1000.0 - delay) % 1.2) + 1.2) % 1.2 / 1.2
            val scale = bounce(t)
            if (scale <= 0f) continue
            canvas.save()
            canvas.rotate(i * 30f, cx, cy)
            val dcx = left + size / 2
            val dcy = top + dotH / 2
            oval.set(dcx - dotW / 2 * scale, dcy - dotH / 2 * scale, dcx + dotW / 2 * scale, dcy + dotH / 2 * scale)
            canvas.drawOval(oval, paint)
            canvas.restore()
        }
    }

    /** keyframes 0%,80%,100% → scale(0); 40% → scale(1), each segment eased with ease-in-out. */
    private fun bounce(t: Double): Float {
        val p = when {
            t < 0.4 -> easeInOut(t / 0.4)
            t < 0.8 -> 1 - easeInOut((t - 0.4) / 0.4)
            else -> 0.0
        }
        return p.toFloat()
    }

    private fun easeInOut(x: Double): Double {
        val f = x.toFloat()
        return cubicEaseInOut(f).toDouble()
    }

    private fun cubicEaseInOut(x: Float): Float {
        var lo = 0f
        var hi = 1f
        var t = x
        repeat(24) {
            val u = 1 - t
            val bx = 3 * u * u * t * 0.42f + 3 * u * t * t * 0.58f + t * t * t
            if (bx < x) lo = t else hi = t
            t = (lo + hi) / 2
        }
        val u = 1 - t
        return 3 * u * u * t * 0f + 3 * u * t * t * 1f + t * t * t
    }
}
