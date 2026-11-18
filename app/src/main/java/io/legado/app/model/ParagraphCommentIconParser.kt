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
        """'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"|`(?:\\.|[^`\\])*`|//[^\r\n]*|/\*[\s\S]*?\*/|\b(show(?:Cmt|JJCmt|QmCmt|QQCmt|Comments?|Reviews?|ParagraphComment)|fqShowReviews|toReview)\s*\(""",
        RegexOption.IGNORE_CASE,
    )
    private val displayNumber = Regex("""(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?\s*(?:[kKwW万亿千]条?|条)?\+?""")
    private val svgCountCall = Regex("""\bcreateSvg\s*\(\s*['"]?(\d+(?:\.\d+)?\+?)['"]?\s*,""", RegexOption.IGNORE_CASE)
    private val types = setOf("paragraph", "paragraphcomment", "paragraph_comment", "comment", "review", "bubble", "段评")
    private val invalidPercent = Regex("""%(?![0-9a-fA-F]{2})""")

    fun displayText(src: String): String? = displayText(src) { null }

    internal inline fun displayText(src: String, originalImageLabel: () -> String?): String? {
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
        val type = options["type"].orEmpty().lowercase()
        if (type !in types && actionTokens.findAll(action).none {
                val call = it.groups[1]?.value
                call != null && (!call.equals("toReview", true) || type == "lofter")
            }) return null
        for (name in listOf("displaytext", "num", "count", "text", "label")) {
            number(options[name])?.let { return it }
        }
        val image = src.substring(0, marker.start()).trim()
        decodeSvg(image)?.let(::svgLabel)?.let { return it }
        // Generated and remote images go through the reader's normal image provider. Read its result
        // before interpreting a literal generator argument: sources can display 154 as "99+".
        if (js.isNotBlank() || !image.startsWith("data:", true)) originalImageLabel()?.let { return it }
        return number(svgCountCall.find(js)?.groupValues?.get(1))
    }

    private fun number(value: String?): String? = value?.trim()
        ?.takeIf { it.length <= 12 && displayNumber.matches(it) }

    private fun JsonElement?.scriptText(): String? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun svgLabel(svg: String): String? {
        val root = Jsoup.parse(svg, "", Parser.xmlParser()).children().singleOrNull() ?: return null
        if (root.normalName() != "svg") return null
        val labels = root.select("text").asSequence()
            .filterNot { it.closest("defs, symbol, clipPath, mask, pattern, [display=none], [visibility=hidden]") != null }
            .map { it.text().trim() }
        return labels.firstNotNullOfOrNull(::number) ?: labels.firstOrNull { it == "无" }
    }

    internal fun cachedSvgLabel(file: File): String? {
        if (!file.isFile || file.length() !in 1L..MAX_SOURCE_SVG.toLong()) return null
        return try {
            file.inputStream().buffered().use { stream ->
                stream.mark(MAX_SOURCE_SVG)
                var first = stream.read()
                while (first in 0..32) first = stream.read()
                if (first != '<'.code && first != 0xEF) return null
                stream.reset()
                svgLabel(stream.reader(Charsets.UTF_8).readText().removePrefix("\uFEFF"))
            }
        } catch (_: IOException) { null }
    }

    private fun decodeSvg(image: String): String? {
        if (!image.startsWith("data:image/svg+xml", true) || image.length > MAX_SOURCE_SVG) return null
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
