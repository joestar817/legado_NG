package io.legado.app.help.book

import io.legado.app.data.entities.BookGroup

internal fun isUserBookGroup(groupId: Long): Boolean =
    groupId > 0L || groupId == Long.MIN_VALUE

internal fun joinedBookCount(bookGroups: List<Long>, groupId: Long): Int =
    bookGroups.count { it and groupId != 0L }

internal data class BookGroupSummary(val count: Int, val singleName: String?)

internal fun summarizeBookGroups(groups: List<BookGroup>, membership: Long): BookGroupSummary {
    var count = 0
    var singleName: String? = null
    for (group in groups) {
        if (isUserBookGroup(group.groupId) && group.groupId and membership != 0L) {
            count++
            singleName = if (count == 1) group.groupName else null
        }
    }
    return BookGroupSummary(count, singleName)
}
