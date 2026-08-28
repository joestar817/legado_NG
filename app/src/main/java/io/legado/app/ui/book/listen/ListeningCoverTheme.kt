package io.legado.app.ui.book.listen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.materialkolor.hct.Hct
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.model.BookCover
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgThemeSnapshot
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 听书／有声书共用的封面色板入口。
 *
 * 这里只从封面生成独立的沉浸式 NG 语义快照，不读取阅读预设，也不写回应用主题。
 * 抽屉仍由 NgBottomDrawerSurface 再派生自己的材质语义。
 */
internal object ListeningCoverTheme {

    private const val SAMPLE_WIDTH = 64
    private const val SAMPLE_HEIGHT = 96
    private val noCoverSeed = Color.rgb(142, 98, 64)
    private val snapshotCache = LruCache<String, NgThemeSnapshot>(16)

    fun cached(
        book: Book?,
        sourceOrigin: String?,
    ): NgThemeSnapshot? = cacheKey(book, sourceOrigin)?.let(snapshotCache::get)

    fun fallback(context: Context, book: Book? = null): NgThemeSnapshot {
        val appContext = context.applicationContext
        val seed = if (book != null && book.getDisplayCover().isNullOrBlank()) {
            noCoverSeed
        } else {
            NgThemeResolver.resolve(appContext).colors.primary
        }
        return buildSnapshot(appContext, seed)
    }

    /** 让封面以外的沉浸视觉源复用同一套播放器深色语义。 */
    fun seedSnapshot(context: Context, seed: Int): NgThemeSnapshot =
        buildSnapshot(context.applicationContext, seed)

    /**
     * 听书抽屉使用封面色底板与浅色内容面，不复用播放器的整套深色语义。
     *
     * 抽屉外壳仍由 [io.legado.app.ui.design.theme.NgDrawerPalette] 根据强调色派生，
     * 而 surface/inputContainer 保持暖白，供设置组、音色卡和目录条目形成稳定层级。
     */
    fun drawerSnapshot(playerSnapshot: NgThemeSnapshot): NgThemeSnapshot {
        val source = Hct.fromInt(playerSnapshot.colors.surfaceTint)
        val hue = source.hue
        val accent = Hct.from(
            hue,
            max(source.chroma, 40.0),
            50.0,
        ).toInt()
        val background = Hct.from(
            hue,
            min(source.chroma * 0.32, 24.0),
            88.0,
        ).toInt()
        val surface = Hct.from(
            hue,
            min(source.chroma * 0.10, 6.0),
            98.0,
        ).toInt()
        return NgThemeResolver.resolve(
            NgLegacyThemeInput(
                primaryColor = background,
                accentColor = accent,
                backgroundColor = background,
                bottomBackground = surface,
                errorColor = playerSnapshot.colors.error,
                isDark = false,
                isEInk = false,
            )
        )
    }

    suspend fun resolve(
        context: Context,
        book: Book,
        sourceOrigin: String?,
    ): NgThemeSnapshot = withContext(IO) {
        val appContext = context.applicationContext
        val key = requireNotNull(cacheKey(book, sourceOrigin))
        snapshotCache[key]?.let { return@withContext it }
        val coverPath = book.getDisplayCover()
        val seed = if (coverPath.isNullOrBlank()) {
            noCoverSeed
        } else {
            loadCoverSeed(
                context = appContext,
                path = coverPath,
                sourceOrigin = sourceOrigin,
            ) ?: NgThemeResolver.resolve(appContext).colors.primary
        }
        buildSnapshot(appContext, seed).also { snapshotCache.put(key, it) }
    }

    private fun cacheKey(book: Book?, sourceOrigin: String?): String? {
        book ?: return null
        return "${book.bookUrl}|${book.getDisplayCover()}|${sourceOrigin.orEmpty()}"
    }

    private fun loadCoverSeed(
        context: Context,
        path: String?,
        sourceOrigin: String?,
    ): Int? = runCatching {
        val target = BookCover.load(
            context = context,
            path = path,
            sourceOrigin = sourceOrigin,
        ).submit(SAMPLE_WIDTH, SAMPLE_HEIGHT)
        try {
            dominantSeed(target.get().toBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT))
        } finally {
            Glide.with(context).clear(target)
        }
    }.getOrNull()

    private fun dominantSeed(bitmap: Bitmap): Int? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val buckets = HashMap<Int, Float>()
        var fallbackRed = 0L
        var fallbackGreen = 0L
        var fallbackBlue = 0L
        var fallbackCount = 0L
        val hsv = FloatArray(3)
        val xStep = max(1, bitmap.width / 32)
        val yStep = max(1, bitmap.height / 48)
        for (y in 0 until bitmap.height step yStep) {
            for (x in 0 until bitmap.width step xStep) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) < 180) continue
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                fallbackRed += red
                fallbackGreen += green
                fallbackBlue += blue
                fallbackCount++
                Color.RGBToHSV(red, green, blue, hsv)
                val saturation = hsv[1]
                val brightness = hsv[2]
                if (brightness < 0.08f) continue
                if (brightness > 0.96f && saturation < 0.10f) continue
                val bucket = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
                val middleWeight = (1f - abs(brightness - 0.56f)).coerceAtLeast(0.2f)
                val score = 0.25f + saturation * 1.65f + middleWeight * 0.45f
                buckets[bucket] = buckets.getOrDefault(bucket, 0f) + score
            }
        }
        val dominantBucket = buckets.maxByOrNull { it.value }?.key
        if (dominantBucket != null) {
            val red = (((dominantBucket shr 8) and 0xF) shl 4) or 0x8
            val green = (((dominantBucket shr 4) and 0xF) shl 4) or 0x8
            val blue = ((dominantBucket and 0xF) shl 4) or 0x8
            return Color.rgb(red, green, blue)
        }
        if (fallbackCount == 0L) return null
        val fallback = Color.rgb(
            (fallbackRed / fallbackCount).toInt(),
            (fallbackGreen / fallbackCount).toInt(),
            (fallbackBlue / fallbackCount).toInt(),
        )
        Color.colorToHSV(fallback, hsv)
        return fallback.takeUnless {
            hsv[2] < 0.10f || (hsv[2] > 0.95f && hsv[1] < 0.10f)
        }
    }

    private fun buildSnapshot(context: Context, seed: Int): NgThemeSnapshot {
        val source = Hct.fromInt(seed)
        val hue = source.hue
        val controlSeed = Hct.from(
            hue,
            max(source.chroma, 36.0),
            if (source.tone < 55.0) 70.0 else 62.0,
        ).toInt()
        val background = Hct.from(
            hue,
            min(source.chroma * 0.28, 18.0),
            9.0,
        ).toInt()
        val surface = Hct.from(
            hue,
            min(source.chroma * 0.36, 22.0),
            17.0,
        ).toInt()
        return NgThemeResolver.resolve(
            NgLegacyThemeInput(
                primaryColor = background,
                accentColor = controlSeed,
                backgroundColor = background,
                bottomBackground = surface,
                errorColor = ContextCompat.getColor(context, R.color.error),
                isDark = true,
                isEInk = false,
            )
        )
    }
}
