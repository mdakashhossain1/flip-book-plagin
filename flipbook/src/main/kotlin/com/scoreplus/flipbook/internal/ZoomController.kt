package com.scoreplus.flipbook.internal

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import com.scoreplus.flipbook.internal.view.MagazineView

internal interface ZoomHost {
    val canvasWidth: Float
    val canvasHeight: Float
    fun visiblePages(): IntArray
    fun setZoomInControls()
    fun setZoomOutControls()
    fun setViewerZoomed(zoomed: Boolean)
    fun renderPages(viewSize: String)
}

/** Port of spflip.controls.zoom (state machine, zoomSet maths, panning bounds). */
internal class ZoomController(private val view: MagazineView, private val host: ZoomHost) {
    private val handler = Handler(Looper.getMainLooper())
    var isZoomDisabled = false
    var isZoomIn = false
        private set
    var pinching = false
        private set

    private var inc = 1.15
    private var scale = 1.0
    private var xLast = 0.0
    private var yLast = 0.0
    private var xImage = 0.0
    private var yImage = 0.0
    private var pinchScale = 0.0

    private var posScale = 1.0
    private var xNew = 0.0
    private var yNew = 0.0
    private var posXImage = 0.0
    private var posYImage = 0.0
    private var dragging = false
    private var xMove = 0f
    private var yMove = 0f
    private var bounds: RectF? = null

    private var renderRunnable: Runnable? = null
    private var pinchRenderRunnable: Runnable? = null

    fun getScale(): Double = if (posScale < 1) 1.0 else posScale

    fun toggle() {
        if (isZoomDisabled) return
        if (isZoomIn) zoomReset() else zoomCenter()
    }

    fun zoomReset(done: (() -> Unit)? = null) {
        pinchScale = 0.0
        scale = 1.0
        xImage = 0.0
        yImage = 0.0
        xLast = 0.0
        yLast = 0.0
        posScale = 1.0
        view.setZoomTransform(1f, 0f, 0f, view.viewport.width() / 2, view.viewport.height() / 2, true) {
            renderPages()
            done?.invoke()
        }
        host.setViewerZoomed(false)
        setZoomOutControls()
    }

    fun zoomCenter(dir: Int = 1, increment: Double = 1.5) {
        pinchScale = 0.0
        zoomSet(size = null, dir = dir, increment = increment, animate = true, x = host.canvasWidth / 2.0, y = host.canvasHeight / 2.0, noRender = false)
    }

    fun pinch(eventScale: Float, centerX: Float, centerY: Float) {
        if (isZoomDisabled) return
        pinching = true
        val i = if (eventScale > 0) 3 * (eventScale - 1) else 3 * (1 - eventScale)
        zoomSet(size = pinchScale + i, dir = 0, increment = null, animate = false, x = centerX.toDouble(), y = centerY.toDouble(), noRender = true)
    }

    fun pinchEnd(eventScale: Float) {
        val i = if (eventScale > 0) 3 * (eventScale - 1) else 3 * (1 - eventScale)
        pinchScale += i
        pinching = false
        pinchRenderRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { if (!pinching) renderPages() }
        pinchRenderRunnable = r
        handler.postDelayed(r, 600)
    }

    private fun zoomSet(
        size: Double?,
        dir: Int,
        increment: Double?,
        animate: Boolean,
        x: Double,
        y: Double,
        noRender: Boolean,
        force: Boolean = false,
    ) {
        xImage += (x - xLast) / scale
        yImage += (y - yLast) / scale
        if (scale > 1) setZoomInControls() else setZoomOutControls()
        if (size != null) {
            if (scale == 1.0 && size > 1) {
                setZoomInControls()
                host.setViewerZoomed(true)
            }
            scale = size.coerceIn(1.0, 6.0)
        } else {
            val s = increment ?: inc
            if (dir > 0) {
                if (scale == 1.0) {
                    setZoomInControls()
                    host.setViewerZoomed(true)
                }
                scale *= s
            } else scale /= s
            scale = if (scale < 1) 1.0 else if (scale > 6 && !force) 6.0 else scale
        }
        val a = (x - xImage) / scale
        val l = (y - yImage) / scale
        xLast = x
        yLast = y
        posScale = scale
        xNew = a
        yNew = l
        posXImage = xImage
        posYImage = yImage
        if (scale == 1.0) {
            zoomReset()
            xLast = 0.0
            yLast = 0.0
            xImage = 0.0
            yImage = 0.0
            if (!noRender) renderPages()
        } else {
            view.setZoomTransform(
                posScale.toFloat(), xNew.toFloat(), yNew.toFloat(),
                posXImage.toFloat(), posYImage.toFloat(), animate,
            ) { if (!noRender) renderPages() }
        }
    }

    private fun setZoomInControls() {
        isZoomIn = true
        host.setZoomInControls()
    }

    private fun setZoomOutControls() {
        isZoomIn = false
        host.setZoomOutControls()
    }

    fun moveStart(x: Float, y: Float) {
        if (isZoomDisabled || scale == 1.0) return
        dragging = true
        xMove = x
        yMove = y
        var left: Float? = null
        var top: Float? = null
        var right: Float? = null
        var bottom: Float? = null
        val r = RectF()
        for (p in host.visiblePages()) {
            if (!view.pageScreenRect(p, r)) continue
            if (left == null || left > r.left) left = r.left
            if (top == null || top > r.top) top = r.top
            if (right == null || right < r.right) right = r.right
            if (bottom == null || bottom < r.bottom) bottom = r.bottom
        }
        bounds = if (left != null) RectF(left, top!!, right!!, bottom!!) else null
    }

    fun moveProgress(x: Float, y: Float) {
        if (isZoomDisabled || scale == 1.0 || !dragging) return
        var s = x - xMove
        var a = y - yMove
        xMove = x
        yMove = y
        bounds?.let { b ->
            val w = host.canvasWidth
            val h = host.canvasHeight
            if ((b.left + s > 0 && s > 0) || (b.right + s < w && s < 0)) s = 0f
            if ((b.top + a > 0 && a > 0) || (b.bottom + a < h && a < 0)) a = 0f
            b.offset(s, a)
        }
        xNew += s / scale
        yNew += a / scale
        xLast += s
        yLast += a
        view.setZoomTransform(posScale.toFloat(), xNew.toFloat(), yNew.toFloat(), posXImage.toFloat(), posYImage.toFloat(), false, null)
    }

    fun moveEnd() {
        if (isZoomDisabled) return
        dragging = false
        bounds = null
    }

    private fun viewSize(): String = when {
        scale <= 1 -> "normal"
        scale <= 1.5 -> "medium"
        scale <= 2.5 -> "large"
        scale <= 4 -> "xlarge"
        else -> "xxlarge"
    }

    fun renderPages() {
        renderRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            renderRunnable = null
            host.renderPages(viewSize())
        }
        renderRunnable = r
        handler.postDelayed(r, 150)
    }

    fun turned() {
        if (getScale() != 1.0) renderPages()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
    }
}
