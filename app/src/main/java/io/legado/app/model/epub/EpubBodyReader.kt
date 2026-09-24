package io.legado.app.model.epub

import io.legado.app.help.book.ContentPositionMap
import io.legado.app.help.book.EpubContentEntities
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.encodeURI
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import java.net.URI
import java.net.URLDecoder

/** The existing local EPUB body extraction, shared by normal loading and source observation. */
internal object EpubBodyReader {
    fun format(elements: Elements, removeRuby: Boolean, capture: EpubSourceCapture?): String {
        //title标签中的内容不需要显示在正文中，去除
        elements.select("title").remove()
        elements.select("[style*=display:none]").remove()
        elements.select("img[src=\"cover.jpeg\"]").forEachIndexed { i, it ->
            if (i > 0) it.remove()
        }
        elements.select("img").forEach {
            if (it.attributesSize() <= 1) {
                return@forEach
            }
            val src = it.attr("src")
            val sourceId = it.attr(EpubSourceCapture.ATTRIBUTE)
            it.clearAttributes()
            it.attr("src", src)
            if (capture != null && sourceId.isNotEmpty()) it.attr(EpubSourceCapture.ATTRIBUTE, sourceId)
        }
        if (removeRuby) {
            capture?.exclude(elements.select("rp, rt"))
            elements.select("rp, rt").remove()
        }
        val html = elements.outerHtml()
        val trace = capture?.let { ContentPositionMap(html) }
        val content = HtmlFormatter.formatKeepImg(html, trace = trace)
        if (capture != null && trace != null) {
            EpubContentEntities.normalize(content, trace)
            capture.finish(elements.toList(), html, trace)
        }
        return content

    }

    fun body(resourceHref: String, html: () -> String, startFragmentId: String?, endFragmentId: String?,
             removeHeadings: Boolean, capture: EpubSourceCapture?): Element {
        // A decoded UTF-8 BOM is not a body character; otherwise HTML reparsing moves the head.
        val document by lazy { Jsoup.parse(html().removePrefix("\uFEFF")) }
        val coverSource = capture?.register(resourceHref, document)
        /**
         * <image width="1038" height="670" xlink:href="..."/>
         * ...titlepage.xhtml
         * 大多数epub文件的封面页都会带有cover，可以一定程度上解决封面读取问题
         */
        if (resourceHref.contains("titlepage.xhtml") ||
            resourceHref.contains("cover")
        ) {
            return Jsoup.parseBodyFragment("<img src=\"cover.jpeg\" />").also { body ->
                if (coverSource != null) body.selectFirst("img")?.attr(EpubSourceCapture.ATTRIBUTE, coverSource)
            }
        }

        // Jsoup可能会修复不规范的xhtml文件 解析处理后再获取
        var bodyElement = document.body()
        bodyElement.children().run {
            select("script").remove()
            select("style").remove()
        }
        // 获取body对应的文本
        var bodyString = bodyElement.outerHtml()
        val originBodyString = bodyString
        /**
         * 某些xhtml文件 章节标题和内容不在一个节点或者不是兄弟节点
         * <div>
         *    <a class="mulu1>目录1</a>
         * </div>
         * <p>....</p>
         * <div>
         *    <a class="mulu2>目录2</a>
         * </div>
         * <p>....</p>
         * 先找到FragmentId对应的Element 然后直接截取之间的html
         */
        if (!startFragmentId.isNullOrBlank()) {
            bodyElement.getElementById(startFragmentId)?.outerHtml()?.let {
                val tagStart = it.substringBefore("\n")
                bodyString = tagStart + bodyString.substringAfter(tagStart)
            }
        }
        if (!endFragmentId.isNullOrBlank() && endFragmentId != startFragmentId) {
            bodyElement.getElementById(endFragmentId)?.outerHtml()?.let {
                val tagStart = it.substringBefore("\n")
                bodyString = bodyString.substringBefore(tagStart)
            }
        }
        //截取过再重新解析
        if (bodyString != originBodyString) {
            bodyElement = Jsoup.parse(bodyString).body()
        }
        /*选择去除正文中的H标签，部分书籍标题与阅读标题重复待优化*/
        if (removeHeadings) {
            bodyElement.run {
                capture?.exclude(select("h1, h2, h3, h4, h5, h6"))
                select("h1, h2, h3, h4, h5, h6").remove()
                //getElementsMatchingOwnText(chapter.title)?.remove()
            }
        }
        bodyElement.select("image").forEach {
            it.tagName("img", Parser.NamespaceHtml)
            it.attr("src", it.attr("xlink:href"))
        }
        bodyElement.select("img").forEach {
            val src = it.attr("src").trim().encodeURI()
            val href = resourceHref.encodeURI()
            val resolvedHref = URLDecoder.decode(URI(href).resolve(src).toString(), "UTF-8")
            it.attr("src", resolvedHref)
        }
        return bodyElement

    }
}
