package io.legado.app.ui.book.read.aloud

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ListeningMotionSettings
import io.legado.app.help.config.NgEffectPackageKind
import io.legado.app.help.config.NgListeningEffectLibraryStore
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.fromListeningPercent
import io.legado.app.help.config.listeningIntensity
import io.legado.app.ui.book.listen.ListeningCoverTheme
import io.legado.app.ui.design.theme.EffectThemeSurface
import io.legado.app.ui.design.theme.NgTheme
import java.io.File
import java.util.Locale

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun ReadAloudEffectPackageBackground(
    settings: ListeningMotionSettings,
    book: Book?,
    sourceOrigin: String?,
) {
    if (!settings.enabled || AppConfig.isEInkMode) return
    val context = LocalContext.current
    val library by NgListeningEffectLibraryStore.observe(context).collectAsState()
    val pack = library.savedThemes.firstOrNull { it.id == settings.packageId } ?: return
    val background = if (ThemeConfig.isDarkTheme(context)) pack.darkBackground else pack.lightBackground
    background.path?.let { path ->
        GlideImage(
            model = File(path), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
        )
    }

    val canAnimate = rememberMotionEnvironmentAllowed()
    val primary = NgTheme.colors.surfaceTint
    val coverColor by produceState(
        initialValue = (ListeningCoverTheme.cached(book, sourceOrigin)
            ?: ListeningCoverTheme.fallback(context, book)).colors.surfaceTint,
        book?.bookUrl, book?.getDisplayCover(), sourceOrigin,
    ) {
        if (book != null) value = ListeningCoverTheme.resolve(context, book, sourceOrigin).colors.surfaceTint
        else value = ListeningCoverTheme.fallback(context).colors.surfaceTint
    }
    val params = pack.listeningIntensity()?.let {
        mapOf(it.key to it.fromListeningPercent(settings.intensity))
    }.orEmpty()
    val environment = mapOf(
        "host.primaryColor" to String.format(Locale.ROOT, "#%08X", primary),
        "host.coverColor" to String.format(Locale.ROOT, "#%08X", coverColor),
    )
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = ::EffectThemeSurface,
        update = { surface ->
            surface.bind(pack, NgEffectPackageKind.LISTENING_EFFECT.target, params, environment,
                animationAllowed = canAnimate, timelineOriginNanos = null)
            surface.setActive(canAnimate)
        },
        onRelease = { it.release() },
    )
}
