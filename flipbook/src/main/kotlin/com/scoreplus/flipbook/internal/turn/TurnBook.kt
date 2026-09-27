package com.scoreplus.flipbook.internal.turn

import com.scoreplus.flipbook.internal.Easing
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal interface TurnHost {
    fun now(): Long
    fun postFrame(action: () -> Unit)
    fun requestRender()

    /** magazine `when.start`; returns true when the turn must be prevented. */
    fun onStart(corner: String?): Boolean
    fun onPeelStart()
    fun onPeelEnd()

    /** magazine `when.turning`; returns true when the turn must be prevented. */
    fun onTurning(page: Int, view: IntArray): Boolean
    fun onTurned(page: Int, view: IntArray)
    fun onMissing(pages: List<Int>)
    fun onCenter(marginLeft: Float)
}

internal class Anim(
    private val book: TurnBook,
    private val from: FloatArray,
    to: FloatArray,
    private val duration: Long,
    val turning: Boolean,
    val hiding: Boolean,
    private val frame: (FloatArray) -> Unit,
    private val onDone: () -> Unit,
) {
    private val delta = FloatArray(to.size) { to[it] - from[it] }
    private var running = true
    private var start = 0L

    fun start() {
        start = book.host.now()
        step()
    }

    fun stop() {
        running = false
    }

    private fun step() {
        if (!running) return
        val d = min(duration, book.host.now() - start)
        val values = FloatArray(from.size) {
            Easing.turnJs(d.toDouble(), from[it].toDouble(), delta[it].toDouble(), duration.toDouble()).toFloat()
        }
        frame(values)
        if (d == duration) {
            running = false
            onDone()
        } else book.host.postFrame { step() }
    }
}

internal class PageSize(val w: Float, val h: Float, val x: Float, val y: Float)

/** Port of the turn.js 4.1 `turn` plugin (display, paging, z-order and events). */
internal class TurnBook(
    val host: TurnHost,
    private val density: Float,
    val duration: Long,
    val gradients: Boolean,
) {
    val elevation = 200f * density
    val cornerSize = 60f * density
    val autoCenter = true

    var width = 0f
        private set
    var height = 0f
        private set
    var display = Display.DOUBLE
        private set
    var direction = Direction.LTR
        private set
    var totalPages = 0
        private set
    var page = 0
        private set
    var tpage = 0
        private set
    var done = false
        private set
    var mouseAction = false
    var disabled = false
        private set

    val pageMv = ArrayList<Int>()
    val pageObjs = HashMap<Int, PageObj>()
    val pageWrap = TreeMap<Int, PageWrap>()
    val pages = TreeMap<Int, Flip>()
    val pagePlace = HashMap<Int, Int>()
    val shadow = ShadowBox()

    /** True in single display: the magazine element itself carries the `.shadow` class. */
    var magazineHasShadowClass = false
        private set

    private var displayInitialized = false
    private var wrapOrder = 0

    fun px(css: Float): Double = (css * density).toDouble()

    fun requestRender() = host.requestRender()

    fun init(w: Float, h: Float, numPages: Int, startDisplay: Display, startDirection: Direction) {
        width = w
        height = h
        totalPages = numPages
        setDisplay(startDisplay)
        setDirection(startDirection)
        goTo(1)
        done = true
    }

    fun pageSize(e: Int): PageSize =
        if (display == Display.SINGLE) PageSize(width, height, 0f, 0f)
        else {
            val a = width / 2
            val odd = e % 2 != 0
            val x = if (direction == Direction.LTR) (if (odd) a else 0f) else (if (odd) 0f else a)
            PageSize(a, height, x, 0f)
        }

    private fun rawView(e0: Int = 0): IntArray {
        val e = if (e0 != 0) e0 else page
        return if (display == Display.DOUBLE) {
            if (e % 2 != 0) intArrayOf(e - 1, e) else intArrayOf(e, e + 1)
        } else intArrayOf(e)
    }

    fun view(e: Int = 0): IntArray {
        val i = rawView(e)
        return if (display == Display.DOUBLE) {
            intArrayOf(if (i[0] > 0) i[0] else 0, if (i[1] <= totalPages) i[1] else 0)
        } else intArrayOf(if (i[0] > 0 && i[0] <= totalPages) i[0] else 0)
    }

    fun range(e0: Int = 0): IntArray {
        val e = when {
            e0 != 0 -> e0
            tpage != 0 -> tpage
            page != 0 -> page
            else -> 1
        }
        val a = rawView(e)
        val a0 = a[0]
        val a1 = if (a.size > 1 && a[1] != 0) a[1] else a0
        val i: Int
        val n: Int
        if (a0 >= 1 && a1 <= totalPages) {
            val t = floor(2.0).toInt()
            if (totalPages - a1 > a0) {
                i = min(a0 - 1, t)
                n = 2 * t - i
            } else {
                n = min(totalPages - a1, t)
                i = 2 * t - n
            }
        } else {
            i = 5
            n = 5
        }
        return intArrayOf(max(1, a0 - i), min(totalPages, a1 + n))
    }

    private fun necessPage(e: Int): Boolean {
        if (e == 0) return true
        val t = range()
        return e >= t[0] && e <= t[1]
    }

    fun addPage(n: Int, hard: Boolean) {
        val o = totalPages + 1
        if (n < 1 || n > o) return
        if (done) stop()
        if (n == o) totalPages = o
        pageObjs[n] = PageObj(n).also {
            it.parity = if (display == Display.DOUBLE) (if (n % 2 != 0) 1 else 0) else -1
            it.hard = hard
        }
        addPageInternal(n)
        removeFromDOM()
    }

    private fun addPageInternal(t: Int) {
        if (!pageObjs.containsKey(t)) return
        if (necessPage(t)) {
            if (pageWrap[t] == null) {
                val wrap = PageWrap(t, wrapOrder++)
                pageWrap[t] = wrap
                if ((pagePlace[t] ?: 0) == 0) pagePlace[t] = t
                applyWrapSize(t, wrap)
            }
            if (pagePlace[t] == t) makeFlip(t)
        } else {
            pagePlace[t] = 0
        }
    }

    private fun applyWrapSize(t: Int, wrap: PageWrap) {
        val s = pageSize(t)
        wrap.x = s.x
        wrap.y = s.y
        wrap.w = s.w
        wrap.h = s.h
    }

    private fun makeFlip(e: Int) {
        if (pages[e] == null && pagePlace[e] == e) {
            val single = display == Display.SINGLE
            val odd = e % 2 != 0
            val flip = Flip(this, e, if (odd || single) e + 1 else e - 1, pageObjs[e]?.hard == true)
            flip.disabled = disabled
            pages[e] = flip
            setPageLoc(e)
        }
    }

    private fun makeRange() {
        if (totalPages < 1) return
        val t = range()
        for (e in t[0]..t[1]) addPageInternal(e)
    }

    private fun removeFromDOM() {
        for (e in pageWrap.keys.toList()) if (!necessPage(e)) removePageFromDOM(e)
    }

    private fun removePageFromDOM(e: Int) {
        pages[e]?.let {
            it.moveFoldingPage(false)
            it.stopAnim()
            pages.remove(e)
        }
        pageWrap.remove(e)
        removeMv(e)
        pagePlace.remove(e)
    }

    fun center(t: Int = 0) {
        var a = 0f
        if (display == Display.DOUBLE) {
            val s = view(if (t != 0) t else if (tpage != 0) tpage else page)
            if (direction == Direction.LTR) {
                if (s[0] != 0) {
                    if (s[1] == 0) a += width / 4
                } else a -= width / 4
            } else {
                if (s[0] != 0) {
                    if (s[1] == 0) a -= width / 4
                } else a += width / 4
            }
        }
        host.onCenter(a)
    }

    fun setSize(w: Float, h: Float) {
        stop()
        width = w
        height = h
        for ((i, wrap) in pageWrap) applyWrapSize(i, wrap)
        resize()
    }

    private fun resize() {
        updateShadow()
        if (autoCenter) center()
    }

    fun setDisplay(t: Display) {
        val wasSet = displayInitialized
        when (t) {
            Display.SINGLE -> {
                if (!pageObjs.containsKey(0)) {
                    stop()
                    pageObjs[0] = PageObj(0)
                }
                magazineHasShadowClass = true
            }
            Display.DOUBLE -> {
                if (pageObjs.containsKey(0)) {
                    stop()
                    pageObjs.remove(0)
                }
                magazineHasShadowClass = false
            }
        }
        display = t
        displayInitialized = true
        if (wasSet) {
            movePages(1)
            setSize(width, height)
            update()
        }
    }

    fun setDirection(t: Direction) {
        direction = t
        if (done) setSize(width, height)
    }

    private fun movePages(e: Int) {
        val single = display == Display.SINGLE
        for (i in e..totalPages) {
            val obj = pageObjs[i] ?: continue
            val odd = i % 2 != 0
            obj.parity = if (odd) 1 else 0
            val wrap = pageWrap[i]
            if ((pagePlace[i] ?: 0) != 0 && wrap != null) {
                pagePlace[i] = i
                applyWrapSize(i, wrap)
                pages[i]?.next = if (single || odd) i + 1 else i - 1
            }
        }
    }

    fun animating(): Boolean = pageMv.isNotEmpty()

    private fun anyCorner(): Boolean = pages.values.any { it.corner != null }

    fun setDisabled(value: Boolean) {
        disabled = value
        val a = view()
        for ((i, flip) in pages) flip.disabled = disabled || i !in a
    }

    fun stop(except: Int? = null, animate: Boolean = false) {
        if (animating()) {
            if (tpage != 0) {
                page = tpage
                tpage = 0
            }
            var i = 0
            while (i < pageMv.size) {
                val mv = pageMv[i]
                if (mv != 0 && mv != except) {
                    val a = pages[mv]
                    if (a != null) {
                        a.hideFoldedPage(animate)
                        if (!animate) a.moveFoldingPage(false)
                        if (a.force) {
                            a.next = if (a.page % 2 == 0) a.page - 1 else a.page + 1
                            a.force = false
                        }
                    }
                }
                i++
            }
        }
        update()
    }

    private fun missing(e: Int) {
        if (totalPages < 1) return
        val n = range(e)
        val a = ArrayList<Int>()
        for (i in n[0]..n[1]) if (!pageObjs.containsKey(i)) a.add(i)
        if (a.isNotEmpty()) host.onMissing(a)
    }

    private fun fitPage(e: Int) {
        val v = view(e)
        missing(e)
        if (pageObjs.containsKey(e)) {
            page = e
            stop()
            removeFromDOM()
            makeRange()
            updateShadow()
            host.onTurned(e, v)
            update()
            if (autoCenter) center()
        }
    }

    private fun turnPage(t: Int) {
        val s = pagePlace[t]
        val r = view()
        val o = view(t)
        if (page != t) {
            val l = page
            if (host.onTurning(t, o)) {
                if (l == page && s != null && s in pageMv) pages[s]?.hideFoldedPage(true)
                return
            }
        }
        val i: Int
        val n: Int
        if (display == Display.SINGLE) {
            i = r[0]
            n = o[0]
        } else if (r[1] != 0 && t > r[1]) {
            i = r[1]
            n = o[0]
        } else if (r[0] != 0 && t < r[0]) {
            i = r[0]
            n = o[1]
        } else return
        val c = pages[i] ?: return
        val u = c.point
        missing(t)
        if (pageObjs.containsKey(t)) {
            stop()
            page = t
            makeRange()
            tpage = n
            if (c.next != n) {
                c.next = n
                c.force = true
            }
            update()
            c.point = u
            val forward = t > i
            if (c.effect == Effect.HARD) {
                c.turnPage(if (direction == Direction.LTR) (if (forward) "r" else "l") else (if (forward) "l" else "r"))
            } else {
                c.turnPage(if (direction == Direction.LTR) (if (forward) "br" else "bl") else (if (forward) "bl" else "br"))
            }
        }
    }

    fun goTo(t: Int) {
        if (disabled) return
        if (t in 1..totalPages && t != page) {
            if (done && t !in view()) turnPage(t) else fitPage(t)
        }
    }

    fun next() {
        goTo(min(totalPages, rawView(page).last() + 1))
    }

    fun previous() {
        goTo(max(1, rawView(page).first() - 1))
    }

    fun peel(corner: String?, animate: Boolean = true) {
        val a = view()
        if (display == Display.SINGLE) {
            pages[page]?.peel(corner?.ifEmpty { null }, animate)
        } else {
            val c = corner ?: ""
            val hasL = c.contains('l')
            val i = if (direction == Direction.LTR) (if (hasL) a[0] else a[1]) else (if (hasL) a[1] else a[0])
            pages[i]?.peel(corner?.ifEmpty { null }, animate)
        }
    }

    fun touchStart(x: Float, y: Float): Boolean {
        for (flip in pages.values.toList()) if (!flip.eventStart(x, y)) return true
        return false
    }

    fun touchMove(x: Float, y: Float) {
        for (flip in pages.values.toList()) flip.eventMove(x, y)
    }

    fun touchEnd() {
        for (flip in pages.values.toList()) flip.eventEnd()
    }

    fun endAllCorners() {
        for (flip in pages.values.toList()) flip.eventEnd()
    }

    fun triggerStart(flip: Flip, corner: String?): Boolean {
        val prevented = host.onStart(corner)
        host.onPeelStart()
        if (!prevented) {
            if (display == Display.SINGLE && corner != null) {
                val left = corner.getOrNull(1) == 'l' || corner.getOrNull(0) == 'l'
                val right = corner.getOrNull(1) == 'r' || corner.getOrNull(0) == 'r'
                if ((left && direction == Direction.LTR) || (right && direction == Direction.RTL)) {
                    flip.next = if (flip.next < flip.page) flip.next else flip.page - 1
                    flip.force = true
                } else {
                    flip.next = if (flip.next > flip.page) flip.next else flip.page + 1
                }
            }
            addMv(flip.page)
        }
        updateShadow()
        return prevented
    }

    fun triggerPressed(flip: Flip) {
        mouseAction = true
        update()
        flip.time = host.now()
    }

    fun triggerReleased(flip: Flip, p: CornerPoint): Boolean {
        val w = flip.width
        val n = if (display == Display.SINGLE) {
            if (p.corner == "br" || p.corner == "tr" || p.corner == "r") p.x < w / 2 else p.x > w / 2
        } else p.x < 0 || p.x > w
        var prevented = false
        if (host.now() - flip.time < 200 || n) {
            prevented = true
            if (p.fit) fitPage(flip.next) else turnPage(flip.next)
        }
        mouseAction = false
        return prevented
    }

    fun triggerFlip(flip: Flip) {
        if (autoCenter) center(flip.next)
    }

    fun triggerEnd(flip: Flip, turned: Boolean) {
        host.onPeelEnd()
        if (turned) {
            val r = if (tpage != 0) tpage else page
            if (r == flip.next || r == flip.page) {
                tpage = 0
                fitPage(if (r != 0) r else flip.next)
            }
        } else {
            removeMv(flip.page)
            updateShadow()
            update()
        }
    }

    private fun removeMv(e: Int): Boolean = pageMv.remove(e)

    private fun addMv(e: Int) {
        removeMv(e)
        pageMv.add(e)
    }

    private class ZInfo(val pageZ: Map<Int, Int>, val partZ: Map<Int, Int>, val pageV: Set<Int>)

    private fun calculateZ(e: List<Int>): ZInfo {
        val l = view()
        val d = if (l[0] != 0) l[0] else l.getOrElse(1) { 0 }
        val c = e.size - 1
        val pageZ = HashMap<Int, Int>()
        val partZ = HashMap<Int, Int>()
        val pageV = HashSet<Int>()
        fun u(p: Int) {
            val v = view(p)
            if (v[0] != 0) pageV.add(v[0])
            if (v.size > 1 && v[1] != 0) pageV.add(v[1])
        }
        for (t in 0..c) {
            val i = e[t]
            val flip = pages[i] ?: continue
            val n = flip.next
            val a = pagePlace[i] ?: i
            u(i)
            u(n)
            val s = if (pagePlace[n] == n) n else i
            pageZ[s] = totalPages - abs(d - s)
            partZ[a] = 2 * totalPages - c + t
        }
        return ZInfo(pageZ, partZ, pageV)
    }

    fun update() {
        if (animating() && pageMv[0] != 0) {
            val s = calculateZ(pageMv)
            val r = anyCorner()
            val o = view()
            val l = view(tpage)
            for ((t, wrap) in pageWrap) {
                wrap.visible = t in s.pageV
                wrap.z = (if (pageObjs[t]?.hard == true) s.partZ[t] else s.pageZ[t]) ?: 0
                pages[t]?.let { n ->
                    n.zIndex = s.partZ[t]
                    if (tpage != 0) {
                        n.hover = false
                        n.disabled = t !in pageMv && t != l[0] && !(l.size > 1 && t == l[1])
                    } else {
                        n.hover = !r
                        n.disabled = t != o[0] && !(o.size > 1 && t == o[1])
                    }
                }
            }
        } else {
            for (t in pageWrap.keys) {
                val d = setPageLoc(t)
                pages[t]?.let {
                    it.disabled = disabled || d != 1
                    it.hover = true
                    it.zIndex = null
                }
            }
        }
        host.requestRender()
    }

    private fun setPageLoc(e: Int): Int {
        val i = view()
        val hasSecond = i.size > 1
        var n = 0
        if (e == i[0] || (hasSecond && e == i[1])) n = 1
        else if ((display == Display.SINGLE && e == i[0] + 1) ||
            (display == Display.DOUBLE && e == i[0] - 2) ||
            (hasSecond && e == i[1] + 2)
        ) n = 2
        if (!animating()) {
            val wrap = pageWrap[e] ?: return n
            when (n) {
                1 -> { wrap.z = totalPages; wrap.visible = true }
                2 -> { wrap.z = totalPages - 1; wrap.visible = true }
                else -> { wrap.z = 0; wrap.visible = false }
            }
        }
        return n
    }

    private fun updateShadow() {
        val s = width
        val r = height
        val o = if (display == Display.SINGLE) s else s / 2
        var t = view()
        var l = 0
        while (l < pageMv.size && t[0] != 0 && t.size > 1 && t[1] != 0) {
            val flip = pages[pageMv[l]]
            if (flip != null) {
                val nt = view(flip.next)
                val i = view(pageMv[l])
                t = intArrayOf(
                    if (nt[0] != 0) i[0] else nt[0],
                    if (nt.size > 1 && nt[1] != 0) i.getOrElse(1) { 0 } else nt.getOrElse(1) { 0 },
                )
            }
            l++
        }
        val ltr = direction == Direction.LTR
        val n = if (t[0] != 0) {
            if (t.size > 1 && t[1] != 0) 3 else if (ltr) 2 else 1
        } else if (ltr) 1 else 2
        shadow.case = n
        shadow.y = 0f
        shadow.h = r
        when (n) {
            1 -> { shadow.w = o; shadow.x = o }
            2 -> { shadow.w = o; shadow.x = 0f }
            else -> { shadow.w = s; shadow.x = 0f }
        }
    }
}
