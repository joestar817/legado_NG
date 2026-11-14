package io.legado.app.help.background

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URLDecoder

/** Discovers only backgrounds explicitly named in an installed preset package. */
internal object ReadingPresetBackgroundCatalog {
    fun packageRootForReference(packageParent: File, reference: String): File? = runCatching {
        val source = File(reference)
        if (!source.isAbsolute) return@runCatching null
        val parent = packageParent.canonicalFile
        var current = source.canonicalFile
        if (!current.toPath().startsWith(parent.toPath()) || current == parent) return@runCatching null
        while (current.parentFile != parent) current = current.parentFile ?: return@runCatching null
        current.takeIf { it.isDirectory && !it.name.startsWith('.') }
    }.getOrNull()

    fun backgrounds(packageRoot: File): Map<String, String> = runCatching {
        val root = packageRoot.canonicalFile
        val metadata = File(root, "readConfig.json")
        if (!metadata.isFile || metadata.length() > 5L * 1024 * 1024) return@runCatching emptyMap()
        val json = metadata.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        val presetName = json.stringValue("name").orEmpty().trim()
        val candidates = listOf(
            Triple("bgType", "bgStr", "日间"),
            Triple("bgTypeNight", "bgStrNight", "夜间"),
            Triple("bgTypeEInk", "bgStrEInk", "墨水屏"),
        ).mapNotNull { (typeKey, referenceKey, mode) ->
            if (json.intValue(typeKey) != 2) return@mapNotNull null
            val reference = json.stringValue(referenceKey)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            resolveInPackage(root, reference)?.let { it.canonicalPath to mode }
        }.distinctBy { it.first }
        candidates.associate { (path, mode) ->
            val base = presetName.ifBlank { File(path).nameWithoutExtension }
            path to if (candidates.size > 1) "$base · $mode" else base
        }
    }.getOrDefault(emptyMap())

    private fun resolveInPackage(root: File, reference: String): File? {
        val normalized = reference.replace('\\', '/')
        if (!normalized.startsWith('/') && ':' !in normalized) {
            val direct = File(root, normalized).canonicalFile
            if (direct.isFile && direct.toPath().startsWith(root.toPath())) return direct
        }
        val basename = normalized.substringAfterLast('/').substringAfterLast(':')
        val names = listOf(basename, runCatching { URLDecoder.decode(basename, "UTF-8") }.getOrDefault(basename))
            .filter(String::isNotBlank).toSet()
        if (names.isEmpty()) return null
        return root.walkTopDown()
            .onEnter { it.canonicalFile.toPath().startsWith(root.toPath()) }
            .filter { it.isFile && it.name in names && it.canonicalFile.toPath().startsWith(root.toPath()) }
            .take(2).toList().singleOrNull()?.canonicalFile
    }

    private fun JsonObject.stringValue(key: String): String? = runCatching { get(key)?.asString }.getOrNull()
    private fun JsonObject.intValue(key: String): Int? = runCatching { get(key)?.asInt }.getOrNull()
}
