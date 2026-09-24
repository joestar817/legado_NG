package io.legado.app.model.epub

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Disposable, bounded cache with an explicit binary schema; never deserializes object graphs. */
internal object EpubLayoutMetadataFile {
    // Bump whenever package validation or interpretation changes. This is not a migration format.
    private const val VERSION = 1
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_ITEMS = 8192

    fun read(file: File, revision: String, limits: EpubArchiveLimits): EpubPublication? = try {
        val size = file.length()
        require(size in 33..MAX_BYTES.toLong())
        val bytes = ByteArray(size.toInt())
        DataInputStream(file.inputStream()).use { it.readFully(bytes); require(it.read() == -1) }
        val payloadSize = bytes.size - 32
        val digest = MessageDigest.getInstance("SHA-256").apply { update(bytes, 0, payloadSize) }.digest()
        require(MessageDigest.isEqual(digest, bytes.copyOfRange(payloadSize, bytes.size)))
        DataInputStream(ByteArrayInputStream(bytes, 0, payloadSize)).use { input ->
            require(input.readInt() == VERSION && input.string() == revision)
            require(input.readLong() == limits.maxArchiveBytes && input.readInt() == limits.maxEntries &&
                input.readLong() == limits.maxEntryBytes && input.readLong() == limits.maxTotalBytes &&
                input.readInt() == limits.maxXmlBytes)
            val path = input.string()
            val version = input.string()
            val identifier = input.nullableString()
            val title = input.nullableString()
            val language = input.nullableString()
            val metadata = input.list { EpubMetadata(string(), string(), attributes(), string()) }
            val manifest = input.list {
                EpubManifestItem(string(), string(), if (readBoolean()) EpubResourceLink(string(), nullableString(), nullableString()) else null,
                    string(), list { string() }.toSet(), nullableString(), nullableString())
            }
            val spine = input.list { EpubSpineItem(string(), readBoolean(), list { string() }.toSet()) }
            val direction = EpubPageProgressionDirection.valueOf(input.string())
            val encryption = input.list { EpubEncryption(string(), string()) }
            val prefixes = input.attributes()
            val warnings = input.list { string() }
            require(input.available() == 0)
            EpubPublication(path, version, identifier, title, language, metadata, manifest, spine,
                direction, emptyList(), encryption, prefixes, warnings)
        }
    } catch (_: Exception) { null }

    fun write(file: File, revision: String, limits: EpubArchiveLimits, publication: EpubPublication) {
        var partial: File? = null
        try {
            val buffer = ByteArrayOutputStream()
            DataOutputStream(buffer).use { output ->
                output.writeInt(VERSION)
                output.string(revision)
                output.writeLong(limits.maxArchiveBytes); output.writeInt(limits.maxEntries)
                output.writeLong(limits.maxEntryBytes); output.writeLong(limits.maxTotalBytes); output.writeInt(limits.maxXmlBytes)
                with(publication) {
                    output.string(packagePath); output.string(version); output.nullableString(uniqueIdentifier)
                    output.nullableString(title); output.nullableString(language)
                    output.list(metadata) { string(it.name); string(it.value); attributes(it.attributes); string(it.namespace) }
                    output.list(manifest) {
                        string(it.id); string(it.href); writeBoolean(it.location != null)
                        it.location?.let { link -> string(link.path); nullableString(link.query); nullableString(link.fragment) }
                        string(it.mediaType); list(it.properties.toList()) { value -> string(value) }
                        nullableString(it.fallback); nullableString(it.mediaOverlay)
                    }
                    output.list(spine) { string(it.idref); writeBoolean(it.linear); list(it.properties.toList()) { value -> string(value) } }
                    output.string(pageProgressionDirection.name)
                    output.list(encryption) { string(it.path); string(it.algorithm) }
                    output.attributes(prefixes); output.list(warnings) { string(it) }
                }
            }
            val bytes = buffer.toByteArray()
            require(bytes.size <= MAX_BYTES - 32)
            partial = File.createTempFile("layout-metadata-", ".part", file.parentFile)
            partial.outputStream().use {
                it.write(bytes); it.write(MessageDigest.getInstance("SHA-256").digest(bytes))
            }
            // Failure/partial writes only lose a cache hit. The original book is never touched.
            Files.move(partial.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // Private cache storage may be unavailable; normal parsing remains sufficient.
        } finally { partial?.delete() }
    }

    private fun DataInputStream.nullableString(): String? {
        val size = readInt()
        if (size == -1) return null
        require(size in 0..available())
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataInputStream.string(): String = requireNotNull(nullableString())
    private fun DataOutputStream.nullableString(value: String?) {
        if (value == null) writeInt(-1) else {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size); write(bytes)
        }
    }
    private fun DataOutputStream.string(value: String) = nullableString(value)
    private fun <T> DataInputStream.list(read: DataInputStream.() -> T): List<T> {
        val count = readInt()
        require(count in 0..MAX_ITEMS && count <= available())
        return List(count) { read(this) }
    }
    private fun <T> DataOutputStream.list(values: List<T>, write: DataOutputStream.(T) -> Unit) {
        require(values.size <= MAX_ITEMS)
        writeInt(values.size); values.forEach { write(this, it) }
    }
    private fun DataInputStream.attributes(): Map<String, String> = list { string() to string() }.toMap()
    private fun DataOutputStream.attributes(values: Map<String, String>) = list(values.toList()) { (key, value) -> string(key); string(value) }
}
