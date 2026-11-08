package io.legado.app.help.resource

import android.util.AtomicFile
import io.legado.app.utils.GSON
import java.io.File

internal data class CachedResourceCatalog(val snapshot: ResourceSnapshot, val loaded: Set<String>)

internal interface ResourceCatalogCache {
    fun load(): CachedResourceCatalog?
    fun remember(snapshot: ResourceSnapshot, complete: Boolean)
    fun invalidate()
}

/** The manifest points to individually verified content indexes. Cached data is optional. */
internal class ResourceCatalogDiskCache(directory: File) : ResourceCatalogCache {
    private val manifest = File(directory, "manifest.json")
    private val indexes = File(directory, "indexes")
    private val indexLock = Any()

    override fun load(): CachedResourceCatalog? = runCatching {
        val bytes = read(manifest)
        val manifest = requireNotNull(GSON.fromJson(bytes.toString(Charsets.UTF_8), ResourceSnapshot::class.java))
        ResourceProtocol.validateLoadedSnapshot(manifest, emptySet())
        var snapshot = manifest
        val loaded = mutableSetOf<String>()
        for (category in manifest.catalog.categories) {
            val index = readIndex(manifest.catalog, category) ?: continue
            val next = snapshot.withResources(category, index.resources)
            if (runCatching { ResourceProtocol.validateLoadedSnapshot(next, loaded + category.id) }.isSuccess) {
                snapshot = next
                loaded += category.id
            }
        }
        CachedResourceCatalog(snapshot, loaded.toSet())
    }.getOrNull()

    override fun remember(snapshot: ResourceSnapshot, complete: Boolean) {
        // Retain the last browsable generation while newer categories are still arriving.
        if (complete || (!manifest.exists() && !File(manifest.path + ".bak").exists())) {
            runCatching { ResourceFiles.writeJson(manifest, snapshot.copy(entries = emptyList())) }
        }
    }

    override fun invalidate() {
        // Blobs have no browsable metadata after this point. A later successful check may reuse their bytes.
        AtomicFile(manifest).delete()
    }

    fun readIndex(catalog: ResourceCatalog, category: ResourceCategory): ResourceIndex? {
        val hash = category.indexSha256 ?: return null
        return synchronized(indexLock) {
            val file = indexFile(hash)
            runCatching {
                decodeResourceContentIndex(read(file), catalog, category).also {
                    file.setLastModified(System.currentTimeMillis())
                }
            }.getOrNull()
        }
    }

    fun rememberIndex(hash: String, bytes: ByteArray) {
        synchronized(indexLock) {
            runCatching {
                val file = indexFile(hash)
                ResourceFiles.writeBytes(file, bytes)
                val cached = indexes.listFiles().orEmpty()
                    .filter { it.isFile && INDEX_FILE.matches(it.name) }
                    .sortedBy { it.lastModified() }
                var size = cached.sumOf { it.length() }
                for (old in cached) {
                    if (size <= MAX_CACHE_BYTES) break
                    if (old == file) continue
                    val length = old.length()
                    AtomicFile(old).delete()
                    if (!old.exists()) size -= length
                }
            }
        }
    }

    private fun indexFile(hash: String): File {
        val name = "$hash.json"
        require(INDEX_FILE.matches(name))
        return File(indexes, name)
    }

    private fun read(file: File): ByteArray = AtomicFile(file).openRead().use {
        require(it.channel.size() in 1L..ResourceProtocol.MAX_INDEX_BYTES.toLong()) { "资源索引大小不合法" }
        it.readBytes()
    }

    private companion object {
        const val MAX_CACHE_BYTES = 32L * 1024 * 1024
        val INDEX_FILE = Regex("[a-f0-9]{64}\\.json")
    }
}

internal fun decodeResourceContentIndex(
    bytes: ByteArray, catalog: ResourceCatalog, category: ResourceCategory,
): ResourceIndex {
    require(bytes.size in 1..ResourceProtocol.MAX_INDEX_BYTES &&
        category.indexSha256 != null && ResourceProtocol.hash(bytes) == category.indexSha256) {
        "分类索引校验失败"
    }
    val content = requireNotNull(GSON.fromJson(bytes.toString(Charsets.UTF_8), ResourceIndex::class.java))
    require(content.sourceCommit.isEmpty()) { "分类索引包含意外的来源提交" }
    return content.copy(sourceCommit = catalog.sourceCommit).also {
        ResourceProtocol.validateIndex(catalog, category, it)
    }
}

internal fun ResourceSnapshot.withResources(category: ResourceCategory, resources: List<ResourceItem>): ResourceSnapshot {
    val groups = entries.groupBy { it.category.id } + (category.id to resources.map {
        ResourceEntry(category, catalog.sourceCommit, it, catalogCommit = catalogCommit)
    })
    return copy(entries = catalog.categories.flatMap { groups[it.id].orEmpty() })
}
