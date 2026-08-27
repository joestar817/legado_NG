package io.legado.app.ui.book.read.aloud

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.help.config.ListeningMotionColorMode
import io.legado.app.help.config.ListeningMotionEffect
import io.legado.app.help.config.ListeningMotionSettings
import io.legado.app.ui.design.theme.NgTheme
import com.materialkolor.hct.Hct
import kotlin.math.max

@Composable
internal fun ReadAloudFireMotionBackground(
    settings: ListeningMotionSettings,
    modifier: Modifier = Modifier,
) {
    val environmentAllowed = rememberMotionEnvironmentAllowed()
    val context = LocalContext.current
    val gles3Supported = remember(context) { context.supportsGles3() }
    if (
        !settings.enabled ||
        settings.effect != ListeningMotionEffect.FLAME ||
        settings.intensity <= 0 ||
        !environmentAllowed ||
        !gles3Supported
    ) {
        return
    }

    val coverColor = NgTheme.colors.primary
    val effectColor = remember(settings.colorMode, settings.customColor, coverColor) {
        when (settings.colorMode) {
            ListeningMotionColorMode.ORIGINAL -> ORIGINAL_FIRE_COLOR
            ListeningMotionColorMode.COVER -> coverColor.toFireColor()
            ListeningMotionColorMode.CUSTOM -> settings.customColor
        }
    }
    val textureView = remember(context) { ListeningFireTextureView(context) }

    DisposableEffect(textureView) {
        onDispose { textureView.release() }
    }

    AndroidView(
        factory = { textureView },
        update = { view ->
            view.update(
                style = settings.fireStyle,
                intensity = settings.intensity,
                color = effectColor,
                accentFollowsMain = settings.colorMode != ListeningMotionColorMode.ORIGINAL,
            )
        },
        modifier = modifier.fillMaxSize(),
    )
}

private const val ORIGINAL_FIRE_COLOR = -0x0000FAE7

private fun Context.supportsGles3(): Boolean {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return activityManager?.deviceConfigurationInfo?.reqGlEsVersion?.let { it >= 0x00030000 }
        ?: false
}

private fun Int.toFireColor(): Int {
    val source = Hct.fromInt(this)
    return Hct.from(
        source.hue,
        max(source.chroma, 72.0),
        43.0,
    ).toInt()
}
