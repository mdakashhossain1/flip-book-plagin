package com.scoreplus.flipbook.internal.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.scoreplus.flipbook.internal.Easing
import com.scoreplus.flipbook.internal.rgba
import kotlin.math.floor
import kotlin.math.roundToInt

internal interface SliderHost {
    fun isSinglePage(): Boolean
    fun loadThumbnails(first: Int, second: Int?, done: (Bitmap?, Bitmap?) -> Unit)
    fun onSliderRelease(page: Int)
    fun onSliderTouch()
}

/** `.control-bottom .page-bar`: the page range slider with preview and page number. */
@SuppressLint("ViewConstructor")
internal class PageSliderView(context: Context, private val host: SliderHost) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.SANS_SERIF
    }
    private val path = Path()
    private val track = RectF()
    private val bar = RectF()
    private val tmp = RectF()

    var numPages = 0
    var showSlider = 3
    var rtl = false
    var viewport = RectF()
    var min = 1
    var max = 1
    var value = 1
        private set
    var barVisible = true
    private var barAlpha = 1f
    private var barFadeFrom = 1f
    private var barFadeTo = 1f
    private var barFadeStart = 0L
    private var barFading = false

    private var drag = false
    private var previewShown = false
    private var previewAlpha = 0f
    private var previewFadeFrom = 0f
    private var previewFadeTo = 0f
    private var previewFadeStart = 0L
    private var numShown = false
    private var numAlpha = 1f
    private var numFadeFrom = 1f
    private var numFadeTo = 1f
    private var numFadeStart = 0L
    private var numFading = false
    private var thumbA: Bitmap? = null
    private var thumbB: Bitmap? = null
    private var thumbToken = 0
    private val hideRunnable = Runnable {
        if (previewEnabled()) fadePreview(false)
        if (numEnabled()) fadeNum(false)
    }
    private var pendingThumb: Runnable? = null

    private fun previewEnabled() = showSlider == 1 || showSlider == 3

    private fun numEnabled() = showSlider == 2 || showSlider == 3

    fun configure() {
        max = if (host.isSinglePage()) numPages
        else if (numPages % 2 == 0) floor((numPages - 2) / 2.0).toInt() + 2
        else floor(numPages / 2.0).toInt() + 1
        if (max < min) max = min
        value = value.coerceIn(min, max)
        invalidate()
    }

    fun setPage(e: Int) {
        val single = host.isSinglePage()
        value = (if (single) e else if (e == 1) 1 else floor(e / 2.0).toInt() + 1).coerceIn(min, max)
        invalidate()
    }

    fun fadeBar(show: Boolean) {
        if (show) barVisible = true
        barFadeFrom = barAlpha
        barFadeTo = if (show) 1f else 0f
        barFadeStart = SystemClock.uptimeMillis()
        barFading = true
        invalidate()
    }

    fun hideBarNow() {
        barVisible = false
        barAlpha = 0f
        barFading = false
        invalidate()
    }

    private fun layoutBar() {
        val d = density
        val cbLeft = viewport.left + viewport.width() * 0.05f
        val cbWidth = viewport.width() * 0.9f
        val barW = kotlin.math.min(cbWidth, 700f * d)
        val left = cbLeft + (cbWidth - barW) / 2
        val bottom = viewport.bottom + 49f * d
        val top = bottom - 38f * d
        bar.set(left, top, left + barW, bottom)
        track.set(left, top + 15f * d, left + barW, top + 23f * d)
    }

    private fun thumbRect(out: RectF) {
        val d = density
        val tw = 87f * d
        val th = 10f * d
        val ratio = if (max > min) (value - min).toFloat() / (max - min) else 0f
        val travel = track.width() - tw
        val x = if (rtl) track.right - tw - ratio * travel else track.left + ratio * travel
        out.set(x, track.top, x + tw, track.top + th)
    }

    /** spflip.controls.slider.positionSliderThumb. */
    private fun labelLeft(labelWidth: Float): Float {
        val i = labelWidth / 2
        val n = track.width()
        val o = n / 2
        var s = if (max > min) (value - 1).toFloat() / (max - min) else 0f
        if (s.isNaN() || s < 0) s = 0f
        val a = s * n
        val l = a - i - 42.5f * density * ((a - o) / o)
        return if (rtl) bar.right - l - labelWidth else bar.left + l
    }

    fun rangeToPage(e: Int): IntArray {
        val i = numPages
        val t = if (i % 2 == 0) (i - 2) / 2 + 2 else (i - 1) / 2 + 1
        return if (host.isSinglePage() || e == 1) intArrayOf(e)
        else if (e == t) {
            if (i % 2 != 0) intArrayOf(2 * (e - 1), 2 * (e - 1) + 1) else intArrayOf(2 * (e - 1))
        } else intArrayOf(2 * (e - 1), 2 * (e - 1) + 1)
    }

    private fun fadePreview(show: Boolean) {
        previewFadeFrom = previewAlpha
        previewFadeTo = if (show) 1f else 0f
        previewFadeStart = SystemClock.uptimeMillis()
        previewShown = true
        invalidate()
    }

    private fun fadeNum(show: Boolean) {
        numFadeFrom = numAlpha
        numFadeTo = if (show) 1f else 0f
        numFadeStart = SystemClock.uptimeMillis()
        numFading = true
        invalidate()
    }

    private fun onInput() {
        drag = true
        host.onSliderTouch()
        if (previewEnabled()) {
            previewShown = false
            previewAlpha = 0f
            val token = ++thumbToken
            pendingThumb?.let { removeCallbacks(it) }
            val r = Runnable {
                val pages = rangeToPage(value)
                host.loadThumbnails(pages[0], pages.getOrNull(1)) { a, b ->
                    if (token != thumbToken) return@loadThumbnails
                    thumbA = a
                    thumbB = b
                    if (drag) fadePreview(true)
                }
            }
            pendingThumb = r
            postDelayed(r, 100)
        }
        if (numEnabled()) {
            numShown = true
            numFading = false
            numAlpha = 1f
        }
        invalidate()
    }

    private fun valueAt(x: Float): Int {
        val tw = 87f * density
        val travel = track.width() - tw
        var ratio = if (travel > 0) (x - track.left - tw / 2) / travel else 0f
        if (rtl) ratio = 1 - ratio
        return (min + (ratio.coerceIn(0f, 1f) * (max - min))).roundToInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (showSlider == 0 || numPages == 0 || !barVisible || barAlpha <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                layoutBar()
                if (!bar.contains(event.x, event.y)) return false
                removeCallbacks(hideRunnable)
                value = valueAt(event.x)
                onInput()
            }
            MotionEvent.ACTION_MOVE -> {
                val v = valueAt(event.x)
                if (v != value) {
                    value = v
                    onInput()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drag = false
                postDelayed(hideRunnable, 500)
                val page = if (host.isSinglePage()) value else if (value == 1) 1 else 2 * value - 2
                host.onSliderRelease(page)
            }
        }
        return true
    }

    fun containsPoint(x: Float, y: Float): Boolean {
        if (showSlider == 0 || !barVisible) return false
        layoutBar()
        return bar.contains(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        if (showSlider == 0 || numPages == 0) return
        val now = SystemClock.uptimeMillis()
        var animating = false
        if (barFading) {
            val p = ((now - barFadeStart) / 200f).coerceIn(0f, 1f)
            barAlpha = barFadeFrom + (barFadeTo - barFadeFrom) * Easing.swing(p)
            if (p >= 1f) {
                barFading = false
                if (barFadeTo == 0f) barVisible = false
            } else animating = true
        }
        if (previewShown && previewAlpha != previewFadeTo) {
            val p = ((now - previewFadeStart) / 200f).coerceIn(0f, 1f)
            previewAlpha = previewFadeFrom + (previewFadeTo - previewFadeFrom) * Easing.swing(p)
            if (p < 1f) animating = true else if (previewFadeTo == 0f) previewShown = false
        }
        if (numFading) {
            val p = ((now - numFadeStart) / 200f).coerceIn(0f, 1f)
            numAlpha = numFadeFrom + (numFadeTo - numFadeFrom) * Easing.swing(p)
            if (p >= 1f) {
                numFading = false
                if (numFadeTo == 0f) numShown = false
            } else animating = true
        }
        if (!barVisible) {
            if (animating) postInvalidateOnAnimation()
            return
        }
        layoutBar()
        val d = density
        paint.shader = null
        paint.color = rgba(0, 0, 0, 0.2 * barAlpha)
        canvas.drawRoundRect(track, 25f * d, 25f * d, paint)
        thumbRect(tmp)
        paint.color = rgba(0, 0, 0, 0.5 * barAlpha)
        canvas.drawRoundRect(tmp, 50f * d, 50f * d, paint)

        if (previewShown && previewAlpha > 0f) drawPreview(canvas)
        if (numShown && numAlpha > 0f) drawNum(canvas)
        if (animating) postInvalidateOnAnimation()
    }

    private fun drawPreview(canvas: Canvas) {
        val d = density
        val a = thumbA ?: return
        val imgH = 130f * d
        val wa = a.width * imgH / a.height
        val b = thumbB
        val wb = if (b != null) b.width * imgH / b.height else 0f
        val boxW = wa + wb + 20f * d
        val top = bar.top - (if (numEnabled()) 195f else 168f) * d
        val left = labelLeft(boxW)
        tmp.set(left, top, left + boxW, top + 150f * d)
        paint.color = rgba(0, 0, 0, 0.2 * previewAlpha)
        canvas.drawRoundRect(tmp, 4f * d, 4f * d, paint)
        path.reset()
        val cx = tmp.centerX()
        path.moveTo(cx - 10f * d, tmp.bottom)
        path.lineTo(cx + 10f * d, tmp.bottom)
        path.lineTo(cx, tmp.bottom + 10f * d)
        path.close()
        canvas.drawPath(path, paint)
        paint.color = Color.BLACK
        paint.alpha = (255 * previewAlpha).roundToInt()
        val x0 = left + 10f * d
        val y0 = top + 10f * d
        canvas.drawBitmap(a, null, RectF(x0, y0, x0 + wa, y0 + imgH), paint)
        if (b != null) canvas.drawBitmap(b, null, RectF(x0 + wa, y0, x0 + wa + wb, y0 + imgH), paint)
    }

    private fun drawNum(canvas: Canvas) {
        val d = density
        val pages = rangeToPage(value)
        val text = if (pages.size == 1) pages[0].toString() else "${pages[0]} - ${pages[1]}"
        textPaint.textSize = 15f * d
        textPaint.alpha = (255 * numAlpha).roundToInt()
        val tw = textPaint.measureText(text)
        val w = tw + 28f * d
        val h = 24f * d
        val bottom = bar.bottom - 24f * d
        val left = labelLeft(w)
        tmp.set(left, bottom - h, left + w, bottom)
        paint.color = rgba(0, 0, 0, 0.4 * numAlpha)
        canvas.drawRoundRect(tmp, 2f * d, 2f * d, paint)
        val fm = textPaint.fontMetrics
        canvas.drawText(text, left + 14f * d, tmp.centerY() - (fm.ascent + fm.descent) / 2, textPaint)
    }
}
