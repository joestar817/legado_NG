package io.legado.app.ui.book.read.aloud

import android.app.ActivityManager
import android.content.Context
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ListeningCartoonType
import io.legado.app.help.config.ListeningMotionEffect
import io.legado.app.help.config.ListeningMotionSettings

@Composable
internal fun ReadAloudCartoonMotionBackground(
    settings: ListeningMotionSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val environmentAllowed = rememberMotionEnvironmentAllowed()
    val availableTypes = remember(context) { context.availableCartoonTypes() }
    if (
        !settings.enabled ||
        settings.effect != ListeningMotionEffect.CARTOON ||
        settings.cartoonType !in availableTypes
    ) {
        return
    }

    key(settings.cartoonType) {
        val textureView: TextureView = remember(context) {
            context.createCartoonMotionTextureView(settings.cartoonType)
        }
        val textureHost = textureView as ListeningCartoonTextureHost
        DisposableEffect(textureHost) {
            onDispose { textureHost.release() }
        }
        AndroidView(
            factory = { textureView },
            update = { view ->
                (view as ListeningCartoonTextureHost).update(
                    intensity = settings.intensity,
                    animationAllowed = environmentAllowed,
                )
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}

internal interface ListeningCartoonTextureHost {
    fun update(
        intensity: Int,
        animationAllowed: Boolean,
        timelineOriginNanos: Long? = null,
    )

    fun release(onReleased: (() -> Unit)? = null)
}

internal fun Context.createCartoonMotionTextureView(
    type: ListeningCartoonType,
): TextureView = when (type) {
    ListeningCartoonType.SAKURA -> ListeningSakuraTextureView(this)
    ListeningCartoonType.CATS -> ListeningCatsTextureView(this)
    ListeningCartoonType.RAIN_NIGHT -> ListeningRainNightTextureView(this)
}

private val cartoonAvailabilityLock = Any()

@Volatile
private var cachedAvailableCartoonTypes: List<ListeningCartoonType>? = null

/**
 * The bundled scenes are available only when the current device can render their complete assets.
 * Source attribution and modification notes live beside the assets in
 * assets/listening_motion/cartoon/README.md.
 */
internal fun Context.availableCartoonTypes(): List<ListeningCartoonType> {
    if (AppConfig.isEInkMode) return emptyList()
    cachedAvailableCartoonTypes?.let { return it }
    return synchronized(cartoonAvailabilityLock) {
        cachedAvailableCartoonTypes?.let { return@synchronized it }
        resolveAvailableCartoonTypes().also { cachedAvailableCartoonTypes = it }
    }
}

private fun Context.resolveAvailableCartoonTypes(): List<ListeningCartoonType> {
    if (!isCartoonMotionEnvironmentAvailable()) return emptyList()
    return buildList {
        if (hasCartoonMotionAssets(SakuraMotionAssets.ROOT, SakuraMotionAssets.REQUIRED_FILES)) {
            add(ListeningCartoonType.SAKURA)
        }
        if (hasCartoonMotionAssets(CatsMotionAssets.ROOT, CatsMotionAssets.REQUIRED_FILES)) {
            add(ListeningCartoonType.CATS)
        }
        if (
            hasCartoonMotionAssets(
                RainNightMotionAssets.ROOT,
                RainNightMotionAssets.REQUIRED_FILES,
            )
        ) {
            add(ListeningCartoonType.RAIN_NIGHT)
        }
    }
}

private fun Context.isCartoonMotionEnvironmentAvailable(): Boolean {
    if (AppConfig.isEInkMode) return false
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val deviceInfo = activityManager?.deviceConfigurationInfo ?: return false
    if (deviceInfo.reqGlEsVersion < 0x00030000 || activityManager.isLowRamDevice) return false
    return true
}

private fun Context.hasCartoonMotionAssets(root: String, requiredFiles: Set<String>): Boolean =
    runCatching { assets.list(root)?.toSet().orEmpty().containsAll(requiredFiles) }
        .getOrDefault(false)

internal object SakuraMotionAssets {
    const val ROOT = "listening_motion/cartoon/sakura"
    const val BACKGROUND = "background.webp"
    const val WATER_NORMAL = "water_normal.png"
    const val WATER_PHASE = "water_phase.png"
    val REQUIRED_FILES = setOf(BACKGROUND, WATER_NORMAL, WATER_PHASE)

    fun path(fileName: String): String = "$ROOT/$fileName"
}
