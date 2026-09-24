package io.legado.app.ui.book.read.epub

import androidx.core.graphics.PathParser
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.entities.column.BaseColumn
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.provider.ReadCharStyle
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import io.legado.app.ui.book.read.page.provider.ReadHighlightInput
import io.legado.app.ui.book.read.page.provider.ReadHighlightMatcher
import io.legado.app.model.epub.EpubNineSliceImage
import io.legado.app.model.epub.EpubBackgroundImage

/** Projects already matched native styles. This layer never evaluates highlighting rules. */
internal class EpubCharStyles(private val loadFont: (String) -> ByteArray?) {
    val styles = JSONArray()
    val ranges = JSONArray()
    val fonts = LinkedHashMap<String, ByteArray>()
    val nineSlices = LinkedHashMap<String, EpubNineSliceImage>()
    val backgrounds = LinkedHashMap<String, EpubBackgroundImage>()
    private val backgroundPaths = HashMap<List<Any>, EpubBackgroundImage?>()
    private val nineSlicePaths = HashMap<List<Any>, EpubNineSliceImage?>()
    private val identities = LinkedHashMap<ReadCharStyle, Int>()
    private val fontPaths = HashMap<String, String?>()

    fun add(from: Int, to: Int, style: ReadCharStyle) {
        if (to <= from) return
        val id = identities.getOrPut(style) {
            val font = if (style.fontPath.isBlank()) null else fontPaths.getOrPut(style.fontPath) {
                loadFont(style.fontPath)?.let { bytes ->
                    val name = MessageDigest.getInstance("SHA-256").digest(bytes)
                        .joinToString("") { "%02x".format(it.toInt() and 255) }
                    fonts[name] = bytes
                    "/__ng_style_font/$name"
                }
            }
            val value = JSONObject()
            style.textColor?.let { value.put("color", cssColor(it)) }
            style.bgColor?.let { value.put("background", cssColor(it)) }
            if (style.bgImageFit == 3 && style.bgImage.isNotBlank()) {
                val imageKey = listOf(style.bgImage, style.npLeft, style.npRight, style.npTop, style.npBottom)
                nineSlicePaths.getOrPut(imageKey) { EpubNineSliceImage.create(style) }?.let {
                    nineSlices[it.id] = it
                    value.put("nineSlice", "/__ng_style_nine/${it.id}")
                }
            } else if (style.bgImage.isNotBlank()) {
                val imageKey = listOf(style.bgImage, style.bgImageFit, style.bgImageScale)
                backgroundPaths.getOrPut(imageKey) { EpubBackgroundImage.create(style) }?.let {
                    backgrounds[it.id] = it
                    value.put("imageBackground", "/__ng_style_background/${it.id}/image")
                        .put("imageInset", io.legado.app.ui.book.read.page.provider.ReadHighlightImageRenderer.CONTENT_INSET_DP)
                }
            }
            if ((style.fontPath.isBlank() || font != null) &&
                (style.fontPath.isNotBlank() || style.fontWeight != 400 || style.isItalic)) {
                value.put("weight", style.fontWeight).put("italic", style.isItalic)
                font?.let { value.put("font", it) }
            }
            if (style.underlineMode in 1..5) {
                value.put("underline", style.underlineMode).put("lineWidth", style.underlineWidth)
                    .put("lineOffset", style.underlineOffset)
                (style.underlineColor ?: style.textColor)?.let { value.put("lineColor", cssColor(it)) }
                if (style.underlineMode == 5 && runCatching {
                        PathParser.createPathFromPathData(style.underlineSvgPath)
                    }.getOrNull() != null) value.put("linePath", style.underlineSvgPath)
            }
            styles.put(value)
            styles.length() - 1
        }
        val previous = ranges.optJSONArray(ranges.length() - 1)
        if (previous != null && previous.getInt(1) == from && previous.getInt(2) == id) previous.put(1, to)
        else ranges.put(JSONArray().put(from).put(to).put(id))
    }

    fun addLine(start: Int, columns: List<BaseColumn>) {
        var position = start
        columns.forEach { column ->
            if (column is TextBaseColumn) {
                (column as? TextColumn)?.readStyle?.let { add(position, position + column.charData.length, it) }
                position += column.charData.length
            } else if (column is ImageColumn) {
                // Inline media occupies one placeholder in the already prepared chapter text.
                position++
            }
        }
    }

    fun rematch(inputs: List<ReadHighlightInput>, matcher: ReadHighlightMatcher) {
        inputs.forEach { input ->
            val values = matcher.match(input.text, input.isTitle) ?: return@forEach
            var start = 0
            while (start < values.size) {
                val style = values[start]
                var end = start + 1
                while (end < values.size && values[end] == style) end++
                if (style != null) add(input.start + start, input.start + end, style)
                start = end
            }
        }
    }

    companion object {
        fun cssColor(value: Int) =
            "rgba(${value ushr 16 and 255},${value ushr 8 and 255},${value and 255},${(value ushr 24) / 255.0})"

        fun create(chapter: TextChapter, loadFont: (String) -> ByteArray?): EpubCharStyles = EpubCharStyles(loadFont).apply {
            chapter.pages.forEach { page -> page.lines.forEach { line ->
                addLine(line.chapterPosition, line.columns)
            } }
        }
    }
}
