package com.scoreplus.flipbook

/**
 * Viewer options. Every field and default mirrors `FLIPBOOK_DEFAULT_DESIGN` in the web
 * version, plus the options the web version read from the page URL.
 */
class FlipbookDesign {
    /** null, "book", "notebook" or "album". */
    var type: String? = null

    /** Background image drawn at 40% opacity (web: `background`). 0 = none. */
    var background: Int = R.drawable.flipbook_background

    var backgroundColor: Int? = null

    /** web: `background_style.size` — "cover" (default), "contain" or "auto". */
    var backgroundSize: String = "cover"

    /** web: `background_style.transparency` (0–100). null keeps the default 0.4 opacity. */
    var backgroundTransparency: Int? = null

    var title: String = ""
    var subtitle: String = ""
    var description: String = ""

    /** 0 = hidden, 1 = page preview, 2 = page numbers, 3 = both. */
    var showSlider: Int = 3
    var showFullscreen: Int = 1
    var showText: Int = 1
    var showShadow: Int = 1
    var showDepth: Int = 1
    var showEdges: Int = 0
    var showRound: Int = 0
    var showBinding: Int = 0
    var showCenter: Int = 1

    /** 0 = automatic, 1 = always two pages, 2 = always one page. */
    var showDouble: Int = 0
    var showZoom: Int = 1

    /** null = first page, -1 = last page the reader saw, or a page number. */
    var loadPage: Int? = null

    /** 0 = off, 1 = wheel zooms, 2 = double-tap / ctrl+wheel zooms. */
    var clickZoom: Int = 2
    var rtl: Int = 0

    /** 0 = invisible tap strips, 1 = corner arrows, 2 = side bars, 3 = none. */
    var arrows: Int = 1

    /** null / "md", "sm" or "lg". */
    var controlsSize: String? = null
    var soundFlip: Int = 1
    var showPageNumber: Int = 1

    /** web URL option `?page=` */
    var startPage: Int? = null

    /** web URL option `?ap=` — seconds between automatic page turns. */
    var autoPlaySeconds: Int? = null

    /** web URL option `?pl=` — "tr", "br", "tl" or "bl" peel hint every 3 s. */
    var peelCorner: String? = null
}
