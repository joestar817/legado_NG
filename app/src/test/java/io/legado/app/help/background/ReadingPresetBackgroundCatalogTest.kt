package io.legado.app.help.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReadingPresetBackgroundCatalogTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun recoversOriginalBackgroundUsingAFontStillReferencedFromThePackage() {
        val parent = temporary.newFolder("packages")
        val root = File(parent, "hash").apply { mkdirs() }
        val image = File(root, "stars-text-safe.png").apply { writeText("image") }
        val font = File(root, "body.ttf").apply { writeText("font") }
        File(root, "readConfig.json").writeText(
            """{"name":"星河秘笺","bgType":2,"bgStr":"stars-text-safe.png"}""",
        )

        assertEquals(root.canonicalFile, ReadingPresetBackgroundCatalog.packageRootForReference(parent, font.path))
        assertEquals(mapOf(image.canonicalPath to "星河秘笺"), ReadingPresetBackgroundCatalog.backgrounds(root))
    }

    @Test
    fun distinctDayAndNightImagesAreNamedSeparately() {
        val root = temporary.newFolder("preset")
        val day = File(root, "day.png").apply { writeText("day") }
        val night = File(root, "night.png").apply { writeText("night") }
        File(root, "readConfig.json").writeText(
            """{"name":"晨夜","bgType":2,"bgStr":"day.png","bgTypeNight":2,"bgStrNight":"night.png"}""",
        )

        assertEquals(mapOf(day.canonicalPath to "晨夜 · 日间", night.canonicalPath to "晨夜 · 夜间"),
            ReadingPresetBackgroundCatalog.backgrounds(root))
    }

    @Test
    fun sharedChineseNamedImageAppearsOnce() {
        val root = temporary.newFolder("chinese")
        val image = File(root, "江湖驿书.png").apply { writeText("image") }
        File(root, "readConfig.json").writeText(
            """{"name":"江湖驿书","bgType":2,"bgStr":"江湖驿书.png","bgTypeNight":2,"bgStrNight":"江湖驿书.png"}""",
        )

        assertEquals(mapOf(image.canonicalPath to "江湖驿书"), ReadingPresetBackgroundCatalog.backgrounds(root))
    }

    @Test
    fun doesNotExposeFilesOutsideThePackage() {
        val parent = temporary.newFolder("packages")
        val root = File(parent, "hash").apply { mkdirs() }
        val outside = File(parent, "outside.png").apply { writeText("outside") }
        File(root, "readConfig.json").writeText(
            """{"name":"示例","bgType":2,"bgStr":"../outside.png"}""",
        )

        assertNull(ReadingPresetBackgroundCatalog.packageRootForReference(parent, outside.path))
        assertEquals(emptyMap<String, String>(), ReadingPresetBackgroundCatalog.backgrounds(root))
    }
}
