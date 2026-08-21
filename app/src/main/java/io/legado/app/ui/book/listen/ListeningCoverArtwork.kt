package io.legado.app.ui.book.listen

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import io.legado.app.model.BookCover
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

private const val ARTWORK_WIDTH = 480
private const val ARTWORK_HEIGHT = 720

/** Compose 播放器共用的封面位图缓存，不改变 BookCover／Glide 的请求协议。 */
internal object ListeningCoverArtwork {

    private val bitmapCache = LruCache<String, Bitmap>(8)

    @Composable
    fun remember(
        context: Context,
        cacheKey: String,
        path: String?,
        sourceOrigin: String?,
    ): State<ImageBitmap?> = produceState<ImageBitmap?>(
        initialValue = bitmapCache[cacheKey]?.asImageBitmap(),
        key1 = cacheKey,
        key2 = path,
        key3 = sourceOrigin,
    ) {
        bitmapCache[cacheKey]?.let {
            value = it.asImageBitmap()
            return@produceState
        }
        value = load(context, path, sourceOrigin)?.also {
            bitmapCache.put(cacheKey, it)
        }?.asImageBitmap()
    }

    private suspend fun load(
        context: Context,
        path: String?,
        sourceOrigin: String?,
    ): Bitmap? = withContext(IO) {
        val appContext = context.applicationContext
        runCatching {
            val target = BookCover.load(
                context = appContext,
                path = path,
                sourceOrigin = sourceOrigin,
            ).submit(ARTWORK_WIDTH, ARTWORK_HEIGHT)
            try {
                target.get()
                    .toBitmap(ARTWORK_WIDTH, ARTWORK_HEIGHT)
                    .copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                Glide.with(appContext).clear(target)
            }
        }.getOrNull()
    }
}
