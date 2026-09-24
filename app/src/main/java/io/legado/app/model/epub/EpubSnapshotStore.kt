package io.legado.app.model.epub

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Blocking I/O. Each open owns a new snapshot; filenames and mutable source metadata are not keys. */
internal class EpubSnapshotStore @JvmOverloads constructor(
    private val directory: File,
    private val limits: EpubArchiveLimits = EpubArchiveLimits(),
    private val onStage: ((String) -> Unit)? = null,
) {
    fun open(
        sourceId: String,
        openInput: () -> InputStream,
        checkCancelled: () -> Unit = {},
    ): EpubSnapshotSession {
        require(sourceId.isNotEmpty())
        checkCancelled()
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create EPUB snapshot directory")
        val lease = EpubSnapshotLease.create(directory)
        val partial = try { File.createTempFile("publication-", ".part", lease.directory) }
            catch (error: Exception) { lease.close(); throw error }
        val complete = File(lease.directory, partial.name.removeSuffix(".part") + ".epub")
        var session: EpubPublicationSession? = null
        var published = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            openInput().use { input ->
                onStage?.invoke("snapshot-input-open")
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        if (count > limits.maxArchiveBytes - size) {
                            throw EpubFormatException("EPUB source exceeds snapshot size limit")
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        size += count
                    }
                    onStage?.invoke("snapshot-copied")
                    output.fd.sync()
                    onStage?.invoke("snapshot-synced")
                }
            }
            checkCancelled()
            // Both paths are private and on the same filesystem. Never replace another session.
            if (complete.exists() || !partial.renameTo(complete)) throw IOException("Cannot publish EPUB snapshot")
            published = true
            val identity = EpubSourceIdentity(
                sha256(sourceId.toByteArray(Charsets.UTF_8)), digest.digest().hex(), size,
            )
            onStage?.invoke("snapshot-before-parse")
            val opened = EpubPublicationSession.openForLayout(complete, limits, identity.contentRevision, onStage, directory)
            session = opened
            checkCancelled()
            return EpubSnapshotSession(identity, opened, complete, lease)
        } catch (error: Exception) {
            try { session?.close() } catch (closeError: Exception) { error.addSuppressed(closeError) }
            for (file in if (published) listOf(partial, complete) else listOf(partial)) {
                if (file.exists() && !file.delete()) error.addSuppressed(IOException("Cannot remove EPUB snapshot"))
            }
            try { lease.close() } catch (closeError: Exception) { error.addSuppressed(closeError) }
            throw error
        }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Process-local identity, not a persisted locator. The raw SAF URI is not exposed to web content. */
internal data class EpubSourceIdentity(val sourceKey: String, val contentRevision: String, val size: Long)

internal class EpubSnapshotSession @JvmOverloads internal constructor(
    val identity: EpubSourceIdentity,
    val publicationSession: EpubPublicationSession,
    private val snapshot: File,
    private val lease: EpubSnapshotLease? = null,
) : Closeable {
    private var closed = false

    @Synchronized
    override fun close() {
        var failure: IOException? = null
        if (!closed) {
            closed = true
            try { publicationSession.close() } catch (error: IOException) { failure = error }
        }
        // Delete only this session's generated file. A second close may retry a failed deletion.
        if (snapshot.exists() && !snapshot.delete()) {
            val error = IOException("Cannot remove EPUB snapshot")
            val prior = failure
            if (prior == null) failure = error else prior.addSuppressed(error)
        }
        try { lease?.close() } catch (error: IOException) {
            val prior = failure
            if (prior == null) failure = error else prior.addSuppressed(error)
        }
        failure?.let { throw it }
    }
}
