package io.legado.app.ui.main

/** Page identities stay stable when optional tabs change their visible positions. */
internal object MainPageIds {
    const val HOME = 4
    const val BOOKSHELF = 0
    const val EXPLORE = 1
    const val RSS = 2
    const val MY = 3

    fun visible(
        showDiscovery: Boolean,
        showRss: Boolean,
        showHome: Boolean = true
    ): Array<Int> = buildList {
        if (showHome) add(HOME)
        add(BOOKSHELF)
        if (showDiscovery) add(EXPLORE)
        if (showRss) add(RSS)
        add(MY)
    }.toTypedArray()

    fun defaultPosition(key: String?, pages: Array<Int>): Int {
        val pageId = when (key) {
            "home" -> HOME
            "bookshelf" -> BOOKSHELF
            "explore" -> EXPLORE
            "rss" -> RSS
            "my" -> MY
            else -> return 0
        }
        return pages.indexOf(pageId).coerceAtLeast(0)
    }

    fun preservePosition(previousPageId: Int?, pages: Array<Int>): Int {
        val previousPosition = pages.indexOf(previousPageId)
        return if (previousPosition >= 0) previousPosition else pages.indexOf(BOOKSHELF)
            .coerceAtLeast(0)
    }
}
