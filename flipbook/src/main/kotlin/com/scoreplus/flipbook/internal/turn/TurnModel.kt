package com.scoreplus.flipbook.internal.turn

import android.graphics.Matrix

internal enum class Display { SINGLE, DOUBLE }

internal enum class Direction { LTR, RTL }

internal enum class Effect { SHEET, HARD }

internal class PageObj(val number: Int) {
    /** -1 = no parity class, 0 = "even", 1 = "odd". */
    var parity = -1
    var hard = false
}

internal class PageWrap(val page: Int, val order: Int) {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
    var z = 0
    var visible = true
}

internal class CornerPoint(var x: Float, var y: Float, var corner: String = "") {
    var fit = false
    var isHovering = false
}

internal class GradientSpec {
    var active = false
    var x0 = 0f
    var y0 = 0f
    var x1 = 0f
    var y1 = 0f
    val positions = FloatArray(3)
    val colors = IntArray(3)
}

/** Output of turn.js `_fold` for the "sheet" effect, expressed as matrices in magazine px. */
internal class SheetFold {
    val pageMatrix = Matrix()
    val wrapperMatrix = Matrix()
    val fwrapperMatrix = Matrix()
    val fpageMatrix = Matrix()
    var wrapperSize = 0f
    var flapShadowAlpha = 0f
    val frontGradient = GradientSpec()
    val backGradient = GradientSpec()
}

/** Output of turn.js `_fold` for the "hard" effect. */
internal class HardFold {
    var angle = 0f
    var originX = 0f
    var flapOriginX = 0f
    var flapTranslateX = 0f
    var wrapperOnTop = true
    var frontShadowAlpha = 0f
    var frontShadowLeft = 0f
    var frontShadowVisible = false
    var backShadowOpacity = 0f
    var backShadowOnWrapper = true
    val backShadow = GradientSpec()
}

internal class ShadowBox {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
    /** 1 = right half ("shadow-half-left"), 2 = left half ("shadow-half-right"), 3 = full. */
    var case = 3
}
