package io.legado.app.ui.book.read.config

import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class ReadStyleEditTargetTest {
    @Test
    fun pickerResultKeepsOriginalObjectAndModeAfterAnotherSelection() {
        val original = ReadBookConfig.Config(bgType = 0, bgStr = "#112233")
        val sharedLayout = ReadBookConfig.Config(bgAlpha = 63)
        val session = ReadStyleEditorSession()
        val target = ReadStyleEditTarget(original, sharedLayout, ReadAppearanceMode.NIGHT, "book")
        session.backgroundTarget = target
        session.selectPresetMode(ReadAppearanceMode.DAY.ordinal)
        val returned = session.backgroundTarget!!
        returned.appearance.setBackgroundForMode(0, "#445566", returned.mode)
        assertSame(original, returned.appearance)
        assertSame(sharedLayout, returned.layout)
        assertEquals(ReadAppearanceMode.DAY, session.presetMode)
        assertEquals("#112233", original.backgroundForMode(ReadAppearanceMode.DAY))
        assertEquals("#445566", original.backgroundForMode(ReadAppearanceMode.NIGHT))
        assertEquals(63, sharedLayout.bgAlpha)
    }

    @Test
    fun nightPreviewUsesTargetBackgroundWithoutChangingRuntimeNinePatchFlag() {
        val preset = ReadBookConfig.Config(bgType = 0, bgStr = "#112233", bgTypeNight = 0, bgStrNight = "#445566")
        val layout = ReadBookConfig.Config(bgAlpha = 100)
        val previousNinePatchFlag = ReadBookConfig.isNineBgImg
        ReadBookConfig.isNineBgImg = true
        val target = ReadStyleEditTarget(preset, layout, ReadAppearanceMode.NIGHT, null)
        val bitmap = target.renderBackground(8, 8)
        try {
            assertEquals(0xFF445566.toInt(), bitmap.getPixel(4, 4))
            assertTrue(ReadBookConfig.isNineBgImg)
        } finally {
            bitmap.recycle()
            ReadBookConfig.isNineBgImg = previousNinePatchFlag
        }
    }
}
