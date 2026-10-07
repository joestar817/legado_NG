package io.legado.app.help.config

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NgThemeDrawerProfileTest {
    @Test
    fun `four sources round trip with independent custom values for each day`() {
        listOf("theme_color", "custom_color", "theme_image", "custom_image").forEach { source ->
            val profile = NgThemeDrawerProfile(
                source = source,
                light = NgThemeDrawerStyle(backgroundColor = 0xFF123456.toInt()),
                dark = NgThemeDrawerStyle(imagePath = "private/night.webp"),
            )
            val restored = GSON.fromJson(GSON.toJson(profile), NgThemeDrawerProfile::class.java).normalized()
            assertEquals(profile, restored)
            assertEquals(source, restored.updated(true, NgThemeDrawerStyle()).source)
            assertEquals(profile.light, restored.forNight(false))
        }
    }

    @Test
    fun `missing or unknown source defaults to theme color without migrating old styling`() {
        val restored = GSON.fromJson(
            """{"source":"unsupported","light":null,"dark":{"mode":"color","cardColor":42,"decoration":"night"},"cornerRadiusDp":38}""",
            NgThemeDrawerProfile::class.java,
        ).normalized()
        assertEquals(NgThemeDrawerProfile(), restored)
        val missing = GSON.fromJson("{}", NgThemeDrawerProfile::class.java).normalized()
        assertEquals(NgThemeDrawerProfile(), missing)
        val explicitNull = GSON.fromJson("""{"source":null,"dark":null}""", NgThemeDrawerProfile::class.java).normalized()
        assertEquals(NgThemeDrawerProfile(), explicitNull)
        val serialized = GSON.toJson(restored)
        listOf("mode", "cardColor", "decoration", "cornerRadiusDp").forEach { assertFalse(serialized.contains(it)) }
    }

    @Test
    fun `custom values normalize without introducing any additional color roles`() {
        val normalized = NgThemeDrawerStyle(backgroundColor = 0x00123456, imagePath = "   ").normalized()
        assertEquals(0xFF123456.toInt(), normalized.backgroundColor)
        assertNull(normalized.imagePath)
        assertEquals("custom.webp", NgThemeDrawerStyle(imagePath = " custom.webp ").normalized().imagePath)
    }

    @Test
    fun `asset references reject traversal absolute paths and URI schemes`() {
        assertTrue(isSafeThemeAssetPath("assets/background-drawer-light.webp"))
        listOf(
            "", "../outside.png", "assets/../image.png", "/outside.png",
            "C:/outside.png", "asset://secret", "file:///secret", "assets\\image.png",
            "assets//image.png", "assets/./image.png",
        ).forEach { assertFalse(it, isSafeThemeAssetPath(it)) }
    }
}
