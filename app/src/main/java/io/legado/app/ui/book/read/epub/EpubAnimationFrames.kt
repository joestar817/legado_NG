package io.legado.app.ui.book.read.epub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import io.legado.app.utils.canvasrecorder.CanvasRecorder
import io.legado.app.utils.canvasrecorder.CanvasRecorderApi29Impl
import io.legado.app.ui.book.read.page.PageCurlRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

/** One owner for the frozen page drawings and their borrowed source pixels until handoff. */
internal class EpubAnimationFrames(val first: Frame, val second: Frame,
    private val release: () -> Unit = {}) : Closeable {
    internal interface PageDrawing : PageCurlRenderer.Page, Closeable {
        fun supportsPaint(paint: Paint?): Boolean = true
    }
    private var rasterSnapshots: Pair<Bitmap, Bitmap>? = null
    val recorded: Boolean get() = first.bitmap == null || second.bitmap == null
    val curlPages: Boolean get() = first.hasCurlDrawing && second.hasCurlDrawing
    val needsFrameCommit: Boolean get() = recorded || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        (first.bitmap?.config == Bitmap.Config.HARDWARE || second.bitmap?.config == Bitmap.Config.HARDWARE)
    // Software consumers can request pixels lazily; hardware curl consumes the frozen drawing.
    val snapshots: Pair<Bitmap, Bitmap> get() = rasterSnapshots
        ?: ((first.bitmap ?: first.snapshot()) to (second.bitmap ?: second.snapshot())).also { rasterSnapshots = it }
    private var closed = false

    /** Flatten once on GPU, then let the unchanged curl renderer consume stable bitmaps. */
    fun flattenForCurl(): EpubAnimationFrames {
        if (!recorded) return this
        var before: Frame? = null
        try {
            before = first.flatten()
            val after = second.flatten()
            return EpubAnimationFrames(before, after) { close() }
        } catch (error: Throwable) {
            try { before?.close() } finally { close() }
            throw error
        }
    }

    /** Keep source leases until asynchronous producers finish, including cancellation/failure. */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun flattenForCurlAsync(shouldContinue: () -> Boolean, submitted: () -> Unit,
                            headerHeight: Int = 0, footerHeight: Int = 0,
                            foldedFirst: Boolean = true,
                            callback: (Result<EpubAnimationFrames>) -> Unit) {
        if (!recorded) { submitted(); callback(Result.success(this)); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            first.canComposeCurl && second.canComposeCurl && first.width == second.width && first.height == second.height &&
            headerHeight >= 0 && footerHeight >= 0 && headerHeight + footerHeight in 1 until first.height / 2 &&
            2L * (headerHeight + footerHeight) + 5 < first.height) {
            prepareCurlPagesAsync(headerHeight, footerHeight, foldedFirst, shouldContinue, submitted, callback)
            return
        }
        val main = Handler(Looper.getMainLooper())
        // UI RenderNodes remain on UI. Only independent Picture copies cross the worker boundary.
        val drawings = runCatching { first.snapshotPicture() to second.snapshotPicture() }
        if (drawings.isFailure) { submitted(); callback(Result.failure(drawings.exceptionOrNull()!!)); return }
        val (beforePicture, afterPicture) = drawings.getOrThrow()
        val timing = EpubStartupTiming("curl-worker")
        timing.mark("submitted")
        fun deliver(result: Result<Triple<Pair<Bitmap, Bitmap>, Long, Long>>, concurrent: Boolean) {
            result.onSuccess { timing.mark("ready concurrent=$concurrent beforeMs=${it.second} afterMs=${it.third}") }
            result.onFailure { timing.mark(if (it is CancellationException) "cancelled" else "failed") }
            callback(result.map { (pixels, _, _) ->
                EpubAnimationFrames(Frame.raster(pixels.first), Frame.raster(pixels.second)) { close() }
            })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val executor = Dispatchers.IO.asExecutor()
            fun schedule(picture: Picture): CompletableFuture<Pair<Bitmap, Long>> = try {
                CompletableFuture.supplyAsync({
                    try {
                        if (!shouldContinue()) throw CancellationException("EPUB animation already completed")
                        val started = SystemClock.elapsedRealtime()
                        renderPicture(picture) to (SystemClock.elapsedRealtime() - started)
                    } finally { clearPicture(picture) }
                }, executor)
            } catch (error: Throwable) {
                clearPicture(picture)
                CompletableFuture<Pair<Bitmap, Long>>().apply { completeExceptionally(error) }
            }
            // Each independent Picture has one worker/producer. Queue both before the
            // live WebView move, rather than waiting for one GPU fence to submit the next.
            val before = schedule(beforePicture)
            val after = schedule(afterPicture)
            CompletableFuture.allOf(before, after).whenComplete { _, failure ->
                // allOf waits for both owners, even when one fails or is cancelled.
                val a = before.takeUnless { it.isCompletedExceptionally || it.isCancelled }?.join()
                val b = after.takeUnless { it.isCompletedExceptionally || it.isCancelled }?.join()
                val result = if (failure == null) {
                    val left = checkNotNull(a)
                    val right = checkNotNull(b)
                    Result.success(Triple(left.first to right.first, left.second, right.second))
                } else {
                    a?.first?.recycle(); b?.first?.recycle()
                    Result.failure(failure.cause ?: failure)
                }
                main.post { deliver(result, concurrent = true) }
            }
            submitted()
            return
        }
        submitted()
        Dispatchers.IO.asExecutor().execute {
            var before: Bitmap? = null
            val result = runCatching {
                if (!shouldContinue()) throw CancellationException("EPUB animation already completed")
                val started = SystemClock.elapsedRealtime()
                before = renderPicture(beforePicture)
                val middle = SystemClock.elapsedRealtime()
                if (!shouldContinue()) throw CancellationException("EPUB animation already completed")
                val after = renderPicture(afterPicture)
                Triple(checkNotNull(before) to after, middle - started, SystemClock.elapsedRealtime() - middle)
            }
            if (result.isFailure) before?.recycle()
            // Drop cloned commands/native image references on their sole consumer thread.
            listOf(beforePicture, afterPicture).forEach(::clearPicture)
            main.post { deliver(result, concurrent = false) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun prepareCurlPagesAsync(top: Int, bottom: Int, foldedFirst: Boolean, shouldContinue: () -> Boolean,
                                      submitted: () -> Unit, callback: (Result<EpubAnimationFrames>) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val offset = top + 2
        val drawings = runCatching {
            // Keep the folded page's original texture grid. Its unused middle rows
            // can also hold the unfiltered underneath page without a second producer.
            val folded = (if (foldedFirst) first else second).informationPicture(top, bottom)
            try {
                val underneath = (if (foldedFirst) second else first).informationPicture(top, bottom, compact = true)
                try {
                    Picture().apply {
                        val canvas = beginRecording(first.width, first.height)
                        try {
                            canvas.drawPicture(folded)
                            canvas.translate(0f, offset.toFloat())
                            canvas.drawPicture(underneath)
                        } finally { endRecording() }
                    }
                } finally { clearPicture(underneath) }
            } finally { clearPicture(folded) }
        }
        if (drawings.isFailure) { submitted(); callback(Result.failure(drawings.exceptionOrNull()!!)); return }
        val picture = drawings.getOrThrow()
        val timing = EpubStartupTiming("curl-information-worker").also {
            it.mark("submitted sharedHeight=${picture.height} offset=$offset")
        }
        val work = try {
            CompletableFuture.supplyAsync({
                var information: Bitmap? = null
                var before: Frame? = null
                var after: Frame? = null
                try {
                    if (!shouldContinue()) throw CancellationException("EPUB animation already completed")
                    val began = SystemClock.elapsedRealtime()
                    val pixels = renderPicture(picture)
                    information = pixels
                    before = Frame.curl(first, pixels, top, bottom, if (foldedFirst) null else offset)
                    after = Frame.curl(second, pixels, top, bottom, if (foldedFirst) offset else null)
                    timing.mark("ready sharedMs=${SystemClock.elapsedRealtime() - began}")
                    val frames = EpubAnimationFrames(checkNotNull(before), checkNotNull(after)) {
                        // Both shaders retire at the existing frame-commit fence before
                        // their one immutable information texture and body leases do.
                        try { pixels.recycle() } finally { this@EpubAnimationFrames.close() }
                    }
                    information = null
                    before = null
                    after = null
                    frames
                } catch (error: Throwable) {
                    try { before?.close() } finally {
                        try { after?.close() } finally { information?.recycle() }
                    }
                    throw error
                } finally { clearPicture(picture) }
            }, Dispatchers.IO.asExecutor())
        } catch (error: Throwable) {
            clearPicture(picture)
            CompletableFuture<EpubAnimationFrames>().apply { completeExceptionally(error) }
        }
        work.whenComplete { frames, failure ->
            val result = if (failure == null) Result.success(checkNotNull(frames)) else {
                timing.mark("failed")
                Result.failure(failure.cause ?: failure)
            }
            // The sole producer has retired, including failed and cancelled turns.
            main.post { callback(result) }
        }
        submitted()
    }

    private fun clearPicture(picture: Picture) {
        runCatching { picture.beginRecording(1, 1); picture.endRecording() }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun renderPicture(picture: Picture): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) EpubPictureRenderer.render(picture)
        else Bitmap.createBitmap(picture)

    override fun close() {
        if (closed) return
        closed = true
        try { first.close() } finally { try { second.close() } finally { release() } }
    }

    internal class Frame private constructor(
        val width: Int,
        val height: Int,
        val bitmap: Bitmap?,
        private val recorder: CanvasRecorder?,
        private val sourceBody: Bitmap? = null,
        private val sourceFrame: Frame? = null,
        private var drawing: PageDrawing? = null,
    ) : Closeable, PageCurlRenderer.Page {
        private var closed = false
        private var raster = bitmap?.takeIf { Build.VERSION.SDK_INT < Build.VERSION_CODES.O || it.config != Bitmap.Config.HARDWARE }
        private var paintRaster: Bitmap? = null
        val canComposeCurl: Boolean get() = sourceBody != null && recorder is CanvasRecorderApi29Impl
        val hasCurlDrawing: Boolean get() = drawing != null

        fun prepareToDraw() { bitmap?.prepareToDraw() }

        fun draw(canvas: Canvas) {
            if (drawing != null) {
                if (canvas.isHardwareAccelerated) drawing?.draw(canvas, null)
                else canvas.drawBitmap(snapshot(), 0f, 0f, null)
            } else if (bitmap != null) canvas.drawBitmap(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE &&
                    !canvas.isHardwareAccelerated) snapshot() else bitmap,
                0f, 0f, null)
            else if (!canvas.isHardwareAccelerated) canvas.drawBitmap(snapshot(), 0f, 0f, null)
            else recorder?.draw(canvas)
        }

        override fun draw(canvas: Canvas, paint: Paint?) {
            if (drawing != null && canvas.isHardwareAccelerated && drawing?.supportsPaint(paint) == true) drawing?.draw(canvas, paint)
            else if (paint == null) draw(canvas)
            else if (drawing != null && canvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // A changed caller may request filtering on the compact side. Keep the
                // original complete raster rather than filtering packed coordinates.
                val pixels = paintRaster ?: paintSnapshot().also { paintRaster = it }
                canvas.drawBitmap(pixels, 0f, 0f, paint)
            }
            else canvas.drawBitmap(bitmap?.takeIf { canvas.isHardwareAccelerated ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O || it.config != Bitmap.Config.HARDWARE } ?: snapshot(), 0f, 0f, paint)
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun paintSnapshot(): Bitmap {
            val source = sourceFrame?.recorder
            return if (source is CanvasRecorderApi29Impl) source.renderBitmap()
            else checkNotNull(snapshot().copy(Bitmap.Config.ARGB_8888, false))
        }

        fun flatten(): Frame = Frame.raster(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && recorder is CanvasRecorderApi29Impl)
            recorder.renderBitmap() else checkNotNull(snapshot().copy(Bitmap.Config.ARGB_8888, false)))

        @RequiresApi(Build.VERSION_CODES.Q)
        fun snapshotPicture(): Picture = if (sourceFrame != null) sourceFrame.snapshotPicture()
        else if (recorder is CanvasRecorderApi29Impl) recorder.snapshotPicture()
        else Picture().apply {
            val canvas = beginRecording(width, height)
            try { canvas.drawBitmap(checkNotNull(bitmap), 0f, 0f, null) } finally { endRecording() }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        fun informationPicture(top: Int, bottom: Int, compact: Boolean = false): Picture {
            val source = snapshotPicture()
            val picture = Picture()
            try {
                val informationHeight = if (compact) top + bottom + 2 else height
                val canvas = picture.beginRecording(width, informationHeight)
                try {
                    // The folded side keeps its grid. Both sides include the original one-pixel halo.
                    var saved = canvas.save()
                    try { canvas.clipRect(0, 0, width, top + 1); canvas.drawPicture(source) }
                    finally { canvas.restoreToCount(saved) }
                    saved = canvas.save()
                    try {
                        canvas.translate(0f, (informationHeight - height).toFloat())
                        canvas.clipRect(0, height - bottom - 1, width, height)
                        canvas.drawPicture(source)
                    }
                    finally { canvas.restoreToCount(saved) }
                } finally { picture.endRecording() }
                return picture
            } finally { source.beginRecording(1, 1); source.endRecording() }
        }

        fun snapshot(): Bitmap = raster ?: (if (sourceFrame != null) checkNotNull(sourceFrame.snapshot().copy(Bitmap.Config.ARGB_8888, false))
        else if (bitmap != null) checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && recorder is CanvasRecorderApi29Impl)
            recorder.snapshotBitmap()
        else Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { recorder?.draw(Canvas(it)) })
            .also { raster = it }

        override fun close() {
            if (closed) return
            closed = true
            try { drawing?.close() } finally {
                drawing = null
                try { recorder?.recycle() } finally {
                    if (paintRaster !== raster && paintRaster !== bitmap) paintRaster?.recycle()
                    if (raster !== bitmap) raster?.recycle()
                    bitmap?.recycle()
                }
            }
        }

        companion object {
            fun raster(bitmap: Bitmap) = Frame(bitmap.width, bitmap.height, bitmap, null)

            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            fun curl(source: Frame, information: Bitmap, top: Int, bottom: Int, informationOffset: Int?): Frame =
                Frame(source.width, source.height, null, null, sourceFrame = source,
                    drawing = EpubCurlPage(checkNotNull(source.sourceBody), information, source.width, source.height, top, bottom, informationOffset))

            @RequiresApi(Build.VERSION_CODES.Q)
            fun record(width: Int, height: Int, sourceBody: Bitmap? = null, draw: (Canvas) -> Unit): Frame {
                val recorder = CanvasRecorderApi29Impl()
                var recording = false
                try {
                    val canvas = recorder.beginRecording(width, height)
                    recording = true
                    draw(canvas)
                    recorder.endRecording()
                    recording = false
                    return Frame(width, height, null, recorder, sourceBody)
                } catch (error: Throwable) {
                    if (recording) runCatching { recorder.endRecording() }
                    recorder.recycle()
                    throw error
                }
            }
        }
    }
}
