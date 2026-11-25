package io.legado.app.ui.book.info

import android.content.SharedPreferences
import io.legado.app.constant.PreferKey

internal data class BookInfoOtherWorksPreferences(
    val displayed: Boolean,
    val autoLoad: Boolean,
) {
    fun allowsSearch(background: Boolean): Boolean = displayed && (!background || autoLoad)

    companion object {
        const val DEFAULT_AUTO_LOAD = true

        fun read(prefs: SharedPreferences) = BookInfoOtherWorksPreferences(
            displayed = BookInfoSection.OTHER_WORKS !in BookInfoLayoutStore.read(prefs).hidden,
            autoLoad = prefs.getBoolean(PreferKey.autoLoadBookOtherWorks, DEFAULT_AUTO_LOAD),
        )
    }
}
