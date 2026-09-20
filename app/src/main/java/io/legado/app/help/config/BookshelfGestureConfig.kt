package io.legado.app.help.config

import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import splitties.init.appCtx

internal enum class BookshelfSwipeMode(val value: Int) {
    MAIN_PAGES(0), GROUPS_FIRST(1), DISABLED(2);

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: MAIN_PAGES
    }
}

internal object BookshelfGestureConfig {
    private const val KEY = "bookshelfSwipeMode"
    var mode: BookshelfSwipeMode
        get() = BookshelfSwipeMode.fromValue(appCtx.getPrefInt(KEY, 0))
        set(value) = appCtx.putPrefInt(KEY, value.value)
}

/** Zero passes the gesture to the main pager; 2 consumes it without changing pages. */
internal fun resolveBookshelfSwipe(mode: BookshelfSwipeMode, direction: Int, canChangeGroup: Boolean): Int =
    when (mode) {
        BookshelfSwipeMode.MAIN_PAGES -> 0
        BookshelfSwipeMode.GROUPS_FIRST -> if (canChangeGroup) direction else 0
        BookshelfSwipeMode.DISABLED -> 2
    }

internal fun allowsBookshelfAiSwipe(mode: BookshelfSwipeMode, canGoToPreviousGroup: Boolean): Boolean =
    mode != BookshelfSwipeMode.GROUPS_FIRST || !canGoToPreviousGroup
