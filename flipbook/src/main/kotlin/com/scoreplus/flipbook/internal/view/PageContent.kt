package com.scoreplus.flipbook.internal.view

import android.graphics.Bitmap
import android.graphics.RectF

/** Rendered state of one page (`.page-cont` in the web version). */
internal class PageContent(val page: Int) {
    var bitmap: Bitmap? = null
    var renderedWidth = 0
    var requestedWidth = 0
    var loading = true

    /** High-resolution crop shown while zoomed, in page-box coordinates of [regionBoxWidth]. */
    var region: Bitmap? = null
    val regionRect = RectF()
    var regionBoxWidth = 0f
    var regionRequest = ""

    fun clearRegion() {
        region?.recycle()
        region = null
        regionRequest = ""
    }

    fun release() {
        bitmap?.recycle()
        bitmap = null
        clearRegion()
        renderedWidth = 0
        requestedWidth = 0
    }

    val byteCount: Int get() = (bitmap?.allocationByteCount ?: 0) + (region?.allocationByteCount ?: 0)
}
