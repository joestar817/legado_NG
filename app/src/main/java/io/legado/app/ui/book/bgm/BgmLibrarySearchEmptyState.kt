package io.legado.app.ui.book.bgm

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.bgm.MusicPlatform
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Shared debossed branding for an idle catalogue or an empty playlist, never for errors. */
@Composable
internal fun BgmLibrarySearchEmptyState(
    platform: MusicPlatform,
    addingSongs: Boolean = false,
    message: String = if (addingSongs) "搜索歌曲，添加到歌单" else "搜索歌曲或歌手",
) {
    val resources = LocalContext.current.resources
    val glyph by produceState<BgmDebossedGlyph?>(null, resources, platform) {
        value = withContext(Dispatchers.Default) { BgmDebossedGlyph.get(resources, platform) }
    }
    val onSurface = Color(NgTheme.colors.onSurface)
    val eInk = NgTheme.snapshot.isEInk
    val listening = LocalBgmListeningMode.current
    val darkListening = listening && NgTheme.snapshot.isDark
    val lightListening = listening && !NgTheme.snapshot.isDark && !eInk
    Column(
        Modifier.fillMaxWidth().bgmListSurface()
            .padding(start = 14.dp, end = 14.dp, bottom = if (listening) 0.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(80.dp)) {
            glyph?.let { image ->
                // Equalize the visible silhouette, not the transparent bitmap canvas.
                val scale = if (listening) 64.dp.toPx() / max(image.bounds.width, image.bounds.height) else 1f
                val target = if (listening) IntSize((image.fill.width * scale).roundToInt(),
                    (image.fill.height * scale).roundToInt())
                    else IntSize(size.width.roundToInt(), size.height.roundToInt())
                val offset = if (listening) IntOffset(
                    (size.width / 2 - (image.bounds.left + image.bounds.right) * scale / 2).roundToInt(),
                    (size.height / 2 - (image.bounds.top + image.bounds.bottom) * scale / 2).roundToInt(),
                ) else IntOffset.Zero
                drawImage(image.fill, dstOffset = offset, dstSize = target, filterQuality = FilterQuality.High,
                    colorFilter = ColorFilter.tint(onSurface.copy(alpha = when {
                        eInk -> .12f
                        darkListening -> .04f
                        lightListening -> .10f
                        else -> .025f
                    })))
                drawImage(image.shadow, dstOffset = offset, dstSize = target, filterQuality = FilterQuality.High,
                    colorFilter = ColorFilter.tint((if (lightListening) onSurface else Color.Black).copy(alpha = when {
                        eInk -> .12f
                        darkListening -> .30f
                        lightListening -> .20f
                        else -> .055f
                    })))
                drawImage(image.highlight, dstOffset = offset, dstSize = target, filterQuality = FilterQuality.High,
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = when {
                        darkListening -> .16f
                        lightListening -> .45f
                        else -> .65f
                    })))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(message, Modifier.fillMaxWidth(),
            color = if (addingSongs) onSurface else Color(NgTheme.colors.onSurfaceVariant),
            fontSize = if (addingSongs) 15.sp else 13.sp, lineHeight = if (addingSongs) 22.sp else 18.sp,
            fontWeight = if (addingSongs) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center)
        if (addingSongs) {
            Spacer(Modifier.height(6.dp))
            Text("点击上方搜索框开始", color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Reuse the shipped brand silhouettes instead of drawing approximate logos.
 * Masks are small, generated off the UI thread once per platform, then tinted by the theme.
 */
private class BgmDebossedGlyph(
    val fill: ImageBitmap,
    val shadow: ImageBitmap,
    val highlight: ImageBitmap,
    val bounds: IntRect,
) {
    companion object {
        private val cache = mutableMapOf<MusicPlatform, BgmDebossedGlyph>()

        fun get(resources: Resources, platform: MusicPlatform): BgmDebossedGlyph = synchronized(cache) {
            cache.getOrPut(platform) {
                val resource = if (platform == MusicPlatform.NETEASE) R.drawable.bgm_platform_netease
                    else R.drawable.bgm_platform_qq
                val source = checkNotNull(BitmapFactory.decodeResource(resources, resource,
                    BitmapFactory.Options().apply { inScaled = false }))
                val width = source.width
                val height = source.height
                val pixels = IntArray(width * height)
                source.getPixels(pixels, 0, width, 0, 0, width, height)
                source.recycle()

                val alpha = FloatArray(pixels.size) { index ->
                    val pixel = pixels[index]
                    val red = (pixel ushr 16) and 255
                    val green = (pixel ushr 8) and 255
                    // NetEase: white glyph on red. QQ: green note on yellow.
                    val coverage = if (platform == MusicPlatform.NETEASE) (green - red * .25f) / .75f
                        else (green - red) * 1.4f
                    coverage.coerceIn(0f, 255f) / 255f * ((pixel ushr 24) and 255) / 255f
                }
                var left = width
                var top = height
                var right = 0
                var bottom = 0
                alpha.forEachIndexed { index, coverage ->
                    if (coverage >= .125f) {
                        val x = index % width
                        val y = index / width
                        left = minOf(left, x)
                        top = minOf(top, y)
                        right = maxOf(right, x + 1)
                        bottom = maxOf(bottom, y + 1)
                    }
                }
                check(left < right && top < bottom)
                fun sample(x: Int, y: Int): Float =
                    if (x in 0 until width && y in 0 until height) alpha[y * width + x] else 0f
                fun softSample(x: Int, y: Int): Float =
                    (sample(x, y) * 4 + sample(x - 1, y) + sample(x + 1, y) +
                        sample(x, y - 1) + sample(x, y + 1)) / 8
                fun mask(coverage: (Int, Int, Float) -> Float): ImageBitmap {
                    val colors = IntArray(pixels.size) { index ->
                        val opacity = (coverage(index % width, index / width, alpha[index]) * 255)
                            .roundToInt().coerceIn(0, 255)
                        (opacity shl 24) or 0x00ffffff
                    }
                    return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
                }
                BgmDebossedGlyph(
                    fill = mask { _, _, coverage -> coverage },
                    shadow = mask { x, y, coverage -> max(0f, coverage - softSample(x - 2, y - 2)) },
                    highlight = mask { x, y, coverage -> max(0f, coverage - softSample(x + 2, y + 2)) },
                    bounds = IntRect(left, top, right, bottom),
                )
            }
        }
    }
}
