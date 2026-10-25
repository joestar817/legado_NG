package io.legado.app.help.bgm

import android.annotation.SuppressLint
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.legado.app.BuildConfig
import io.legado.app.utils.FileDoc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import splitties.init.appCtx
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

internal class LocalAudioImportStopped : CancellationException()

internal data class LocalAudioMetadata(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val mime: String,
    val coverUrl: String = "",
)

@SuppressLint("UnsafeOptInUsageError")
internal object LocalAudioAccess {
    const val MAX_CONCURRENT_PROBES = 8
    private val metadataGate = Semaphore(MAX_CONCURRENT_PROBES)
    private val nativeMetadataGate = Semaphore(4)
    private val metadataCache = LocalAudioMetadataCache()
    private val mp3Probes = AtomicInteger()
    private val nativeProbes = AtomicInteger()
    fun debugProbeCounts() = "mp3_headers=${mp3Probes.get()} platform_probes=${nativeProbes.get()}"
    private val codecs by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS) }
    private val documentProjection = arrayOf(
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )
    private val coverDirectory get() = File(appCtx.cacheDir, "bgm/local-covers").apply { mkdirs() }

    /** A document keeps its identity when it is reached through another granted ancestor tree. */
    fun identity(uri: Uri): String = runCatching {
        if (uri.scheme == "content") "${uri.authority}:${DocumentsContract.getDocumentId(uri)}"
        else java.net.URI(uri.toString()).normalize().toString()
    }.getOrDefault(uri.toString())

    fun root(uri: Uri): FileDoc {
        return when (uri.scheme) {
            "content" -> {
                if (!DocumentsContract.isTreeUri(uri)) throw MusicProviderException("请重新选择音乐目录")
                val doc = DocumentFile.fromTreeUri(appCtx, uri)
                    ?: throw MusicProviderException("目录不可访问，请重新选择")
                if (!doc.isDirectory || !doc.canRead()) throw MusicProviderException("目录不可访问，请重新授权")
                FileDoc.fromDocumentFile(doc)
            }
            "file" -> {
                val file = File(uri.path.orEmpty()).canonicalFile
                if (!file.isDirectory || !file.canRead()) throw MusicProviderException("目录不可访问，请重新选择")
                FileDoc(file.name, true, 0, file.lastModified(), Uri.fromFile(file))
            }
            else -> throw MusicProviderException("目录不可访问，请重新选择")
        }
    }

    /** Content providers enforce the tree grant; file paths must stay inside the chosen canonical root. */
    fun belongsToRoot(uri: Uri, root: Uri): Boolean = runCatching {
        when {
            uri.scheme == "content" && root.scheme == "content" ->
                uri.authority == root.authority && DocumentsContract.isTreeUri(uri) &&
                    DocumentsContract.isTreeUri(root) &&
                    DocumentsContract.getTreeDocumentId(uri) == DocumentsContract.getTreeDocumentId(root)
            uri.scheme == "file" && root.scheme == "file" -> {
                val parent = File(root.path.orEmpty()).canonicalFile
                val child = File(uri.path.orEmpty()).canonicalFile
                child.path.startsWith(parent.path.trimEnd(File.separatorChar) + File.separator)
            }
            else -> false
        }
    }.getOrDefault(false)

    fun referenceIsValid(reference: LocalMusicReference): Boolean = runCatching {
        reference.name.isNotBlank() && belongsToRoot(Uri.parse(reference.uri), Uri.parse(reference.rootUri))
    }.getOrDefault(false)

    fun source(reference: LocalMusicReference): MusicPlaybackSource {
        if (!referenceIsValid(reference)) throw MusicProviderException("音乐位置不可用，请重新定位")
        val uri = Uri.parse(reference.uri)
        try {
            appCtx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { }
                ?: throw MusicProviderException("文件不可访问，请重新定位")
        } catch (_: SecurityException) {
            throw MusicProviderException("目录授权已失效，请重新授权")
        } catch (_: Exception) {
            throw MusicProviderException("文件已移动或删除，请重新定位")
        }
        return MusicPlaybackSource(reference.uri, preview = false)
    }

    fun document(uri: Uri): FileDoc = when (uri.scheme) {
        "file" -> {
            val file = File(uri.path.orEmpty())
            if (!file.isFile || !file.canRead()) throw MusicProviderException("文件不可访问")
            FileDoc(file.name, false, file.length(), file.lastModified(), uri)
        }
        "content" -> {
            // DocumentFile's individual getters each query the provider again. Read the row once.
            appCtx.contentResolver.query(uri, documentProjection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) throw MusicProviderException("文件不可访问")
                val mime = cursor.getString(3)
                if (mime.isNullOrBlank() || mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    throw MusicProviderException("文件不可访问")
                }
                FileDoc(cursor.getString(0).orEmpty(), false, cursor.getLong(1), cursor.getLong(2), uri)
            } ?: throw MusicProviderException("文件不可访问")
        }
        else -> throw MusicProviderException("文件不可访问")
    }

    suspend fun inspect(uri: Uri, name: String, size: Long, modified: Long, loadCover: Boolean = false,
        coverIdentity: String? = null): LocalAudioMetadata {
        currentCoroutineContext().ensureActive()
        val key = LocalAudioMetadataCache.Key(identity(uri), name, size, modified)
        metadataCache.get(key, loadCover)?.let { return it }
        return metadataGate.withPermit {
            metadataCache.get(key, loadCover) ?: inspectNow(uri, name, size, modified, loadCover, coverIdentity)
                .also { metadataCache.put(key, it, loadCover) }
        }
    }

    private suspend fun inspectNow(uri: Uri, name: String, size: Long, modified: Long, loadCover: Boolean,
        coverIdentity: String?): LocalAudioMetadata {
        if (name.substringAfterLast('.', "").equals("mp3", ignoreCase = true)) {
            val context = currentCoroutineContext()
            val probe = try {
                appCtx.contentResolver.openInputStream(uri)?.use { stream ->
                    LocalMp3Probe.read(stream, size) { context.ensureActive() }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            if (probe != null) {
                if (BuildConfig.DEBUG) mp3Probes.incrementAndGet()
                val format = MediaFormat.createAudioFormat("audio/mpeg", probe.format.sampleRate, probe.format.channelCount)
                if (codecs.findDecoderForFormat(format) == null) {
                    throw MusicProviderException("此设备不支持该音频编码")
                }
                val artwork = if (loadCover) runCatching {
                    cover(coverIdentity ?: "${identity(uri)}:$size:$modified") { probe.tags.artworkData }
                }.getOrDefault("") else ""
                return LocalAudioMetadata(probe.tags.title.toString(), probe.tags.artist.toString(),
                    probe.tags.albumTitle.toString(), probe.durationMs, "audio/mpeg", artwork)
            }
        }
        return nativeMetadataGate.withPermit { try {
            if (BuildConfig.DEBUG) nativeProbes.incrementAndGet()
            val descriptor = appCtx.contentResolver.openAssetFileDescriptor(uri, "r")
                ?: throw MusicProviderException("文件不可访问")
            descriptor.use { inspectDescriptor(it, uri, name, size, modified, loadCover, coverIdentity) }
        } catch (error: MusicProviderException) {
            throw error
        } catch (_: SecurityException) {
            throw MusicProviderException("目录授权已失效，请重新授权")
        } catch (_: Exception) {
            throw MusicProviderException("无法识别或读取音频")
        } }
    }

    private fun inspectDescriptor(descriptor: AssetFileDescriptor, uri: Uri, name: String, size: Long,
        modified: Long, loadCover: Boolean, coverIdentity: String?): LocalAudioMetadata {
        val extractor = MediaExtractor()
        val audio: MediaFormat
        try {
            if (descriptor.declaredLength >= 0) extractor.setDataSource(descriptor.fileDescriptor,
                descriptor.startOffset, descriptor.declaredLength)
            else extractor.setDataSource(descriptor.fileDescriptor)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            if (formats.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }) {
                throw MusicProviderException("此文件包含视频")
            }
            audio = formats.firstOrNull { it.getString(MediaFormat.KEY_MIME) in LocalAudioFormat.audioMimes }
                ?: throw MusicProviderException("暂不支持此音频编码")
            val mime = audio.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime != "audio/raw" && codecs.findDecoderForFormat(audio) == null) {
                throw MusicProviderException("此设备不支持该音频编码")
            }
        } catch (error: MusicProviderException) {
            throw error
        } catch (_: SecurityException) {
            throw MusicProviderException("目录授权已失效，请重新授权")
        } catch (_: Exception) {
            throw MusicProviderException("无法识别或读取音频")
        } finally {
            extractor.release()
        }
        var title = name.substringBeforeLast('.', name)
        var artist = ""
        var album = ""
        var duration = runCatching { audio.getLong(MediaFormat.KEY_DURATION) / 1000 }.getOrDefault(0L)
        var cover = ""
        val retriever = MediaMetadataRetriever()
        try {
            if (descriptor.declaredLength >= 0) retriever.setDataSource(descriptor.fileDescriptor,
                descriptor.startOffset, descriptor.declaredLength)
            else retriever.setDataSource(descriptor.fileDescriptor)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: title
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: duration
            if (loadCover) cover = cover(coverIdentity ?: "${identity(uri)}:$size:$modified") { retriever.embeddedPicture }
        } catch (_: Exception) {
            // Missing tags or artwork do not invalidate an audio track whose codec was checked above.
        } finally {
            retriever.release()
        }
        return LocalAudioMetadata(title.ifBlank { name }, artist, album, duration.coerceAtLeast(0),
            audio.getString(MediaFormat.KEY_MIME).orEmpty(), cover)
    }

    private fun cover(identity: String, picture: () -> ByteArray?): String {
        val key = UUID.nameUUIDFromBytes(identity.toByteArray()).toString()
        val file = File(coverDirectory, "$key.jpg")
        if (file.isFile) return Uri.fromFile(file).toString()
        val bytes = picture() ?: return ""
        if (bytes.size > 8 * 1024 * 1024) return ""
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ""
        val options = BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit((maxOf(bounds.outWidth, bounds.outHeight) / 192).coerceAtLeast(1))
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return ""
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        finally { bitmap.recycle() }
        return Uri.fromFile(file).toString()
    }

    suspend fun digest(uri: Uri, shouldStop: () -> Boolean = { false }): String {
        val digest = MessageDigest.getInstance("SHA-256")
        appCtx.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                if (shouldStop()) throw LocalAudioImportStopped()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: throw MusicProviderException("文件不可访问")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun track(doc: FileDoc, rootUri: String, metadata: LocalAudioMetadata, digest: String = "",
        id: String = UUID.nameUUIDFromBytes(identity(doc.uri).toByteArray()).toString()): MusicTrack =
        MusicTrack(id, MusicPlatform.LOCAL, metadata.title, metadata.artist, metadata.album,
            metadata.coverUrl, metadata.durationMs, localReference = LocalMusicReference(
                doc.uri.toString(), rootUri, doc.name, doc.size, doc.lastModified, digest))
}
