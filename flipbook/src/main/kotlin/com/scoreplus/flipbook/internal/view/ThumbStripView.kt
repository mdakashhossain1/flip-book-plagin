package com.scoreplus.flipbook.internal.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import com.scoreplus.flipbook.internal.rgba
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal interface ThumbStripHost {
    fun stripThumbnail(page: Int, widthPx: Int, done: (Bitmap?) -> Unit)
    fun onStripPage(page: Int)
    fun onStripTouch()
}

/** `.thumb-strip`: scrollable page thumbnails above the slider; tapping one opens that page. */
@SuppressLint("ViewConstructor")
internal class ThumbStripView(context: Context, private val host: ThumbStripHost) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.SANS_SERIF
        textAlign = Paint.Align.CENTER
    }
    private val strip = RectF()
    private val rect = RectF()
    private val scroller = OverScroller(context)
    private val pending = HashSet<Int>()
    private val cache = object : LruCache<Int, Bitmap>(40) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (oldValue !== newValue) oldValue.recycle()
        }
    }
    private var scroll = 0f
    private var generation = 0
    private var dragging = false

    var numPages = 0
    var aspect = 1.414f
    var viewport = RectF()
    var active = IntArray(0)
        private set
    var shown = true

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            dragging = true
            scroll = (scroll + dx).coerceIn(0f, maxScroll())
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            scroller.fling(scroll.roundToInt(), 0, -vx.roundToInt(), 0, 0, maxScroll().roundToInt(), 0, 0)
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            pageAt(e.x)?.let { host.onStripPage(it) }
            return true
        }
    })

    private fun thumbH() = 60f * density
    private fun thumbW() = thumbH() / aspect
    private fun gap() = 6f * density
    private fun contentW() = if (numPages == 0) 0f else numPages * thumbW() + (numPages - 1) * gap() + 4f * density
    private fun maxScroll() = max(0f, contentW() - strip.width())
    private fun offset() = max(0f, (strip.width() - contentW()) / 2) + 2f * density
    private fun thumbLeft(page: Int) = strip.left + offset() + (page - 1) * (thumbW() + gap()) - scroll

    private fun layoutStrip() {
        val left = viewport.left + viewport.width() * 0.05f
        val top = viewport.bottom + 3f * density
        strip.set(left, top + 8f * density, left + viewport.width() * 0.9f, top + 8f * density + thumbH())
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    private fun pageAt(x: Float): Int? {
        val i = ((x - strip.left - offset() + scroll) / (thumbW() + gap())).toInt() + 1
        if (i < 1 || i > numPages) return null
        return if (x - thumbLeft(i) <= thumbW()) i else null
    }

    fun containsPoint(x: Float, y: Float): Boolean {
        if (!shown || numPages == 0) return false
        layoutStrip()
        return y >= strip.top - 8f * density && y <= strip.bottom + 8f * density && x >= strip.left && x <= strip.right
    }

    fun setActive(pages: IntArray) {
        val changed = !pages.contentEquals(active)
        active = pages
        if (changed && !dragging && pages.isNotEmpty()) {
            layoutStrip()
            val center = thumbLeft(pages[0]) + scroll + (pages.size * thumbW() + (pages.size - 1) * gap()) / 2
            val target = (center - strip.left - strip.width() / 2).coerceIn(0f, maxScroll())
            scroller.forceFinished(true)
            scroller.startScroll(scroll.roundToInt(), 0, (target - scroll).roundToInt(), 0, 300)
        }
        postInvalidateOnAnimation()
    }

    fun clear() {
        generation++
        cache.evictAll()
        pending.clear()
        numPages = 0
        scroll = 0f
        active = IntArray(0)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (!containsPoint(event.x, event.y)) return false
            host.onStripTouch()
        }
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) dragging = false
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scroll = scroller.currX.toFloat().coerceIn(0f, maxScroll())
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!shown || numPages == 0) return
        layoutStrip()
        val d = density
        val tw = thumbW()
        val th = thumbH()
        val first = max(1, ((scroll - offset()) / (tw + gap())).toInt())
        val last = min(numPages, first + (strip.width() / (tw + gap())).toInt() + 2)
        canvas.save()
        canvas.clipRect(strip.left - 3f * d, strip.top - 3f * d, strip.right + 3f * d, strip.bottom + 3f * d)
        labelPaint.textSize = 10f * d
        for (page in first..last) {
            val x = thumbLeft(page)
            rect.set(x, strip.top, x + tw, strip.top + th)
            val isActive = page in active
            val alpha = if (isActive) 255 else 191
            paint.color = Color.WHITE
            paint.alpha = alpha
            canvas.drawRect(rect, paint)
            val bmp = cache.get(page)
            if (bmp != null && !bmp.isRecycled) {
                paint.color = Color.BLACK
                paint.alpha = alpha
                canvas.drawBitmap(bmp, null, rect, paint)
            } else request(page, tw.roundToInt())
            paint.color = rgba(0, 0, 0, 0.4 * alpha / 255)
            canvas.drawRect(rect.left, rect.bottom - 14f * d, rect.right, rect.bottom, paint)
            labelPaint.alpha = alpha
            canvas.drawText(page.toString(), rect.centerX(), rect.bottom - 4f * d, labelPaint)
            border.strokeWidth = if (isActive) 2f * d else 1f * d
            border.color = if (isActive) rgba(0, 0, 0, 0.5) else rgba(0, 0, 0, 0.15)
            canvas.drawRect(rect, border)
        }
        canvas.restore()
    }

    private fun request(page: Int, widthPx: Int) {
        if (!pending.add(page)) return
        val gen = generation
        host.stripThumbnail(page, max(1, widthPx)) { bmp ->
            if (gen != generation) return@stripThumbnail run { bmp?.recycle() }
            pending.remove(page)
            if (bmp == null) return@stripThumbnail
            cache.put(page, bmp)
            invalidate()
        }
    }
}
