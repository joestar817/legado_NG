package io.legado.app.model.epub

/** Preserve a known legacy declaration before Chromium discards unknown CSS properties.
 * ISO-8859-1 here is a byte-preserving transport, not a change to the stylesheet encoding.
 */
internal object EpubCssCompat {
    fun stylesheet(bytes: ByteArray): ByteArray = declarations(bytes.toString(Charsets.ISO_8859_1))
        .toByteArray(Charsets.ISO_8859_1)

    fun declarations(css: String): String {
        val out = StringBuilder(css.length)
        var i = 0
        var declarationStart = true
        var parentheses = 0
        while (i < css.length) {
            val c = css[i]
            if (c == '/' && css.getOrNull(i + 1) == '*') {
                val end = css.indexOf("*/", i + 2).let { if (it < 0) css.length else it + 2 }
                out.append(css, i, end); i = end; continue
            }
            if (c == '\'' || c == '"') {
                val quote = c
                out.append(c); i++
                while (i < css.length) {
                    val next = css[i++]; out.append(next)
                    if (next == '\\' && i < css.length) out.append(css[i++])
                    else if (next == quote) break
                }
                declarationStart = false
                continue
            }
            if (parentheses == 0 && declarationStart && css.regionMatches(i, "duokan-bleed", 0, 12, ignoreCase = true)) {
                var end = i + 12
                while (end < css.length && css[end].isWhitespace()) end++
                if (css.getOrNull(end) == ':') {
                    out.append("--ng-duokan-bleed")
                    i += 12; declarationStart = false; continue
                }
            }
            out.append(c); i++
            if (c == '(') parentheses++
            if (c == ')') parentheses = (parentheses - 1).coerceAtLeast(0)
            if (parentheses == 0 && (c == '{' || c == ';')) declarationStart = true
            else if (!c.isWhitespace()) declarationStart = false
        }
        return out.toString()
    }
}
