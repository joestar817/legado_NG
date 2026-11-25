package io.legado.app.ui.book.info

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import io.legado.app.R

internal enum class BookInfoSection(val key: String, @param:StringRes val titleRes: Int) {
    METADATA("meta", R.string.book_info),
    INTRO("intro", R.string.book_intro_short),
    CHARACTERS("characters", R.string.book_characters),
    OTHER_WORKS("otherWorks", R.string.book_other_works),
}

internal data class BookInfoLayout(
    val coverPercent: Int = DEFAULT_COVER_PERCENT,
    val order: List<BookInfoSection> = BookInfoSection.entries,
    val hidden: Set<BookInfoSection> = emptySet(),
) {
    fun visibleSections(charactersAvailable: Boolean, otherWorksAvailable: Boolean) =
        order.filter { section ->
            section !in hidden && when (section) {
                BookInfoSection.METADATA, BookInfoSection.INTRO -> true
                BookInfoSection.CHARACTERS -> charactersAvailable
                BookInfoSection.OTHER_WORKS -> otherWorksAvailable
            }
        }

    fun setVisible(section: BookInfoSection, visible: Boolean): BookInfoLayout =
        copy(hidden = if (visible) hidden - section else hidden + section)

    fun move(fromKey: String, toKey: String): BookInfoLayout {
        val from = order.indexOfFirst { it.key == fromKey }
        val to = order.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0 || from == to) return this
        return copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun encode(): String {
        val orderKeys = order.joinToString(",") { it.key }
        val hiddenKeys = order.filter { it in hidden }.joinToString(",") { it.key }
        return "$coverPercent|$orderKeys|$hiddenKeys"
    }

    companion object {
        const val DEFAULT_COVER_PERCENT = 100
        val COVER_PERCENT_RANGE = 70..160

        private val sectionsByKey = BookInfoSection.entries.associateBy { it.key }

        fun decode(encoded: String?): BookInfoLayout {
            val parts = encoded?.split('|', limit = 3) ?: return BookInfoLayout()
            val savedOrder = parts.getOrNull(1).orEmpty().split(',').mapNotNull(sectionsByKey::get)
            val hidden = parts.getOrNull(2).orEmpty().split(',').mapNotNull(sectionsByKey::get).toSet()
            return BookInfoLayout(
                coverPercent = (parts[0].toIntOrNull() ?: DEFAULT_COVER_PERCENT)
                    .coerceIn(COVER_PERCENT_RANGE),
                order = (savedOrder + BookInfoSection.entries).distinct(),
                hidden = hidden,
            )
        }
    }
}

internal object BookInfoLayoutStore {
    const val KEY = "bookInfoLayout"

    fun read(prefs: SharedPreferences): BookInfoLayout =
        BookInfoLayout.decode(prefs.getString(KEY, null))

    fun save(prefs: SharedPreferences, layout: BookInfoLayout) {
        prefs.edit { putString(KEY, layout.encode()) }
    }
}
