package io.legado.app.help.book

import io.legado.app.data.entities.BookGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookGroupMembershipTest {
    private val groups = listOf(
        BookGroup(groupId = BookGroup.IdAll, groupName = "全部"),
        BookGroup(groupId = BookGroup.IdLocal, groupName = "本地"),
        BookGroup(groupId = BookGroup.IdNoGroup, groupName = "未分组"),
        BookGroup(groupId = 1, groupName = "在读"),
        BookGroup(groupId = 2, groupName = "收藏", show = false),
        BookGroup(groupId = Long.MIN_VALUE, groupName = "第64组"),
    )

    @Test
    fun onlyActualUserGroupsContributeToTheSummary() {
        assertEquals(BookGroupSummary(0, null), summarizeBookGroups(groups, 4))
        assertEquals(BookGroupSummary(1, "在读"), summarizeBookGroups(groups, 1))
        assertEquals(BookGroupSummary(2, null), summarizeBookGroups(groups, 3))
        assertEquals(BookGroupSummary(3, null), summarizeBookGroups(groups, 3L or Long.MIN_VALUE))
    }

    @Test
    fun lastBitIsAUserGroupWhileBuiltInNegativeIdsAreNot() {
        assertTrue(isUserBookGroup(Long.MIN_VALUE))
        assertEquals(BookGroupSummary(1, "第64组"), summarizeBookGroups(groups, Long.MIN_VALUE))
        listOf(BookGroup.IdRoot, BookGroup.IdAll, BookGroup.IdLocal, BookGroup.IdNoGroup,
            BookGroup.IdNovel, BookGroup.IdAudio, BookGroup.IdManga, BookGroup.IdVideo).forEach {
            assertFalse(isUserBookGroup(it))
        }
    }

    @Test
    fun batchMembershipDistinguishesPartialAndCompleteIncludingTheLastBit() {
        val memberships = listOf(1L, 3L, 2L or Long.MIN_VALUE)
        assertEquals(2, joinedBookCount(memberships, 1))
        assertEquals(2, joinedBookCount(memberships, 2))
        assertEquals(1, joinedBookCount(memberships, Long.MIN_VALUE))
        assertEquals(3, joinedBookCount(memberships.map { it or 4 }, 4))
    }

    @Test
    fun all64GroupsHaveOneCountAndDoNotCountBuiltInSelectors() {
        val allGroups = (0 until 64).map { BookGroup(1L shl it, "组$it") } + groups.take(3)
        assertEquals(BookGroupSummary(64, null), summarizeBookGroups(allGroups, -1))
    }
}
