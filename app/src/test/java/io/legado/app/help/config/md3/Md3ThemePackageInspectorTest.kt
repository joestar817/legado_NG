package io.legado.app.help.config.md3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Md3ThemePackageInspectorTest {

    @Test
    fun `portable V1 reports fields assets and cover albums without applying`() {
        val manifest = """
            {
              "formatVersion": 1,
              "name": "测试主题",
              "config": {
                "themeMode": "2",
                "composeEngine": "miuix",
                "themeColor": -1,
                "appFontPath": "assets/app.ttf",
                "futureField": true
              },
              "assets": {
                "background.light": "assets/light.png"
              },
              "coverAlbums": [{
                "ref": "album-1",
                "name": "封面",
                "lightImages": [{"path": "covers/1.png"}],
                "darkImages": []
              }]
            }
        """.trimIndent()
        val inspection = inspectZip(
            "manifest.json" to manifest,
            "assets/light.png" to "image",
            "assets/app.ttf" to "font",
            "covers/1.png" to "cover",
        )

        assertEquals(Md3ThemePackageFormat.PORTABLE_V1, inspection.format)
        assertEquals("测试主题", inspection.name)
        assertEquals(setOf("futureField"), inspection.unknownConfigFields)
        assertEquals("assets/light.png", inspection.assetBindings["background.light"])
        assertEquals("assets/app.ttf", inspection.assetBindings[Md3ThemeAssetSlots.FONT_APP])
        assertTrue(inspection.warnings.any { "Miuix" in it })
        assertTrue(inspection.warnings.any { "主题模式" in it })

        val report = inspection.compatibilityReport()
        assertEquals(4, report.presentKnownFieldCount)
        assertEquals(1, report.coverAlbumCount)
        assertEquals(1, report.coverImageCount)
    }

    @Test
    fun `legacy application theme V1 is recognized`() {
        val manifest = """
            {
              "version": 1,
              "config": {
                "name": "阿尼亚",
                "dayTheme": {
                  "themeName": "日间",
                  "backgroundImgPath": "themes/day/background.png"
                },
                "nightTheme": {
                  "themeName": "夜间",
                  "backgroundImgPath": "themes/night/background.png"
                }
              }
            }
        """.trimIndent()
        val inspection = inspectZip(
            "application_theme.json" to manifest,
            "themes/day/background.png" to "day",
            "themes/night/background.png" to "night",
        )

        assertEquals(Md3ThemePackageFormat.LEGACY_APPLICATION_THEME_V1, inspection.format)
        assertEquals("阿尼亚", inspection.name)
        assertEquals(
            "themes/day/background.png",
            inspection.assetBindings[Md3ThemeAssetSlots.BACKGROUND_LIGHT],
        )
    }

    @Test
    fun `unnamed compatible packages use a neutral theme name`() {
        val portable = inspectZip(
            "manifest.json" to """{"formatVersion":1,"config":{}}""",
        )
        val legacy = inspectZip(
            "application_theme.json" to """{"version":1,"config":{"dayTheme":{}}}""",
        )

        assertEquals("未命名主题", portable.name)
        assertEquals(portable.name, legacy.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsafe archive path is rejected`() {
        inspectZip(
            "manifest.json" to """{"formatVersion":1,"config":{}}""",
            "../escape.png" to "bad",
        )
    }

    @Test(expected = Md3ThemePackageNotRecognizedException::class)
    fun `reading NG package is delegated to native importer`() {
        inspectZip(
            "manifest.json" to """{"format":"reading-ng-theme","version":1,"theme":{}}""",
        )
    }

    @Test(expected = Md3ThemePackageNotRecognizedException::class)
    fun `reading NG export can retain the compatible source manifest`() {
        inspectZip(
            "manifest.json" to """{"format":"reading-ng-theme","version":1,"theme":{}}""",
            "application_theme.json" to """{"version":1,"config":{"dayTheme":{}}}""",
        )
    }

    @Test
    fun `coverage registry contains exact portable field set`() {
        assertEquals(110, Md3ThemeCoverageRegistry.fields.size)
        assertEquals(110, Md3ThemeCoverageRegistry.knownFieldNames.size)
        assertEquals(12, Md3ThemeAssetSlots.all.size)
        assertTrue(Md3ThemeCoverageRegistry.fields.all {
            it.transport == Md3ThemeTransportCoverage.LOSSLESS &&
                it.roundTrip == Md3ThemeRoundTripCoverage.PRESERVE_SOURCE_LITERAL
        })
        assertEquals(
            1,
            Md3ThemeCoverageRegistry.fields.count {
                it.runtimeCoverage == Md3ThemeRuntimeCoverage.NOT_APPLICABLE
            },
        )
    }

    private fun inspectZip(vararg entries: Pair<String, String>): Md3ThemePackageInspection {
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
        return Md3ThemePackageInspector.inspect(ByteArrayInputStream(bytes))
    }
}
