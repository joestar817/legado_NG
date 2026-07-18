package io.legado.app.ui.book.source.manage

import io.legado.app.utils.NetworkUtils

internal object BookSourceAutoGroup {

    fun sharedBaseUrlGroups(sourceUrls: Iterable<String>): Map<String, String> {
        val distinctUrls = sourceUrls.distinct()
        val baseUrlCounts = distinctUrls.mapNotNull(::resolvedBaseUrl)
            .groupingBy { it }
            .eachCount()
        return distinctUrls.mapNotNull { sourceUrl ->
            val baseUrl = resolvedBaseUrl(sourceUrl) ?: return@mapNotNull null
            if (baseUrlCounts.getValue(baseUrl) < 2) return@mapNotNull null
            sourceUrl to baseUrl
        }.toMap()
    }

    private fun resolvedBaseUrl(sourceUrl: String): String? {
        val resolvedRootUrl = NetworkUtils.getAbsoluteURL(sourceUrl, "/")
        return NetworkUtils.getBaseUrl(resolvedRootUrl)
    }
}
