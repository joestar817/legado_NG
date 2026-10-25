package io.legado.app.help.config

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.utils.GSON
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.epub.EpubWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Runs Kotlin Regex on Android ICU, rather than the desktop JDK regex implementation. */
@RunWith(AndroidJUnit4::class)
class EpubReadStylePackageAndroidTest {
    @Test
    fun placeholdersCompileAndReplaceOnAndroidForHeadingAndFallbackTemplates() {
        val config = ReadBookConfig.Config(bgType = 0, titleSegType = 1, titleSegDistance = 5,
            titleSegScaling = 1.25f, titleLineSpacingExtra = 0)
        val style = EpubReadStylePackage.fromPackage(config, packageBytes(config))
        listOf("<h2>{title}</h2>", "<section>{title}</section>").forEach { heading ->
            val html = style.chapter("第100章 <标题&>", "正文", "<html><head><title>{ori_title}</title></head><body>$heading{content}</body></html>", "Text/1.html")
                .data.toString(Charsets.UTF_8)
            assertTrue(html.contains("ng-read-title"))
            assertTrue(html.contains("&lt;标题&amp;&gt;"))
            assertTrue(html.contains("<p>正文</p>"))
            assertFalse(html.contains("{title}"))
            assertFalse(html.contains("{ori_title}"))
            assertFalse(html.contains("{content}"))
        }
        assertTrue(style.titleCss().contains("line-height:normal;"))
    }

    @Test
    fun realPngWithHashOnlyFilenameKeepsBytesAndPngManifestTypeInWrittenEpub() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        val png = try {
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        val name = "read_header_image_${"a".repeat(64)}"
        val config = ReadBookConfig.Config(bgType = 0, headerImage = name, headerImageEnabled = true,
            headerImageWidth = 2, headerImageHeight = 1)
        val style = EpubReadStylePackage.fromPackage(config, packageBytes(config, mapOf(name to png)))
        val epub = EpubBook().apply { metadata.titles.add("图片导出测试") }
        style.addTo(epub)
        epub.addSection("第1章", style.chapter("第1章", "正文", "<html><head><title>{ori_title}</title></head><body><h2>{title}</h2>{content}</body></html>", "Text/1.html"))
        val output = ByteArrayOutputStream()
        EpubWriter().write(epub, output)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val exportedPng = entries.entries.single { it.key.endsWith("NG/assets/$name") }
        assertArrayEquals(png, exportedPng.value)
        val opf = entries.entries.single { it.key.endsWith(".opf") }.value.toString(Charsets.UTF_8)
        assertTrue(opf.contains("NG/assets/$name"))
        assertTrue(opf.contains("media-type=\"image/png\""))
    }

    private fun packageBytes(config: ReadBookConfig.Config, resources: Map<String, ByteArray> = emptyMap()): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            (resources + ("readConfig.json" to GSON.toJson(config).toByteArray())).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
