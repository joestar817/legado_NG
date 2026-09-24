package io.legado.app.model.epub

import org.jsoup.select.Elements

/** A narrow source-read shortcut; the caller must validate it against native content provenance. */
internal object EpubSingleDocumentSource {
    fun read(
        publication: EpubPublication,
        path: String,
        nextPath: String,
        startFragment: String?,
        endFragment: String?,
        removeHeadings: Boolean,
        removeRuby: Boolean,
        html: () -> String,
    ): EpubSourceChapter? {
        val paths = publication.spine.map { publication.resourcesById[it.idref]?.location?.path }
        val index = paths.indexOf(path)
        if (index < 0 || paths.count { it == path } != 1 || nextPath.isEmpty()) return null
        if (publication.layoutFor(publication.spine[index]) != EpubLayout.REFLOWABLE) return null
        if (publication.resourcesByPath[path]?.mediaType !in setOf("application/xhtml+xml", "text/html")) return null
        if (nextPath != path) {
            // A fragment in the following resource includes part of that second document.
            if (!endFragment.isNullOrBlank() || paths.getOrNull(index + 1) != nextPath ||
                paths.count { it == nextPath } != 1) return null
        }
        val capture = EpubSourceCapture().apply { sourceOccurrence = index }
        val body = EpubBodyReader.body(path, html, startFragment, endFragment, removeHeadings, capture)
        EpubBodyReader.format(Elements(body), removeRuby, capture)
        return capture.result
    }
}
