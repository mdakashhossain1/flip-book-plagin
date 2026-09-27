package com.scoreplus.flipbook

/** Android equivalent of the web `scoreplusTurning` / `scoreplusTurned` document events. */
interface FlipbookListener {
    fun onLoaded(numPages: Int) {}

    /** [page] is null when the reader starts dragging a corner. */
    fun onTurning(page: Int?) {}

    fun onTurned(page: Int, visiblePages: IntArray) {}

    fun onError(error: Throwable) {}
}
