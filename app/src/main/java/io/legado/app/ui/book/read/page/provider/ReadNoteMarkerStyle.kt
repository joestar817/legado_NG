package io.legado.app.ui.book.read.page.provider

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.ColorUtils

/** Existing note-marker presentation, shared by native and EPUB rendering. No note state. */
internal object ReadNoteMarkerStyle {
    const val SIZE_DP = 12
    const val GAP_DP = 2
    const val TRAILING_GAP_DP = 2
    const val TOUCH_SIZE_DP = 24
    private const val ALPHA = 0.76f

    fun drawable(context: Context) = AppCompatResources.getDrawable(context, R.drawable.ic_ai_chat_suggestion)
        ?.let { DrawableCompat.wrap(it).mutate() }

    fun color(): Int = if (AppConfig.isEInkMode) ReadBookConfig.textColor
        else ColorUtils.withAlpha(ReadBookConfig.textAccentColor, ALPHA)

    fun notes(bookmarks: List<Bookmark>) = bookmarks.asSequence()
        .filter { it.isTextHighlight && it.content.isNotBlank() }
        .sortedBy(Bookmark::time).toList()
}
