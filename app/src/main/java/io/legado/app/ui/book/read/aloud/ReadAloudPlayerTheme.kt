package io.legado.app.ui.book.read.aloud

import android.content.Context
import androidx.annotation.ColorInt
import io.legado.app.data.entities.Book
import io.legado.app.help.config.ListeningCartoonType
import io.legado.app.help.config.ListeningMotionEffect
import io.legado.app.help.config.ListeningMotionSettings
import io.legado.app.ui.book.listen.ListeningCoverTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot

/** 在书籍封面与当前卡通场景之间选择听书播放器的真实视觉取色源。 */
internal object ReadAloudPlayerTheme {

    fun key(
        context: Context,
        book: Book?,
        sourceOrigin: String?,
        settings: ListeningMotionSettings,
    ): String {
        val cartoonType = activeCartoonType(context, settings)
        return if (cartoonType != null) {
            "cartoon:${cartoonType.storageValue}"
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
        val cartoonType = activeCartoonType(context, settings)
        return if (cartoonType != null) {
            ListeningCoverTheme.seedSnapshot(context, cartoonType.playerThemeSeed())
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
        val cartoonType = activeCartoonType(context, settings)
        return when {
            cartoonType != null -> {
                ListeningCoverTheme.seedSnapshot(context, cartoonType.playerThemeSeed())
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

    private fun activeCartoonType(
        context: Context,
        settings: ListeningMotionSettings,
    ): ListeningCartoonType? {
        if (!settings.enabled || settings.effect != ListeningMotionEffect.CARTOON) return null
        return settings.cartoonType.takeIf { it in context.availableCartoonTypes() }
    }
}

/**
 * 使用与封面取色相同的主色采样规则，从三张已验收场景底图离线得到稳定色种。
 * 场景素材更新时应连同对应色种一起重新验收。
 */
@ColorInt
internal fun ListeningCartoonType.playerThemeSeed(): Int = when (this) {
    ListeningCartoonType.SAKURA -> 0xFF084888.toInt()
    ListeningCartoonType.CATS -> 0xFF98B848.toInt()
    ListeningCartoonType.RAIN_NIGHT -> 0xFF081818.toInt()
}
