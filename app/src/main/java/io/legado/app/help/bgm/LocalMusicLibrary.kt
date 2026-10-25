package io.legado.app.help.bgm

import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal object LocalMusicLibrary {
    private const val MAX_FILE_BYTES = 512L * 1024 * 1024
    private val directory get() = File(appCtx.noBackupFilesDir, "bgm/audio").apply { mkdirs() }

    data class ImportedAudio(val track: MusicTrack, val digest: String, val name: String)
    class ImportFailure(val displayName: String, val reason: String) : Exception(reason)

    suspend fun importAudio(uri: Uri, shouldStop: () -> Boolean = { false }): ImportedAudio {
        val resolver = appCtx.contentResolver
        var name = if (uri.scheme == "file") safeName(uri.lastPathSegment) else "本地音乐"
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    name = safeName(it.getString(0))
                    if (!it.isNull(1) && it.getLong(1) > MAX_FILE_BYTES) {
                        throw MusicProviderException("音频文件不能超过 512 MB")
                    }
                }
            }
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri))
                ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,10}")) } ?: "audio"
            var committedFile: File? = null
            val temporary = File(directory, "${UUID.randomUUID()}.part")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                resolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (shouldStop()) throw LocalAudioImportStopped()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_FILE_BYTES) throw MusicProviderException("音频文件不能超过 512 MB")
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                        }
                        if (total == 0L) throw MusicProviderException("音频文件为空")
                    }
                } ?: throw MusicProviderException("无法读取音频文件")
                val contentHash = digest.digest().joinToString("") { "%02x".format(it) }
                val id = UUID.randomUUID().toString()
                val metadata = LocalAudioAccess.inspect(Uri.fromFile(temporary), name, temporary.length(),
                    temporary.lastModified(), loadCover = true, coverIdentity = contentHash)
                val file = File(directory, "$id.$contentHash.$extension")
                check(temporary.renameTo(file)) { "无法保存本地音乐" }
                committedFile = file
                val track = MusicTrack(id, MusicPlatform.LOCAL, metadata.title, artist = metadata.artist,
                    album = metadata.album, coverUrl = metadata.coverUrl,
                    durationMs = metadata.durationMs, localPath = file.name)
                return ImportedAudio(track, contentHash, name)
            } catch (error: Exception) {
                committedFile?.delete()
                if (error is CancellationException || error is MusicProviderException) throw error
                throw MusicProviderException("无法导入此音频文件")
            } finally {
                temporary.delete()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw ImportFailure(name, (error as? MusicProviderException)?.message ?: "无法读取或复制音频文件")
        }
    }

    /** Hash once per batch, including older UUID entries without changing their saved identity. */
    suspend fun contentDigests(tracks: List<MusicTrack>, shouldStop: () -> Boolean = { false }): Map<String, String> {
        val digests = HashMap<String, String>()
        for (track in tracks) {
            currentCoroutineContext().ensureActive()
            if (shouldStop()) throw LocalAudioImportStopped()
            track.localReference?.let { reference ->
                val current = runCatching { LocalAudioAccess.document(Uri.parse(reference.uri)) }.getOrNull()
                if (current != null) {
                    if (current.lastModified > 0 && current.size == reference.size && current.lastModified == reference.modified &&
                        reference.digest.orEmpty().matches(Regex("[a-f0-9]{64}"))) digests[track.key] = reference.digest
                    else try { digests[track.key] = LocalAudioAccess.digest(current.uri, shouldStop) }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { /* An inaccessible source must not block adding its replacement. */ }
                }
            }
            val file = ownedFile(track)?.takeIf(File::isFile) ?: continue
            val storedDigest = track.localPath.removePrefix(track.id + ".").substringBefore('.')
            if (storedDigest.matches(Regex("[a-f0-9]{64}"))) {
                digests[track.key] = storedDigest
                continue
            }
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (shouldStop()) throw LocalAudioImportStopped()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                digests[track.key] = digest.digest().joinToString("") { "%02x".format(it) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // An unreadable existing copy must not prevent importing a usable replacement.
            }
        }
        return digests
    }

    /** Catalogue size includes referenced audio, not only the app's private copies. */
    fun storageBytes(tracks: List<MusicTrack>): Long =
        directory.listFiles().orEmpty().filter { it.isFile && it.extension != "part" }.sumOf(File::length) +
            externalStorageBytes(tracks)

    internal fun externalStorageBytes(tracks: List<MusicTrack>): Long = tracks
        .filter { it.platform == MusicPlatform.LOCAL }
        .mapNotNull(MusicTrack::localReference)
        .distinctBy { LocalAudioAccess.identity(Uri.parse(it.uri)) }
        .sumOf { it.size.coerceAtLeast(0) }

    private fun safeName(value: String?): String = value?.substringAfterLast('/')?.substringAfterLast('\\')
        ?.replace(Regex("[\\p{Cntrl}]"), "")?.take(120)?.ifBlank { "本地音乐" } ?: "本地音乐"

    fun source(track: MusicTrack): MusicPlaybackSource {
        track.localReference?.let { return LocalAudioAccess.source(it) }
        val file = ownedFile(track) ?: throw MusicProviderException("本地音乐已丢失，请重新导入")
        if (!file.isFile) throw MusicProviderException("本地音乐已丢失，请重新导入")
        return MusicPlaybackSource(Uri.fromFile(file).toString(), preview = false)
    }

    fun remove(track: MusicTrack) {
        ownedFile(track)?.let { file ->
            check(!file.exists() || file.delete()) { "无法删除本地音乐副本" }
        }
    }

    private fun ownedFile(track: MusicTrack): File? {
        if (track.platform != MusicPlatform.LOCAL || track.localReference != null || track.localPath.isBlank()) return null
        val root = directory.canonicalFile
        val file = File(root, track.localPath).canonicalFile
        return file.takeIf { it.parentFile == root && it.name.startsWith(track.id + ".") }
    }
}
