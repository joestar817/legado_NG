package io.legado.app.ui.book.search

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.lang.ref.WeakReference

/** 复用缩放后的占位封面，原图使用弱引用，缓存上限为 4 MiB。 */
internal class SearchCoverBitmapCache {
    private class Key(bitmap: Bitmap, val width: Int, val height: Int) {
        private val bitmapRef = WeakReference(bitmap)
        private val bitmapHash = System.identityHashCode(bitmap)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Key || bitmapHash != other.bitmapHash ||
                width != other.width || height != other.height
            ) return false
            val bitmap = bitmapRef.get() ?: return false
            return bitmap === other.bitmapRef.get()
        }

        override fun hashCode(): Int = 31 * (31 * bitmapHash + width) + height
    }

    private val scaled = object : LruCache<Key, Bitmap>(4 * 1024) {
        override fun sizeOf(key: Key, value: Bitmap): Int =
            ((value.allocationByteCount.toLong() + 1023) / 1024).toInt().coerceAtLeast(1)
    }

    fun toImageBitmap(drawable: Drawable, width: Int, height: Int): ImageBitmap {
        val original = (drawable as? BitmapDrawable)?.bitmap
            ?: return drawable.toBitmap(width, height).asImageBitmap()
        if ((original.width == width && original.height == height) || original.isRecycled) {
            return drawable.toBitmap(width, height).asImageBitmap()
        }
        val key = Key(original, width, height)
        val bitmap = scaled[key]?.takeUnless { it.isRecycled }
            ?: drawable.toBitmap(width, height).also { scaled.put(key, it) }
        return bitmap.asImageBitmap()
    }
}
