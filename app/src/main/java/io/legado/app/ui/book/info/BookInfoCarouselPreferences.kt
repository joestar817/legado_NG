package io.legado.app.ui.book.info

import android.content.SharedPreferences
import androidx.core.content.edit

internal data class BookInfoCarouselPreferences(
    val autoPlay: Boolean = DEFAULT_AUTO_PLAY,
    val intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
) {
    fun save(prefs: SharedPreferences) {
        prefs.edit {
            putBoolean(KEY_AUTO_PLAY, autoPlay)
            putInt(KEY_INTERVAL, intervalSeconds.coerceIn(INTERVAL_RANGE))
        }
    }

    companion object {
        const val KEY_AUTO_PLAY = "bookInfoCharacterAutoPlay"
        const val KEY_INTERVAL = "bookInfoCharacterIntervalSeconds"
        private const val DEFAULT_AUTO_PLAY = true
        const val DEFAULT_INTERVAL_SECONDS = 3
        val INTERVAL_RANGE = 3..15

        fun read(prefs: SharedPreferences) = BookInfoCarouselPreferences(
            autoPlay = prefs.getBoolean(KEY_AUTO_PLAY, DEFAULT_AUTO_PLAY),
            intervalSeconds = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SECONDS)
                .coerceIn(INTERVAL_RANGE),
        )
    }
}
