package com.scoreplus.flipbook

import android.net.Uri
import java.io.File

/** Where the PDF comes from. */
sealed class FlipbookSource {
    /** A file inside the app's `assets/` folder, e.g. `Asset("books/sample.pdf")`. */
    class Asset(val path: String) : FlipbookSource()

    class LocalFile(val file: File) : FlipbookSource()

    class ContentUri(val uri: Uri) : FlipbookSource()

    internal val name: String
        get() = when (this) {
            is Asset -> path.substringAfterLast('/')
            is LocalFile -> file.name
            is ContentUri -> uri.lastPathSegment ?: uri.toString()
        }
}
