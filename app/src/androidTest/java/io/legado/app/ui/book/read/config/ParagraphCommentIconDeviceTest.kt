package io.legado.app.ui.book.read.config

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.help.config.ParagraphCommentIconConfig
import io.legado.app.help.config.ParagraphCommentIconSettings
import io.legado.app.help.config.ParagraphCommentIconShape
import io.legado.app.help.config.ParagraphCommentIconSource
import io.legado.app.model.ParagraphCommentIconRenderer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Run on Android so template substitution is checked against the platform ICU regex engine. */
@RunWith(AndroidJUnit4::class)
class ParagraphCommentIconDeviceTest {
    @Test
    fun placeholdersResolveOnAndroid() {
        val template = "${'$'}{num}|${'$'}{color}|{{displayText}}|{{displayColor}}|${'$'}num|${'$'}color"
        assertEquals("99+|#8C4940|99+|#8C4940|99+|#8C4940",
            ParagraphCommentIconSettings.resolveTemplate(template, "99+", 0xFF8C4940.toInt()))
    }

    @Test
    fun communityTokensUseTheCountBubbleColorAndReadingTextColor() {
        val resolved = ParagraphCommentIconSettings.resolveTemplate(
            "{n}|{c}|{t}|{n}|{{other}}", "1<&", Color.RED, Color.BLUE,
        )
        assertEquals("1&lt;&amp;|#FF0000|#0000FF|1&lt;&amp;|{{other}}", resolved)
    }

    @Suppress("DEPRECATION")
    @Test
    fun embeddedPicturesAndTextColorsRenderInTheSameTemplate() {
        val formats = listOf(
            Bitmap.CompressFormat.PNG to "image/png",
            Bitmap.CompressFormat.JPEG to "image/jpeg",
            Bitmap.CompressFormat.WEBP to "image/webp",
        )
        for ((format, mime) in formats) {
            val source = embeddedPicture(format, mime)
            val svg = """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 6 2">
                <image width="2" height="2" xlink:href="$source"/>
                <rect x="2" width="2" height="2" fill="{c}"/><rect x="4" width="2" height="2" fill="{t}"/>
                </svg>"""
            ParagraphCommentIconSettings.validate(svg)
            val config = ParagraphCommentIconConfig(source = ParagraphCommentIconSource.CUSTOM, customSvg = svg)
            for (ink in listOf(Color.BLUE, Color.CYAN)) {
                val result = requireNotNull(ParagraphCommentIconRenderer.bitmap(config, "12", Color.RED, 60, 20, ink))
                assertTrue(mime, Color.green(result.getPixel(10, 10)) > 220)
                assertEquals(Color.RED, result.getPixel(30, 10))
                assertEquals(ink, result.getPixel(50, 10))
            }
        }
    }

    @Test
    fun onlyBoundedSelfContainedRasterImagesAreAccepted() {
        fun image(reference: String) = """<image width="2" height="2" href="$reference"/>"""
        fun template(content: String) = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2 2">$content</svg>"""
        val png = embeddedPicture(Bitmap.CompressFormat.PNG, "image/png")
        ParagraphCommentIconSettings.validate(template(image(png)))
        for (reference in listOf("https://example.org/image.png", "file:///image.png", "data:image/png;base64,AAAA",
            png.replace("image/png", "image/webp"))) {
            assertThrows(IllegalArgumentException::class.java) { ParagraphCommentIconSettings.validate(template(image(reference))) }
        }
        val large = embeddedPicture(Bitmap.CompressFormat.PNG, "image/png", side = 768)
        ParagraphCommentIconSettings.validate(template(image(large)))
        assertThrows(IllegalArgumentException::class.java) {
            ParagraphCommentIconSettings.validate(template(image(large) + image(large)))
        }
    }

    @Test
    fun unprefixedSvgImageReferencesRenderInTheBodyAndEmptyViewportsAreRejected() {
        val image = embeddedPicture(Bitmap.CompressFormat.PNG, "image/png")
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="8" height="8"><image width="8" height="8" href="$image"/></svg>"""
        ParagraphCommentIconSettings.validate(svg)
        val config = ParagraphCommentIconConfig(source = ParagraphCommentIconSource.CUSTOM, customSvg = svg)
        val bitmap = requireNotNull(ParagraphCommentIconRenderer.bitmap(config, "99", Color.BLACK, 16, 16))
        assertEquals(Color.GREEN, bitmap.getPixel(8, 8))
        assertThrows(IllegalArgumentException::class.java) {
            ParagraphCommentIconSettings.validate(svg.replace("width=\"8\" height=\"8\"><image", "width=\"8\" height=\"8\" viewBox=\"0 0 0 0\"><image"))
        }
    }

    private fun embeddedPicture(format: Bitmap.CompressFormat, mime: String, side: Int = 2): String {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        val bytes = ByteArrayOutputStream()
        check(bitmap.compress(format, 100, bytes))
        bitmap.recycle()
        return "data:$mime;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    @Test
    fun allBuiltinsContainVisiblePixels() {
        for (shape in ParagraphCommentIconShape.entries) {
            val config = ParagraphCommentIconConfig(source = ParagraphCommentIconSource.BUILTIN, shape = shape)
            ParagraphCommentIconSettings.validate(config.template())
            val bitmap = ParagraphCommentIconRenderer.bitmap(config, "12", config.dayColor, 108, 78)
            assertNotNull("Missing bitmap for $shape", bitmap)
            val pixels = IntArray(108 * 78)
            bitmap!!.getPixels(pixels, 0, 108, 0, 0, 108, 78)
            assertTrue("Blank bitmap for $shape", pixels.any { it ushr 24 != 0 })
        }
    }

    @Test
    fun editorReceivesTheTemplateAndARegisteredGrammar() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = ParagraphCommentIconConfig(source = ParagraphCommentIconSource.BUILTIN)
        val intent = paragraphCommentIconEditorIntent(context, config)
        assertEquals(config.template(), intent.getStringExtra("text"))
        assertFalse(intent.getStringExtra("text").isNullOrBlank())
        assertTrue(intent.getBooleanExtra("returnUnchangedText", false))
        val languages = context.assets.open("textmate/languages.json").bufferedReader().use { it.readText() }
        val grammars = JSONObject(languages).getJSONArray("languages")
        val scope = intent.getStringExtra("languageName")
        assertTrue((0 until grammars.length()).any { grammars.getJSONObject(it).getString("scopeName") == scope })
    }
}
