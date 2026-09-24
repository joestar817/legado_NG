package io.legado.app.utils

import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.help.book.ContentEdit
import io.legado.app.help.book.ContentPositionMap
import java.net.URL
import java.util.regex.Pattern

@Suppress("RegExpRedundantEscape")
object HtmlFormatter {
    private val nbspRegex = "(&nbsp;)+".toRegex()
    private val espRegex = "(&ensp;|&emsp;)".toRegex()
    private val noPrintRegex = "(&thinsp;|&zwnj;|&zwj;|\u2009|\u200C|\u200D)".toRegex()
    private val wrapHtmlRegex = "</?(?:div|p|br|hr|h\\d|article|dd|dl)[^>]*>".toRegex()
    private val commentRegex = "<!--[^>]*-->".toRegex() //注释
    private val notImgHtmlRegex = "</?(?!img)[a-zA-Z]+(?=[ >])[^<>]*>".toRegex()
    private val otherHtmlRegex = "</?[a-zA-Z]+(?=[ >])[^<>]*>".toRegex()
    private val formatImagePattern = Pattern.compile(
        "<img[^>]*\\ssrc\\s*=\\s*['\"]([^'\"{>]*\\{(?:[^{}]|\\{[^}>]+\\})+\\})['\"][^>]*>|<img[^>]*\\sdata-(?:src|original|srcset)\\s*=\\s*['\"]([^'\">]+)['\"][^>]*>|<img[^>]*\\ssrc\\s*=\\s*\"([^\">]+)\"[^>]*>|<img[^>]*\\s(?:data-[^=>]*|src)=\\s*['\"]([^'\">]*)['\"][^>]*>",
        Pattern.CASE_INSENSITIVE
    )
    private val indent1Regex = "\\s*\\n+\\s*".toRegex()
    private val indent2Regex = "^[\\n\\s]+".toRegex()
    private val lastRegex = "[\\n\\s]+$".toRegex()

    @JvmOverloads
    fun format(html: String?, otherRegex: Regex = otherHtmlRegex, trace: ContentPositionMap? = null): String {
        html ?: return ""
        return html.formatReplace(nbspRegex, " ", trace)
            .formatReplace(espRegex, " ", trace)
            .formatReplace(noPrintRegex, "", trace)
            .formatReplace(wrapHtmlRegex, "\n", trace)
            .formatReplace(commentRegex, "", trace)
            .formatReplace(otherRegex, "", trace)
            .formatReplace(indent1Regex, "\n　　", trace)
            .formatReplace(indent2Regex, "　　", trace)
            .formatReplace(lastRegex, "", trace)
    }

    private fun String.formatReplace(regex: Regex, replacement: String, trace: ContentPositionMap?): String =
        trace?.regex(this, regex, display = false) { replacement } ?: regex.replace(this, replacement)

    @JvmOverloads
    fun formatKeepImg(html: String?, redirectUrl: URL? = null, trace: ContentPositionMap? = null): String {
        html ?: return ""
        val keepImgHtml = format(html, notImgHtmlRegex, trace)

        //正则的“|”处于顶端而不处于（）中时，具有类似||的熔断效果，故以此机制简化原来的代码
        val matcher = formatImagePattern.matcher(keepImgHtml)
        var appendPos = 0
        val sb = StringBuilder()
        val edits = if (trace != null) ArrayList<ContentEdit>() else null
        while (matcher.find()) {
            var param = ""
            val rawSource = matcher.group(1)?.let {
                val urlMatcher = AnalyzeUrl.paramPattern.matcher(it)
                if (urlMatcher.find()) {
                    param = ',' + it.substring(urlMatcher.end())
                    it.substring(0, urlMatcher.start())
                } else {
                    it
                }
            } ?: matcher.group(2) ?: matcher.group(3) ?: matcher.group(4).orEmpty()
            val imageUrl = if (rawSource.isBlank()) {
                ""
            } else {
                NetworkUtils.getAbsoluteURL(redirectUrl, rawSource)
            }
            sb.append(keepImgHtml.substring(appendPos, matcher.start()))
            val replacement = if (imageUrl.isNotBlank()) "<img src=\"${imageUrl + param}\">" else ""
            sb.append(replacement)
            edits?.add(ContentEdit(matcher.start(), matcher.end(), replacement))
            appendPos = matcher.end()
        }
        if (appendPos < keepImgHtml.length) sb.append(
            keepImgHtml.substring(
                appendPos,
                keepImgHtml.length
            )
        )
        return sb.toString().also { output -> if (edits != null) trace?.record(keepImgHtml, output, edits, display = false) }
    }
}
