package com.scoreplus.flipbook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import com.scoreplus.flipbook.internal.FlipSounds
import com.scoreplus.flipbook.internal.ZoomController
import com.scoreplus.flipbook.internal.ZoomHost
import com.scoreplus.flipbook.internal.jsRound
import com.scoreplus.flipbook.internal.pdf.PdfEngine
import com.scoreplus.flipbook.internal.pdf.PdfInfo
import com.scoreplus.flipbook.internal.turn.Direction
import com.scoreplus.flipbook.internal.turn.Display
import com.scoreplus.flipbook.internal.turn.TurnBook
import com.scoreplus.flipbook.internal.turn.TurnHost
import com.scoreplus.flipbook.internal.view.BackgroundView
import com.scoreplus.flipbook.internal.view.LoaderLineView
import com.scoreplus.flipbook.internal.view.MagazineHost
import com.scoreplus.flipbook.internal.view.MagazineView
import com.scoreplus.flipbook.internal.view.PageContent
import com.scoreplus.flipbook.internal.view.PageNumberView
import com.scoreplus.flipbook.internal.view.PageSliderView
import com.scoreplus.flipbook.internal.view.SliderHost
import com.scoreplus.flipbook.internal.view.TitleView
import com.scoreplus.flipbook.internal.view.ToolbarView
import com.scoreplus.flipbook.internal.view.ZoomStepView
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Native Android port of the ScorePlus web flipbook. Call [open] with a PDF.
 */
class FlipbookView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var listener: FlipbookListener? = null

    val pageCount: Int get() = numPages

    val currentPage: Int get() = book?.page ?: 0

    var soundEnabled: Boolean
        get() = sounds?.enabled ?: soundOn
        set(value) {
            soundOn = value
            sounds?.enabled = value
            toolbar.soundOn = value
            toolbar.invalidate()
        }

    /** [path] is an `http(s)://` link (server / CDN) or a file in the app's `assets/` folder. */
    fun open(path: String, design: FlipbookDesign = FlipbookDesign()) =
        open(
            if (path.startsWith("http://", true) || path.startsWith("https://", true)) FlipbookSource.Url(path)
            else FlipbookSource.Asset(path),
            design,
        )

    fun open(source: FlipbookSource, design: FlipbookDesign = FlipbookDesign()) {
        release()
        this.design = design
        name = source.name
        sounds = FlipSounds(context).also { it.enabled = soundOn }
        designLoad()
        magazine.ready = false
        magazine.invalidate()
        val e = PdfEngine(context)
        e.currentPageProvider = { book?.page ?: 1 }
        engine = e
        e.open(source, ::onPdfReady) { listener?.onError(it) }
    }

    fun goToPage(page: Int) = controlsGoTo(page)

    fun nextPage() = controlsGoTo(if (design.rtl == 1) PREVIOUS else NEXT)

    fun previousPage() = controlsGoTo(if (design.rtl == 1) NEXT else PREVIOUS)

    fun zoomIn() = zoom.zoomCenter(1)

    fun zoomOut() = zoom.zoomCenter(-1)

    fun release() {
        engine?.close()
        engine = null
        sounds?.release()
        sounds = null
        zoom.release()
        mainHandler.removeCallbacksAndMessages(null)
        for (c in contents.values) c.release()
        contents.clear()
        for (t in thumbs.values) t.recycle()
        thumbs.clear()
        loadedPages.clear()
        book = null
        numPages = 0
        pageNumber.text = ""
        pageNumber.invalidate()
        slider.numPages = 0
        slider.invalidate()
        magazine.ready = false
    }

    // ------------------------------------------------------------------ internals

    private val density = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("com.scoreplus.flipbook", Context.MODE_PRIVATE)
    private var design = FlipbookDesign()
    private var name = ""
    private var engine: PdfEngine? = null
    private var sounds: FlipSounds? = null
    private var soundOn = true
    private var book: TurnBook? = null
    private var numPages = 0
    private var pdfW = 0f
    private var pdfH = 0f
    private var renderAspectRatio = 0f
    private val contents = HashMap<Int, PageContent>()
    private val thumbs = LinkedHashMap<Int, Bitmap>(16, 0.75f, true)
    private val loadedPages = HashSet<Int>()
    private var peelTurning = false
    private var startDisabled = false
    private var viewerZoomedIn = false
    private var disableTurnEvents = false
    private var disableFirstMove = false
    private var fullscreenOn = false
    private var peelTimer: Runnable? = null
    private var peelState = ""

    private val turnHost = object : TurnHost {
        override fun now(): Long = SystemClock.uptimeMillis()
        override fun postFrame(action: () -> Unit) = magazine.postOnAnimation(action)
        override fun requestRender() = magazine.invalidate()
        override fun onStart(corner: String?): Boolean {
            if (viewerZoomedIn) {
                startDisabled = true
                return true
            }
            startDisabled = false
            scoreplusTurning(null)
            return false
        }
        override fun onPeelStart() {
            if (!peelTurning && !startDisabled) sounds?.peelStart()
        }
        override fun onPeelEnd() {}
        override fun onTurning(page: Int, view: IntArray): Boolean {
            peelTurning = true
            scoreplusTurning(page)
            adjustPageDepth(page)
            return false
        }
        override fun onTurned(page: Int, view: IntArray) {
            peelTurning = false
            book?.center()
            scoreplusTurned(page)
            for (t in 4 until 5) {
                val p = page + t
                if (p <= numPages && p !in loadedPages) addPage(p)
            }
        }
        override fun onMissing(pages: List<Int>) {
            for (p in pages) addPage(p)
        }
        override fun onCenter(marginLeft: Float) = magazine.setMargin(marginLeft)
    }

    private val magazineHost = object : MagazineHost {
        override val book: TurnBook? get() = this@FlipbookView.book
        override fun content(page: Int): PageContent? = contents[page]
        override fun onArrowTap(isNext: Boolean) {
            firstInteraction()
            if (isNext) controlsGoTo(if (design.rtl == 1) PREVIOUS else NEXT)
            else controlsGoTo(if (design.rtl == 1) NEXT else PREVIOUS)
        }
        override fun onMagazineTouch(action: Int, x: Float, y: Float) = turnTouch(action, x, y)
        override fun ensureRendered(page: Int) {
            if (contents[page]?.let { it.bitmap == null && it.requestedWidth == 0 } == true) loadPageView(page)
        }
    }

    private val sliderHost = object : SliderHost {
        override fun isSinglePage(): Boolean = this@FlipbookView.isSinglePage()
        override fun loadThumbnails(first: Int, second: Int?, done: (Bitmap?, Bitmap?) -> Unit) {
            thumbnail(first) { a -> if (second == null) done(a, null) else thumbnail(second) { b -> done(a, b) } }
        }
        override fun onSliderRelease(page: Int) = controlsGoTo(page)
        override fun onSliderTouch() = firstInteraction()
    }

    private val zoomHost = object : ZoomHost {
        override val canvasWidth: Float get() = width.toFloat()
        override val canvasHeight: Float get() = height.toFloat()
        override fun visiblePages(): IntArray = getVisiblePages()
        override fun setZoomInControls() {
            disableTurnEvents = true
            if (design.showSlider != 0) slider.hideBarNow()
            toolbar.zoomedIn = true
            toolbar.invalidate()
            if (!zoomStep.shown) zoomStep.fade(true)
            magazine.fadeArrows(false)
        }
        override fun setZoomOutControls() {
            disableTurnEvents = false
            if (design.showSlider != 0 && !slider.barVisible) slider.fadeBar(true)
            progressBar("cancel")
            toolbar.zoomedIn = false
            toolbar.invalidate()
            if (zoomStep.shown) zoomStep.fade(false)
            if (design.arrows != 3) magazine.fadeArrows(true)
        }
        override fun setViewerZoomed(zoomed: Boolean) {
            viewerZoomedIn = zoomed
        }
        override fun renderPages(viewSize: String) = renderPagesAt(viewSize)
    }

    private val background = BackgroundView(context)
    private val title = TitleView(context)
    private val magazine = MagazineView(context, magazineHost)
    private val loaderLine = LoaderLineView(context)
    private val slider = PageSliderView(context, sliderHost)
    private val zoomStep = ZoomStepView(context) { onZoomStep(it) }
    private val toolbar = ToolbarView(context) { onToolbar(it) }
    private val pageNumber = PageNumberView(context)
    private val zoom = ZoomController(magazine, zoomHost)

    init {
        for (v in listOf(background, title, magazine, loaderLine, slider, pageNumber, zoomStep, toolbar)) {
            addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.WHITE)
    }

    // ---------------------------------------------------------------- scoreplusDesign

    private fun designLoad() {
        val e = design
        toolbar.showZoom = e.showZoom != 0
        toolbar.showSound = e.soundFlip == 1
        toolbar.showFullscreen = e.showFullscreen != 0 && findActivity() != null
        toolbar.soundOn = soundOn
        toolbar.setShownImmediately(toolbar.hasIcons())
        background.drawable = if (e.background != 0) context.getDrawable(e.background) else null
        background.color = e.backgroundColor ?: Color.WHITE
        background.size = e.backgroundSize
        background.opacity = e.backgroundTransparency?.let { 1 - it / 100f } ?: 0.4f
        background.invalidate()
        title.title = if (e.showText == 1) e.title else ""
        title.subtitle = if (e.showText == 1) e.subtitle else ""
        title.description = if (e.showText == 1) e.description else ""
        magazine.showDepth = e.showDepth != 0
        magazine.showShadow = e.showShadow != 0
        magazine.showRound = e.showRound == 1 || e.type == "notebook"
        magazine.showBinding = e.showBinding == 1
        magazine.showCenter = e.showCenter != 0
        magazine.arrowsMode = e.arrows
        magazine.rtl = e.rtl == 1
        slider.showSlider = e.showSlider
        slider.rtl = e.rtl == 1
        zoom.isZoomDisabled = e.showZoom == 0 && e.clickZoom == 0
        requestLayoutAll()
    }

    private fun setNextPrevious() {
        val atStart = (book?.page ?: 1) <= 1
        magazine.nextArrow.shown = true
        magazine.prevArrow.shown = true
        when (design.arrows) {
            1, 2 -> if (atStart) {
                if (design.rtl == 1) magazine.nextArrow.shown = false else magazine.prevArrow.shown = false
            }
            3 -> {
                magazine.nextArrow.shown = false
                magazine.prevArrow.shown = false
            }
        }
        magazine.invalidate()
    }

    // ---------------------------------------------------------------- loading

    private fun onPdfReady(info: PdfInfo) {
        numPages = info.pageCount
        pdfW = info.firstPageWidth
        pdfH = info.firstPageHeight
        renderAspectRatio = if (pdfW > 0) pdfH / pdfW else 0f
        val loadOnLastPage = when (val lp = design.loadPage) {
            null -> null
            -1 -> prefs.getInt("lastpage-$name", 0).takeIf { it > 0 }
            else -> lp
        }
        magazine.numPages = numPages
        slider.numPages = numPages
        sounds?.pageTurnSound = design.soundFlip == 1
        render()
        slider.configure()
        slider.setPage(book?.page ?: 1)
        setNextPrevious()
        if (design.showSlider == 0) slider.hideBarNow()
        design.autoPlaySeconds?.let { startAutoPlay(it) }
        val target = loadOnLastPage ?: design.startPage
        if (target != null) controlsGoTo(target)
        design.peelCorner?.takeIf { it in listOf("tr", "br", "tl", "bl") }?.let { corner ->
            val r = object : Runnable {
                override fun run() {
                    peelState = if (peelState == "") corner else ""
                    book?.peel(peelState)
                    mainHandler.postDelayed(this, 3000)
                }
            }
            peelTimer = r
            mainHandler.postDelayed(r, 3000)
        }
        listener?.onLoaded(numPages)
    }

    private fun render() {
        val b = TurnBook(turnHost, density, 1000, numPages > 1)
        book = b
        magazine.ready = true
        magazine.marginAnimated = false
        b.init(
            pdfW * density * 2, pdfH * density, numPages,
            if (design.showDouble == 2) Display.SINGLE else Display.DOUBLE,
            if (design.rtl == 1) Direction.RTL else Direction.LTR,
        )
        magazine.marginAnimated = true
        resizeViewport()
    }

    private fun addPage(e: Int) {
        val b = book ?: return
        val hard = when (design.type) {
            "book", "notebook" -> e == 1 || e == 2 || e == numPages || (numPages % 2 == 0 && e == numPages - 1)
            "album" -> true
            else -> false
        }
        if (!contents.containsKey(e)) contents[e] = PageContent(e)
        b.addPage(e, hard)
        loadedPages.add(e)
        loadPageView(e)
    }

    private fun desiredNormalWidth(): Int {
        val b = book ?: return 0
        var t = b.pageSize(1).w
        if (t <= 0f) t = max(width / 2f, 300f * density)
        return t.toInt()
    }

    private fun loadPageView(page: Int) {
        val e = engine ?: return
        val c = contents.getOrPut(page) { PageContent(page) }
        val desired = desiredNormalWidth()
        if (desired <= 0) return
        if (c.bitmap != null && c.renderedWidth == desired) return
        if (c.requestedWidth == desired) return
        c.requestedWidth = desired
        e.renderPage(page, desired) { bmp ->
            val cur = contents[page]
            if (cur == null || bmp == null) {
                bmp?.recycle()
                return@renderPage
            }
            if (cur.requestedWidth != desired) {
                bmp.recycle()
                return@renderPage
            }
            cur.bitmap?.takeIf { it !== bmp }?.recycle()
            cur.bitmap = bmp
            cur.renderedWidth = desired
            cur.requestedWidth = 0
            cur.loading = false
            progressBar("progress", 10f)
            enforceBudget()
            magazine.invalidate()
        }
    }

    private fun enforceBudget() {
        val budget = (Runtime.getRuntime().maxMemory() / 6).coerceIn(32L shl 20, 96L shl 20)
        var total = contents.values.sumOf { it.byteCount.toLong() }
        if (total <= budget) return
        val visible = getVisiblePages().toSet()
        val current = book?.page ?: 1
        val candidates = contents.values
            .filter { it.bitmap != null && it.page !in visible && book?.pageWrap?.get(it.page)?.visible != true }
            .sortedByDescending { abs(it.page - current) }
        for (c in candidates) {
            if (total <= budget) break
            total -= c.byteCount
            c.release()
            c.loading = true
        }
    }

    private fun thumbnail(page: Int, done: (Bitmap?) -> Unit) {
        thumbs[page]?.let { return done(it) }
        val e = engine ?: return done(null)
        e.renderPage(page, 253) { bmp ->
            if (bmp != null) {
                thumbs[page] = bmp
                while (thumbs.size > 12) {
                    val eldest = thumbs.entries.iterator().next()
                    eldest.value.recycle()
                    thumbs.remove(eldest.key)
                }
            }
            done(bmp)
        }
    }

    private fun adjustResolution(delay: Long = 500) {
        mainHandler.postDelayed({
            for (p in getVisiblePages()) if (contents.containsKey(p)) loadPageView(p)
        }, delay)
    }

    private fun renderPagesAt(viewSize: String) {
        val b = book ?: return
        val e = engine ?: return
        val pages = getVisiblePages()
        progressBar("start", pages.size.toFloat())
        if (viewSize == "normal") {
            e.cancelRegions()
            for (p in pages) {
                contents[p]?.clearRegion()
                loadPageView(p)
                progressBar("end")
            }
            magazine.invalidate()
            return
        }
        val k = when (viewSize) {
            "medium" -> 1.5f
            "large" -> 3f
            "xlarge" -> 4.5f
            else -> 6f
        }
        val screen = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val m = Matrix()
        magazine.zoomMatrix(m)
        val inverse = Matrix()
        m.invert(inverse)
        for (p in pages) {
            val c = contents[p]
            val wrap = b.pageWrap[p]
            if (c == null || wrap == null) {
                progressBar("end")
                continue
            }
            val size = e.cachedPageSize(p) ?: floatArrayOf(pdfW, pdfH)
            val box = RectF(0f, 0f, wrap.w, wrap.h)
            val fit = min(wrap.w / size[0], wrap.h / size[1])
            val draw = RectF(
                (wrap.w - size[0] * fit) / 2, (wrap.h - size[1] * fit) / 2,
                (wrap.w + size[0] * fit) / 2, (wrap.h + size[1] * fit) / 2,
            )
            val onScreen = RectF()
            magazine.pageScreenRect(p, onScreen)
            if (!onScreen.intersect(screen)) {
                progressBar("end")
                continue
            }
            val local = RectF(onScreen)
            inverse.mapRect(local)
            local.offset(-(magazine.magLeft() + wrap.x), -(magazine.magTop() + wrap.y))
            if (!local.intersect(draw) || !local.intersect(box)) {
                progressBar("end")
                continue
            }
            var kk = k
            val maxArea = width.toFloat() * height * 2.25f
            if (local.width() * kk * local.height() * kk > maxArea) {
                kk = sqrt(maxArea / (local.width() * local.height()))
            }
            val fullWidth = draw.width() * kk
            val left = ((local.left - draw.left) * kk).roundToInt()
            val top = ((local.top - draw.top) * kk).roundToInt()
            val rw = (local.width() * kk).roundToInt()
            val rh = (local.height() * kk).roundToInt()
            val key = "$fullWidth:$left:$top:$rw:$rh"
            if (c.regionRequest == key && c.region != null) {
                progressBar("end")
                continue
            }
            c.regionRequest = key
            val boxWidth = wrap.w
            e.renderRegion(p, fullWidth, left, top, rw, rh) { bmp ->
                progressBar("end")
                val cur = contents[p]
                if (bmp == null || cur == null || cur.regionRequest != key || !zoom.isZoomIn) {
                    bmp?.recycle()
                    return@renderRegion
                }
                cur.region?.recycle()
                cur.region = bmp
                cur.regionRect.set(local)
                cur.regionBoxWidth = boxWidth
                magazine.invalidate()
            }
        }
    }

    // ---------------------------------------------------------------- viewer geometry

    private val viewportWidthDp: Float get() = magazine.viewport.width() / density
    private val viewportHeightDp: Float get() = magazine.viewport.height() / density

    private fun smallPortrait(): Boolean =
        viewportWidthDp < 750 && magazine.viewport.width() <= magazine.viewport.height()

    private fun isSinglePage(): Boolean = when (design.showDouble) {
        1 -> false
        2 -> true
        else -> smallPortrait()
    }

    private fun isLastPage(e: Int): Boolean =
        if ((smallPortrait() && design.showDouble == 0) || design.showDouble == 2) numPages == e
        else (numPages % 2 != 0 && (e == numPages || e == numPages - 1)) || (numPages % 2 == 0 && e == numPages)

    private fun getVisiblePages(e0: Int? = null): IntArray {
        val i = e0 ?: book?.page ?: 1
        return if ((smallPortrait() && design.showDouble == 0) || design.showDouble == 2) intArrayOf(i)
        else if (i == 1) intArrayOf(1)
        else if (isLastPage(i)) {
            if (numPages % 2 == 0) intArrayOf(numPages) else intArrayOf(numPages - 1, numPages)
        } else if (i % 2 == 0) intArrayOf(i, i + 1)
        else intArrayOf(i - 1, i)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post {
            requestLayoutAll()
            if (book != null) {
                resizeViewport()
                adjustResolution()
            }
        }
    }

    private fun requestLayoutAll() {
        if (width == 0 || height == 0) return
        val wDp = width / density
        val hDp = height / density
        val sizeClass = when {
            wDp <= 475 || hDp <= 475 -> "sm"
            wDp <= 575 || hDp <= 575 -> if (design.controlsSize == "sm") "sm" else "md"
            else -> design.controlsSize?.takeIf { it == "sm" || it == "lg" } ?: "md"
        }
        val iconPx = when (sizeClass) {
            "sm" -> 24f
            "lg" -> 42f
            else -> 32f
        }
        toolbar.iconSize = iconPx
        toolbar.iconMargin = if (wDp <= 575) 3f else 6f
        toolbar.right = if (wDp < 790) 5f else if (wDp < 930) 20f else 50f
        toolbar.top = if (hDp < 700) 5f else 20f
        toolbar.layoutPanel()
        zoomStep.iconSize = iconPx
        zoomStep.right = if (wDp < 790) 10f else 50f
        zoomStep.layoutPanel()

        val canvasH = if (wDp < 790) 10f else 20f
        val canvasV = if (wDp < 790) 10f else 20f
        val arrows = if (design.arrows != 3 && wDp > 750) (if (design.arrows == 1) 32f else 14f) else 0f
        val padLR = (canvasH + arrows) * density
        val padT = canvasV * density
        val padB = ((if (design.showSlider == 0) 0f else 24f) + canvasV) * density
        magazine.viewport.set(padLR, padT, width - padLR, height - padB)
        magazine.mediaWidthDp = wDp
        slider.viewport = RectF(magazine.viewport)
        title.windowWidthDp = wDp
        title.invalidate()
        slider.invalidate()
        magazine.invalidate()
    }

    private fun resizeViewport() {
        val b = book ?: return
        requestLayoutAll()
        val t = viewportWidthDp
        val i = viewportHeightDp
        if (t <= 0 || i <= 0) return
        magazine.marginAnimated = false
        val nW = pdfW * 2
        val nH = pdfH
        var o = false
        var s = nW
        when (design.showDouble) {
            0 -> if (t < 750 && t <= i) {
                s = nW / 2
                o = true
                if (b.display == Display.DOUBLE) b.setDisplay(Display.SINGLE)
            } else if (b.display == Display.SINGLE) b.setDisplay(Display.DOUBLE)
            1 -> if (b.display == Display.SINGLE) b.setDisplay(Display.DOUBLE)
            2 -> {
                s = nW / 2
                o = true
                if (b.display == Display.DOUBLE) b.setDisplay(Display.SINGLE)
            }
        }
        magazine.single = o
        val a = calculateBound(s, nH, min(s, t), min(nH, i))
        if (a[0] % 2 != 0f) a[0] = a[0] - 1
        if (renderAspectRatio > 0) {
            a[1] = if (o) floor(a[0] * renderAspectRatio) else floor(a[0] / 2 * renderAspectRatio)
        }
        val wPx = a[0] * density
        val hPx = a[1] * density
        if (abs(wPx - b.width) > 0.5f || abs(hPx - b.height) > 0.5f) b.setSize(wPx, hPx)
        magazine.magW = b.width
        magazine.magH = b.height
        b.center()
        magazine.marginAnimated = true
        slider.configure()
        slider.setPage(b.page)
        adjustPageDepth(b.page)
        updatePageNumber()
        magazine.invalidate()
    }

    private fun updatePageNumber() {
        pageNumber.text = if (design.showPageNumber == 0 || numPages == 0) ""
        else getVisiblePages().joinToString("-") + " / " + numPages
        val wDp = width / density
        pageNumber.leftDp = if (wDp < 790) 5f else if (wDp < 930) 20f else 50f
        val bar = toolbar.panelBounds
        pageNumber.centerY = if (toolbar.hasIcons()) bar.centerY() else toolbar.top * density + 12f * density
        pageNumber.invalidate()
    }

    private fun calculateBound(width: Float, height: Float, boundWidth: Float, boundHeight: Float): FloatArray {
        val t = floatArrayOf(width, height)
        if (t[0] > boundWidth || t[1] > boundHeight) {
            val i = t[0] / t[1]
            if (boundWidth / i > boundHeight && boundHeight * i <= boundWidth) {
                t[0] = jsRound(boundHeight * i)
                t[1] = boundHeight
            } else {
                t[0] = boundWidth
                t[1] = jsRound(boundWidth / i)
            }
        }
        return t
    }

    // ---------------------------------------------------------------- page depth (show_edges)

    private var prevDepthPage = 0
    private var depthRightTimer: Runnable? = null
    private var depthLeftTimer: Runnable? = null

    private fun adjustPageDepth(e: Int) {
        if (design.showEdges != 1) return
        val b = book ?: return
        var t = 4f
        if (design.showRound == 1) t = if (pdfW >= pdfH) 6f else 5.3f
        if (numPages < 4) return
        if (numPages < 8) t = 6f
        val i = magazine.magW * 0.04f / t
        val n = (numPages - 1).toFloat()
        val o = i - (i / n) * (e - 1)
        val s = i - (i / n) * (numPages - e)
        val a = b.display
        val rtl = design.rtl == 1
        val duration = 1000L
        fun clearLeft() = depthLeftTimer?.let { mainHandler.removeCallbacks(it) }
        fun clearRight() = depthRightTimer?.let { mainHandler.removeCallbacks(it) }
        fun hideRight(delay: Long = 100) {
            magazine.moveDepth(magazine.depthRight, true, 0f)
            mainHandler.postDelayed({ magazine.depthRight.visible = false; magazine.invalidate() }, delay)
        }
        fun showRight(off: Float, page: Int) {
            val delay = if (rtl) (if (prevDepthPage > page) 100 else duration - 200) else (if (prevDepthPage > page) duration - 200 else 100)
            val r = Runnable {
                magazine.depthRight.visible = true
                mainHandler.postDelayed({ magazine.moveDepth(magazine.depthRight, true, off) }, 10)
            }
            depthRightTimer = r
            mainHandler.postDelayed(r, delay)
        }
        fun hideLeft(delay: Long = 100) {
            magazine.moveDepth(magazine.depthLeft, true, 0f)
            mainHandler.postDelayed({ magazine.depthLeft.visible = false; magazine.invalidate() }, delay)
        }
        fun showLeft(off: Float, page: Int) {
            val delay = if (rtl) (if (prevDepthPage < page) 100 else duration - 200) else (if (prevDepthPage < page) duration - 200 else 100)
            val r = Runnable {
                magazine.depthLeft.visible = true
                mainHandler.postDelayed({ magazine.moveDepth(magazine.depthLeft, true, off) }, 10)
            }
            depthLeftTimer = r
            mainHandler.postDelayed(r, delay)
        }
        if (e == numPages) {
            if (rtl) { hideLeft(0); clearLeft() } else { hideRight(0); clearRight() }
        } else if (e == 1) {
            if (rtl) { hideRight(0); clearRight() } else { hideLeft(0); clearLeft() }
        }
        if (o < 1 || e == numPages || e == numPages - 1 || (a == Display.DOUBLE && e == numPages - 2)) {
            if (rtl) hideLeft() else hideRight()
        } else if (rtl) showLeft(o, e) else showRight(o, e)
        if (s < 1 || e == 1 || e == 2 || (a == Display.DOUBLE && e == 3)) {
            if (rtl) hideRight() else hideLeft()
        } else if (rtl) showRight(s, e) else showLeft(s, e)
        prevDepthPage = e
    }

    // ---------------------------------------------------------------- scoreplus.turning / turned

    private fun scoreplusTurning(page: Int?) {
        if (page != null) {
            slider.setPage(page)
            showHide(page)
            sounds?.turning(page, getVisiblePages())
        }
        listener?.onTurning(page)
    }

    private fun scoreplusTurned(page: Int) {
        zoom.turned()
        showHide(page)
        adjustResolution(0)
        updatePageNumber()
        if (design.loadPage == -1) prefs.edit().putInt("lastpage-$name", page).apply()
        listener?.onTurned(page, getVisiblePages())
    }

    private fun showHide(e: Int) {
        if (design.arrows == 3) return
        if (design.rtl == 1) {
            magazine.nextArrow.shown = e != 1
            magazine.prevArrow.shown = !isLastPage(e)
        } else {
            magazine.prevArrow.shown = e != 1
            magazine.nextArrow.shown = !isLastPage(e)
        }
        magazine.invalidate()
    }

    // ---------------------------------------------------------------- navigation

    private fun controlsGoTo(page: Int) {
        val b = book ?: return
        when (page) {
            NEXT -> b.next()
            PREVIOUS -> b.previous()
            else -> b.goTo(page)
        }
    }

    private fun startAutoPlay(seconds: Int) {
        if (seconds < 1) return
        val r = object : Runnable {
            override fun run() {
                if (isLastPage(book?.page ?: 1)) controlsGoTo(1) else controlsGoTo(NEXT)
                mainHandler.postDelayed(this, seconds * 1000L)
            }
        }
        mainHandler.postDelayed(r, seconds * 1000L)
    }

    private fun firstInteraction() {
        peelTimer?.let { mainHandler.removeCallbacks(it) }
        peelTimer = null
    }

    // ---------------------------------------------------------------- toolbar

    private fun onToolbar(id: Int) {
        firstInteraction()
        when (id) {
            ToolbarView.ID_ZOOM -> zoom.toggle()
            ToolbarView.ID_FULLSCREEN -> toggleFullscreen()
            ToolbarView.ID_SOUND -> soundEnabled = !soundEnabled
        }
    }

    private fun onZoomStep(id: Int) {
        if (id == ZoomStepView.ID_MORE) zoom.zoomCenter(1) else zoom.zoomCenter(-1)
    }

    private fun findActivity(): Activity? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    private fun toggleFullscreen() {
        val window = findActivity()?.window ?: return
        fullscreenOn = !fullscreenOn
        if (Build.VERSION.SDK_INT >= 30) {
            val c = window.insetsController ?: return
            if (fullscreenOn) {
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsets.Type.systemBars())
            } else c.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (fullscreenOn) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            } else 0
        }
        toolbar.fullscreenOn = fullscreenOn
        toolbar.invalidate()
    }

    // ---------------------------------------------------------------- progress bar (#loaderLine)

    private var progressStarted = false
    private var progressValue = 0f
    private var progressNum = 1f
    private var progressEnded = 0
    private var progressVisible = false
    private var progressShowTimer: Runnable? = null

    private fun progressBar(action: String, value: Float = 0f) {
        if (action != "start" && !progressStarted) return
        when (action) {
            "start" -> {
                progressStarted = true
                progressEnded = 0
                progressValue = 0f
                progressNum = max(1f, value)
                progressVisible = false
                progressShowTimer?.let { mainHandler.removeCallbacks(it) }
                val r = Runnable {
                    progressVisible = true
                    loaderLine.lineWidth = progressValue * width / 100f
                    loaderLine.shown = true
                }
                progressShowTimer = r
                mainHandler.postDelayed(r, 2000)
            }
            "progress" -> progressValue += value / progressNum
            "end" -> {
                progressEnded++
                if (progressEnded >= progressNum) {
                    progressStarted = false
                    progressShowTimer?.let { mainHandler.removeCallbacks(it) }
                    if (progressVisible) {
                        loaderLine.lineWidth = width.toFloat()
                        mainHandler.postDelayed({ loaderLine.shown = false }, 200)
                    }
                }
                return
            }
            "cancel" -> {
                progressStarted = false
                progressShowTimer?.let { mainHandler.removeCallbacks(it) }
                loaderLine.shown = false
            }
        }
        if (progressVisible) loaderLine.lineWidth = progressValue * width / 100f
    }

    // ---------------------------------------------------------------- touch

    private fun turnTouch(action: Int, x: Float, y: Float) {
        val b = book ?: return
        when (action) {
            MotionEvent.ACTION_DOWN -> if (!disableTurnEvents) b.touchStart(x, y) else disableFirstMove = true
            MotionEvent.ACTION_MOVE -> {
                if (!disableTurnEvents) b.touchMove(x, y)
                else if (disableFirstMove) {
                    b.endAllCorners()
                    disableFirstMove = false
                }
            }
            MotionEvent.ACTION_UP -> if (!disableTurnEvents) b.touchEnd() else disableFirstMove = true
        }
    }

    private var tapDownTime = 0L
    private var tapDownX = 0f
    private var tapDownY = 0f
    private var tapMoved = false
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var preventDoubleTap = false
    private var pinchStartDist = 0f
    private var pinchActive = false
    private var pinchLastScale = 1f
    private var multiTouch = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.getX(0)
        val y = ev.getY(0)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                firstInteraction()
                tapDownTime = ev.eventTime
                tapDownX = x
                tapDownY = y
                tapMoved = false
                multiTouch = false
                zoom.moveStart(x, y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (ev.pointerCount == 2) {
                multiTouch = true
                pinchStartDist = distance(ev)
                pinchActive = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(x - tapDownX, y - tapDownY) > 9f * density) tapMoved = true
                if (ev.pointerCount >= 2 && pinchStartDist > 0f) {
                    val scale = distance(ev) / pinchStartDist
                    if (pinchActive || scale != 1f) {
                        pinchActive = true
                        pinchLastScale = scale
                        disableTurnEvents = true
                        zoom.pinch(scale, (ev.getX(0) + ev.getX(1)) / 2, (ev.getY(0) + ev.getY(1)) / 2)
                    }
                } else if (!multiTouch) zoom.moveProgress(x, y)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (ev.pointerCount == 2 && pinchActive) {
                    pinchActive = false
                    zoom.pinchEnd(pinchLastScale)
                    if (!zoom.isZoomIn) disableTurnEvents = false
                }
                pinchStartDist = 0f
            }
            MotionEvent.ACTION_UP -> {
                sounds?.userActivated = true
                zoom.moveEnd()
                if (!multiTouch && !tapMoved && ev.eventTime - tapDownTime < 250) onTap(x, y, ev.eventTime)
            }
            MotionEvent.ACTION_CANCEL -> zoom.moveEnd()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun distance(ev: MotionEvent): Float = hypot(ev.getX(0) - ev.getX(1), ev.getY(0) - ev.getY(1))

    private fun onTap(x: Float, y: Float, time: Long) {
        val onControls = toolbar.containsPoint(x, y)
        val disableZoom = zoomStep.containsPoint(x, y) || magazine.isOnArrow(x, y)
        val isDouble = time - lastTapTime < 300 && hypot(x - lastTapX, y - lastTapY) < 10f * density
        if (isDouble) {
            if (!preventDoubleTap && !onControls && !disableZoom && design.clickZoom != 0) zoom.toggle()
            lastTapTime = 0L
        } else {
            lastTapTime = time
            lastTapX = x
            lastTapY = y
        }
        preventDoubleTap = onControls || disableZoom
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        sounds?.userActivated = true
        val rtl = design.rtl == 1
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> controlsGoTo(if (rtl) NEXT else PREVIOUS)
            KeyEvent.KEYCODE_DPAD_RIGHT -> controlsGoTo(if (rtl) PREVIOUS else NEXT)
            KeyEvent.KEYCODE_ESCAPE -> zoom.zoomReset()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }

    private companion object {
        const val NEXT = -1
        const val PREVIOUS = -2
    }
}
