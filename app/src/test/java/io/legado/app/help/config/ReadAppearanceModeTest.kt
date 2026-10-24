package io.legado.app.help.config

import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = InterfaceFontTestApplication::class)
class ReadAppearanceModeTest {
    @Test
    fun editingNightDoesNotChangeDayInkOrReaderPolicy() {
        ReadBookConfig.selectThemeMode(ReadThemeMode.DAY, systemNightMode = false)
        val preset = ReadBookConfig.Config(bgType = 0, bgStr = "#112233", bgStrNight = "#445566")
        val day = preset.textColorForMode(ReadAppearanceMode.DAY)
        val ink = preset.textColorForMode(ReadAppearanceMode.EINK)
        val dayAccent = preset.textAccentColorForMode(ReadAppearanceMode.DAY)
        preset.setTextColorForMode(0, ReadAppearanceMode.NIGHT)
        preset.setTextAccentColorForMode(0xFF789ABC.toInt(), ReadAppearanceMode.NIGHT)
        preset.setBackgroundForMode(0, "#123456", ReadAppearanceMode.NIGHT)
        preset.setUnderlineColorForMode(0xFFABCDEF.toInt(), ReadAppearanceMode.NIGHT)
        assertEquals(0, preset.textColorForMode(ReadAppearanceMode.NIGHT))
        assertEquals(day, preset.textColorForMode(ReadAppearanceMode.DAY))
        assertEquals(ink, preset.textColorForMode(ReadAppearanceMode.EINK))
        assertEquals(dayAccent, preset.textAccentColorForMode(ReadAppearanceMode.DAY))
        assertEquals("#112233", preset.backgroundForMode(ReadAppearanceMode.DAY))
        assertEquals("#123456", preset.backgroundForMode(ReadAppearanceMode.NIGHT))
        assertEquals(0xFFABCDEF.toInt(), preset.underlineColorForMode(ReadAppearanceMode.NIGHT))
        assertFalse(ReadBookConfig.isNightTheme)
        assertEquals(ReadThemeMode.DAY, ReadBookConfig.currentThemeMode())
    }

    @Test
    fun editingDayDuringSystemNightKeepsSystemFollowingAndNightValues() {
        ReadBookConfig.selectThemeMode(ReadThemeMode.FOLLOW_SYSTEM, systemNightMode = true)
        val preset = ReadBookConfig.Config()
        val night = preset.textColorForMode(ReadAppearanceMode.NIGHT)
        preset.setTextColorForMode(0xFF123456.toInt(), ReadAppearanceMode.DAY)
        preset.setBackgroundForMode(0, "#FEDCBA", ReadAppearanceMode.DAY)
        assertEquals(night, preset.textColorForMode(ReadAppearanceMode.NIGHT))
        assertTrue(ReadBookConfig.isNightTheme)
        assertEquals(ReadThemeMode.FOLLOW_SYSTEM, ReadBookConfig.currentThemeMode())
    }

    @Test
    fun inkSlotAndFloatingFollowFlagsStayIndependent() {
        val preset = ReadBookConfig.Config(readFloatingSeed = 0xFF445566.toInt())
        preset.setTextColorForMode(17, ReadAppearanceMode.EINK)
        preset.setBackgroundForMode(0, "#FFFFFF", ReadAppearanceMode.EINK)
        val day = preset.textColorForMode(ReadAppearanceMode.DAY)
        preset.setTextColorForMode(34, ReadAppearanceMode.NIGHT)
        assertEquals(17, preset.textColorForMode(ReadAppearanceMode.EINK))
        assertEquals(day, preset.textColorForMode(ReadAppearanceMode.DAY))
        preset.clearReadFloatingSeedForMode(ReadAppearanceMode.NIGHT)
        assertTrue(preset.readFloatingFollowsApplicationForMode(ReadAppearanceMode.NIGHT))
        assertEquals(0xFF445566.toInt(), preset.readFloatingSeedForMode(ReadAppearanceMode.DAY))
        preset.setReadFloatingSeedForMode(0xFF778899.toInt(), ReadAppearanceMode.NIGHT)
        assertFalse(preset.readFloatingFollowsApplicationForMode(ReadAppearanceMode.NIGHT))
        val restored = GSON.fromJson(GSON.toJson(preset), ReadBookConfig.Config::class.java)
        assertEquals(preset.textColorForMode(ReadAppearanceMode.NIGHT), restored.textColorForMode(ReadAppearanceMode.NIGHT))
        assertEquals(preset.readFloatingSeedForMode(ReadAppearanceMode.NIGHT), restored.readFloatingSeedForMode(ReadAppearanceMode.NIGHT))
        assertFalse(GSON.toJson(preset).contains("editorMode"))
    }
}
