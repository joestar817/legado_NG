package io.legado.app.help.config

import io.legado.app.data.entities.Book
import io.legado.app.model.epub.EpubManifestItem
import io.legado.app.model.epub.EpubPageProgressionDirection
import io.legado.app.model.epub.EpubPublication
import io.legado.app.model.epub.EpubResourceLink
import io.legado.app.utils.GSON
import me.ag2s.epublib.domain.EpubBook
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubReadStylePackageTest {
    @Test
    fun `hash named images use actual png jpeg and webp media types without renaming or transcoding`() {
        val images = mapOf(
            "read_header_image_${"a".repeat(64)}" to ("image/png" to java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/6h0AAAAASUVORK5CYII=")),
            "read_footer_image_${"b".repeat(64)}" to ("image/jpeg" to byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())),
            "read_footer_image_${"c".repeat(64)}" to ("image/webp" to "RIFF0000WEBP".toByteArray()),
        )
        val config = ReadBookConfig.Config(bgType = 0)
        val style = EpubReadStylePackage.fromPackage(config, packageBytes(config, images.mapValues { it.value.second }))
        val epub = EpubBook()
        style.addTo(epub)
        images.forEach { (name, expected) ->
            val resource = epub.resources.getByHref("NG/assets/$name")
            assertEquals(expected.first, resource.mediaType.name)
            assertArrayEquals(expected.second, resource.data)
        }
    }

    @Test
    fun `zero title line spacing uses normal rather than collapsing exported title lines`() {
        val config = ReadBookConfig.Config(bgType = 0, titleLineSpacingExtra = 0)
        val style = EpubReadStylePackage.fromPackage(config, packageBytes(config))
        assertTrue(style.titleCss().contains("line-height:normal;"))
        assertFalse(style.titleCss().contains("line-height:0;"))
    }

    @Test
    fun `epub has real font and unmodified transparent image bytes as well as portable preset`() {
        val header = byteArrayOf(0, 10, 20, 30)
        val footer = byteArrayOf(3, 1, 4, 1, 5)
        val font = byteArrayOf(9, 2, 6, 5)
        val config = ReadBookConfig.Config(bgType = 0, titleFont = "title.ttf", headerImage = "header.png",
            headerImageEnabled = true, headerImageWidth = 1600, headerImageHeight = 400,
            footerImage = "footer.png", footerImageEnabled = true, footerImageWidth = 1600, footerImageHeight = 160)
        val bytes = packageBytes(config, mapOf("header.png" to header, "footer.png" to footer, "title.ttf" to font))
        val style = EpubReadStylePackage.fromPackage(config, bytes)
        val epub = EpubBook()
        style.addTo(epub)

        assertArrayEquals(bytes, epub.resources.getById(EpubReadStylePackage.STYLE_ID).data)
        assertEquals(EpubReadStylePackage.STYLE_MIME, epub.resources.getById(EpubReadStylePackage.STYLE_ID).mediaType.name)
        assertArrayEquals(header, epub.resources.getByHref("NG/assets/header.png").data)
        assertArrayEquals(footer, epub.resources.getByHref("NG/assets/footer.png").data)
        assertArrayEquals(font, epub.resources.getByHref("NG/assets/title.ttf").data)
        assertTrue(style.titleCss().contains("../NG/assets/title.ttf"))
        val css = style.decorationCss()
        assertTrue(css.contains("center top,center bottom"))
        assertTrue(css.contains("background-repeat:no-repeat,no-repeat"))
        assertTrue(css.contains("padding-top:calc(min(25vw,25vh)"))
        assertTrue(css.contains("padding-bottom:calc(min(10vw,25vh)"))
        assertFalse(css.contains("position:fixed"))
    }

    @Test
    fun `chapter keeps template heading and parser splitting while escaping title markup`() {
        val config = ReadBookConfig.Config(bgType = 0, titleMode = 1, titleSegType = 1,
            titleSegDistance = 5, titleSegScaling = 1.25f, titleBold = 700,
            titleColor = 0xffbb0000.toInt(), titleColorNight = 0xffeeeeee.toInt())
        val style = EpubReadStylePackage.fromPackage(config, packageBytes(config))
        val title = "第100章 <破灾& {content}>"
        val template = """<html><head><title>{ori_title}</title></head><body><h2 id="chapter" class="custom">{title}</h2>{content}<aside>保留</aside></body></html>"""
        val html = style.chapter(title, "原文", template, "Text/1.html").data.toString(Charsets.UTF_8)
        assertTrue(html.contains("id=\"chapter\" class=\"ng-read-title custom\""))
        assertTrue(html.contains("font-size:100%"))
        assertTrue(html.contains("font-size:125%"))
        assertTrue(html.contains("&lt;破灾&amp; &#123;content&#125;&gt;"))
        assertTrue(html.contains("<p>原文</p>"))
        assertTrue(html.contains("<aside>保留</aside>"))
        assertFalse(html.contains("chapter-sequence-number"))
        assertTrue(style.titleCss().contains("text-align:center"))
        assertTrue(style.titleCss().contains("font-weight:700"))
        assertTrue(style.titleCss().contains("prefers-color-scheme:dark"))
        assertTrue(style.titleCss().contains("line-height:1.2;"))
        assertTrue(html.contains("margin-top:0.96em"))
        val headingText = org.jsoup.Jsoup.parse(html).selectFirst("h2")!!.wholeText()
        assertEquals(title, headingText)
    }

    @Test
    fun `title font inherits portable body font and a head title placeholder is plain text`() {
        val config = ReadBookConfig.Config(bgType = 0, textFont = "font-without-extension", titleFont = "")
        val style = EpubReadStylePackage.fromPackage(config,
            packageBytes(config, mapOf("font-without-extension" to "wOF2test".toByteArray())))
        val epub = EpubBook()
        style.addTo(epub)
        assertEquals("font/woff2", epub.resources.getByHref("NG/assets/font-without-extension").mediaType.name)
        assertTrue(style.titleCss().contains("../NG/assets/font-without-extension"))
        val html = style.chapter("<章&>", "正文", "<html><head><title>前缀{title}</title></head><body><h2 data-class=\"keep\">{title}</h2>{content}</body></html>", "Text/1.html")
            .data.toString(Charsets.UTF_8)
        assertTrue(html.contains("<title>前缀&lt;章&amp;&gt;</title>"))
        assertTrue(html.contains("data-class=\"keep\" class=\"ng-read-title\""))
        assertEquals(1, org.jsoup.Jsoup.parse(html).select(".ng-read-title").size)
    }

    @Test
    fun `hidden title keeps text in source while inactive images reserve no space`() {
        val config = ReadBookConfig.Config(bgType = 0, titleMode = 2, headerImage = "header.png",
            headerImageEnabled = false, headerImageWidth = 1600, headerImageHeight = 400)
        val style = EpubReadStylePackage.fromPackage(config, packageBytes(config, mapOf("header.png" to byteArrayOf(1))))
        assertTrue(style.titleCss().contains("display:none"))
        assertTrue(style.titleMarkup("第1章 标题").contains("第1章 标题"))
        assertFalse(style.decorationCss().contains("padding-"))
        assertFalse(style.decorationCss().contains("url("))
    }

    @Test
    fun `only exact manifest id mime and href permit import and native decoration suppression`() {
        val marker = EpubManifestItem(EpubReadStylePackage.STYLE_ID, EpubReadStylePackage.STYLE_HREF,
            EpubResourceLink("OEBPS/NG/read-style.zip"), EpubReadStylePackage.STYLE_MIME, emptySet())
        val css = EpubManifestItem(EpubReadStylePackage.DECORATION_ID, EpubReadStylePackage.DECORATION_HREF,
            EpubResourceLink("OEBPS/Styles/ng-page-decorations.css"), "text/css", emptySet())
        assertEquals(marker.location!!.path, EpubReadStylePackage.styleLocation(publication(listOf(marker, css))))
        assertEquals(css.location!!.path, EpubReadStylePackage.decorationLocation(publication(listOf(marker, css))))
        for (wrong in listOf(marker.copy(id = "readConfig"), marker.copy(mediaType = "application/zip"),
            marker.copy(href = "Other/read-style.zip"), marker.copy(location = null))) {
            assertNull(EpubReadStylePackage.styleLocation(publication(listOf(wrong, css))))
            assertNull(EpubReadStylePackage.decorationLocation(publication(listOf(wrong, css))))
        }
        assertNull(EpubReadStylePackage.decorationLocation(publication(listOf(marker, css.copy(id = "publisher-css")))))
    }

    @Test
    fun `imported preset binds only to the target book and never replaces its later choice`() {
        val imported = ReadBookConfig.Config(bgType = 0, titleSize = 7, footerImageEnabled = true)
        val first = Book(bookUrl = "file:///first.epub")
        val other = Book(bookUrl = "file:///other.epub")
        var calls = 0
        val importer: (ByteArray) -> ReadStylePackageManager.ImportResult = {
            calls++
            ReadStylePackageManager.ImportResult(imported, "ng", listOf("warning"), File("installed"))
        }
        assertEquals(listOf("warning"), EpubReadStylePackage.bindImportedStyle(first, byteArrayOf(1), importer))
        assertEquals(7, GSON.fromJson(first.config.independentReadStyle, ReadBookConfig.Config::class.java).titleSize)
        assertNull(other.config.independentReadStyle)
        val retained = first.config.independentReadStyle
        EpubReadStylePackage.bindImportedStyle(first, byteArrayOf(2), importer)
        assertEquals(1, calls)
        assertEquals(retained, first.config.independentReadStyle)
    }

    private fun publication(items: List<EpubManifestItem>) = EpubPublication("OEBPS/content.opf", "2.0", null,
        "test", "zh", emptyList(), items, emptyList(), EpubPageProgressionDirection.DEFAULT, emptyList(), emptyList())

    private fun packageBytes(config: ReadBookConfig.Config, resources: Map<String, ByteArray> = emptyMap()): ByteArray {
        val root = GSON.toJsonTree(config).asJsonObject.apply { addProperty("ngResourcePackageVersion", 1) }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            (resources + ("readConfig.json" to GSON.toJson(root).toByteArray())).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
