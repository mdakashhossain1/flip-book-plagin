package com.scoreplus.flipbook.internal.turn

import android.graphics.Matrix
import com.scoreplus.flipbook.internal.jsRound
import com.scoreplus.flipbook.internal.rgba
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private const val A = PI
private const val S = PI / 2

internal val CORNERS_BACKWARD = listOf("bl", "tl")
internal val CORNERS_FORWARD = listOf("br", "tr")
internal val CORNERS_ALL = listOf("tl", "bl", "tr", "br", "l", "r")

/** Port of the turn.js `flip` plugin for one page. */
internal class Flip(private val book: TurnBook, var page: Int, var next: Int, hard: Boolean) {
    val effect = if (hard) Effect.HARD else Effect.SHEET
    var disabled = false
    var hover = false
    var corner: CornerPoint? = null
    var point: CornerPoint? = null
    var status = ""
    var visible = false
    var folding: Int? = null
    var foldingObj: Int? = null
    var force = false
    var time = 0L
    var zIndex: Int? = null
    var anim: Anim? = null
    val sheet = SheetFold()
    val hardFold = HardFold()

    val width: Float get() = book.pageSize(page).w
    val height: Float get() = book.pageSize(page).h

    fun cAllowed(): List<String> {
        val n = page % 2
        val ltr = book.direction == Direction.LTR
        val single = book.display == Display.SINGLE
        return if (effect == Effect.HARD) {
            if (single && page != 1 && page != book.totalPages) listOf("r", "l")
            else if (single) {
                if (page == 1) (if (ltr) listOf("r") else listOf("l"))
                else (if (ltr) listOf("l") else listOf("r"))
            } else if (ltr) listOf(if (n != 0) "r" else "l") else listOf(if (n != 0) "l" else "r")
        } else if (single) {
            if (page == 1) (if (ltr) CORNERS_FORWARD else CORNERS_BACKWARD)
            else if (page == book.totalPages) (if (ltr) CORNERS_BACKWARD else CORNERS_FORWARD)
            else CORNERS_ALL
        } else if (ltr) (if (n != 0) CORNERS_FORWARD else CORNERS_BACKWARD)
        else (if (n != 0) CORNERS_BACKWARD else CORNERS_FORWARD)
    }

    fun cornerActivated(x: Float, y: Float, isHovering: Boolean): CornerPoint? {
        val n = width
        val a = height
        val r = book.cornerSize
        if (x <= 0 || y <= 0 || x >= n || y >= a) return null
        val allowed = cAllowed()
        val s = CornerPoint(x, y, "")
        when (effect) {
            Effect.HARD -> {
                if (isHovering && !(y < a / 6 || y >= a - a / 6)) return null
                s.corner = if (x > n - r) "r" else if (x < r) "l" else return null
            }
            Effect.SHEET -> {
                var top = a / 2
                var bottom = a / 2
                if (isHovering) {
                    top = a / 6
                    bottom = a - a / 6
                }
                s.corner = if (y < top) "t" else if (y >= bottom) "b" else return null
                s.corner += if (x <= r) "l" else if (x >= n - r) "r" else return null
            }
        }
        return if (s.corner.isNotEmpty() && s.corner in allowed) s else null
    }

    fun isIArea(pageX: Float, pageY: Float, isHovering: Boolean): CornerPoint? {
        val wrap = book.pageWrap[page] ?: return null
        return cornerActivated(pageX - wrap.x, pageY - wrap.y, isHovering)
    }

    fun c(corner: String, t: Float = 0f): CornerPoint = when (corner) {
        "tl" -> CornerPoint(t, t)
        "tr" -> CornerPoint(width - t, t)
        "bl" -> CornerPoint(t, height - t)
        "br" -> CornerPoint(width - t, height - t)
        "l" -> CornerPoint(t, 0f)
        else -> CornerPoint(width - t, 0f)
    }

    private fun c2(corner: String): CornerPoint = when (corner) {
        "tl", "l" -> CornerPoint(2 * width, 0f)
        "tr", "r" -> CornerPoint(-width, 0f)
        "bl" -> CornerPoint(2 * width, height)
        else -> CornerPoint(-width, height)
    }

    /** Page number of the object shown on the back of the fold; 0 = blank temporal page. */
    fun foldingPage(): Int? =
        if (book.display == Display.SINGLE) {
            if ((next > 1 || page > 1) && book.pageObjs.containsKey(0)) 0 else null
        } else if (book.pageObjs.containsKey(next)) next else null

    fun backGradient(): Boolean =
        book.gradients && (book.display == Display.SINGLE ||
            (page != 2 && page != book.totalPages - 1))

    private fun fold(e: CornerPoint) {
        if (effect == Effect.HARD) foldHard(e) else foldSheet(e)
        point = e
    }

    private fun foldHard(e: CornerPoint) {
        var l = width.toDouble()
        val o = c(e.corner)
        if (e.corner == "l") e.x = min(max(e.x.toDouble(), 0.0), 2 * l).toFloat()
        else e.x = max(min(e.x.toDouble(), l), -l).toFloat()
        val m = book.totalPages
        var b = if (o.x != 0f) (o.x - e.x) / l else e.x / l
        var angle = 90 * b
        val x = angle < 90
        val f = hardFold
        val c: Double
        val p: Boolean
        val u: Int
        if (e.corner == "l") {
            f.originX = width
            f.flapOriginX = 0f
            if (x) {
                c = 0.0; p = next - 1 > 0; u = 1
            } else {
                c = width.toDouble(); p = page + 1 < m; u = 0
            }
        } else {
            f.originX = 0f
            f.flapOriginX = width
            angle = -angle
            l = -l
            if (x) {
                c = 0.0; p = next + 1 < m; u = 0
            } else {
                c = -width.toDouble(); p = page != 1; u = 1
            }
        }
        f.angle = angle.toFloat()
        f.flapTranslateX = l.toFloat()
        if (x) {
            b = 1 - b
            f.wrapperOnTop = true
        } else {
            b -= 1
            f.wrapperOnTop = false
        }
        if (book.gradients) {
            f.frontShadowVisible = p
            f.frontShadowLeft = c.toFloat()
            f.frontShadowAlpha = (0.5 * b).toFloat()
            f.backShadowOpacity = (1 - b).toFloat()
            f.backShadowOnWrapper = x
            val g = f.backShadow
            g.active = true
            g.x0 = width * u
            g.y0 = 0f
            g.x1 = width * (1 - u)
            g.y1 = 0f
            g.positions[0] = 0f; g.colors[0] = rgba(0, 0, 0, 0.3)
            g.positions[1] = 1f; g.colors[1] = rgba(0, 0, 0, 0.0)
            g.positions[2] = 1f; g.colors[2] = rgba(0, 0, 0, 0.0)
        }
    }

    private fun foldSheet(e: CornerPoint) {
        val l = width.toDouble()
        val d = height.toDouble()
        val o = c(e.corner)
        val wSize = jsRound(sqrt(l.pow(2) + d.pow(2)))
        val nTop = e.corner[0] == 't'
        val xLeft = e.corner[1] == 'l'
        var angleDeg = 0.0
        var lx = 0.0
        var ly = 0.0
        var fx = 0.0
        var fy = 0.0
        var hx = 0.0
        var hy = 0.0
        var cx = 0.0
        var cy = 0.0
        var ax = 0.0
        var ay = 0.0
        var dollar = 0.0
        var k = 0.0
        var oo = 0.0

        when (e.corner) {
            "tl", "bl" -> e.x = max(e.x, 1f)
            "tr", "br" -> e.x = min(e.x, width - 1)
            else -> return
        }

        while (true) {
            val tx = if (o.x != 0f) (o.x - e.x).toDouble() else e.x.toDouble()
            val ty = if (o.y != 0f) (o.y - e.y).toDouble() else e.y.toDouble()
            val nx = if (xLeft) l - tx / 2 else e.x + tx / 2
            val ny = ty / 2
            val cAng = S - atan2(ty, tx)
            val pAng = cAng - atan2(ny, nx)
            val u = max(0.0, sin(pAng) * sqrt(nx.pow(2) + ny.pow(2)))
            angleDeg = cAng / A * 180
            hx = u * sin(cAng)
            hy = u * cos(cAng)
            if (cAng > S) {
                hx += abs(hy * ty / tx)
                hy = 0.0
                if (jsRound(hx * tan(A - cAng)) < d) {
                    var ey = sqrt(d.pow(2) + 2 * nx * tx)
                    if (nTop) ey = d - ey
                    e.y = ey.toFloat()
                    continue
                }
            }
            if (cAng > S) {
                val h = A - cAng
                val f = wSize - d / sin(h)
                lx = jsRound(f * cos(h))
                ly = jsRound(f * sin(h))
                if (xLeft) lx = -lx
                if (nTop) ly = -ly
            }
            val mm = jsRound(hy / tan(cAng) + hx)
            val m = l - mm
            val g = m * cos(2 * cAng)
            val v = m * sin(2 * cAng)
            fx = jsRound(if (xLeft) m - g else mm + g)
            fy = jsRound(if (nTop) v else d - v)
            if (book.gradients) {
                val z = m * sin(cAng)
                val w2 = c2(e.corner)
                val b = sqrt((w2.x - e.x).toDouble().pow(2) + (w2.y - e.y).toDouble().pow(2)) / l
                oo = sin(S * (if (b > 1) 2 - b else b))
                k = min(b, 1.0)
                dollar = if (z > book.px(100f)) (z - book.px(100f)) / z else 0.0
                cx = z * sin(cAng) / l * 100
                cy = z * cos(cAng) / d * 100
                if (backGradient()) {
                    ax = 1.2 * z * sin(cAng) / l * 100
                    ay = 1.2 * z * cos(cAng) / d * 100
                    if (!xLeft) ax = 100 - ax
                    if (!nTop) ay = 100 - ay
                }
            }
            hx = jsRound(hx)
            hy = jsRound(hy)
            break
        }

        val i: IntArray
        val n: IntArray
        val ex: Double
        val ey: Double
        val a: Double
        when (e.corner) {
            "tl" -> { ex = hx; ey = hy; i = intArrayOf(1, 0, 0, 1); n = intArrayOf(100, 0); a = angleDeg }
            "tr" -> { ex = -hx; ey = hy; i = intArrayOf(0, 0, 0, 1); n = intArrayOf(0, 0); a = -angleDeg }
            "bl" -> { ex = hx; ey = -hy; i = intArrayOf(1, 1, 0, 0); n = intArrayOf(100, 100); a = -angleDeg }
            else -> { ex = -hx; ey = -hy; i = intArrayOf(0, 1, 1, 0); n = intArrayOf(0, 100); a = angleDeg }
        }

        val fold = sheet
        fold.wrapperSize = wSize.toFloat()
        val oOff = (l - wSize) * n[0] / 100
        val cOff = (d - wSize) * n[1] / 100
        val h = if (a != 90.0 && a != -90.0) (if (xLeft) -1.0 else 1.0) else 0.0
        val posX = if (i[0] == 0) 0.0 else wSize - l
        val posY = if (i[1] == 0) 0.0 else wSize - d
        val pageOx = l * n[0] / 100
        val pageOy = d * n[1] / 100
        val wrapOx = wSize * n[0] / 100
        val wrapOy = wSize * n[1] / 100

        cssMatrix(fold.pageMatrix, posX, posY, pageOx, pageOy) {
            preRotate(a.toFloat())
            preTranslate((ex + h).toFloat(), ey.toFloat())
        }
        cssMatrix(fold.wrapperMatrix, 0.0, 0.0, wrapOx, wrapOy) {
            preTranslate((-ex + oOff - h).toFloat(), (-ey + cOff).toFloat())
            preRotate(-a.toFloat())
        }
        val wrap = book.pageWrap[page]
        val wrapX = wrap?.x ?: 0f
        val wrapY = wrap?.y ?: 0f
        cssMatrix(fold.fwrapperMatrix, wrapX.toDouble(), wrapY.toDouble(), wrapOx, wrapOy) {
            preTranslate((-ex + lx + oOff).toFloat(), (-ey + ly + cOff).toFloat())
            preRotate(-a.toFloat())
        }
        cssMatrix(fold.fpageMatrix, posX, posY, pageOx, pageOy) {
            preRotate(a.toFloat())
            preTranslate(
                (ex + fx - lx - l * n[0] / 100).toFloat(),
                (ey + fy - ly - d * n[1] / 100).toFloat(),
            )
            preRotate((180 - 2 * a).toFloat())
        }

        if (book.gradients) {
            if (n[0] != 0) cx = 100 - cx
            if (n[1] != 0) cy = 100 - cy
            fold.flapShadowAlpha = (0.5 * oo).toFloat()
            fold.frontGradient.set(
                l, d,
                if (xLeft) 100.0 else 0.0, if (nTop) 0.0 else 100.0, cx, cy,
                floatArrayOf(dollar.toFloat(), (0.8 * (1 - dollar) + dollar).toFloat(), 1f),
                intArrayOf(rgba(0, 0, 0, 0.0), rgba(0, 0, 0, 0.2 * k), rgba(255, 255, 255, 0.2 * k)),
            )
            if (backGradient()) {
                fold.backGradient.set(
                    l, d,
                    if (xLeft) 0.0 else 100.0, if (nTop) 0.0 else 100.0, ax, ay,
                    floatArrayOf(0.6f, 0.8f, 1f),
                    intArrayOf(rgba(0, 0, 0, 0.0), rgba(0, 0, 0, 0.3 * k), rgba(0, 0, 0, 0.0)),
                )
            } else fold.backGradient.active = false
        } else {
            fold.flapShadowAlpha = 0f
            fold.frontGradient.active = false
            fold.backGradient.active = false
        }
    }

    private inline fun cssMatrix(
        out: Matrix,
        left: Double,
        top: Double,
        originX: Double,
        originY: Double,
        transforms: Matrix.() -> Unit,
    ) {
        out.reset()
        out.preTranslate((left + originX).toFloat(), (top + originY).toFloat())
        out.transforms()
        out.preTranslate(-originX.toFloat(), -originY.toFloat())
    }

    private fun GradientSpec.set(
        w: Double, h: Double,
        fromXPct: Double, fromYPct: Double, toXPct: Double, toYPct: Double,
        stops: FloatArray, stopColors: IntArray,
    ) {
        active = true
        x0 = (fromXPct / 100 * w).toFloat()
        y0 = (fromYPct / 100 * h).toFloat()
        x1 = (toXPct / 100 * w).toFloat()
        y1 = (toYPct / 100 * h).toFloat()
        for (j in 0 until 3) {
            positions[j] = stops[j]
            colors[j] = stopColors[j]
        }
    }

    fun moveFoldingPage(move: Boolean) {
        if (move) {
            val s = next
            if (book.pagePlace[s] != page) {
                if (folding != null) moveFoldingPage(false)
                val r = foldingPage()
                if (r != null) foldingObj = r
                book.pagePlace[s] = page
                folding = s
            }
            book.update()
        } else if (folding != null) {
            val f = folding!!
            foldingObj = null
            if (book.pagePlace.containsKey(f)) book.pagePlace[f] = f
            folding = null
        }
    }

    fun showFoldedPage(e: CornerPoint, animate: Boolean): Boolean {
        if (foldingPage() == null) return false
        var s = visible
        if (!s || point == null || point!!.corner != e.corner) {
            val r = if (status == "hover" || status == "peel" || book.mouseAction) e.corner else null
            s = false
            if (book.triggerStart(this, r)) return false
        }
        if (animate) {
            val l = point?.takeIf { it.corner == e.corner } ?: c(e.corner, 1f)
            animatef(
                floatArrayOf(l.x, l.y), floatArrayOf(e.x, e.y), 500,
                frame = { v ->
                    e.x = jsRound(v[0])
                    e.y = jsRound(v[1])
                    fold(e)
                    book.requestRender()
                },
            )
        } else {
            fold(e)
            if (anim != null && !anim!!.turning) stopAnim()
        }
        if (!s) {
            visible = true
            moveFoldingPage(true)
        }
        book.requestRender()
        return true
    }

    fun hide() {
        visible = false
        book.requestRender()
    }

    fun hideFoldedPage(animate: Boolean) {
        val n = point ?: return
        val done = {
            point = null
            status = ""
            hide()
            book.triggerEnd(this, false)
        }
        if (animate) {
            val s = c(n.corner)
            val r = if (n.corner[0] == 't') min(0f, n.y - s.y) / 2 else max(0f, n.y - s.y) / 2
            val o = CornerPoint(n.x, n.y + r)
            val l = CornerPoint(s.x, s.y - r)
            animatef(
                floatArrayOf(0f), floatArrayOf(1f), 800, hiding = true,
                frame = { v ->
                    val t = bezier(n, o, l, s, v[0].toDouble())
                    n.x = t.x
                    n.y = t.y
                    fold(n)
                    book.requestRender()
                },
                complete = done,
            )
        } else {
            stopAnim()
            done()
        }
    }

    fun turnPage(cornerArg: String?) {
        val e = CornerPoint(0f, 0f, corner?.corner ?: cornerArg ?: cAllowed()[0])
        val a = point ?: c(e.corner, book.elevation)
        val s = c2(e.corner)
        book.triggerFlip(this)
        animatef(
            floatArrayOf(0f), floatArrayOf(1f), book.duration, turning = true,
            frame = { v ->
                val n = bezier(a, a, s, s, v[0].toDouble())
                e.x = n.x
                e.y = n.y
                showFoldedPage(e, false)
            },
            complete = { book.triggerEnd(this, true) },
        )
        corner = null
    }

    fun moving(): Boolean = anim != null

    fun isTurning(): Boolean = anim?.turning == true

    fun eventStart(pageX: Float, pageY: Float): Boolean {
        if (book.display == Display.SINGLE) {
            if (corner == null && !disabled) {
                val pt = point
                if (isTurning() && pt != null) {
                    pt.fit = true
                    if (!book.triggerReleased(this, pt)) hideFoldedPage(false)
                    return eventStart(pageX, pageY)
                }
                corner = isIArea(pageX, pageY, false)
                if (corner != null && foldingPage() != null) {
                    book.triggerPressed(this)
                    showFoldedPage(corner!!, false)
                    return false
                }
                corner = null
            }
        } else if (corner == null && !disabled && !isTurning() && book.pagePlace[page] == page) {
            corner = isIArea(pageX, pageY, false)
            if (corner != null && foldingPage() != null) {
                book.triggerPressed(this)
                showFoldedPage(corner!!, false)
                return false
            }
            corner = null
        }
        return true
    }

    fun eventMove(pageX: Float, pageY: Float) {
        if (disabled) return
        val cp = corner
        if (cp != null) {
            val wrap = book.pageWrap[page] ?: return
            cp.x = pageX - wrap.x
            cp.y = pageY - wrap.y
            showFoldedPage(cp, false)
        } else if (hover && anim == null && book.pageWrap[page]?.visible == true) {
            val n = isIArea(pageX, pageY, true)
            if (n != null) {
                if ((effect == Effect.SHEET && n.corner.length == 2) || effect == Effect.HARD) {
                    status = "hover"
                    val a = c(n.corner, book.cornerSize / 2)
                    n.x = a.x
                    n.y = a.y
                    showFoldedPage(n, true)
                }
            } else if (status == "hover") {
                status = ""
                hideFoldedPage(true)
            }
        }
    }

    fun eventEnd() {
        val t = corner
        if (!disabled && t != null && !book.triggerReleased(this, point ?: t)) hideFoldedPage(true)
        corner = null
    }

    fun peel(cornerName: String?, animate: Boolean) {
        if (cornerName != null) {
            if (cornerName in cAllowed()) {
                val a = c(cornerName, book.cornerSize / 2)
                status = "peel"
                showFoldedPage(CornerPoint(a.x, a.y, cornerName), animate)
            }
        } else {
            status = ""
            hideFoldedPage(animate)
        }
    }

    private fun animatef(
        from: FloatArray,
        to: FloatArray,
        duration: Long,
        turning: Boolean = false,
        hiding: Boolean = false,
        frame: (FloatArray) -> Unit,
        complete: (() -> Unit)? = null,
    ) {
        anim?.stop()
        val a = Anim(book, from, to, duration, turning, hiding, frame) {
            anim = null
            complete?.invoke()
        }
        anim = a
        a.start()
    }

    fun stopAnim() {
        anim?.stop()
        anim = null
    }

    companion object {
        fun bezier(p0: CornerPoint, p1: CornerPoint, p2: CornerPoint, p3: CornerPoint, a: Double): CornerPoint {
            val s = 1 - a
            val r = s * s * s
            val o = a * a * a
            return CornerPoint(
                jsRound(r * p0.x + 3 * a * s * s * p1.x + 3 * a * a * s * p2.x + o * p3.x).toFloat(),
                jsRound(r * p0.y + 3 * a * s * s * p1.y + 3 * a * a * s * p2.y + o * p3.y).toFloat(),
            )
        }
    }
}
