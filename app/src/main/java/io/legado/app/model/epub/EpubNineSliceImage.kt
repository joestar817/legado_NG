package io.legado.app.model.epub

import android.graphics.Bitmap
import android.graphics.Matrix
import io.legado.app.ui.book.read.page.provider.ReadCharStyle
import io.legado.app.ui.book.read.page.provider.ReadHighlightImageRenderer
import io.legado.app.ui.book.read.page.provider.ReadNineSliceGeometry
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** A registered native rule image, with geometry supplied by the existing renderer. */
internal class EpubNineSliceImage private constructor(
    val id: String,
    private val bitmap: Bitmap,
    private val style: ReadCharStyle,
    private val png: ByteArray,
) {
    private val verticalPng by lazy {
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(90f) }, true)
        try { encode(rotated) } finally { if (rotated !== bitmap) rotated.recycle() }
    }

    fun image(vertical: Boolean): ByteArray = if (vertical) verticalPng else png

    /** Browser lengths are CSS px; native cut widths and the 3dp text inset keep their units. */
    @JvmOverloads
    fun layout(height: Float, lineHeight: Float, density: Float, vertical: Boolean, boxWidth: Float? = null): JSONObject {
        require(height.isFinite() && height > 0 && height <= 4096)
        require(lineHeight.isFinite() && lineHeight > 0 && lineHeight <= 16384)
        require(density.isFinite() && density in 0.1f..16f)
        require(boxWidth == null || boxWidth.isFinite() && boxWidth > 0 && boxWidth <= 16384)
        val cuts = ReadNineSliceGeometry.from(bitmap.width, bitmap.height, style)
        val frame = if (boxWidth == null) cuts.forContentHeight(height * density)
            else cuts.forOuterSize(boxWidth * density, height * density)
        val border = listOf(frame.topHeight / density, frame.rightWidth / density,
            frame.bottomHeight / density, frame.leftWidth / density)
        val slices = listOf(cuts.top, bitmap.width - cuts.right, bitmap.height - cuts.bottom, cuts.left)
        fun <T> orient(values: List<T>) = if (vertical) listOf(values[3], values[0], values[1], values[2]) else values
        return JSONObject().put("border", JSONArray(orient(border)))
            .put("lineHeight", maxOf(lineHeight, height + (frame.topHeight + frame.bottomHeight) / density))
            .put("slice", JSONArray(orient(slices))).put("inset", 3)
            .put("image", "/__ng_style_nine/$id/image?vertical=${if (vertical) 1 else 0}")
    }

    companion object {
        private fun encode(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            it.toByteArray()
        }

        fun create(style: ReadCharStyle): EpubNineSliceImage? {
            if (style.bgImageFit != 3 || style.bgImage.isBlank()) return null
            val bitmap = ReadHighlightImageRenderer.loadBitmap(style.bgImage) ?: return null
            val png = encode(bitmap)
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(png)
            digest.update("${style.npLeft}/${style.npRight}/${style.npTop}/${style.npBottom}".toByteArray())
            val id = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            return EpubNineSliceImage(id, bitmap, style, png)
        }
    }
}
