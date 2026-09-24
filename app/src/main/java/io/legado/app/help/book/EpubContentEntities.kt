package io.legado.app.help.book

import org.apache.commons.text.StringEscapeUtils
import org.apache.commons.text.translate.CharSequenceTranslator
import java.io.StringWriter
import java.io.Writer

/** The existing local-EPUB normalization, with optional observation of actual entity consumption. */
internal object EpubContentEntities {
    fun normalize(content: String, trace: ContentPositionMap? = null): String {
        if ('&' !in content) return content
        val guarded = if (trace == null) content.replace("&lt;img", "&lt; img", true)
            else trace.regex(content, Regex("&lt;img", RegexOption.IGNORE_CASE), display = false) { "&lt; img" }
        return decode(guarded, trace)
    }

    fun decode(content: String, trace: ContentPositionMap? = null): String {
        if (trace == null) return StringEscapeUtils.unescapeHtml4(content)
        val edits = ArrayList<ContentEdit>()
        val observer = object : CharSequenceTranslator() {
            override fun translate(input: CharSequence, index: Int, out: Writer): Int {
                if (input[index] != '&') return 0
                val result = StringWriter()
                val consumed = StringEscapeUtils.UNESCAPE_HTML4.translate(input, index, result)
                if (consumed > 0) {
                    val value = result.toString()
                    out.write(value)
                    edits.add(ContentEdit(index, Character.offsetByCodePoints(input, index, consumed), value))
                }
                return consumed
            }
        }
        return observer.translate(content).also { trace.record(content, it, edits, display = false) }
    }
}
