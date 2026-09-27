package com.scoreplus.flipbook.internal.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.scoreplus.flipbook.internal.Easing
import com.scoreplus.flipbook.internal.draw.IconSprite
import com.scoreplus.flipbook.internal.rgba
import kotlin.math.roundToInt

/** `.controls-pdf` panel background: rgba(255,255,255,.85), radius 5px. */
internal abstract class PanelView(context: Context) : View(context) {
    protected val density = resources.displayMetrics.density
    protected val sprite = IconSprite(resources)
    protected val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgba(255, 255, 255, 0.85) }
    protected val panel = RectF()
    val panelBounds: RectF get() = panel
    protected var alphaValue = 1f
    private var fadeFrom = 1f
    private var fadeTo = 1f
    private var fadeStart = 0L
    private var fading = false
    var shown = true
        private set
    protected var pressed = -1

    fun fade(show: Boolean) {
        if (show) shown = true
        fadeFrom = alphaValue
        fadeTo = if (show) 1f else 0f
        fadeStart = SystemClock.uptimeMillis()
        fading = true
        invalidate()
    }

    fun setShownImmediately(show: Boolean) {
        shown = show
        alphaValue = if (show) 1f else 0f
        fading = false
        invalidate()
    }

    protected fun stepFade(): Boolean {
        if (!fading) return false
        val p = ((SystemClock.uptimeMillis() - fadeStart) / 200f).coerceIn(0f, 1f)
        alphaValue = fadeFrom + (fadeTo - fadeFrom) * Easing.swing(p)
        if (p >= 1f) {
            fading = false
            if (fadeTo == 0f) shown = false
        }
        return fading
    }

    protected fun drawPanel(canvas: Canvas) {
        panelPaint.alpha = (0.85f * 255 * alphaValue).roundToInt()
        canvas.drawRoundRect(panel, 5f * density, 5f * density, panelPaint)
    }

    protected abstract fun iconRects(): List<Pair<Int, RectF>>

    protected abstract fun onIcon(id: Int)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!shown || alphaValue <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!panel.contains(event.x, event.y)) return false
                pressed = iconRects().firstOrNull { it.second.contains(event.x, event.y) }?.first ?: -1
                return true
            }
            MotionEvent.ACTION_UP -> {
                val hit = iconRects().firstOrNull { it.second.contains(event.x, event.y) }?.first
                if (hit != null && hit == pressed) onIcon(hit)
                pressed = -1
            }
            MotionEvent.ACTION_CANCEL -> pressed = -1
        }
        return true
    }

    fun containsPoint(x: Float, y: Float): Boolean = shown && alphaValue > 0f && panel.contains(x, y)
}

/** `#pnlControls`: zoom, fullscreen and sound buttons at the top right. */
@SuppressLint("ViewConstructor")
internal class ToolbarView(context: Context, private val onClick: (Int) -> Unit) : PanelView(context) {
    var showZoom = true
    var showFullscreen = true
    var showSound = true
    var zoomedIn = false
    var fullscreenOn = false
    var soundOn = true

    /** 24, 32 or 42 (controls-sm / md / lg). */
    var iconSize = 32f
    var iconMargin = 6f
    var right = 50f
    var top = 20f

    private val rects = ArrayList<Pair<Int, RectF>>()

    fun hasIcons(): Boolean = showZoom || showFullscreen || showSound

    fun layoutPanel() {
        rects.clear()
        val d = density
        val icon = iconSize * d
        val margin = iconMargin * d
        val contentH = if (showZoom) icon + 2f * d else icon
        val items = ArrayList<Int>()
        if (showZoom) items.add(ID_ZOOM)
        if (showFullscreen) items.add(ID_FULLSCREEN)
        if (showSound) items.add(ID_SOUND)
        val contentW = items.size * (icon + 2 * margin)
        val w = contentW + 20f * d
        val h = contentH + 16f * d
        val r = width - right * d
        panel.set(r - w, top * d, r, top * d + h)
        var x = panel.left + 10f * d + margin
        val boxTop = panel.top + 8f * d
        for (id in items) {
            val y = if (id == ID_ZOOM) boxTop else boxTop + (contentH - icon) / 2
            rects.add(id to RectF(x, y, x + icon, y + icon))
            x += icon + 2 * margin
        }
        invalidate()
    }

    override fun iconRects(): List<Pair<Int, RectF>> = rects

    override fun onIcon(id: Int) = onClick(id)

    override fun onDraw(canvas: Canvas) {
        val animating = stepFade()
        if (shown && hasIcons()) {
            drawPanel(canvas)
            val a = (255 * alphaValue).roundToInt()
            for ((id, r) in rects) {
                val index = when (id) {
                    ID_ZOOM -> if (zoomedIn) IconSprite.ZOOM_OUT else IconSprite.ZOOM_IN
                    ID_FULLSCREEN -> if (fullscreenOn) IconSprite.FULLSCREEN_OFF else IconSprite.FULLSCREEN_ON
                    else -> if (soundOn) IconSprite.SOUND_ON else IconSprite.SOUND_OFF
                }
                sprite.draw(canvas, index, r, alpha = a)
            }
        }
        if (animating) postInvalidateOnAnimation()
    }

    companion object {
        const val ID_ZOOM = 1
        const val ID_FULLSCREEN = 2
        const val ID_SOUND = 3
    }
}

/** `#pnlZoomStep`: zoom more / zoom less, shown while zoomed in. */
@SuppressLint("ViewConstructor")
internal class ZoomStepView(context: Context, private val onClick: (Int) -> Unit) : PanelView(context) {
    var iconSize = 32f
    var right = 50f
    private val rects = ArrayList<Pair<Int, RectF>>()

    init {
        setShownImmediately(false)
    }

    fun layoutPanel() {
        rects.clear()
        val d = density
        val icon = iconSize * d
        val gap = 13f * d
        val w = icon + 16f * d
        val h = gap * 3 + icon * 2
        val r = width - right * d
        val top = 100f * d
        panel.set(r - w, top, r, top + h)
        val x = panel.left + 8f * d
        rects.add(ID_MORE to RectF(x, top + gap, x + icon, top + gap + icon))
        rects.add(ID_LESS to RectF(x, top + gap * 2 + icon, x + icon, top + gap * 2 + icon * 2))
        invalidate()
    }

    override fun iconRects(): List<Pair<Int, RectF>> = rects

    override fun onIcon(id: Int) = onClick(id)

    override fun onDraw(canvas: Canvas) {
        val animating = stepFade()
        if (shown) {
            drawPanel(canvas)
            val a = (255 * alphaValue).roundToInt()
            for ((id, r) in rects) {
                sprite.draw(canvas, if (id == ID_MORE) IconSprite.ZOOM_MORE else IconSprite.ZOOM_LESS, r, alpha = a)
            }
        }
        if (animating) postInvalidateOnAnimation()
    }

    companion object {
        const val ID_MORE = 1
        const val ID_LESS = 2
    }
}
