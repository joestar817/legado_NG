package io.legado.app.ui.book.manage

internal enum class BookshelfManageSort {
    FOLLOW_BOOKSHELF,
    RECENT_READ,
    RECENT_UPDATE,
    NAME,
    CACHE_SIZE,
    MANUAL;

    val allowsDragging: Boolean get() = this == FOLLOW_BOOKSHELF || this == MANUAL
}

/** 管理页局部排序；不改输入及全局偏好，同值沿用原组内顺序，未知容量排在最后。 */
internal fun <T> List<T>.sortedForBookshelfManage(
    sort: BookshelfManageSort,
    readTime: (T) -> Long,
    updateTime: (T) -> Long,
    compareNames: (T, T) -> Int,
    manualOrder: (T) -> Int,
    cacheBytes: (T) -> Long?,
): List<T> = when (sort) {
    BookshelfManageSort.FOLLOW_BOOKSHELF -> this
    BookshelfManageSort.RECENT_READ -> sortedByDescending(readTime)
    BookshelfManageSort.RECENT_UPDATE -> sortedByDescending(updateTime)
    BookshelfManageSort.NAME -> sortedWith(Comparator(compareNames))
    BookshelfManageSort.CACHE_SIZE -> sortedByDescending { cacheBytes(it) ?: -1L }
    BookshelfManageSort.MANUAL -> sortedBy(manualOrder)
}
