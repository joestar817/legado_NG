package io.legado.app.model.epub

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** In-process resource address; not a persistence or JavaScript bridge model. */
internal data class EpubResourceLink(
    val path: String,
    /** Original, encoded query, without '?'. Null and an empty query are distinct. */
    val query: String? = null,
    /** Decoded fragment, without '#'. A plus sign remains a plus sign. */
    val fragment: String? = null,
)

/**
 * ZIP names and URL references are different namespaces. ZIP names are never percent-decoded;
 * URL paths are decoded exactly once before resolving them inside the publication root.
 */
internal object EpubPaths {

    private val schemePrefix = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private const val HEX = "0123456789ABCDEF"

    /** Validates an unencoded, case-sensitive ZIP file name, without changing it. */
    fun entryName(name: String): String {
        require(name.isNotEmpty()) { "An EPUB resource needs a file name" }
        validateCharacters(name)
        require(!name.startsWith('/')) { "An EPUB entry must be root-relative" }
        require(!schemePrefix.containsMatchIn(name)) { "An EPUB entry cannot have a URI scheme" }
        require(name.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "An EPUB entry must have canonical path segments"
        }
        return name
    }

    /**
     * Resolves an original URL against the containing document's canonical ZIP name.
     * A leading single slash refers to the publication root, never the device filesystem.
     * Query text is preserved; its escapes are validated but never used as part of the ZIP key.
     */
    fun resolve(basePath: String, href: String): EpubResourceLink {
        val base = entryName(basePath)
        validateCharacters(href)

        val fragmentStart = href.indexOf('#')
        val resourceReference = if (fragmentStart < 0) href else href.substring(0, fragmentStart)
        val fragment = if (fragmentStart < 0) null else decode(href.substring(fragmentStart + 1))
        val queryStart = resourceReference.indexOf('?')
        val rawPath = if (queryStart < 0) resourceReference else resourceReference.substring(0, queryStart)
        val query = if (queryStart < 0) null else resourceReference.substring(queryStart + 1)
        if (query != null) decode(query)

        require(!rawPath.startsWith("//")) { "Network-path EPUB references are not local resources" }
        require(!schemePrefix.containsMatchIn(rawPath)) { "An EPUB resource must not have a URI scheme" }
        val path = decode(rawPath, rejectEncodedSeparators = true)
        require(!schemePrefix.containsMatchIn(path)) { "An EPUB resource must not have a URI scheme" }
        if (path.isEmpty()) return EpubResourceLink(base, query, fragment)

        val segments = path.removePrefix("/").split('/')
        require(segments.none { it.isEmpty() }) { "An EPUB reference cannot have empty path segments" }
        require(segments.last() != "." && segments.last() != "..") {
            "An EPUB reference must identify a file, not a directory"
        }
        val resolved: MutableList<String> = if (path.startsWith('/')) {
            mutableListOf()
        } else {
            base.split('/').dropLast(1).toMutableList()
        }
        for (segment in segments) {
            when (segment) {
                "." -> Unit
                ".." -> {
                    require(resolved.isNotEmpty()) { "An EPUB reference cannot escape the publication root" }
                    resolved.removeAt(resolved.lastIndex)
                }
                else -> resolved.add(segment)
            }
        }
        return EpubResourceLink(entryName(resolved.joinToString("/")), query, fragment)
    }

    /** Encodes a canonical ZIP name for a URL path; only RFC 3986 unreserved bytes and '/' remain. */
    fun encodePath(path: String): String {
        val bytes = entryName(path).toByteArray(Charsets.UTF_8)
        return buildString {
            for (byte in bytes) {
                val value = byte.toInt() and 0xff
                if (value in 'a'.code..'z'.code || value in 'A'.code..'Z'.code ||
                    value in '0'.code..'9'.code || value == '-'.code || value == '.'.code ||
                    value == '_'.code || value == '~'.code || value == '/'.code
                ) {
                    append(value.toChar())
                } else {
                    append('%')
                    append(HEX[value ushr 4])
                    append(HEX[value and 0x0f])
                }
            }
        }
    }

    private fun decode(value: String, rejectEncodedSeparators: Boolean = false): String {
        val decoded = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '%') {
                decoded.append(value[index++])
                continue
            }
            val bytes = ByteArrayOutputStream()
            while (index < value.length && value[index] == '%') {
                require(index + 2 < value.length) { "Incomplete EPUB URL escape" }
                val high = hexDigit(value[index + 1])
                val low = hexDigit(value[index + 2])
                require(high >= 0 && low >= 0) { "Invalid EPUB URL escape" }
                val byte = high * 16 + low
                require(!rejectEncodedSeparators || (byte != '/'.code && byte != '\\'.code)) {
                    "Encoded path separators are not allowed in EPUB references"
                }
                bytes.write(byte)
                index += 3
            }
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            try {
                decoded.append(decoder.decode(ByteBuffer.wrap(bytes.toByteArray())))
            } catch (error: CharacterCodingException) {
                throw IllegalArgumentException("EPUB URL escapes must contain valid UTF-8", error)
            }
        }
        return decoded.toString().also(::validateCharacters)
    }

    private fun hexDigit(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }

    private fun validateCharacters(value: String) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            require(char != '\\' && !Character.isISOControl(char)) {
                "EPUB resource references cannot contain backslashes or control characters"
            }
            if (Character.isHighSurrogate(char)) {
                require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) {
                    "EPUB resource references must contain valid Unicode"
                }
                index += 2
            } else {
                require(!Character.isLowSurrogate(char)) {
                    "EPUB resource references must contain valid Unicode"
                }
                index++
            }
        }
    }
}
