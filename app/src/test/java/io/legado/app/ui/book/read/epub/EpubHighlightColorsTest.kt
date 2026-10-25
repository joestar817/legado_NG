package io.legado.app.ui.book.read.epub

import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.ui.book.read.page.provider.ReadHighlightMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EpubHighlightColorsTest {
    @Test
    fun bothPalettesUseTheSameStyleIdAndKeepNightDefaultsAbsent() {
        val rule = ReadHighlightRule(
            pattern = "目标", textColor = 0xFF112233.toInt(), bgColor = 0xFF445566.toInt(),
            underlineMode = 1, underlineColor = 0xFF778899.toInt(),
            nightFollowDay = false, textColorNightMode = ReadHighlightRule.NIGHT_DEFAULT,
            bgColorNightMode = ReadHighlightRule.NIGHT_DEFAULT, underlineColorNight = 0,
        )
        val style = ReadHighlightMatcher(listOf(rule)).match("目标", false)!!.first()!!
        val projected = EpubCharStyles { null }
        projected.add(1, 3, style)
        projected.add(3, 5, style)
        assertEquals(1, projected.styles.length())
        assertEquals(1, projected.nightStyles.length())
        assertEquals("[[1,5,0]]", projected.ranges.toString())
        assertEquals(projected.ranges.toString(), projected.nightRanges.toString())
        val day = projected.styles.getJSONObject(0)
        val night = projected.nightStyles.getJSONObject(0)
        assertEquals(EpubCharStyles.cssColor(rule.textColor!!), day.getString("color"))
        assertEquals(EpubCharStyles.cssColor(rule.bgColor!!), day.getString("background"))
        assertFalse(night.has("color"))
        assertFalse(night.has("background"))
        assertEquals("rgba(0,0,0,0.0)", night.getString("lineColor"))
        assertEquals(day.getInt("underline"), night.getInt("underline"))
        assertEquals(day.getDouble("lineWidth"), night.getDouble("lineWidth"), 0.0)
    }

    @Test
    fun defaultFollowingProjectsIdenticalPalettes() {
        val rule = ReadHighlightRule(pattern = "正文", textColor = 17, bgColor = 0x33000000)
        val style = ReadHighlightMatcher(listOf(rule)).match("正文", false)!!.first()!!
        val projected = EpubCharStyles { null }
        projected.add(0, 2, style)
        assertEquals(projected.styles.toString(), projected.nightStyles.toString())
    }

    @Test
    fun dormantNightColorsDoNotSplitTheOriginalDaytimePaintRun() {
        val first = ReadHighlightRule(pattern = "目", textColor = 17, textColorNight = 34, underlineMode = 3)
        val second = first.copy(pattern = "标", textColorNight = 51)
        val matched = ReadHighlightMatcher(listOf(first, second)).match("目标", false)!!
        assertEquals(matched[0]!!.forTheme(false), matched[1]!!.forTheme(false))
        val projected = EpubCharStyles { null }
        projected.add(0, 1, matched[0]!!)
        projected.add(1, 2, matched[1]!!)
        assertEquals(1, projected.styles.length())
        assertEquals("[[0,2,0]]", projected.ranges.toString())
        assertEquals(2, projected.nightStyles.length())
        assertEquals("[[0,1,0],[1,2,1]]", projected.nightRanges.toString())
    }
}
