package io.legado.app.ui.main.home

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import android.content.res.Resources
import android.util.Log
import io.legado.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Original bitmaps stay shared for this home view, with a bounded retained size. */
internal class HomeWidgetArtworkCache {
    private data class Entry(val bitmap: ImageBitmap, val bytes: Long)
    private val lock = Any()
    private val bitmaps = LinkedHashMap<Int, Entry>(16, 0.75f, true)
    private var retainedBytes = 0L
    private var hitCount = 0L
    private var loadCount = 0L
    private var prepared = false

    fun getOrLoad(@DrawableRes id: Int, load: () -> ImageBitmap): ImageBitmap {
        synchronized(lock) {
            bitmaps[id]?.let {
                hitCount++
                return it.bitmap
            }
        }
        // Decoding stays outside the lock: a foreground cache miss never waits for warm-up I/O.
        val bitmap = load()
        val bytes = bitmap.asAndroidBitmap().allocationByteCount.toLong()
        synchronized(lock) {
            loadCount++
            bitmaps[id]?.let {
                hitCount++
                return it.bitmap
            }
            if (bytes > MaximumRetainedBytes) return bitmap
            val entries = bitmaps.entries.iterator()
            while (retainedBytes + bytes > MaximumRetainedBytes && entries.hasNext()) {
                val entry = entries.next()
                retainedBytes -= entry.value.bytes
                entries.remove()
            }
            bitmaps[id] = Entry(bitmap, bytes)
            retainedBytes += bytes
            return bitmap
        }
    }

    suspend fun prepare(resources: Resources) {
        if (synchronized(lock) { prepared }) return
        var complete = true
        for (id in PreviewArtwork) {
            currentCoroutineContext().ensureActive()
            try {
                getOrLoad(id) { ImageBitmap.imageResource(resources, id) }.asAndroidBitmap().prepareToDraw()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                complete = false
                Log.w("HomeWidgetPreview", "Unable to prepare artwork $id", error)
            }
        }
        currentCoroutineContext().ensureActive()
        synchronized(lock) { prepared = complete }
    }

    fun debugSummary(): String = synchronized(lock) {
        "images=${bitmaps.size}, decoded=$loadCount, reused=$hitCount, bytes=$retainedBytes, prepared=$prepared"
    }

    private companion object {
        const val MaximumRetainedBytes = 48L * 1024 * 1024
        val PreviewArtwork = intArrayOf(
            R.drawable.ng_home_reading_story_owl,
            R.drawable.ng_home_listening_night_desk,
            R.drawable.ng_home_listening_large_night_leaves,
            R.drawable.ng_home_listening_large_paper_bottom,
            R.drawable.ng_home_listening_storybook,
            R.drawable.ng_home_listening_large_story_scene,
            R.drawable.ng_home_listening_large_wood,
            R.drawable.ng_home_calendar_story_cat,
            R.drawable.ng_home_calendar_night_lamp,
            R.drawable.ng_home_updates_story_girl,
        )
    }
}

internal val LocalHomeWidgetArtworkCache = staticCompositionLocalOf<HomeWidgetArtworkCache?> { null }

/** Actual cards keep painterResource; a drawer reuses the same full-resolution bitmap. */
@Composable
internal fun homeWidgetArtworkPainter(@DrawableRes id: Int): Painter {
    val cache = LocalHomeWidgetArtworkCache.current ?: return painterResource(id)
    val resources = LocalContext.current.resources
    val bitmap = remember(cache, id, resources) {
        cache.getOrLoad(id) { ImageBitmap.imageResource(resources, id) }
    }
    return remember(bitmap) { BitmapPainter(bitmap) }
}
