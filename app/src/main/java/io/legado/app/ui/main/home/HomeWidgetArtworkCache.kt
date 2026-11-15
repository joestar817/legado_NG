package io.legado.app.ui.main.home

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import io.legado.app.help.home.HomeWidgetSkinPackage
import io.legado.app.help.home.InstalledHomeWidgetSkin
import java.util.zip.ZipFile

/** Share the bounded decoded artwork between the live cards and the editing previews. */
internal class HomeWidgetArtworkCache {
    private data class Key(val revision: String, val role: String)
    private data class Entry(val bitmap: ImageBitmap, val bytes: Long)
    private val lock = Any()
    private val bitmaps = LinkedHashMap<Key, Entry>(16, 0.75f, true)
    private var retainedBytes = 0L

    private fun load(skin: InstalledHomeWidgetSkin, role: String): ImageBitmap? {
        val key = Key(skin.revision, role)
        synchronized(lock) { bitmaps[key]?.let { return it.bitmap } }
        if (role !in skin.renderer.artwork && role !in skin.renderer.optionalArtwork) return null
        val bitmap = try {
            ZipFile(skin.archive).use { zip ->
                val entry = zip.getEntry(HomeWidgetSkinPackage.artworkPath(role)) ?: return null
                zip.getInputStream(entry).use(BitmapFactory::decodeStream)
            } ?: return null
        } catch (error: Exception) {
            Log.w("HomeWidgetArtwork", "Skin artwork unavailable: $role", error)
            return null
        }
        val bytes = bitmap.allocationByteCount.toLong()
        synchronized(lock) {
            bitmaps[key]?.let {
                bitmap.recycle()
                return it.bitmap
            }
            val image = bitmap.asImageBitmap()
            val iterator = bitmaps.entries.iterator()
            while (retainedBytes + bytes > MAX_RETAINED_BYTES && iterator.hasNext()) {
                retainedBytes -= iterator.next().value.bytes
                iterator.remove()
            }
            if (bytes <= MAX_RETAINED_BYTES) {
                bitmaps[key] = Entry(image, bytes)
                retainedBytes += bytes
            }
            return image
        }
    }

    fun prepare(skin: InstalledHomeWidgetSkin): Boolean {
        val optional = if (skin.renderer.optionalArtwork.isEmpty()) emptyList() else runCatching {
            ZipFile(skin.archive).use { zip ->
                skin.renderer.optionalArtwork.filter { zip.getEntry(HomeWidgetSkinPackage.artworkPath(it)) != null }
            }
        }.getOrNull() ?: return false
        return (skin.renderer.artwork + optional).all { load(skin, it) != null }
    }

    fun bitmap(skin: InstalledHomeWidgetSkin, role: String): ImageBitmap? =
        synchronized(lock) { bitmaps[Key(skin.revision, role)]?.bitmap }

    private companion object {
        const val MAX_RETAINED_BYTES = 48L * 1024 * 1024
    }
}

internal val LocalHomeWidgetArtworkCache = staticCompositionLocalOf<HomeWidgetArtworkCache?> { null }
internal val LocalHomeWidgetSkins = staticCompositionLocalOf { HomeWidgetSkinSelection(emptyList()) }

@Composable
internal fun homeWidgetArtworkPainter(styleId: String, role: String): Painter? {
    val skin = LocalHomeWidgetSkins.current.installedStyle(styleId) ?: return null
    val cache = LocalHomeWidgetArtworkCache.current ?: return null
    return remember(cache, skin, role) { cache.bitmap(skin, role)?.let { BitmapPainter(it) } }
}
