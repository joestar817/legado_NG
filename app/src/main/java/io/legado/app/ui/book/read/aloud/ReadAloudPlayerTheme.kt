package io.legado.app.ui.book.read.aloud

import android.content.Context
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ListeningMotionEffect
import io.legado.app.help.config.ListeningMotionSettings
import io.legado.app.help.config.NgListeningEffectLibraryStore
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.book.listen.ListeningCoverTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot

/** 在书籍封面与已选动效包之间选择播放器取色源。 */
internal object ReadAloudPlayerTheme {

    fun key(
        context: Context,
        book: Book?,
        sourceOrigin: String?,
        settings: ListeningMotionSettings,
    ): String {
        val pack = palettePackage(context, settings)
        return if (pack != null) {
            "effect:${pack.id}:${pack.publication?.sha256}:${pack.playerSeed(context)}"
        } else {
            "cover:${book?.bookUrl.orEmpty()}|${book?.getDisplayCover()}|${sourceOrigin.orEmpty()}"
        }
    }

    fun initialSnapshot(
        context: Context,
        book: Book?,
        sourceOrigin: String?,
        settings: ListeningMotionSettings,
    ): NgThemeSnapshot {
        val pack = palettePackage(context, settings)
        return if (pack != null) {
            ListeningCoverTheme.seedSnapshot(context, pack.playerSeed(context))
        } else {
            ListeningCoverTheme.cached(book, sourceOrigin)
                ?: ListeningCoverTheme.fallback(context, book)
        }
    }

    suspend fun resolveSnapshot(
        context: Context,
        book: Book?,
        sourceOrigin: String?,
        settings: ListeningMotionSettings,
    ): NgThemeSnapshot {
        val pack = palettePackage(context, settings)
        return when {
            pack != null -> {
                ListeningCoverTheme.seedSnapshot(context, pack.playerSeed(context))
            }
            book != null -> {
                ListeningCoverTheme.resolve(context, book, sourceOrigin)
            }
            else -> {
                ListeningCoverTheme.fallback(context)
            }
        }
    }

    fun initialDrawerSnapshot(
        context: Context,
        book: Book?,
        sourceOrigin: String?,
        settings: ListeningMotionSettings,
    ): NgThemeSnapshot = ListeningCoverTheme.drawerSnapshot(
        initialSnapshot(context, book, sourceOrigin, settings)
    )

    suspend fun resolveDrawerSnapshot(
        context: Context,
        book: Book?,
        sourceOrigin: String?,
        settings: ListeningMotionSettings,
    ): NgThemeSnapshot = ListeningCoverTheme.drawerSnapshot(
        resolveSnapshot(context, book, sourceOrigin, settings)
    )

    private fun palettePackage(
        context: Context,
        settings: ListeningMotionSettings,
    ): NgManagedTheme? {
        if (!settings.enabled || settings.effect != ListeningMotionEffect.PACKAGE || AppConfig.isEInkMode) return null
        return NgListeningEffectLibraryStore.allEffects(context).firstOrNull {
            it.id == settings.packageId && it.effect?.playerPalette == "theme"
        }
    }

    private fun NgManagedTheme.playerSeed(context: Context): Int =
        if (ThemeConfig.isDarkTheme(context)) colors.darkSeed else colors.lightSeed
}
