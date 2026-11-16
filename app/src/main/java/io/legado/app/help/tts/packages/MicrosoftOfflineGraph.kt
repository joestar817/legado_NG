package io.legado.app.help.tts.packages

import java.security.MessageDigest

/** Published content only: personal playback preferences never participate in this fingerprint. */
internal fun microsoftOfflineCatalogFingerprint(graph: Map<String, String>, voiceIds: Collection<String>): String {
    val values = listOf(encodeMicrosoftOfflineGraph(graph)) + voiceIds.distinct().sorted()
    val encoded = values.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
}

/** A stable, non-secret reference to the exact component graph used by one playback. */
internal fun encodeMicrosoftOfflineGraph(graph: Map<String, String>): String =
    graph.toSortedMap().entries.joinToString("|") { "${it.key}:${it.value}" }

internal fun decodeMicrosoftOfflineGraph(revision: String): Map<String, String> {
    require(revision.isNotBlank() && revision.length <= 16_384) { "离线引擎引用无效" }
    val result = linkedMapOf<String, String>()
    revision.split('|').forEach { entry ->
        val parts = entry.split(':')
        require(parts.size == 2 && parts[0].matches(Regex("[a-z0-9._-]{1,256}")) &&
            parts[1].matches(Regex("[a-f0-9]{64}")) && result.put(parts[0], parts[1]) == null
        ) { "离线引擎引用无效" }
    }
    require(result.size <= 32 && MicrosoftTtsPackageStore.ENGINE in result &&
        encodeMicrosoftOfflineGraph(result) == revision) { "离线引擎引用无效" }
    return result
}
