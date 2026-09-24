package io.legado.app.model.epub

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.util.UUID

/** Pure request router, never a network client. The owner must separately close the publication. */
internal class EpubResourceGateway(
    private val session: EpubPublicationSession,
    private val readerRuntime: Boolean = false,
) : Closeable {
    // Native evaluateJavascript runs only bundled code; CSP still denies all book scripts.
    private val responseHeaders = if (readerRuntime) HEADERS + ("Content-Security-Policy" to
        (HEADERS.getValue("Content-Security-Policy").replace("connect-src 'none'", "connect-src 'self'") + " allow-scripts")) else HEADERS
    val origin = "https://epub-${UUID.randomUUID()}.invalid"
    private val host = URI(origin).host
    @Volatile private var closed = false
    @Volatile private var documentPath: String? = null
    private val containerPath = "__ng_reader_${UUID.randomUUID()}/index.html"
    private val contentPath = "__ng_reader_${UUID.randomUUID()}/content.json"
    private data class DocumentContent(val path: String, val html: ByteArray, val data: ByteArray)
    @Volatile private var content: DocumentContent? = null
    @Volatile private var documentContents: Map<String, DocumentContent> = emptyMap()
    private val readerFontPath = "__ng_reader_${UUID.randomUUID()}/font"
    @Volatile private var readerFont: ByteArray? = null
    @Volatile private var readerFontRevision = 0L
    private val titleFontPath = "__ng_reader_${UUID.randomUUID()}/title-font"
    @Volatile private var titleFont: ByteArray? = null
    @Volatile private var titleFontRevision = 0L
    @Volatile private var styleFonts: Map<String, ByteArray> = emptyMap()
    @Volatile private var nineSlices: Map<String, EpubNineSliceImage> = emptyMap()
    @Volatile private var backgrounds: Map<String, EpubBackgroundImage> = emptyMap()

    fun setBackgrounds(images: Map<String, EpubBackgroundImage>) {
        require(images.all { (id, image) -> id == image.id && id.matches(Regex("[a-f0-9]{64}")) })
        backgrounds = images.toMap()
    }

    fun setNineSlices(images: Map<String, EpubNineSliceImage>) {
        require(images.all { (id, image) -> id == image.id && id.matches(Regex("[a-f0-9]{64}")) })
        nineSlices = images.toMap()
    }

    /** Only native rule results can register font bytes; no book-provided filesystem paths. */
    fun setStyleFonts(fonts: Map<String, ByteArray>) {
        require(fonts.keys.all { it.matches(Regex("[a-zA-Z0-9-]+")) })
        styleFonts = fonts.toMap()
    }

    fun setReaderFont(bytes: ByteArray?) {
        if (readerFont !== bytes) { readerFont = bytes; readerFontRevision++ }
    }
    fun readerFontUrl(): String = "$origin/$readerFontPath?revision=$readerFontRevision"

    fun setTitleFont(bytes: ByteArray?) {
        if (titleFont !== bytes) { titleFont = bytes; titleFontRevision++ }
    }
    fun titleFontUrl(): String = "$origin/$titleFontPath?revision=$titleFontRevision"

    fun contentUrl(): String = "$origin/$contentPath"

    fun contentUrl(key: String): String = "$origin/$contentPath?document=$key"

    fun setDocumentContents(values: List<Triple<String, String, Pair<String, String>>>) {
        documentContents = values.associate { (key, path, value) ->
            require(key.matches(Regex("[a-zA-Z0-9-]+")))
            key to DocumentContent(path, value.first.toByteArray(Charsets.UTF_8), value.second.toByteArray(Charsets.UTF_8))
        }
    }

    fun setContent(path: String, html: String, data: String) {
        check(!closed)
        content = DocumentContent(path, html.toByteArray(Charsets.UTF_8), data.toByteArray(Charsets.UTF_8))
    }
    /** App-owned top-level document; publication resources never supply this shell. */
    fun prepareContainer(): String {
        check(!closed) { "EPUB resource gateway is closed" }
        documentPath = containerPath
        return "$origin/$containerPath"
    }

    fun isContainer(url: String): Boolean = resolve(url)?.path == containerPath && documentPath == containerPath

    fun prepareDocument(location: EpubResourceLink): String {
        val url = documentUrl(location)
        documentPath = location.path
        return url
    }

    /** Resolve child URLs without changing the top-level navigation authority. */
    fun documentUrl(location: EpubResourceLink): String {
        check(!closed) { "EPUB resource gateway is closed" }
        val item = session.publication.resourcesByPath[location.path]
            ?: throw EpubFormatException("Unknown EPUB document")
        if (item.mediaType !in DOCUMENT_TYPES) throw EpubFormatException("Resource is not an EPUB document")
        val url = buildString {
            append(origin).append('/').append(EpubPaths.encodePath(location.path))
            location.query?.let { append('?').append(it) }
            location.fragment?.let { append('#').append(URLEncoder.encode(it, "UTF-8").replace("+", "%20")) }
        }
        // Validate caller-created links too; query text must not alter path/fragment boundaries.
        val resolved = resolve(url) ?: throw EpubFormatException("Invalid EPUB document URL")
        if (resolved != location) throw EpubFormatException("Ambiguous EPUB document URL")
        return url
    }

    fun resolve(url: String): EpubResourceLink? = try {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.host != host || uri.rawUserInfo != null || uri.port != -1) null
        else EpubPaths.resolve("mimetype", buildString {
            append(uri.rawPath)
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        })
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: java.net.URISyntaxException) {
        null
    }

    /** Every request receives a response, including misses; returning null would permit networking. */
    fun serve(url: String, method: String, mainFrame: Boolean, requestHeaders: Map<String, String> = emptyMap()): EpubWebResponse {
        if (closed) return error(410, "Gone")
        if (method != "GET" && method != "HEAD") return error(405, "Method Not Allowed")
        val link = resolve(url) ?: return error(403, "Forbidden")
        if (mainFrame && link.path != documentPath) return error(403, "Forbidden")
        val key = link.query?.takeIf { it.startsWith("document=") }?.removePrefix("document=")
        val current = if (key == null) content else documentContents[key]
        if (!mainFrame && link.path.startsWith("__ng_style_background/")) {
            val parts = link.path.removePrefix("__ng_style_background/").split('/')
            if (parts.size != 2 || parts[1] != "image") return error(404, "Not Found")
            val image = backgrounds[parts[0]] ?: return error(404, "Not Found")
            val density = link.query?.takeIf { it.startsWith("density=") }?.removePrefix("density=")?.toFloatOrNull()
            val data = runCatching { image.svg(requireNotNull(density)) }.getOrNull() ?: return error(400, "Bad Request")
            return bytes(data, "image/svg+xml", method)
        }
        if (!mainFrame && link.path.startsWith("__ng_style_nine/")) {
            val parts = link.path.removePrefix("__ng_style_nine/").split('/')
            val image = nineSlices[parts.first()] ?: return error(404, "Not Found")
            val query = link.query.orEmpty().split('&').mapNotNull { item ->
                item.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap()
            val vertical = query["vertical"] == "1"
            return when (parts.getOrNull(1).takeIf { parts.size == 2 }) {
                "image" -> bytes(image.image(vertical), "image/png", method)
                "layout" -> {
                    val data = runCatching { image.layout(
                        requireNotNull(query["height"]?.toFloatOrNull()),
                        requireNotNull(query["line"]?.toFloatOrNull()),
                        requireNotNull(query["density"]?.toFloatOrNull()), vertical,
                        if (query.containsKey("boxWidth")) requireNotNull(query["boxWidth"]?.toFloatOrNull()) else null)
                    }.getOrNull() ?: return error(400, "Bad Request")
                    bytes(data.toString().toByteArray(Charsets.UTF_8), "application/json", method)
                }
                else -> error(404, "Not Found")
            }
        }
        if (!mainFrame && link.path.startsWith("__ng_style_font/")) {
            return styleFonts[link.path.removePrefix("__ng_style_font/")]?.let {
                bytes(it, "application/octet-stream", method)
            } ?: error(404, "Not Found")
        }
        if (!mainFrame && link.path == readerFontPath) {
            return readerFont?.let { bytes(it, "application/octet-stream", method) } ?: error(404, "Not Found")
        }
        if (!mainFrame && link.path == titleFontPath) {
            return titleFont?.let { bytes(it, "application/octet-stream", method) } ?: error(404, "Not Found")
        }
        if (!mainFrame && link.path == contentPath && current != null) {
            return bytes(current.data, "application/json", method)
        }
        if (current?.path == link.path) return bytes(current.html, "text/html", method)
        if (link.path == containerPath) {
            if (!mainFrame || documentPath != containerPath) return error(403, "Forbidden")
            val bytes = CONTAINER.toByteArray(Charsets.UTF_8)
            return EpubWebResponse(200, "OK", "text/html", responseHeaders + ("Content-Length" to bytes.size.toString()),
                ByteArrayInputStream(if (method == "HEAD") ByteArray(0) else bytes))
        }
        val mediaType = session.resourceMediaType(link.path) ?: return error(404, "Not Found")
        if (mainFrame && mediaType !in DOCUMENT_TYPES) return error(403, "Forbidden")
        return try {
            val resource = session.openResource(link.path)
            if (mediaType == "text/css" && resource.size <= 2 * 1024 * 1024) {
                val bytes = resource.use { EpubCssCompat.stylesheet(it.data.readBytes()) }
                return EpubWebResponse(200, "OK", mediaType,
                    responseHeaders + ("Content-Length" to bytes.size.toString()),
                    ByteArrayInputStream(if (method == "HEAD") ByteArray(0) else bytes))
            }
            val media = mediaType.startsWith("video/") || mediaType.startsWith("audio/")
            var headers = responseHeaders + ("Content-Length" to resource.size.toString())
            if (media) headers = headers + ("Accept-Ranges" to "bytes")
            val rangeHeader = requestHeaders.entries.firstOrNull { it.key.equals("Range", true) }?.value
            if (media && method == "GET" && rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = EpubByteRange.parse(rangeHeader, resource.size)
                if (range == null) {
                    resource.close()
                    return EpubWebResponse(416, "Range Not Satisfiable", "text/plain",
                        responseHeaders + mapOf("Content-Range" to "bytes */${resource.size}", "Content-Length" to "0"),
                        ByteArrayInputStream(ByteArray(0)))
                }
                val data = try { EpubRangeInputStream(resource.data, range.first, range.last - range.first + 1) }
                    catch (error: IOException) { resource.close(); throw error }
                return EpubWebResponse(206, "Partial Content", resource.mediaType, headers + mapOf(
                    "Content-Range" to "bytes ${range.first}-${range.last}/${resource.size}",
                    "Content-Length" to (range.last - range.first + 1).toString()), data)
            }
            val stream = if (method == "HEAD") {
                resource.close()
                ByteArrayInputStream(ByteArray(0))
            } else resource.data
            EpubWebResponse(200, "OK", resource.mediaType, headers, stream)
        } catch (_: IOException) {
            error(422, "Unprocessable Content")
        }
    }

    private fun bytes(data: ByteArray, mediaType: String, method: String) = EpubWebResponse(
        200, "OK", mediaType, responseHeaders + mapOf("Content-Length" to data.size.toString(),
            "Content-Type" to if (mediaType.startsWith("text/") || mediaType == "application/json") "$mediaType; charset=utf-8" else mediaType),
        ByteArrayInputStream(if (method == "HEAD") ByteArray(0) else data),
    )

    override fun close() { closed = true; documentPath = null; content = null; documentContents = emptyMap(); readerFont = null; titleFont = null; styleFonts = emptyMap(); nineSlices = emptyMap(); backgrounds = emptyMap() }

    private fun error(status: Int, reason: String) = EpubWebResponse(
        status, reason, "text/plain", responseHeaders + ("Content-Length" to "0"), ByteArrayInputStream(ByteArray(0)),
    )

    private companion object {
        const val CONTAINER = "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>html,body{margin:0;padding:0;overflow:hidden;background:transparent}</style>" +
            "</head><body><main id=\"ng-epub-continuous\"></main></body></html>"
        val DOCUMENT_TYPES = setOf("application/xhtml+xml", "image/svg+xml", "text/html")
        val HEADERS = mapOf(
            "Content-Security-Policy" to "default-src 'none'; script-src 'none'; connect-src 'none'; " +
                "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; " +
                "media-src 'self'; frame-src 'self'; object-src 'self'; base-uri 'none'; " +
                "form-action 'none'; frame-ancestors 'self'; sandbox allow-same-origin",
            "Cache-Control" to "no-store",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer",
        )
    }
}

/** Runtime response; not serialized. No forced charset overrides original XML/CSS encoding. */
internal data class EpubWebResponse(
    val status: Int, val reason: String, val mediaType: String,
    val headers: Map<String, String>, val data: InputStream,
) : Closeable {
    override fun close() = data.close()
}
