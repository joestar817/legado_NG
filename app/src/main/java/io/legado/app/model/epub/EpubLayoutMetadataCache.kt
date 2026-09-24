package io.legado.app.model.epub

import java.io.File

/** One small, immutable package description; never retains an archive, source file or reader session. */
internal object EpubLayoutMetadataCache {
    private data class Entry(val revision: String, val limits: EpubArchiveLimits, val publication: EpubPublication)
    @Volatile private var entry: Entry? = null

    @JvmOverloads
    fun getOrParse(revision: String, limits: EpubArchiveLimits, file: File? = null, parse: () -> EpubPublication): EpubPublication {
        val cached = entry
        if (cached != null && cached.revision == revision && cached.limits == limits) return cached.publication
        file?.let { EpubLayoutMetadataFile.read(it, revision, limits) }?.takeIf(::cacheable)?.let {
            entry = Entry(revision, limits, it)
            return it
        }
        val publication = parse()
        // Large/metadata-heavy books remain session-owned. A cache miss never blocks another parse.
        entry = if (cacheable(publication)) Entry(revision, limits, publication) else null
        if (file != null && entry?.publication === publication) EpubLayoutMetadataFile.write(file, revision, limits, publication)
        return publication
    }

    private fun cacheable(publication: EpubPublication): Boolean {
        if (publication.manifest.size + publication.spine.size + publication.metadata.size +
            publication.encryption.size > 8192 || publication.navigation.isNotEmpty()) return false
        var characters = 0L
        fun add(value: String?) { characters += value?.length ?: 0 }
        fun attributes(values: Map<String, String>) { values.forEach { (key, value) -> add(key); add(value) } }
        add(publication.packagePath); add(publication.version); add(publication.uniqueIdentifier)
        add(publication.title); add(publication.language)
        publication.manifest.forEach {
            add(it.id); add(it.href); add(it.location?.path); add(it.location?.query); add(it.location?.fragment)
            add(it.mediaType); add(it.fallback); add(it.mediaOverlay); it.properties.forEach(::add)
        }
        publication.spine.forEach { add(it.idref); it.properties.forEach(::add) }
        publication.metadata.forEach { add(it.name); add(it.value); add(it.namespace); attributes(it.attributes) }
        publication.encryption.forEach { add(it.path); add(it.algorithm) }
        attributes(publication.prefixes); publication.warnings.forEach(::add)
        return characters <= 512 * 1024
    }
}
