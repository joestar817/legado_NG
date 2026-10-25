package io.legado.app.help.config

import com.google.gson.JsonObject
import io.legado.app.data.entities.Book
import io.legado.app.model.epub.EpubPublication
import io.legado.app.ui.book.read.page.provider.ReadTitleStyleParser
import io.legado.app.utils.GSON
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.domain.MediaType
import me.ag2s.epublib.domain.MediaTypes
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.util.StringUtil
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/** An explicit NG attachment, plus ordinary EPUB styles for readers that do not understand it. */
internal class EpubReadStylePackage private constructor(
    private val config: ReadBookConfig.Config,
    private val packageBytes: ByteArray,
    private val entries: Map<String, ByteArray>,
    private val portable: JsonObject,
) {
    fun addTo(book: EpubBook) {
        require(book.resources.getById(STYLE_ID) == null && book.resources.getByHref(STYLE_HREF) == null) {
            "EPUB 模板占用了 NG 阅读样式资源路径"
        }
        book.resources.add(Resource(STYLE_ID, packageBytes, STYLE_HREF, MediaType(STYLE_MIME, ".zip")))
        val fontNames = listOf("textFont", "titleFont", "headerFont", "footerFont").mapNotNull { portable.string(it) }.toSet()
        entries.filterKeys { it != "readConfig.json" }.forEach { (name, bytes) ->
            val href = "$ASSET_DIR/$name"
            require(book.resources.getByHref(href) == null) { "EPUB 模板资源路径冲突: $href" }
            val mediaType = imageMediaType(bytes) ?: MediaTypes.determineMediaType(href)
                ?.takeUnless { it == MediaTypes.UNKNOWN } ?: if (name in fontNames) {
                val signature = bytes.take(4).toByteArray().toString(Charsets.ISO_8859_1)
                MediaType(when (signature) { "wOF2" -> "font/woff2"; "wOFF" -> "font/woff"; "OTTO" -> "font/otf"; else -> "font/ttf" }, "")
            } else MediaTypes.UNKNOWN
            book.resources.add(Resource(null, bytes, href, mediaType))
        }
        listOf(TITLE_HREF to titleCss(), DECORATION_HREF to decorationCss()).forEach { (href, css) ->
            val id = if (href == DECORATION_HREF) DECORATION_ID else TITLE_ID
            require(book.resources.getByHref(href) == null && book.resources.getById(id) == null) { "EPUB 模板资源路径冲突: $href" }
            book.resources.add(Resource(id,
                css.toByteArray(Charsets.UTF_8), href, MediaType("text/css", ".css")))
        }
    }

    /** Keep the surrounding user template intact; replace only its explicit title placeholder. */
    fun chapter(title: String, content: String, template: String, href: String): Resource {
        val titleText = xml(title)
        val titleMarkup = titleMarkup(title)
        val titleElement = Regex("""<(h[1-6]|div|p|span)([^<>]*)>\s*\{title\}\s*</\1\s*>""", RegexOption.IGNORE_CASE)
        var found = false
        val textTitle = Regex("""<title([^<>]*)>(.*?)</title\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(template) {
                "<title${it.groupValues[1]}>${it.groupValues[2].replace("{title}", titleText)}</title>"
            }
        val model = titleElement.replace(textTitle) { match ->
            found = true
            // Reuse classes/ids from the external template instead of replacing the whole heading.
            val attrs = match.groupValues[2]
            val classAttribute = Regex("""(^|\s)class\s*=\s*(["'])""").find(attrs)
            val withClass = classAttribute?.let { attribute ->
                attrs.replaceRange(attribute.range,
                    "${attribute.groupValues[1]}class=${attribute.groupValues[2]}ng-read-title ")
            } ?: "$attrs class=\"ng-read-title\""
            "<${match.groupValues[1]}$withClass data-ng-read-title=\"1\">$titleMarkup</${match.groupValues[1]}>"
        }.let {
            if (found) it else Regex("""(?<=>)[^<]*\{title\}[^<]*(?=<)""").replace(it) { text ->
                text.value.replace("{title}", "<span class=\"ng-read-title\" data-ng-read-title=\"1\">$titleMarkup</span>")
            }
        }
        // A single replacement pass prevents book text resembling a placeholder being interpreted.
        val html = Regex("""\{title\}|\{ori_title\}|\{content\}""").replace(model) { match ->
            if (match.value == "{content}") StringUtil.formatHtml(content) else titleText
        }
        val links = "<link href=\"../$TITLE_HREF\" type=\"text/css\" rel=\"stylesheet\"/>" +
            "<link href=\"../$DECORATION_HREF\" type=\"text/css\" rel=\"stylesheet\"/>"
        val styled = Regex("</head\\s*>", RegexOption.IGNORE_CASE).find(html)?.let { end ->
            html.substring(0, end.range.first) + links + html.substring(end.range.first)
        } ?: error("EPUB 章节模板缺少 head，无法写入阅读样式")
        return Resource(styled.toByteArray(Charsets.UTF_8), href)
    }

    internal fun titleMarkup(title: String): String {
        val segments = ReadTitleStyleParser.parse(title, config.titleSegType, config.titleSegDistance,
            config.titleSegFlag, config.titleSegScaling)
        var cursor = 0
        var previousScale = 1f
        return segments.mapIndexed { index, segment ->
            val at = title.indexOf(segment.text, cursor).coerceAtLeast(cursor)
            val end = if (index == segments.lastIndex) title.length else at + segment.text.length
            // Preserve source whitespace for EPUB coordinates and a later native segmentation pass.
            val text = title.substring(cursor, end)
            cursor = end
            val gap = if (segment.main) 0f else config.titleLineSpacingSub.coerceAtLeast(0) / 10f * previousScale / segment.scale
            previousScale = segment.scale
            "<span class=\"ng-title-segment\" style=\"font-size:${number(segment.scale * 100)}%;" +
                "margin-top:${number(gap)}em\">${xml(text)}</span>"
        }.joinToString("")
    }

    internal fun titleCss(): String = buildString {
        val font = portableResource("titleFont") ?: portableResource("textFont")
        font?.let { path ->
            append("@font-face{font-family:ng-export-title;src:url('../$path')}\n")
        }
        append(".ng-read-title[data-ng-read-title='1']{")
        append("display:${if (config.titleMode == 2) "none" else "block"};")
        append("text-align:${if (config.titleMode == 1) "center" else "left"};text-indent:0;")
        append("font-size:${config.textSize + config.titleSize}px;font-weight:${weight(config.titleBold)};")
        append("margin:${config.titleTopSpacing.coerceAtLeast(0)}px 0 ${config.titleBottomSpacing.coerceAtLeast(0)}px;")
        val lineHeight = if (config.titleLineSpacingExtra > 0) number(config.titleLineSpacingExtra / 10f) else "normal"
        append("padding:0;line-height:$lineHeight;")
        if (font != null) append("font-family:ng-export-title;")
        append("color:${color(config.titleColor, portable.string("textColor") ?: "#3E3D3B")};}\n")
        append(".ng-read-title[data-ng-read-title='1']>.ng-title-segment{display:block;text-indent:0;text-align:inherit;")
        append("font-family:inherit;font-weight:inherit;font-style:inherit;color:inherit;line-height:inherit;")
        append("background:transparent;border:0;border-radius:0;padding:0;}\n")
        append("@media(prefers-color-scheme:dark){.ng-read-title[data-ng-read-title='1']{color:")
        append(color(config.titleColorNight, color(config.titleColor, portable.string("textColorNight") ?: "#ADADAD")))
        append(";}}\n")
    }

    /** Root viewport backgrounds stay independent of the reader's transformed body. */
    internal fun decorationCss(): String {
        val images = listOfNotNull(
            decoration("headerImage", config.headerImageEnabled, config.headerImageWidth, config.headerImageHeight, "top"),
            decoration("footerImage", config.footerImageEnabled, config.footerImageWidth, config.footerImageHeight, "bottom"),
        )
        if (images.isEmpty()) return "/* NG page decorations: none */\n"
        val cap = if (images.size == 1) 45 else 25
        fun height(image: Decoration) = "min(${number(100f * image.height / image.width)}vw,${cap}vh)"
        fun width(image: Decoration) = "min(100vw,${number(cap.toFloat() * image.width / image.height)}vh)"
        return buildString {
            append("/* NG page decorations v1; only this stylesheet is replaced by native page chrome. */\n")
            append("html:not([data-ng-native-page-decorations]){background-image:${images.joinToString(",") { "url('../${it.path}')" }};")
            append("background-position:${images.joinToString(",") { "center ${it.position}" }};")
            append("background-size:${images.joinToString(",") { "${width(it)} auto" }};")
            append("background-repeat:${images.joinToString(",") { "no-repeat" }};background-attachment:fixed;}\n")
            append("html:not([data-ng-native-page-decorations]) body{box-sizing:border-box;")
            images.firstOrNull { it.position == "top" }?.let {
                append("padding-top:calc(${height(it)} + ${config.paddingTop.coerceAtLeast(0)}px);")
            }
            images.firstOrNull { it.position == "bottom" }?.let {
                append("padding-bottom:calc(${height(it)} + ${config.paddingBottom.coerceAtLeast(0)}px);")
            }
            append("}\n")
        }
    }

    private data class Decoration(val path: String, val width: Int, val height: Int, val position: String)
    private fun decoration(field: String, enabled: Boolean, width: Int, height: Int, position: String): Decoration? =
        if (enabled && width > 0 && height > 0) portableResource(field)?.let { Decoration(it, width, height, position) } else null

    private fun portableResource(field: String): String? = portable.string(field)?.takeIf { it in entries }
        ?.let { "$ASSET_DIR/$it" }

    companion object {
        const val STYLE_ID = "ng-read-style-v1"
        const val STYLE_HREF = "NG/read-style.zip"
        const val STYLE_MIME = "application/vnd.legado.read-style+zip"
        const val DECORATION_ID = "ng-page-decorations-v1"
        const val DECORATION_HREF = "Styles/ng-page-decorations.css"
        const val TITLE_ID = "ng-read-title-v1"
        const val TITLE_HREF = "Styles/ng-read-title.css"
        private const val ASSET_DIR = "NG/assets"
        private const val MAX_PACKAGE_BYTES = 256 * 1024 * 1024

        /** Imported decoration filenames are content hashes, so extensions are not a MIME source. */
        private fun imageMediaType(bytes: ByteArray): MediaType? {
            fun at(offset: Int, vararg expected: Int): Boolean = bytes.size >= offset + expected.size &&
                expected.indices.all { (bytes[offset + it].toInt() and 255) == expected[it] }
            return when {
                at(0, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> MediaTypes.PNG
                at(0, 0xff, 0xd8, 0xff) -> MediaTypes.JPG
                at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> MediaTypes.WEBP
                else -> null
            }
        }

        fun create(book: Book): EpubReadStylePackage {
            val config = ReadBookConfig.getExportConfig(book)
            val output = ByteArrayOutputStream()
            ReadStylePackageManager.export(config, output, readerSettings = null)
            return fromPackage(config, output.toByteArray())
        }

        internal fun fromPackage(config: ReadBookConfig.Config, bytes: ByteArray): EpubReadStylePackage {
            require(bytes.size <= MAX_PACKAGE_BYTES) { "EPUB 阅读样式包过大" }
            val entries = linkedMapOf<String, ByteArray>()
            var size = 0
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name.matches(Regex("[A-Za-z0-9._-]+"))) { "导出排版资源路径无效" }
                    require(entries.size < 512 && entry.name !in entries) { "导出排版资源过多或重名" }
                    val data = zip.readBytes()
                    size += data.size
                    require(size <= MAX_PACKAGE_BYTES) { "EPUB 阅读样式资源过大" }
                    entries[entry.name] = data
                }
            }
            val portable = GSON.fromJson(entries.getValue("readConfig.json").toString(Charsets.UTF_8), JsonObject::class.java)
            require(!portable.has("ngReaderSettings")) { "书籍样式不能携带全局阅读设置" }
            return EpubReadStylePackage(config, bytes, entries, portable)
        }

        /** All three fields must match. A random readConfig.json is never an import trigger. */
        fun styleLocation(publication: EpubPublication): String? = publication.resourcesById[STYLE_ID]?.takeIf {
            it.href == STYLE_HREF && it.mediaType == STYLE_MIME
        }?.location?.path

        fun decorationLocation(publication: EpubPublication): String? {
            if (styleLocation(publication) == null) return null
            return publication.resourcesById[DECORATION_ID]?.takeIf {
                it.href == DECORATION_HREF && it.mediaType == "text/css"
            }?.location?.path
        }

        internal fun bindImportedStyle(book: Book, bytes: ByteArray, importer: (ByteArray) -> ReadStylePackageManager.ImportResult = ReadStylePackageManager::import): List<String> {
            if (book.config.independentReadStyle != null) return emptyList()
            val imported = importer(bytes)
            require(imported.readerSettings == null) { "书籍样式包包含全局阅读设置，已忽略" }
            book.config.independentReadStyle = GSON.toJson(imported.config)
            return imported.warnings
        }

        private fun xml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            .replace("{", "&#123;").replace("}", "&#125;")
        private fun weight(value: Int): Int = when (value) { 0 -> 400; 1 -> 900; 2 -> 300; else -> value.coerceIn(100, 900) }
        private fun number(value: Float): String = String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.')
        private fun color(value: Int, fallback: String): String = if (value == 0) fallback else
            "rgba(${value ushr 16 and 255},${value ushr 8 and 255},${value and 255},${number((value ushr 24) / 255f)})"
        private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
    }
}
