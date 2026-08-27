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
import io.legado.app.help.config.ListeningMotionEffect
import io.legado.app.help.config.ListeningMotionSettings

/**
 * Full-screen Pavel-style fluid layer. The native Compose cover and controls remain above it,
 * so no screenshot-derived player foreground is needed in the App runtime.
 */
@Composable
internal fun ReadAloudFluidMotionBackground(
    settings: ListeningMotionSettings,
    modifier: Modifier = Modifier,
) {
    val environmentAllowed = rememberMotionEnvironmentAllowed()
    val context = LocalContext.current
    val deviceAllowed = remember(context) { context.supportsFluidRuntime() }
    if (
        !settings.enabled ||
        settings.effect != ListeningMotionEffect.FLUID ||
        settings.intensity <= 0 ||
        !environmentAllowed ||
        !deviceAllowed
    ) {
        return
    }

    val textureView = remember(context) { ListeningFluidTextureView(context) }
    DisposableEffect(textureView) {
        onDispose { textureView.release() }
    }

    AndroidView(
        factory = { textureView },
        update = { view ->
            view.update(
                type = settings.fluidType,
                intensity = settings.intensity,
            )
        },
        modifier = modifier.fillMaxSize(),
    )
}

private fun Context.supportsFluidRuntime(): Boolean {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return false
    return !activityManager.isLowRamDevice &&
        activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x00030000
}
