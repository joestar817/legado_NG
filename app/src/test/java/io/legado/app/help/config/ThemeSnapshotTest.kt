package io.legado.app.help.config

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSnapshotTest {
    @Test
    fun `JavaScript theme JSON and map keep the existing field contract`() {
        val snapshot = ThemeConfig.ThemeSnapshot(
            themeName = "测试主题",
            isNightTheme = true,
            primaryColor = "#112233",
            accentColor = "#223344",
            backgroundColor = "#334455",
            bottomBackground = "#445566",
            transparentNavBar = true,
            backgroundImgPath = "/background/paper.webp",
            backgroundImgBlur = 4,
        )
        val expected = linkedMapOf(
            "themeName" to "测试主题", "isNightTheme" to true,
            "primaryColor" to "#112233", "accentColor" to "#223344",
            "backgroundColor" to "#334455", "bottomBackground" to "#445566",
            "transparentNavBar" to true, "backgroundImgPath" to "/background/paper.webp",
            "backgroundImgBlur" to 4,
        )
        assertEquals(expected, snapshot.toMap())
        // Explicit wire names survive a naming policy change (and are protected from R8 renaming).
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        assertEquals(gson.toJsonTree(expected), gson.toJsonTree(snapshot))
    }
}
