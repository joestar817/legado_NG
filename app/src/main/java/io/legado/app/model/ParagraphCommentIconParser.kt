package io.legado.app.model

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.legado.app.model.analyzeRule.AnalyzeUrl
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.net.URLDecoder

/** Reads display metadata only. The source URL and its action remain owned by the image column. */
object ParagraphCommentIconParser {
    private const val MAX_SOURCE_SVG = 256 * 1024
    // Skip JS strings and comments so a label such as 'showCmt()' is never a click action.
    private val actionTokens = Regex(
        """'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"|`(?:\\.|[^`\\])*`|//[^\r\n]*|/\*[\s\S]*?\*/|\b(show(?:Cmt|JJCmt|QmCmt|QQCmt|Comments?|Reviews?|ParagraphComment)|(?:fq|qq)ShowReviews|toReview)\s*\(""",
        RegexOption.IGNORE_CASE,
    )
    private val displayNumber = Regex("""(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?\s*(?:[kKwW万亿千]条?|条)?\+?""")
    private val types = setOf("paragraph", "paragraphcomment", "paragraph_comment", "comment", "review", "bubble", "段评")
    private val invalidPercent = Regex("""%(?![0-9a-fA-F]{2})""")
    private val hiddenStyle = Regex(
        """(?:^|[;{])\s*(?:display\s*:\s*none|visibility\s*:\s*(?:hidden|collapse)|opacity\s*:\s*(?:0(?:\.0+)?|\.0+)%?)\s*(?:!\s*important\s*)?(?:[;}]|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val commentHint = Regex("段评|段落评论|本段说|本章说|评论|paragraph\\s+(?:comments?|reviews?)", RegexOption.IGNORE_CASE)

    fun displayText(src: String): String? = displayText(src) { null }

    internal inline fun displayText(src: String, originalImage: () -> File?): String? {
        val marker = AnalyzeUrl.paramPattern.matcher(src)
        if (!marker.find()) return null
        val json = runCatching { JsonParser.parseString(src.substring(marker.end())).asJsonObject }.getOrNull() ?: return null
        val js = json["js"].scriptText().orEmpty()
        // Both reader click handlers look up these exact keys. Never promote another field
        // (or a non-script value) into a paragraph action.
        val click = json["click"]?.takeUnless { it.isJsonNull }
        val action = if (click == null) js else click.scriptText().orEmpty()
        if (action.isBlank()) return null
        val options = json.entrySet().filter { it.value.isJsonPrimitive }
            .associate { it.key.lowercase() to it.value.asString }
        val style = options["style"]
        if (!style.isNullOrBlank() && !style.equals("TEXT", true)) return null
        val image = src.substring(0, marker.start()).trim()
        // Inline SVGs can dispatch any source script. Only older, unmarked image formats
        // need the compatibility check for known paragraph actions.
        val inlineSvg = style.equals("TEXT", true) && isSvgDataUri(image)
        val type = options["type"].orEmpty().lowercase()
        if (!inlineSvg && type !in types && actionTokens.findAll(action).none {
                val call = it.groups[1]?.value
                call != null && (!call.equals("toReview", true) || type == "lofter")
            }) return null
        val inferred = inlineSvg && type !in types
        val metadata = listOf("displaytext", "num", "count", "text", "label")
            .firstNotNullOfOrNull { number(options[it]) }
        if (!inferred && metadata != null) return metadata
        if (isSvgDataUri(image)) {
            if (image.indexOf(',') != image.lastIndex) {
                return decodeSvg(image)?.let { svgLabel(it, inferred, metadata) }
            }
            // Only an empty SVG payload with a separate generator and tap action needs resolving.
            if (js.isBlank() || click == null) return null
        } else if (image.startsWith("data:", true) && js.isBlank()) return null
        return originalImage()?.let { cachedSvgLabel(it, inferred, metadata) }
    }

    private fun number(value: String?): String? = value?.trim()
        ?.takeIf { it.length <= 12 && displayNumber.matches(it) }

    private fun JsonElement?.scriptText(): String? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun svgLabel(svg: String, inferred: Boolean, metadata: String?): String? {
        val root = Jsoup.parse(svg, "", Parser.xmlParser()).children().singleOrNull() ?: return null
        if (root.normalName() != "svg") return null
        // CSS selectors and their cascade require a renderer. Don't infer a count from
        // possibly hidden text when a private icon's stylesheet controls visibility.
        if (inferred && root.select("style").any { hiddenStyle.containsMatchIn(it.html()) }) return null
        root.select("defs, symbol, clipPath, mask, pattern").remove()
        for (element in root.select("[display], [visibility], [opacity], [style]")) {
            val visibility = element.attr("visibility").trim()
            if (element.attr("display").trim().equals("none", true) ||
                visibility.equals("hidden", true) || visibility.equals("collapse", true) ||
                element.attr("opacity").trim().removeSuffix("%").toFloatOrNull() == 0f ||
                hiddenStyle.containsMatchIn(element.attr("style"))) {
                if (element === root) return null
                element.remove()
            }
        }
        val labels = root.select("text").map { it.text().trim() }.filter { it.isNotEmpty() }
        // A private source can supply the number separately for an icon containing no text.
        // For a labelled image use the displayed count; unrelated captions or other values
        // are evidence of a diagram even if its URL options also contain a numeric field.
        if (labels.isEmpty()) return metadata
        var count: String? = null
        for (label in labels) {
            val value = number(label) ?: label.takeIf { it == "无" }
            if (value == null) {
                if (inferred && !commentHint.containsMatchIn(label)) return null
            } else {
                if (count != null && count != value) return null
                count = value
            }
        }
        return count
    }

    private fun cachedSvgLabel(file: File, inferred: Boolean, metadata: String?): String? {
        if (!file.isFile || file.length() !in 1L..MAX_SOURCE_SVG.toLong()) return null
        return try {
            file.inputStream().buffered().use { stream ->
                stream.mark(MAX_SOURCE_SVG)
                var first = stream.read()
                while (first in 0..32) first = stream.read()
                if (first != '<'.code && first != 0xEF) return null
                stream.reset()
                svgLabel(stream.reader(Charsets.UTF_8).readText().removePrefix("\uFEFF"), inferred, metadata)
            }
        } catch (_: IOException) { null }
    }

    private fun isSvgDataUri(image: String): Boolean =
        image.startsWith("data:image/svg+xml;", true) || image.startsWith("data:image/svg+xml,", true)

    private fun decodeSvg(image: String): String? {
        if (!isSvgDataUri(image) || image.length > MAX_SOURCE_SVG) return null
        val comma = image.indexOf(',')
        if (comma < 0) return null
        return runCatching {
            val payload = image.substring(comma + 1)
            if (image.substring(0, comma).contains(";base64", true)) {
                cn.hutool.core.codec.Base64.decodeStr(payload, Charsets.UTF_8)
            } else {
                URLDecoder.decode(invalidPercent.replace(payload, "%25").replace("+", "%2B"), "UTF-8")
            }
        }.getOrNull()
    }
}
