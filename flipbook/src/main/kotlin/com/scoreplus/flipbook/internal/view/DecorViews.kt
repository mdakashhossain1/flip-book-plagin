package com.scoreplus.flipbook.internal.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.scoreplus.flipbook.internal.rgba
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** `body` background colour + `.logo-backs` image (cover, center left, 40% opacity, height + 50px). */
internal class BackgroundView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    var drawable: Drawable? = null
    var color: Int = Color.WHITE
    var opacity = 0.4f
    var size = "cover"

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(color)
        val d = drawable ?: return
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        if (iw <= 0 || ih <= 0) return
        val w = width.toFloat()
        val h = height + 50f * density
        val s = when (size) {
            "contain" -> min(w / iw, h / ih)
            "auto" -> 1f
            else -> max(w / iw, h / ih)
        }
        val dw = iw * s
        val dh = ih * s
        val top = (h - dh) / 2
        d.setBounds(0, top.roundToInt(), dw.roundToInt(), (top + dh).roundToInt())
        d.alpha = (opacity * 255).roundToInt()
        d.draw(canvas)
    }
}

/** `#loaderLine`: 6px progress bar in --primary-color shown when rendering takes over 2 s. */
internal class LoaderLineView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint().apply { color = rgba(0x3e, 0x54, 0x5e, 1.0) }
    var lineWidth = 0f
        set(value) {
            field = value
            invalidate()
        }
    var shown = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (shown) canvas.drawRect(0f, 0f, lineWidth, 6f * density, paint)
    }
}

/** `.flipbook-title`: title / subtitle / description card. */
internal class TitleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgba(255, 255, 255, 0.9) }
    private val rect = RectF()
    var title = ""
    var subtitle = ""
    var description = ""
    var windowWidthDp = 0f
    var topDp = 20f

    fun hasText(): Boolean = title.isNotBlank() || subtitle.isNotBlank() || description.isNotBlank()

    private fun layout(text: String, sizeRem: Float, color: Int, width: Int, justify: Boolean): StaticLayout {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizeRem * 17f * density
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        @Suppress("DEPRECATION")
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, p, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, if (justify) 1.6f else 1.1f)
        if (justify && android.os.Build.VERSION.SDK_INT >= 26) {
            builder.setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
        return builder.build()
    }

    override fun onDraw(canvas: Canvas) {
        if (!hasText()) return
        val d = density
        val maxW = when {
            windowWidthDp <= 609f -> windowWidthDp - 11f
            windowWidthDp <= 760f -> windowWidthDp / 2 - 20f
            else -> 380f
        }
        val w = min(windowWidthDp - 11f - 10f, maxW) * d
        val inner = (w - 30f * d).toInt()
        val small = windowWidthDp <= 790f
        val tiny = windowWidthDp <= 575f
        val parts = ArrayList<Pair<StaticLayout, Float>>()
        if (title.isNotBlank()) parts.add(layout(title, if (small) 1.1f else 1.6f, rgba(0x22, 0x26, 0x2e, 1.0), inner, false) to 10f)
        if (subtitle.isNotBlank()) parts.add(layout(subtitle, if (small) 1f else 1.17f, Color.GRAY, inner, false) to 20f)
        if (description.isNotBlank()) parts.add(layout(description, if (tiny) 0.6f else if (small) 0.7f else 0.82f, Color.GRAY, inner, true) to 10f)
        var h = 30f * d
        for ((l, m) in parts) h += l.height + 2 * m * d
        val left = 11f * d
        val top = topDp * d
        rect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(rect, 5f * d, 5f * d, bg)
        var y = top + 15f * d
        for ((l, m) in parts) {
            y += m * d
            canvas.save()
            canvas.translate(left + 15f * d, y)
            l.draw(canvas)
            canvas.restore()
            y += l.height + m * d
        }
    }
}
