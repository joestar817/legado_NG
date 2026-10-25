package io.legado.app.help.bgm

/** Only successful probes of a known file version are reusable; no persisted or permission state. */
internal class LocalAudioMetadataCache(private val capacity: Int = 512) {
    data class Key(val identity: String, val name: String, val size: Long, val modified: Long)
    private data class CachedMetadata(val metadata: LocalAudioMetadata, val artworkChecked: Boolean)
    private val entries = object : LinkedHashMap<Key, CachedMetadata>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, CachedMetadata>?): Boolean = size > capacity
    }

    @Synchronized
    fun get(key: Key, needsArtwork: Boolean): LocalAudioMetadata? {
        if (key.size <= 0 || key.modified <= 0) return null
        val entry = entries[key] ?: return null
        return entry.metadata.takeIf { !needsArtwork || entry.artworkChecked }
    }

    @Synchronized
    fun put(key: Key, metadata: LocalAudioMetadata, artworkChecked: Boolean) {
        if (key.size <= 0 || key.modified <= 0) return
        if (entries[key]?.artworkChecked == true && !artworkChecked) return
        entries[key] = CachedMetadata(metadata, artworkChecked)
    }
}
