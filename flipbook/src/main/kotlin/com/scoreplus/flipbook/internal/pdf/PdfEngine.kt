package com.scoreplus.flipbook.internal.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.scoreplus.flipbook.FlipbookSource
import java.io.File
import java.io.FileOutputStream

internal class PdfInfo(val pageCount: Int, val firstPageWidth: Float, val firstPageHeight: Float)

/** Single-threaded PdfRenderer wrapper (PdfRenderer is not thread-safe). */
internal class PdfEngine(private val context: Context) {
    private val thread = HandlerThread("flipbook-pdf").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val pageSizes = HashMap<Int, FloatArray>()
    private val queue = ArrayList<Job>()
    private var busy = false
    @Volatile private var closed = false

    /** Returns the page (1-based) the queue should prioritise, like sortRenderQueue in the web version. */
    var currentPageProvider: () -> Int = { 1 }

    private class Job(
        val page: Int,
        val key: String,
        val work: (PdfRenderer) -> Bitmap?,
        val callback: (Bitmap?) -> Unit,
    )

    fun open(source: FlipbookSource, onReady: (PdfInfo) -> Unit, onError: (Throwable) -> Unit) {
        worker.post {
            try {
                val descriptor = openDescriptor(source)
                val r = PdfRenderer(descriptor)
                fd = descriptor
                renderer = r
                val size = sizeOf(r, 1)
                val info = PdfInfo(r.pageCount, size[0], size[1])
                main.post { if (!closed) onReady(info) }
            } catch (t: Throwable) {
                main.post { if (!closed) onError(t) }
            }
        }
    }

    private fun openDescriptor(source: FlipbookSource): ParcelFileDescriptor = when (source) {
        is FlipbookSource.LocalFile ->
            ParcelFileDescriptor.open(source.file, ParcelFileDescriptor.MODE_READ_ONLY)
        is FlipbookSource.ContentUri ->
            context.contentResolver.openFileDescriptor(source.uri, "r")
                ?: error("Cannot open ${source.uri}")
        is FlipbookSource.Asset -> {
            val cached = File(context.cacheDir, "flipbook_" + source.path.replace('/', '_'))
            val assetLength = try {
                context.assets.openFd(source.path).use { it.length }
            } catch (e: Exception) {
                -1L
            }
            if (!cached.exists() || (assetLength >= 0 && cached.length() != assetLength)) {
                context.assets.open(source.path).use { input ->
                    FileOutputStream(cached).use { input.copyTo(it) }
                }
            }
            ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    private fun sizeOf(r: PdfRenderer, page: Int): FloatArray =
        pageSizes.getOrPut(page) {
            r.openPage(page - 1).use { floatArrayOf(it.width.toFloat(), it.height.toFloat()) }
        }

    /** Page size in PDF points, or null before the page has been measured. */
    fun cachedPageSize(page: Int): FloatArray? = synchronized(pageSizes) { pageSizes[page] }

    fun renderPage(page: Int, desiredWidth: Int, callback: (Bitmap?) -> Unit) {
        enqueue(Job(page, "p$page@$desiredWidth", { r ->
            val size = synchronized(pageSizes) { sizeOf(r, page) }
            var scale = desiredWidth / size[0]
            val maxArea = 11e7f
            if (size[0] * scale * size[1] * scale > maxArea) {
                scale = kotlin.math.sqrt(maxArea / (size[1] / size[0])) / size[0]
            }
            val w = canvasDim(size[0] * scale)
            val h = canvasDim(size[1] * scale)
            if (w <= 0 || h <= 0) return@Job null
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            r.openPage(page - 1).use { p ->
                val m = Matrix().apply { setScale(w / size[0], h / size[1]) }
                p.render(bitmap, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            bitmap
        }, callback))
    }

    /** Renders the part [left, top, left+w, top+h] of the page drawn at fullWidth px wide. */
    fun renderRegion(page: Int, fullWidth: Float, left: Int, top: Int, w: Int, h: Int, callback: (Bitmap?) -> Unit) {
        enqueue(Job(page, "r$page@$fullWidth:$left,$top,$w,$h", { r ->
            val size = synchronized(pageSizes) { sizeOf(r, page) }
            if (w <= 0 || h <= 0) return@Job null
            val scale = fullWidth / size[0]
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            r.openPage(page - 1).use { p ->
                val m = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(-left.toFloat(), -top.toFloat())
                }
                p.render(bitmap, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            bitmap
        }, callback))
    }

    private fun canvasDim(v: Float): Int =
        if (v - v.toInt() > 0.9999f) Math.round(v) else v.toInt()

    private fun enqueue(job: Job) {
        synchronized(queue) {
            queue.removeAll { it.key == job.key }
            queue.add(job)
        }
        pump()
    }

    fun cancelRegions() {
        synchronized(queue) { queue.removeAll { it.key.startsWith("r") } }
    }

    private fun pump() {
        worker.post {
            if (busy || closed) return@post
            val job = synchronized(queue) {
                if (queue.isEmpty()) return@post
                val current = currentPageProvider() - 1
                queue.sortWith { a, b ->
                    val ea = a.page
                    val eb = b.page
                    when {
                        (ea < current && eb < current) || (ea > current && eb > current) -> ea - eb
                        ea >= current && eb < current -> -1
                        ea < current && eb >= current -> 1
                        else -> ea - eb
                    }
                }
                queue.removeAt(0)
            }
            busy = true
            val result = try {
                renderer?.let { job.work(it) }
            } catch (t: Throwable) {
                null
            }
            busy = false
            main.post {
                if (closed) result?.recycle() else job.callback(result)
            }
            pump()
        }
    }

    fun close() {
        closed = true
        synchronized(queue) { queue.clear() }
        worker.post {
            try {
                renderer?.close()
                fd?.close()
            } catch (_: Exception) {
            }
            renderer = null
            fd = null
            thread.quitSafely()
        }
    }
}
