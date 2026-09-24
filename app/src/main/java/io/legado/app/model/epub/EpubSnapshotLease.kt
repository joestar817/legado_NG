package io.legado.app.model.epub

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.util.UUID

/** A kernel-held liveness lease. Process death releases it without lifecycle callbacks. */
internal class EpubSnapshotLease private constructor(
    private val root: File,
    val directory: File,
    private val owner: RandomAccessFile,
    private val lock: FileLock,
) : Closeable {
    private var closed = false

    @Synchronized
    override fun close() {
        if (closed) return
        try {
            allocation(root) {
                val empty = removePayloads(directory)
                release()
                if (empty) {
                    File(directory, OWNER).delete()
                    directory.delete()
                }
            }
        } finally {
            closed = true
            try { release() } finally { synchronized(Companion) { active.remove(directory.path) } }
        }
    }

    private fun release() {
        try { if (lock.isValid) lock.release() } finally { owner.close() }
    }

    companion object {
        private const val OWNER = ".owner"
        // On POSIX, closing a second descriptor for the same inode can release this process's
        // original fcntl lock. Never even open a locally active owner's lock file during scans.
        private val active = HashSet<String>()
        private val directoryName = Regex("snapshot-v1-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val payloadName = Regex("(?:publication-[0-9]+\\.(?:part|epub)|media-[0-9]+\\.bin)")

        fun create(directory: File): EpubSnapshotLease {
            val root = directory.canonicalFile
            if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create EPUB snapshot directory")
            return allocation(root) {
                root.listFiles()?.forEach { child ->
                    if (directoryName.matches(child.name) && child.isDirectory && !Files.isSymbolicLink(child.toPath()) &&
                        child.canonicalFile.parentFile == root) reclaim(child)
                }
                val session = File(root, "snapshot-v1-${UUID.randomUUID()}")
                if (!session.mkdir()) throw IOException("Cannot create EPUB snapshot session")
                var owner: RandomAccessFile? = null
                try {
                    owner = RandomAccessFile(File(session, OWNER), "rw")
                    EpubSnapshotLease(root, session, owner, owner.channel.lock()).also { active.add(session.path) }
                } catch (error: Exception) {
                    try { owner?.close() } catch (closeError: Exception) { error.addSuppressed(closeError) }
                    File(session, OWNER).delete()
                    session.delete()
                    throw error
                }
            }
        }

        /** Protect creation/lock acquisition from another process's orphan scan. */
        @Synchronized
        private fun <T> allocation(root: File, block: () -> T): T =
            RandomAccessFile(File(root, ".snapshot-allocation.lock"), "rw").use { gate ->
                gate.channel.lock().use { block() }
            }

        private fun reclaim(directory: File) {
            if (directory.path in active) return
            val ownerFile = File(directory, OWNER)
            if (Files.isSymbolicLink(ownerFile.toPath())) return
            try {
                val removed = RandomAccessFile(ownerFile, "rw").use { owner ->
                    val held = try { owner.channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                    if (held == null) return
                    held.use { removePayloads(directory) }
                }
                if (removed) { ownerFile.delete(); directory.delete() }
            } catch (_: IOException) {
                // Cleanup failure may leave an orphan; it must never evict an active reader.
            }
        }

        /** No recursive deletion and no traversal of links or unrecognized contents. */
        private fun removePayloads(directory: File): Boolean {
            val files = directory.listFiles() ?: return false
            if (files.any { !it.isFile || Files.isSymbolicLink(it.toPath()) || it.name != OWNER && !payloadName.matches(it.name) }) return false
            var removed = true
            files.filter { it.name != OWNER }.forEach { if (it.exists() && !it.delete()) removed = false }
            return removed
        }
    }
}
