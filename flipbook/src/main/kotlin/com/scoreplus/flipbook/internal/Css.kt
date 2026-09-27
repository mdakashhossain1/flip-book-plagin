package com.scoreplus.flipbook.internal

import android.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

internal fun jsRound(v: Double): Double = floor(v + 0.5)

internal fun jsRound(v: Float): Float = floor(v + 0.5f)

internal fun rgba(r: Int, g: Int, b: Int, a: Double): Int =
    Color.argb((a.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt(), r, g, b)

internal object Easing {
    /** jQuery "swing", used by fadeIn/fadeOut. */
    fun swing(p: Float): Float = (0.5 - cos(p * PI) / 2).toFloat()

    /** turn.js animatef easing (easeOutCirc). */
    fun turnJs(elapsed: Double, from: Double, delta: Double, duration: Double): Double {
        val t = elapsed / duration - 1
        return delta * sqrt(1 - t * t) + from
    }

    /** CSS "ease" = cubic-bezier(0.25, 0.1, 0.25, 1). */
    fun cssEase(p: Float): Float = cubicBezier(p, 0.25f, 0.1f, 0.25f, 1f)

    private fun cubicBezier(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var t = x
        repeat(8) {
            val cx = bez(t, x1, x2) - x
            val d = bezDerivative(t, x1, x2)
            if (kotlin.math.abs(cx) < 1e-5f || d == 0f) return bez(t, y1, y2)
            t -= cx / d
        }
        var lo = 0f
        var hi = 1f
        t = x
        repeat(20) {
            val cx = bez(t, x1, x2)
            if (cx < x) lo = t else hi = t
            t = (lo + hi) / 2
        }
        return bez(t, y1, y2)
    }

    private fun bez(t: Float, p1: Float, p2: Float): Float {
        val u = 1 - t
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t
    }

    private fun bezDerivative(t: Float, p1: Float, p2: Float): Float {
        val u = 1 - t
        return 3 * u * u * p1 + 6 * u * t * (p2 - p1) + 3 * t * t * (1 - p2)
    }
}

/** Converts a CSS box-shadow blur radius (in px) to an Android shadow/blur radius. */
internal fun cssBlurToAndroidRadius(cssBlurPx: Float): Float {
    val sigma = cssBlurPx / 2f
    return ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
}
