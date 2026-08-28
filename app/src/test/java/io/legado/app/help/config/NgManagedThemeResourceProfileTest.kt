package io.legado.app.help.config

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NgManagedThemeResourceProfileTest {

    @Test
    fun `normalization keeps safe package relative resource paths`() {
        val normalized = NgBuiltInThemes.autumn.copy(
            resourceProfile = NgThemeResourceProfile(
                navigation = NgThemeNavigationAssets(
                    bookshelf = "assets/icons/bookshelf.png",
                    explore = "assets\\icons\\explore.png",
                ),
                appFont = "assets/fonts/app.ttf",
            ),
        ).normalized()

        val resources = requireNotNull(normalized.resourceProfile)
        assertEquals("assets/icons/bookshelf.png", resources.navigation.bookshelf)
        assertEquals("assets/icons/explore.png", resources.navigation.explore)
        assertEquals("assets/fonts/app.ttf", resources.appFont)
    }

    @Test
    fun `normalization rejects package traversal and absolute paths`() {
        val normalized = NgBuiltInThemes.autumn.copy(
            resourceProfile = NgThemeResourceProfile(
                navigation = NgThemeNavigationAssets(
                    bookshelf = "../outside.png",
                    explore = "C:/outside.png",
                    rss = "/absolute.png",
                ),
            ),
        ).normalized()

        val navigation = requireNotNull(normalized.resourceProfile).navigation
        assertNull(navigation.bookshelf)
        assertNull(navigation.explore)
        assertNull(navigation.rss)
        assertTrue(normalized.resourceProfile?.appFont == null)
    }

    @Test
    fun `managed theme persists functional cover profile`() {
        val theme = NgBuiltInThemes.autumn.copy(
            coverProfile = NgThemeCoverProfile(
                applyAlbumSelection = true,
                albumId = " album-id ",
                loadOnlyWifi = true,
                useDefault = true,
                showName = false,
                showAuthor = true,
                showNameDark = false,
                showAuthorDark = true,
            ),
        ).normalized()

        val restored = GSON.fromJson(GSON.toJson(theme), NgManagedTheme::class.java)
        val cover = requireNotNull(restored.coverProfile)
        assertTrue(cover.applyAlbumSelection)
        assertEquals("album-id", cover.albumId)
        assertEquals(true, cover.loadOnlyWifi)
        assertEquals(true, cover.useDefault)
        assertEquals(false, cover.showName)
        assertEquals(true, cover.showAuthor)
        assertEquals(false, cover.showNameDark)
        assertEquals(true, cover.showAuthorDark)
    }
}
