package com.scoreplus.flipbook.internal.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.scoreplus.flipbook.R
import com.scoreplus.flipbook.internal.Easing
import com.scoreplus.flipbook.internal.draw.IconSprite
import com.scoreplus.flipbook.internal.draw.ShadowPainter
import com.scoreplus.flipbook.internal.draw.Spinner
import com.scoreplus.flipbook.internal.rgba
import com.scoreplus.flipbook.internal.turn.Effect
import com.scoreplus.flipbook.internal.turn.Flip
import com.scoreplus.flipbook.internal.turn.GradientSpec
import com.scoreplus.flipbook.internal.turn.PageWrap
import com.scoreplus.flipbook.internal.turn.TurnBook
import kotlin.math.min
import kotlin.math.roundToInt

internal interface MagazineHost {
    val book: TurnBook?
    fun content(page: Int): PageContent?
    fun onArrowTap(isNext: Boolean)
    fun onMagazineTouch(action: Int, x: Float, y: Float)
    fun ensureRendered(page: Int)
}

/** `#magazineViewport` + `.magazine`: draws pages, folds, shadows, depth strips and arrows. */
@SuppressLint("ViewConstructor")
internal class MagazineView(context: Context, private val host: MagazineHost) : View(context) {
    private val density = resources.displayMetrics.density
    private val sprite = IconSprite(resources)
    private val arrowsBitmap: Bitmap = BitmapFactory.decodeResource(
        resources, R.drawable.flipbook_arrows, BitmapFactory.Options().apply { inScaled = false },
    )
    private val pageShadow = ShadowPainter(20f * density)
    private val spinner = Spinner(density)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rect2 = RectF()
    private val src = Rect()
    private val path = Path()
    private val camera = Camera()
    private val tmpMatrix = Matrix()

    val viewport = RectF()
    var magW = 0f
    var magH = 0f
    var ready = false

    var single = false
    var mediaWidthDp = 0f
    var showShadow = true
    var showDepth = true
    var showRound = false
    var showBinding = false
    var showCenter = true
    var arrowsMode = 1
    var rtl = false
    var numPages = 0

    private var marginCurrent = 0f
    private var marginFrom = 0f
    private var marginTo = 0f
    private var marginStart = 0L
    var marginAnimated = false

    var zoomScale = 1f
    var zoomTx = 0f
    var zoomTy = 0f
    var zoomOx = Float.NaN
    var zoomOy = Float.NaN
    private var zoomAnim: FloatArray? = null
    private var zoomAnimFrom = FloatArray(5)
    private var zoomAnimStart = 0L
    private var zoomAnimDone: (() -> Unit)? = null

    val nextArrow = ArrowState()
    val prevArrow = ArrowState()
    private var pressedArrow: ArrowState? = null

    val depthLeft = DepthStrip()
    val depthRight = DepthStrip()

    class ArrowState {
        var shown = true
        var alpha = 1f
        var fadeFrom = 1f
        var fadeTo = 1f
        var fadeStart = 0L
        var fading = false
        val rect = RectF()
    }

    class DepthStrip {
        var visible = false
        var offset = 0f
        var from = 0f
        var to = 0f
        var start = 0L
        var animating = false
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    fun magLeft(): Float = viewport.left + viewport.width() / 2 - magW / 2 + (if (showCenter) marginCurrent else 0f)

    fun magTop(): Float = viewport.top + viewport.height() / 2 - magH / 2

    fun setMargin(target: Float) {
        if (!showCenter) {
            marginCurrent = 0f
            return
        }
        if (marginAnimated && ready) {
            marginFrom = marginCurrent
            marginTo = target
            marginStart = now()
        } else {
            marginCurrent = target
            marginFrom = target
            marginTo = target
        }
        invalidate()
    }

    private fun stepMargin(t: Long): Boolean {
        if (marginCurrent == marginTo) return false
        val p = ((t - marginStart) / 500f).coerceIn(0f, 1f)
        marginCurrent = marginFrom + (marginTo - marginFrom) * Easing.cssEase(p)
        return p < 1f
    }

    // ---- zoom transform: transform: scale(s) translate(tx,ty); transform-origin: ox oy ----

    fun originX(): Float = if (zoomOx.isNaN()) viewport.width() / 2 else zoomOx

    fun originY(): Float = if (zoomOy.isNaN()) viewport.height() / 2 else zoomOy

    fun setZoomTransform(scale: Float, tx: Float, ty: Float, ox: Float, oy: Float, animate: Boolean, done: (() -> Unit)?) {
        if (animate) {
            zoomAnimFrom = floatArrayOf(zoomScale, zoomTx, zoomTy, originX(), originY())
            zoomAnim = floatArrayOf(scale, tx, ty, ox, oy)
            zoomAnimStart = now()
            zoomAnimDone = done
        } else {
            zoomAnim = null
            zoomScale = scale
            zoomTx = tx
            zoomTy = ty
            zoomOx = ox
            zoomOy = oy
            done?.invoke()
        }
        invalidate()
    }

    private fun stepZoom(t: Long): Boolean {
        val target = zoomAnim ?: return false
        val p = ((t - zoomAnimStart) / 200f).coerceIn(0f, 1f)
        val e = Easing.cssEase(p)
        val f = zoomAnimFrom
        zoomScale = f[0] + (target[0] - f[0]) * e
        zoomTx = f[1] + (target[1] - f[1]) * e
        zoomTy = f[2] + (target[2] - f[2]) * e
        zoomOx = f[3] + (target[3] - f[3]) * e
        zoomOy = f[4] + (target[4] - f[4]) * e
        if (p >= 1f) {
            zoomAnim = null
            val d = zoomAnimDone
            zoomAnimDone = null
            post { d?.invoke() }
            return false
        }
        return true
    }

    fun isZoomAnimating(): Boolean = zoomAnim != null

    fun zoomMatrix(out: Matrix) {
        val ox = originX()
        val oy = originY()
        out.reset()
        out.preTranslate(viewport.left, viewport.top)
        out.preTranslate(ox, oy)
        out.preScale(zoomScale, zoomScale)
        out.preTranslate(zoomTx, zoomTy)
        out.preTranslate(-ox, -oy)
        out.preTranslate(-viewport.left, -viewport.top)
    }

    /** Screen rectangle of a page wrapper (like jQuery offset() + width()*scale). */
    fun pageScreenRect(page: Int, out: RectF): Boolean {
        val book = host.book ?: return false
        val wrap = book.pageWrap[page] ?: return false
        out.set(magLeft() + wrap.x, magTop() + wrap.y, magLeft() + wrap.x + wrap.w, magTop() + wrap.y + wrap.h)
        zoomMatrix(tmpMatrix)
        tmpMatrix.mapRect(out)
        return true
    }

    // ---- arrows ----

    fun fadeArrows(show: Boolean) {
        for (a in arrayOf(nextArrow, prevArrow)) {
            a.fadeFrom = a.alpha
            a.fadeTo = if (show) 1f else 0f
            a.fadeStart = now()
            a.fading = true
        }
        invalidate()
    }

    private fun stepArrows(t: Long): Boolean {
        var active = false
        for (a in arrayOf(nextArrow, prevArrow)) {
            if (!a.fading) continue
            val p = ((t - a.fadeStart) / 200f).coerceIn(0f, 1f)
            a.alpha = a.fadeFrom + (a.fadeTo - a.fadeFrom) * Easing.swing(p)
            if (p >= 1f) a.fading = false else active = true
        }
        return active
    }

    private fun small(): Boolean = mediaWidthDp <= 750f

    private fun layoutArrows() {
        val d = density
        if (arrowsMode == 1) {
            val s = 32f * d
            val top = magH - 5f * d - s
            val nx = if (small()) magW - s else magW + 42f * d - s
            val px = if (small()) 0f else -42f * d
            nextArrow.rect.set(nx, top, nx + s, top + s)
            prevArrow.rect.set(px, top, px + s, top + s)
        } else {
            val w = 22f * d
            val top = if (showRound) 100f * d else 0f
            val h = if (showRound) magH - 200f * d else magH
            val nx = if (small()) magW - w else magW
            val px = if (small()) 0f else -w
            nextArrow.rect.set(nx, top, nx + w, top + h)
            prevArrow.rect.set(px, top, px + w, top + h)
        }
    }

    private fun drawArrows(canvas: Canvas) {
        if (arrowsMode == 3) return
        layoutArrows()
        for (a in arrayOf(prevArrow, nextArrow)) {
            if (!a.shown || a.alpha <= 0f) continue
            val isNext = a === nextArrow
            val alpha = (a.alpha * 255).roundToInt()
            when (arrowsMode) {
                1 -> sprite.draw(canvas, IconSprite.CORNER_ARROW, a.rect, mirror = isNext, alpha = alpha)
                2 -> {
                    paint.shader = null
                    paint.color = rgba(0, 0, 0, 0.2 * a.alpha)
                    val r = if (small()) 0f else 15f * density
                    path.reset()
                    val radii = if (isNext) floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
                    else floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                    path.addRoundRect(a.rect, radii, Path.Direction.CW)
                    canvas.drawPath(path, paint)
                    val offset = if (isNext) 38 else 4
                    src.set(offset, 0, min(offset + 22, arrowsBitmap.width), arrowsBitmap.height)
                    val cy = a.rect.centerY()
                    val ih = 32f * density
                    rect2.set(a.rect.left, cy - ih / 2, a.rect.left + (src.width()) * density, cy + ih / 2)
                    paint.color = Color.BLACK
                    paint.alpha = alpha
                    canvas.drawBitmap(arrowsBitmap, src, rect2, paint)
                }
            }
        }
    }

    // ---- depth strips (show_edges) ----

    fun moveDepth(strip: DepthStrip, visible: Boolean, offset: Float) {
        strip.visible = visible
        strip.from = strip.offset
        strip.to = offset
        strip.start = now()
        strip.animating = true
        invalidate()
    }

    private fun stepDepth(t: Long): Boolean {
        var active = false
        for (s in arrayOf(depthLeft, depthRight)) {
            if (!s.animating) continue
            val p = ((t - s.start) / 200f).coerceIn(0f, 1f)
            s.offset = s.from + (s.to - s.from) * Easing.cssEase(p)
            if (p >= 1f) s.animating = false else active = true
        }
        return active
    }

    private fun drawDepth(canvas: Canvas) {
        for (s in arrayOf(depthLeft, depthRight)) {
            if (!s.visible) continue
            val w = magW * 0.04f
            val top = 1f * density
            val h = magH - 2f * density
            val isRight = s === depthRight
            val left = if (isRight) magW - w + s.offset else -s.offset
            canvas.save()
            rect.set(left, top, left + w, top + h)
            if (!isRight) canvas.rotate(180f, rect.centerX(), rect.centerY())
            canvas.clipRect(rect)
            val unit = h / 100f
            val imgW = 20f * unit
            val imgLeft = rect.right - imgW
            if (showRound) {
                path.reset()
                rect2.set(imgLeft, rect.top, rect.right, rect.bottom)
                path.addRoundRect(rect2, 4f * unit, 4f * unit, Path.Direction.CW)
                canvas.clipPath(path)
            }
            paint.shader = null
            paint.color = 0xFFFAFAFA.toInt()
            canvas.drawRect(rect, paint)
            for (i in 0 until 10) {
                val x = imgLeft + (1f + i * 2f) * unit
                paint.color = if (i % 2 == 0) 0xFFD9D9D9.toInt() else 0xFFF2F2F2.toInt()
                canvas.drawRect(x - unit / 2, rect.top, x + unit / 2, rect.bottom, paint)
            }
            canvas.restore()
        }
    }

    // ---- page content ----

    private fun cornerRadii(parity: Int, isTemporal: Boolean): FloatArray? {
        if (!showRound) return null
        val r = (0.024f * magW).roundToInt().toFloat()
        val all = floatArrayOf(r, r, r, r, r, r, r, r)
        val rightSide = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        val leftSide = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        if (isTemporal) return all
        if (single) return if (rtl) leftSide else rightSide
        return when (parity) {
            0 -> if (rtl) rightSide else if (small()) rightSide else leftSide
            1 -> if (rtl) leftSide else rightSide
            else -> null
        }
    }

    private fun drawPage(canvas: Canvas, page: Int, w: Float, h: Float, time: Long) {
        val book = host.book ?: return
        val obj = book.pageObjs[page]
        val parity = obj?.parity ?: -1
        canvas.save()
        cornerRadii(parity, page == 0)?.let {
            path.reset()
            rect.set(0f, 0f, w, h)
            path.addRoundRect(rect, it, Path.Direction.CW)
            canvas.clipPath(path)
        }
        paint.shader = null
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, w, h, paint)
        if (page == 0) {
            canvas.restore()
            return
        }
        val content = host.content(page)
        val bmp = content?.bitmap
        if (bmp != null && !bmp.isRecycled) {
            val scale = min(w / bmp.width, h / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            rect.set((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
            paint.color = Color.BLACK
            canvas.drawBitmap(bmp, null, rect, paint)
            val region = content.region
            if (region != null && !region.isRecycled && content.regionBoxWidth > 0f) {
                val k = w / content.regionBoxWidth
                rect.set(
                    content.regionRect.left * k, content.regionRect.top * k,
                    content.regionRect.right * k, content.regionRect.bottom * k,
                )
                canvas.drawBitmap(region, null, rect, paint)
            }
        } else host.ensureRendered(page)

        if (showDepth) drawPageGradient(canvas, parity, w, h)
        if (showBinding) drawBinding(canvas, page, parity, w, h)
        if (content == null || content.loading || bmp == null) {
            spinner.draw(canvas, w / 2, h / 2, time)
        }
        canvas.restore()
    }

    private fun drawPageGradient(canvas: Canvas, parity: Int, w: Float, h: Float) {
        val darkLeft: Boolean
        val alpha: Double
        when {
            single -> { darkLeft = true; alpha = 0.15 }
            parity == 0 -> { darkLeft = false; alpha = if (small()) 0.15 else 0.2 }
            parity == 1 -> { darkLeft = true; alpha = 0.15 }
            else -> return
        }
        val dark = rgba(0, 0, 0, alpha)
        val clear = rgba(0, 0, 0, 0.0)
        gradientPaint.shader = if (darkLeft) {
            LinearGradient(w, 0f, 0f, 0f, intArrayOf(clear, clear, dark), floatArrayOf(0f, 0.95f, 1f), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, 0f, w, 0f, intArrayOf(clear, clear, dark), floatArrayOf(0f, 0.95f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, gradientPaint)
    }

    private fun drawBinding(canvas: Canvas, page: Int, parity: Int, w: Float, h: Float) {
        val lastEven = page == numPages && parity == 0
        if (page != 1 && !lastEven) return
        val fromLeft = if (page == 1) !rtl else rtl
        val colors = intArrayOf(
            rgba(0, 0, 0, 0.31), rgba(0, 0, 0, 0.2), rgba(255, 255, 255, 0.1),
            rgba(0, 0, 0, 0.22), rgba(255, 255, 255, 0.0),
        )
        val stops = floatArrayOf(0f, 0.03f, 0.035f, 0.036f, 1f)
        gradientPaint.shader = if (fromLeft) LinearGradient(0f, 0f, w, 0f, colors, stops, Shader.TileMode.CLAMP)
        else LinearGradient(w, 0f, 0f, 0f, colors, stops, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, gradientPaint)
    }

    private fun drawGradientSpec(canvas: Canvas, g: GradientSpec, w: Float, h: Float, opacity: Float = 1f) {
        if (!g.active) return
        val colors = IntArray(3) {
            val c = g.colors[it]
            Color.argb((Color.alpha(c) * opacity).roundToInt(), Color.red(c), Color.green(c), Color.blue(c))
        }
        if (g.x0 == g.x1 && g.y0 == g.y1) return
        gradientPaint.shader = LinearGradient(g.x0, g.y0, g.x1, g.y1, colors, g.positions, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, gradientPaint)
    }

    private fun movedObjects(book: TurnBook): Set<Int> {
        val out = HashSet<Int>()
        for (f in book.pages.values) if (f.visible) f.foldingObj?.let { out.add(it) }
        return out
    }

    private fun drawWrap(canvas: Canvas, book: TurnBook, wrap: PageWrap, moved: Set<Int>, time: Long) {
        val flip: Flip? = book.pages[wrap.page]
        canvas.save()
        if (flip?.visible == true && flip.effect == Effect.HARD) {
            drawHardWrap(canvas, book, wrap, flip, time)
            canvas.restore()
            return
        }
        canvas.clipRect(wrap.x, wrap.y, wrap.x + wrap.w, wrap.y + wrap.h)
        canvas.translate(wrap.x, wrap.y)
        if (flip?.visible == true) {
            val fold = flip.sheet
            drawGradientSpec(canvas, fold.backGradient, wrap.w, wrap.h)
            canvas.concat(fold.wrapperMatrix)
            canvas.clipRect(0f, 0f, fold.wrapperSize, fold.wrapperSize)
            canvas.concat(fold.pageMatrix)
            drawPage(canvas, wrap.page, wrap.w, wrap.h, time)
        } else if (wrap.page !in moved) {
            drawPage(canvas, wrap.page, wrap.w, wrap.h, time)
        }
        canvas.restore()
    }

    private fun drawFlap(canvas: Canvas, flip: Flip, time: Long) {
        val obj = flip.foldingObj ?: return
        val fold = flip.sheet
        val w = flip.width
        val h = flip.height
        canvas.save()
        canvas.concat(fold.fwrapperMatrix)
        canvas.clipRect(0f, 0f, fold.wrapperSize, fold.wrapperSize)
        canvas.concat(fold.fpageMatrix)
        rect.set(0f, 0f, w, h)
        pageShadow.draw(canvas, rect, rgba(0, 0, 0, fold.flapShadowAlpha.toDouble()))
        drawPage(canvas, obj, w, h, time)
        drawGradientSpec(canvas, fold.frontGradient, w, h)
        canvas.restore()
    }

    private fun drawHardWrap(canvas: Canvas, book: TurnBook, wrap: PageWrap, flip: Flip, time: Long) {
        val f = flip.hardFold
        val w = wrap.w
        val h = wrap.h
        canvas.translate(wrap.x, wrap.y)
        if (f.frontShadowVisible) {
            paint.shader = null
            paint.color = rgba(0, 0, 0, f.frontShadowAlpha.toDouble())
            canvas.drawRect(f.frontShadowLeft, 0f, f.frontShadowLeft + w, h, paint)
        }
        val drawFront = {
            canvas.save()
            rotateY(canvas, f.angle, f.originX, f.originX, h)
            pageShadow.draw(canvas, RectF(0f, 0f, w, h), rgba(0, 0, 0, 0.2))
            drawPage(canvas, wrap.page, w, h, time)
            if (f.backShadowOnWrapper) drawGradientSpec(canvas, f.backShadow, w, h, f.backShadowOpacity)
            canvas.restore()
        }
        val drawBack = {
            val obj = flip.foldingObj
            if (obj != null) {
                canvas.save()
                rotateY(canvas, 180f + f.angle, f.flapOriginX, f.flapOriginX + f.flapTranslateX, h)
                drawPage(canvas, obj, w, h, time)
                if (!f.backShadowOnWrapper) drawGradientSpec(canvas, f.backShadow, w, h, f.backShadowOpacity)
                canvas.restore()
            }
        }
        if (f.wrapperOnTop) {
            drawBack(); drawFront()
        } else {
            drawFront(); drawBack()
        }
    }

    /** CSS rotateY about [pivotX] (element coords) under `perspective: 3000px` centred on the spine. */
    private fun rotateY(canvas: Canvas, degrees: Float, pivotX: Float, spineX: Float, h: Float) {
        camera.save()
        camera.setLocation(0f, 0f, -3000f * density / 72f)
        camera.rotateY(-degrees)
        camera.getMatrix(tmpMatrix)
        camera.restore()
        tmpMatrix.preTranslate(-pivotX, -h / 2)
        tmpMatrix.postTranslate(spineX, h / 2)
        canvas.concat(tmpMatrix)
    }

    override fun onDraw(canvas: Canvas) {
        val time = now()
        var animating = stepMargin(time) or stepZoom(time) or stepArrows(time) or stepDepth(time)
        canvas.save()
        zoomMatrix(tmpMatrix)
        canvas.concat(tmpMatrix)
        val book = host.book
        if (book == null || !ready) {
            spinner.draw(canvas, viewport.centerX(), viewport.centerY(), time)
            canvas.restore()
            postInvalidateOnAnimation()
            return
        }
        canvas.translate(magLeft(), magTop())
        if (single) canvas.clipRect(0f, -100000f, magW, 100000f)
        drawDepth(canvas)
        if (showShadow && numPages > 1) {
            val s = book.shadow
            rect.set(s.x, s.y, s.x + s.w, s.y + s.h)
            pageShadow.draw(canvas, rect, 0xFFCCCCCC.toInt())
            if (book.magazineHasShadowClass) {
                rect.set(0f, 0f, magW, magH)
                pageShadow.draw(canvas, rect, 0xFFCCCCCC.toInt())
            }
        }
        val moved = movedObjects(book)
        val wraps = book.pageWrap.values.filter { it.visible }.sortedWith(compareBy({ it.z }, { it.order }))
        for (wrap in wraps) drawWrap(canvas, book, wrap, moved, time)
        val flaps = book.pages.values.filter { it.visible && it.effect == Effect.SHEET }
            .sortedBy { it.zIndex ?: 0 }
        for (flip in flaps) drawFlap(canvas, flip, time)
        drawArrows(canvas)
        canvas.restore()
        val loading = book.pageWrap.values.any { w ->
            w.visible && (host.content(w.page)?.let { it.loading || it.bitmap == null } ?: true)
        }
        if (animating || loading) postInvalidateOnAnimation()
    }

    // ---- input ----

    private val inverse = Matrix()
    private val pt = FloatArray(2)

    fun toMagazine(x: Float, y: Float): FloatArray {
        zoomMatrix(tmpMatrix)
        tmpMatrix.invert(inverse)
        pt[0] = x
        pt[1] = y
        inverse.mapPoints(pt)
        pt[0] -= magLeft()
        pt[1] -= magTop()
        return pt
    }

    private fun arrowAt(mx: Float, my: Float): ArrowState? {
        if (!ready || arrowsMode == 3) return null
        if (single && (mx < 0f || mx > magW)) return null
        layoutArrows()
        for (a in arrayOf(nextArrow, prevArrow)) {
            if (a.shown && a.alpha > 0f && a.rect.contains(mx, my)) return a
        }
        return null
    }

    fun isOnArrow(x: Float, y: Float): Boolean {
        val m = toMagazine(x, y)
        return arrowAt(m[0], m[1]) != null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val m = toMagazine(event.x, event.y)
        val mx = m[0]
        val my = m[1]
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedArrow = arrowAt(mx, my)
                if (pressedArrow == null) host.onMagazineTouch(MotionEvent.ACTION_DOWN, mx, my)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedArrow == null) host.onMagazineTouch(MotionEvent.ACTION_MOVE, mx, my)
            }
            MotionEvent.ACTION_UP -> {
                val a = pressedArrow
                pressedArrow = null
                if (a != null) {
                    if (arrowAt(mx, my) === a) host.onArrowTap(a === nextArrow)
                } else host.onMagazineTouch(MotionEvent.ACTION_UP, mx, my)
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pressedArrow == null) host.onMagazineTouch(MotionEvent.ACTION_UP, mx, my)
                pressedArrow = null
            }
        }
        return true
    }
}
