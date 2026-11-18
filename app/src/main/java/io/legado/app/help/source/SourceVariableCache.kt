package io.legado.app.help.source

import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.deleteByKeysChunked
import io.legado.app.help.AppCacheManager

/** Call after deleting/updating source rows, within the same database transaction. */
internal fun CacheDao.deleteSourceVariables(
    sourceKeys: Collection<String>,
    cacheKeys: Collection<String> = allKeys(),
) {
    if (sourceKeys.isEmpty()) return
    val selection = deleteStoredSourceVariables(sourceKeys, cacheKeys)
    AppCacheManager.clearSourceVariables(selection::contains)
}

internal fun CacheDao.deleteStoredSourceVariables(
    sourceKeys: Collection<String>,
    cacheKeys: Collection<String> = allKeys(),
): SourceVariableCacheSelection {
    val selection = SourceVariableCacheSelection(sourceKeys, this.sourceKeys())
    deleteByKeysChunked(cacheKeys.filter(selection::contains))
    return selection
}

internal fun CacheDao.clearSourceVariablesFromMemory(sourceKeys: Collection<String>) {
    val selection = SourceVariableCacheSelection(sourceKeys, this.sourceKeys())
    AppCacheManager.clearSourceVariables(selection::contains)
}

internal class SourceVariableCacheSelection(
    sourceKeys: Collection<String>,
    retainedSourceKeys: Collection<String>,
) {
    private val selected = sourceKeys.toHashSet()
    private val retained = retainedSourceKeys.toHashSet()

    fun contains(cacheKey: String): Boolean {
        if (cacheKey.startsWith("v_")) {
            // Legacy keys don't encode the boundary between source URL and variable name.
            // A key may belong to several sources; retain it while any such source exists.
            var matchesSelected = false
            var boundary = cacheKey.indexOf('_', 2)
            while (boundary >= 0) {
                val sourceKey = cacheKey.substring(2, boundary)
                if (sourceKey in retained) return false
                if (sourceKey in selected) matchesSelected = true
                boundary = cacheKey.indexOf('_', boundary + 1)
            }
            return matchesSelected
        }
        val prefix = exactPrefixes.firstOrNull(cacheKey::startsWith) ?: return false
        val sourceKey = cacheKey.substring(prefix.length)
        return sourceKey in selected && sourceKey !in retained
    }

    private companion object {
        val exactPrefixes = listOf("userInfo_", "loginHeader_", "sourceVariable_", "infoMap_")
    }
}
