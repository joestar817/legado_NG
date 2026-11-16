package io.legado.app.ui.main.bookshelf.style1

import io.legado.app.data.entities.BookGroup

/** 分组变化时保留仍存在的选择；首次加载或原分组消失时沿用保存下标。 */
internal fun resolveUpdatedBookshelfGroupIndex(
    groups: List<BookGroup>,
    selectedGroupId: Long?,
    savedPosition: Int,
): Int {
    if (groups.isEmpty()) return 0
    val selectedIndex = selectedGroupId?.let { id -> groups.indexOfFirst { it.groupId == id } } ?: -1
    return if (selectedIndex >= 0) selectedIndex else savedPosition.coerceIn(0, groups.lastIndex)
}
