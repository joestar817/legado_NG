package io.legado.app.help.config

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReadHeaderImageConfigTest {
    @Test
    fun existingConfigWithoutImageFieldsRemainsDisabled() {
        val config = GSON.fromJson(
            """{"bgType":0,"textSize":24,"titleFont":"title.ttf","headerMode":1}""",
            ReadBookConfig.Config::class.java,
        )

        assertFalse(config.headerImageEnabled)
        assertEquals("", config.headerImage)
        assertEquals(0, config.headerImageWidth)
        assertEquals(0, config.headerImageHeight)
        assertFalse(config.footerImageEnabled)
        assertEquals("", config.footerImage)
        assertEquals(0, config.footerImageWidth)
        assertEquals(0, config.footerImageHeight)
        assertEquals(24, config.textSize)
        assertEquals("title.ttf", config.titleFont)
        assertEquals(1, config.headerMode)
    }

    @Test
    fun stableJsonFieldsPreserveSelectedImageWhenEnabledOrDisabled() {
        listOf(true, false).forEach { enabled ->
            val config = ReadBookConfig.Config(
                headerImage = "/private/header.png",
                headerImageEnabled = enabled,
                headerImageWidth = 1920,
                headerImageHeight = 540,
                footerImage = "/private/footer.png",
                footerImageEnabled = !enabled,
                footerImageWidth = 1080,
                footerImageHeight = 120,
                titleColor = -125,
                titleColorNight = -250,
            )
            val json = GSON.toJsonTree(config).asJsonObject

            assertEquals("/private/header.png", json.get("headerImage").asString)
            assertEquals(enabled, json.get("headerImageEnabled").asBoolean)
            assertEquals(1920, json.get("headerImageWidth").asInt)
            assertEquals(540, json.get("headerImageHeight").asInt)
            val restored = GSON.fromJson(json, ReadBookConfig.Config::class.java)
            assertEquals(config.headerImage, restored.headerImage)
            assertEquals(config.headerImageEnabled, restored.headerImageEnabled)
            assertEquals(config.headerImageWidth, restored.headerImageWidth)
            assertEquals(config.headerImageHeight, restored.headerImageHeight)
            assertEquals("/private/footer.png", json.get("footerImage").asString)
            assertEquals(!enabled, json.get("footerImageEnabled").asBoolean)
            assertEquals(1080, json.get("footerImageWidth").asInt)
            assertEquals(120, json.get("footerImageHeight").asInt)
            assertEquals(config.footerImage, restored.footerImage)
            assertEquals(config.footerImageEnabled, restored.footerImageEnabled)
            assertEquals(config.footerImageWidth, restored.footerImageWidth)
            assertEquals(config.footerImageHeight, restored.footerImageHeight)
            assertEquals(config.titleColor, restored.titleColor)
            assertEquals(config.titleColorNight, restored.titleColorNight)
        }
    }

    @Test
    fun independentBookSnapshotUsesSharedLayoutImageWithoutChangingPreset() {
        val preset = ReadBookConfig.Config(
            bgType = 0, bgStr = "#112233",
            headerImage = "preset.png", headerImageEnabled = false,
            headerImageWidth = 200, headerImageHeight = 100,
            footerImage = "preset-footer.png", footerImageEnabled = false,
        )
        val shared = ReadBookConfig.Config(
            headerImage = "shared.png", headerImageEnabled = true,
            headerImageWidth = 1920, headerImageHeight = 540,
            footerImage = "shared-footer.png", footerImageEnabled = true,
            footerImageWidth = 1080, footerImageHeight = 120,
            titleColor = -123, titleColorNight = -456,
        )

        val book = preset.copyForBook(shared)
        assertEquals("#112233", book.bgStr)
        assertEquals("shared.png", book.headerImage)
        assertEquals(true, book.headerImageEnabled)
        assertEquals(1920, book.headerImageWidth)
        assertEquals(540, book.headerImageHeight)
        assertEquals("shared-footer.png", book.footerImage)
        assertEquals(true, book.footerImageEnabled)
        assertEquals(1080, book.footerImageWidth)
        assertEquals(120, book.footerImageHeight)
        assertEquals(-123, book.titleColor)
        assertEquals(-456, book.titleColorNight)
        val copy = book.copy()
        assertEquals(book.headerImage, copy.headerImage)
        assertEquals(book.headerImageEnabled, copy.headerImageEnabled)
        assertEquals(book.headerImageWidth, copy.headerImageWidth)
        assertEquals(book.headerImageHeight, copy.headerImageHeight)

        book.headerImage = "book.png"
        book.headerImageEnabled = false
        book.footerImage = "book-footer.png"
        book.footerImageEnabled = false
        assertEquals("preset.png", preset.headerImage)
        assertFalse(preset.headerImageEnabled)
        assertEquals("shared.png", shared.headerImage)
        assertEquals(true, shared.headerImageEnabled)
        assertEquals("shared.png", copy.headerImage)
        assertEquals("preset-footer.png", preset.footerImage)
        assertFalse(preset.footerImageEnabled)
        assertEquals("shared-footer.png", shared.footerImage)
        assertEquals(true, shared.footerImageEnabled)
        assertEquals("shared-footer.png", copy.footerImage)
    }
}
