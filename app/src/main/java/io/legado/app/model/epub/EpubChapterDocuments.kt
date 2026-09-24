package io.legado.app.model.epub

/** A TOC chapter can cover several spine documents, or a fragment of one document. */
internal data class EpubChapterDocument(
    val location: EpubResourceLink,
    val endFragment: String?,
    val spineIndex: Int,
)

/** Uses existing chapter URLs. Opening a renderer never regenerates the book's chapter list. */
internal fun EpubPublication.chapterDocuments(
    chapterUrl: String,
    nextChapterUrl: String?,
    startFragment: String?,
    endFragment: String?,
): List<EpubChapterDocument> {
    val start = EpubPaths.resolve(packagePath, chapterUrl).let {
        it.copy(fragment = startFragment ?: it.fragment)
    }
    val next = nextChapterUrl?.takeIf(String::isNotBlank)?.let { EpubPaths.resolve(packagePath, it) }
    val documents = spine.mapIndexedNotNull { index, item ->
        resourcesById[item.idref]?.location?.let { index to it }
    }
    val first = documents.indexOfFirst { it.second.path == start.path }
    if (first < 0) {
        // Non-linear TOC destinations are valid documents too.
        return listOf(EpubChapterDocument(readableDocument(start), endFragment, -1))
    }
    val result = ArrayList<EpubChapterDocument>()
    for (index in first until documents.size) {
        val (spineIndex, resource) = documents[index]
        val isFirst = index == first
        val isEnd = next?.path == resource.path
        if (!isFirst && isEnd && endFragment.isNullOrEmpty()) break
        result.add(EpubChapterDocument(
            readableDocument(if (isFirst) start else resource),
            if (isEnd) endFragment else null,
            spineIndex,
        ))
        if (isEnd) break
    }
    return result
}
