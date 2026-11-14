package io.legado.app.help.config

import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadBookDefaultFloatingAppearanceTest {

    @Test
    fun `built in presets keep the approved daytime floating appearance`() {
        val asset = sequenceOf(
            File("src/main/assets/defaultData/readConfig.json"),
            File("app/src/main/assets/defaultData/readConfig.json"),
        ).first(File::isFile)
        val configs = GSON.fromJsonArray<ReadBookConfig.Config>(asset.readText()).getOrThrow()
        val actual = configs.associateBy(ReadBookConfig.Config::name)
        val expected = listOf(
            Expected("秋山书意", 0xFFF8E4D2.toInt(), ReadFloatingColorStyle.FRUIT_SALAD),
            Expected("微信读书", 0xFFC0EDC6.toInt(), ReadFloatingColorStyle.VIBRANT),
            Expected("起点读书", 0xFFEECEA1.toInt(), ReadFloatingColorStyle.FRUIT_SALAD),
            Expected("番茄小说", 0xFFE8E3CE.toInt(), ReadFloatingColorStyle.VIBRANT),
            Expected("经典纯白", 0, ReadFloatingColorStyle.VIBRANT),
            Expected("暖纸书香", 0xFFDDC090.toInt(), ReadFloatingColorStyle.FRUIT_SALAD),
        )

        assertEquals(expected.map(Expected::name), configs.map(ReadBookConfig.Config::name))
        expected.forEach { preset ->
            val config = requireNotNull(actual[preset.name])
            assertEquals(preset.seed, config.readFloatingSeed)
            assertEquals(0, config.readFloatingSeedNight)
            listOf(ReadAppearanceMode.DAY, ReadAppearanceMode.NIGHT).forEach { mode ->
                val effective = resolveEffectiveReadFloatingColor(
                    isEInk = false,
                    globallyFollowsApplication = false,
                    globalColorStyle = ReadFloatingColorStyle.VIBRANT,
                    presetSeed = config.readFloatingSeedForMode(mode),
                    presetColorStyle = config.curReadFloatingColorStyle(),
                )
                assertFalse(effective.followsApplication)
            }
            assertEquals(0, config.curReadFloatingTransparency())
            assertEquals(100, config.curReadFloatingPrimaryStrength())
            assertEquals(preset.style, config.curReadFloatingColorStyle())
            assertEquals(preset.name == "起点读书", config.showHeaderBackButton)
        }
        val qidian = requireNotNull(actual["起点读书"])
        assertEquals(1, qidian.headerMode)
        assertEquals(ReadTipConfig.chapterTitle, qidian.tipHeaderLeft)
        assertEquals(ReadTipConfig.pageAndTotal, qidian.tipFooterLeft)
        assertEquals(ReadTipConfig.timeBattery, qidian.tipFooterRight)
    }

    @Test
    fun `header back button survives preset serialization and missing field stays off`() {
        val config = ReadBookConfig.Config(showHeaderBackButton = true)
        val json = GSON.toJsonTree(config).asJsonObject
        assertTrue(json.get("showHeaderBackButton").asBoolean)
        assertTrue(GSON.fromJson(json, ReadBookConfig.Config::class.java).showHeaderBackButton)
        assertEquals(true, config.toMap()["showHeaderBackButton"])
        assertFalse(GSON.fromJson("{\"headerMode\":1}", ReadBookConfig.Config::class.java).showHeaderBackButton)
    }

    private data class Expected(
        val name: String,
        val seed: Int,
        val style: ReadFloatingColorStyle,
    )
}
