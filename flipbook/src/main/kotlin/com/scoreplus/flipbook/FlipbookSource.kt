package com.scoreplus.flipbook

import android.net.Uri
import java.io.File

/** Where the PDF comes from. */
sealed class FlipbookSource {
    /** A file inside the app's `assets/` folder, e.g. `Asset("books/sample.pdf")`. */
    class Asset(val path: String) : FlipbookSource()

    class LocalFile(val file: File) : FlipbookSource()

    class ContentUri(val uri: Uri) : FlipbookSource()

    /**
     * A PDF on a server or CDN. It is downloaded once into the app cache while the loading
     * spinner shows; later opens reuse the cached copy unless [refresh] is true.
     */
    class Url @JvmOverloads constructor(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val refresh: Boolean = false,
    ) : FlipbookSource()

    internal val name: String
        get() = when (this) {
            is Asset -> path.substringAfterLast('/')
            is LocalFile -> file.name
            is ContentUri -> uri.lastPathSegment ?: uri.toString()
            is Url -> url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        }
}
