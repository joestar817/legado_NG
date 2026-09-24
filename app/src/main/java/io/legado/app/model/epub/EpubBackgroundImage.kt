package io.legado.app.model.epub

import android.graphics.Bitmap
import android.util.Base64
import io.legado.app.ui.book.read.page.provider.ReadCharStyle
import io.legado.app.ui.book.read.page.provider.ReadHighlightImageRenderer
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Standard SVG image primitives adapt the native tile/stretch/cover result to each inline fragment. */
internal class EpubBackgroundImage private constructor(
    val id: String,
    private val width: Int,
    private val height: Int,
    private val fit: Int,
    private val scale: Float,
    private val data: String,
) {
    fun svg(density: Float): ByteArray {
        require(density.isFinite() && density in .1f..16f)
        val image = when (fit) {
            0 -> {
                val w = width * scale / density
                val h = height * scale / density
                "<defs><pattern id=\"tile\" patternUnits=\"userSpaceOnUse\" width=\"$w\" height=\"$h\">" +
                    "<image width=\"$w\" height=\"$h\" href=\"$data\"/></pattern></defs>" +
                    "<rect width=\"100%\" height=\"100%\" fill=\"url(#tile)\"/>"
            }
            1 -> {
                val size = scale * 100
                val offset = (100 - size) / 2
                "<image x=\"$offset%\" y=\"$offset%\" width=\"$size%\" height=\"$size%\" preserveAspectRatio=\"none\" href=\"$data\"/>"
            }
            else -> {
                val size = scale * 100
                val offset = (100 - size) / 2
                "<svg x=\"$offset%\" y=\"$offset%\" width=\"$size%\" height=\"$size%\" viewBox=\"0 0 $width $height\" " +
                    "preserveAspectRatio=\"xMidYMid slice\" overflow=\"visible\">" +
                    "<image width=\"$width\" height=\"$height\" href=\"$data\"/></svg>"
            }
        }
        // SVG 1.1 WebViews use xlink:href for embedded images.
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"100%\" height=\"100%\" overflow=\"hidden\">${image.replace(" href=", " xlink:href=")}</svg>")
            .toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun create(style: ReadCharStyle): EpubBackgroundImage? {
            if (style.bgImageFit !in 0..2 || style.bgImage.isBlank()) return null
            val bitmap = ReadHighlightImageRenderer.loadBitmap(style.bgImage) ?: return null
            val bytes = ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.toByteArray()
            }
            val scale = ReadHighlightImageRenderer.scale(style)
            val digest = MessageDigest.getInstance("SHA-256").apply {
                update(bytes); update("${style.bgImageFit}/$scale".toByteArray())
            }.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            return EpubBackgroundImage(digest, bitmap.width, bitmap.height, style.bgImageFit, scale,
                "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
    }
}
