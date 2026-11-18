package io.legado.app.help.config

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import com.caverock.androidsvg.SVG
import io.legado.app.utils.defaultSharedPreferences
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import splitties.init.appCtx
import java.util.Locale

enum class ParagraphCommentIconSource { ORIGINAL, BUILTIN, CUSTOM }
enum class ParagraphCommentIconShape { ROUNDED, DIALOG, MINIMAL }

/** Stored as individual preferences, also included in the existing configuration backup. */
data class ParagraphCommentIconConfig(
    val source: ParagraphCommentIconSource = ParagraphCommentIconSource.ORIGINAL,
    val shape: ParagraphCommentIconShape = ParagraphCommentIconShape.ROUNDED,
    val size: Int = 100,
    val followTextColor: Boolean = false,
    val dayColor: Int = 0xFF8C4940.toInt(),
    val nightColor: Int = 0xFFC4CFDE.toInt(),
    val customName: String = "",
    val customSvg: String = "",
) {
    private val resolvedTemplate: String by lazy {
        if (source == ParagraphCommentIconSource.CUSTOM && customSvg.isNotBlank()) {
            customSvg
        } else {
            ParagraphCommentIconSettings.builtin(shape)
        }
    }

    fun template(): String = resolvedTemplate

    fun color(night: Boolean, textColor: Int): Int = when {
        followTextColor -> textColor
        night -> nightColor
        else -> dayColor
    }
}

object ParagraphCommentIconSettings {
    const val MAX_TEMPLATE_BYTES = 128 * 1024
    private const val MAX_EMBEDDED_PIXELS = 1024L * 1024
    private const val PREFIX = "paragraphCommentIcon."
    private val blockedElements = setOf("script", "foreignobject", "iframe", "audio", "video")
    private val placeholders = Regex(
        """\$\{(?:num|displayText|color|displayColor)\}|\{\{(?:num|displayText|color|displayColor)\}\}|\$(?:num|displayText|color|displayColor)\b|(?<![\{\$])\{[nct]\}(?!\})""",
        RegexOption.IGNORE_CASE,
    )

    fun load(): ParagraphCommentIconConfig = ParagraphCommentBubbleSettings.loadConfig()

    internal fun loadAppearance(): ParagraphCommentIconConfig {
        val preferences = appCtx.defaultSharedPreferences
        return ParagraphCommentIconConfig(
            size = preferences.getInt(PREFIX + "size", 100).coerceIn(70, 150),
            followTextColor = preferences.getBoolean(PREFIX + "followTextColor", false),
            dayColor = preferences.getInt(PREFIX + "dayColor", 0xFF8C4940.toInt()),
            nightColor = preferences.getInt(PREFIX + "nightColor", 0xFFC4CFDE.toInt()),
        )
    }

    internal fun saveAppearance(editor: android.content.SharedPreferences.Editor, config: ParagraphCommentIconConfig) {
        editor
            .putInt(PREFIX + "size", config.size.coerceIn(70, 150))
            .putBoolean(PREFIX + "followTextColor", config.followTextColor)
            .putInt(PREFIX + "dayColor", config.dayColor)
            .putInt(PREFIX + "nightColor", config.nightColor)
    }

    fun builtin(shape: ParagraphCommentIconShape): String {
        val path = when (shape) {
            ParagraphCommentIconShape.ROUNDED ->
                "M10 4h31a7 7 0 0 1 7 7v14a7 7 0 0 1-7 7H10a7 7 0 0 1-7-7v-3l-5-4 5-4v-3a7 7 0 0 1 7-7Z"
            ParagraphCommentIconShape.DIALOG ->
                "M10 3h31a7 7 0 0 1 7 7v13a7 7 0 0 1-7 7H19l-8 6v-6h-1a7 7 0 0 1-7-7V10a7 7 0 0 1 7-7Z"
            ParagraphCommentIconShape.MINIMAL -> "M9 6H4v25h5m33-25h5v25h-5"
        }
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="-3 0 54 39">
              <path d="$path" fill="none" stroke="${'$'}{color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
              <text x="25" y="23" text-anchor="middle" font-family="sans-serif" font-size="16" fill="${'$'}{color}">${'$'}{num}</text>
            </svg>
        """.trimIndent()
    }

    @JvmOverloads
    fun resolveTemplate(template: String, text: String, color: Int, textColor: Int = color): String {
        val escapedText = text.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        val svgColor = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
        val svgTextColor = if (textColor == color) svgColor else String.format(Locale.ROOT, "#%06X", textColor and 0xFFFFFF)
        return placeholders.replace(template) { match ->
            when (match.value.trim('$', '{', '}').lowercase(Locale.ROOT)) {
                "n", "num", "displaytext" -> escapedText
                "t" -> svgTextColor
                else -> svgColor
            }
        }
    }

    /** Templates are self-contained SVG. Validation happens before replacing the saved template. */
    fun validate(template: String) {
        require(template.toByteArray(Charsets.UTF_8).size <= MAX_TEMPLATE_BYTES) { "SVG 文件过大" }
        require(!template.contains("<!DOCTYPE", true) && !template.contains("<!ENTITY", true)) { "SVG 格式不支持" }
        val resolved = resolveTemplate(template, "99+", Color.GRAY)
        val document = Jsoup.parse(resolved, "", Parser.xmlParser())
        require(document.children().singleOrNull()?.normalName() == "svg") { "请检查 SVG 格式" }
        var remainingPixels = MAX_EMBEDDED_PIXELS
        document.allElements.forEach { element ->
            val tag = element.normalName().substringAfterLast(':')
            require(tag !in blockedElements) { "SVG 格式不支持" }
            var hasImageSource = false
            element.attributes().forEach { attribute ->
                val key = attribute.key.substringAfterLast(':')
                require(!key.startsWith("on", true)) { "SVG 格式不支持" }
                if (key.equals("href", true)) {
                    if (tag == "image") {
                        remainingPixels -= validateEmbeddedImage(attribute.value.trim(), remainingPixels)
                        hasImageSource = true
                    } else {
                        require(attribute.value.startsWith("#")) { "SVG 不能引用外部文件" }
                    }
                }
            }
            require(tag != "image" || hasImageSource) { "SVG 图片缺少内嵌数据" }
        }
        Regex("""url\(([^)]*)\)""", RegexOption.IGNORE_CASE).findAll(resolved).forEach { match ->
            require(match.groupValues[1].trim().trim('\'', '"').startsWith("#")) { "SVG 不能引用外部文件" }
        }
        require(!resolved.contains("@import", true)) { "SVG 不能引用外部文件" }
        SVG.getFromString(resolved).ensureParagraphIconViewBox()
    }

    private fun validateEmbeddedImage(reference: String, remainingPixels: Long): Long {
        require(reference.startsWith("data:")) { "SVG 不能引用外部图片" }
        val header = reference.substringBefore(',')
        val mime = when (header) {
            "data:image/png;base64" -> "image/png"
            "data:image/jpeg;base64" -> "image/jpeg"
            "data:image/webp;base64" -> "image/webp"
            else -> throw IllegalArgumentException("SVG 图片须内嵌为 PNG、JPEG 或 WebP")
        }
        val bytes = try {
            Base64.decode(reference.substringAfter(',', ""), Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("SVG 内嵌图片数据无效")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType == mime && bounds.outWidth > 0 && bounds.outHeight > 0) { "SVG 内嵌图片数据无效" }
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        require(pixels <= remainingPixels) { "SVG 内嵌图片像素过多" }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        requireNotNull(bitmap) { "SVG 内嵌图片数据无效" }.recycle()
        return pixels
    }
}

internal fun SVG.ensureParagraphIconViewBox() {
    val bounds = documentViewBox
    val width = bounds?.width() ?: documentWidth
    val height = bounds?.height() ?: documentHeight
    require(width.isFinite() && height.isFinite() && width > 0 && height > 0) { "SVG 缺少有效尺寸" }
    if (bounds == null) setDocumentViewBox(0f, 0f, width, height)
}
