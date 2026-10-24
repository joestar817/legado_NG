package io.legado.app.help.bgm

import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.net.URI
import kotlin.random.Random

/** Saved selection and catalogue. Playback remains owned by the active listening service. */
object BgmRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val importMutex = Mutex()
    private val gson = Gson()
    private val mutableSettings = MutableStateFlow(BgmSettings())
    private val mutablePlayback = MutableStateFlow(BgmPlaybackState())
    private val mutableProgress = MutableStateFlow(BgmPlaybackProgress())
    val settings: StateFlow<BgmSettings> = mutableSettings.asStateFlow()
    val playback: StateFlow<BgmPlaybackState> = mutablePlayback.asStateFlow()
    val progress: StateFlow<BgmPlaybackProgress> = mutableProgress.asStateFlow()
    private var owner = 0L
    private val file get() = File(appCtx.filesDir, "bgm/settings.json")
    private val ready = scope.async {
        val restored = runCatching {
            if (file.isFile) normalize(gson.fromJson(file.readText(), BgmSettings::class.java)) else BgmSettings()
        }.getOrElse { BgmSettings() }
        mutableSettings.value = restored
    }
    private data class Mutation(
        val transform: (BgmSettings) -> BgmSettings,
        val expectedOwner: Long? = null,
        val expectedRevision: Long? = null,
        val completion: CompletableDeferred<Boolean>? = null,
        val afterCommit: (() -> Unit)? = null,
        val operation: BgmQueueOperation? = null
    )
    private val mutations = Channel<Mutation>(Channel.UNLIMITED)
    private val mutationProcessor = scope.launch {
        ready.await()
        for (mutation in mutations) {
            val committed = try {
                edit(mutation.expectedOwner, mutation.expectedRevision, mutation.operation, mutation.transform)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutablePlayback.value = BgmPlaybackState(BgmPlaybackStatus.ERROR, message = "无法保存背景音乐设置")
                false
            }
            var completed = committed
            if (committed) {
                runCatching { mutation.afterCommit?.invoke() }.onFailure {
                    mutablePlayback.value = BgmPlaybackState(BgmPlaybackStatus.ERROR, message = "无法删除本地音乐副本")
                    completed = false
                }
            }
            mutation.completion?.complete(completed)
        }
    }

    fun setEnabled(enabled: Boolean) = change { it.copy(enabled = enabled && it.queue.isNotEmpty()) }
    fun setVolume(volume: Float) = change { it.copy(volume = volume.coerceIn(0f, 1f)) }
    fun setRepeatMode(mode: BgmRepeatMode) = change { it.copy(repeatMode = mode) }

    fun replaceQueue(
        tracks: List<MusicTrack>,
        startIndex: Int = 0,
        operation: BgmQueueOperation? = null
    ): CompletableDeferred<Boolean> {
        val selected = tracks.toList()
        if (selected.isEmpty()) return CompletableDeferred(false)
        return change(operation) { current ->
            val queue = selected.distinctBy(MusicTrack::key)
            val key = selected.getOrNull(startIndex)?.key
            current.copy(queue = queue, index = queue.indexOfFirst { it.key == key }.coerceAtLeast(0),
                enabled = queue.isNotEmpty(), selectionRevision = current.selectionRevision + 1)
        }
    }

    fun addToQueue(track: MusicTrack, operation: BgmQueueOperation? = null) =
        addToQueue(listOf(track), operation)

    fun addToQueue(
        tracks: List<MusicTrack>,
        operation: BgmQueueOperation? = null
    ): CompletableDeferred<Boolean> {
        val selected = tracks.toList()
        return change(operation) { current ->
            current.copy(queue = (current.queue + selected).distinctBy(MusicTrack::key))
        }
    }

    fun moveInQueue(trackKey: String, targetIndex: Int): CompletableDeferred<Boolean> = change { current ->
        val oldIndex = current.queue.indexOfFirst { it.key == trackKey }
        if (oldIndex < 0 || targetIndex !in current.queue.indices) current else {
            val queue = current.queue.toMutableList().apply { add(targetIndex, removeAt(oldIndex)) }
            current.copy(queue = queue, index = queue.indexOfFirst { it.key == current.currentTrack?.key }
                .coerceAtLeast(0))
        }
    }

    fun select(index: Int) = change { current ->
        if (index !in current.queue.indices) current
        else current.copy(index = index, enabled = true, selectionRevision = current.selectionRevision + 1)
    }

    fun select(key: String) = change { current ->
        val index = current.queue.indexOfFirst { it.key == key }
        if (index < 0) current else current.copy(index = index, enabled = true,
            selectionRevision = current.selectionRevision + 1)
    }

    fun next() = move(1)
    fun previous() = move(-1)

    private fun move(direction: Int) = change { current -> moved(current, direction) }

    /** A delayed callback must never advance a replacement host's selection. */
    fun advanceIfCurrent(expectedOwner: Long, expectedRevision: Long): CompletableDeferred<Boolean> {
        val completion = CompletableDeferred<Boolean>()
        mutations.trySend(Mutation({ moved(it, 1) }, expectedOwner, expectedRevision, completion))
        return completion
    }

    private fun moved(current: BgmSettings, direction: Int): BgmSettings =
        if (current.queue.isEmpty()) current else {
            val next = if (current.repeatMode == BgmRepeatMode.SHUFFLE && current.queue.size > 1) {
                (current.index + 1 + Random.nextInt(current.queue.size - 1)) % current.queue.size
            } else (current.index + direction + current.queue.size) % current.queue.size
            current.copy(index = next, selectionRevision = current.selectionRevision + 1)
        }

    fun removeFromQueue(index: Int) = change { current ->
        if (index !in current.queue.indices) current else {
            val queue = current.queue.toMutableList().apply { removeAt(index) }
            val nextIndex = if (index < current.index) current.index - 1 else current.index
            current.copy(queue = queue, index = nextIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
                enabled = current.enabled && queue.isNotEmpty(),
                selectionRevision = current.selectionRevision + if (index == current.index) 1 else 0)
        }
    }

    fun removeFromQueue(key: String) = change { current ->
        val index = current.queue.indexOfFirst { it.key == key }
        if (index < 0) current else {
            val queue = current.queue.filterNot { it.key == key }
            val nextIndex = if (index < current.index) current.index - 1 else current.index
            current.copy(queue = queue, index = nextIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
                enabled = current.enabled && queue.isNotEmpty(),
                selectionRevision = current.selectionRevision + if (index == current.index) 1 else 0)
        }
    }

    fun clearQueue() = change { it.copy(queue = emptyList(), index = 0, enabled = false,
        selectionRevision = it.selectionRevision + 1) }

    suspend fun importLocal(uris: List<Uri>): Int {
        val result = importLocalBatch(uris)
        if (result.imported == 0 && result.duplicates == 0 && uris.isNotEmpty()) {
            throw MusicProviderException(result.failures.firstOrNull()?.reason ?: "没有可导入的音频文件")
        }
        return result.imported
    }

    internal suspend fun importLocalBatch(uris: List<Uri>, shouldStop: () -> Boolean = { false },
        onProgress: (LocalMusicImportProgress) -> Unit = {}): LocalMusicImportResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext LocalMusicImportResult(0, 0, emptyList())
        ready.await()
        importMutex.withLock {
            val digests = LocalMusicLibrary.contentDigests(mutableSettings.value.localTracks, shouldStop).toMutableMap()
            var imported = 0
            var duplicates = 0
            val duplicateKeys = HashSet<String>()
            val failures = ArrayList<LocalMusicImportFailure>()
            var processed = 0
            var stopped = false
            for (uri in uris) {
                if (shouldStop()) { stopped = true; break }
                try {
                    onProgress(LocalMusicImportProgress(processed, uris.size, ""))
                    val audio = LocalMusicLibrary.importAudio(uri, shouldStop)
                    val track = audio.track
                    // A completed file copy must not be orphaned by screen cancellation before indexing it.
                    withContext(NonCancellable) {
                        var indexed = false
                        var duplicate = false
                        val committed: Boolean
                        try {
                            committed = edit { current ->
                                // Check the live catalogue at commit time: a concurrent removal may have
                                // invalidated the batch's initial digest snapshot.
                                duplicate = current.localTracks.any { digests[it.key] == audio.digest }
                                if (duplicate) current else current.copy(localTracks = current.localTracks + track)
                            }
                            indexed = committed && !duplicate
                        } finally {
                            // An exception in edit's own final cleanup can occur after publication.
                            if (!indexed && mutableSettings.value.localTracks.none { it.key == track.key }) {
                                LocalMusicLibrary.remove(track)
                            }
                        }
                        when {
                            !committed -> failures += LocalMusicImportFailure(audio.name, "无法保存本地音乐", uri.toString())
                            duplicate -> { duplicates++; duplicateKeys += LocalAudioAccess.identity(uri) }
                            else -> {
                                digests[track.key] = audio.digest
                                imported++
                            }
                        }
                    }
                } catch (_: LocalAudioImportStopped) {
                    stopped = true
                    break
                } catch (error: CancellationException) {
                    throw error
                } catch (error: LocalMusicLibrary.ImportFailure) {
                    failures += LocalMusicImportFailure(error.displayName, error.reason, uri.toString())
                } catch (_: Exception) {
                    failures += LocalMusicImportFailure("本地音乐", "无法保存或清理音乐副本", uri.toString())
                }
                processed++
                onProgress(LocalMusicImportProgress(processed, uris.size, ""))
            }
            LocalMusicImportResult(imported, duplicates, failures, uris.size - processed, stopped, duplicateKeys)
        }
    }

    /** Persist references in small batches. Never copy or delete the external audio files. */
    internal suspend fun importLocalReferences(
        files: List<io.legado.app.utils.FileDoc>, rootUri: String,
        shouldStop: () -> Boolean, onProgress: (LocalMusicImportProgress) -> Unit,
    ): LocalMusicImportResult = withContext(Dispatchers.IO) {
        ready.await()
        importMutex.withLock {
            val digests = LocalMusicLibrary.contentDigests(mutableSettings.value.localTracks, shouldStop).toMutableMap()
            val pending = ArrayList<MusicTrack>()
            val failures = ArrayList<LocalMusicImportFailure>()
            var imported = 0
            var duplicates = 0
            val duplicateKeys = HashSet<String>()
            var processed = 0
            var stopped = false
            suspend fun flush() {
                if (pending.isEmpty()) return
                val batch = pending.toList()
                pending.clear()
                withContext(NonCancellable) {
                    var additions = emptyList<MusicTrack>()
                    val saved = try { edit { current ->
                        val currentUris = current.localTracks.mapNotNull { it.localReference?.uri }
                            .mapTo(HashSet()) { LocalAudioAccess.identity(Uri.parse(it)) }
                        val currentDigests = current.localTracks.mapNotNull { digests[it.key] }.toHashSet()
                        additions = batch.filter { track ->
                            val reference = requireNotNull(track.localReference)
                            currentUris.add(LocalAudioAccess.identity(Uri.parse(reference.uri))) && currentDigests.add(reference.digest)
                        }
                        current.copy(localTracks = current.localTracks + additions)
                    } } catch (_: Exception) { false }
                    if (saved) {
                        imported += additions.size
                        duplicates += batch.size - additions.size
                        val accepted = additions.mapTo(HashSet(), MusicTrack::key)
                        batch.filterNot { it.key in accepted }.forEach {
                            duplicateKeys += LocalAudioAccess.identity(Uri.parse(requireNotNull(it.localReference).uri))
                        }
                        additions.forEach { digests[it.key] = requireNotNull(it.localReference).digest }
                    } else batch.forEach { failures += LocalMusicImportFailure(it.title, "无法保存本地音乐", it.localReference?.uri.orEmpty()) }
                }
            }
            try {
                onProgress(LocalMusicImportProgress(0, files.size, files.firstOrNull()?.name.orEmpty()))
                prepareLocalAudioInOrder(files, prepare = { file ->
                    currentCoroutineContext().ensureActive()
                    if (shouldStop()) throw LocalAudioImportStopped()
                    if (!LocalAudioAccess.belongsToRoot(file.uri, Uri.parse(rootUri))) {
                        throw MusicProviderException("文件不在所选目录中")
                    }
                    val current = LocalAudioAccess.document(file.uri)
                    val metadata = LocalAudioAccess.inspect(file.uri, current.name, current.size,
                        current.lastModified, loadCover = true)
                    val digest = LocalAudioAccess.digest(file.uri, shouldStop)
                    val after = LocalAudioAccess.document(file.uri)
                    if (after.size != current.size || after.lastModified != current.lastModified) {
                        throw MusicProviderException("文件已变化，请重新扫描")
                    }
                    LocalAudioAccess.track(current, rootUri, metadata, digest)
                }) { file, prepared ->
                    currentCoroutineContext().ensureActive()
                    if (shouldStop()) throw LocalAudioImportStopped()
                    onProgress(LocalMusicImportProgress(processed, files.size, file.name))
                    try {
                        if (!LocalAudioAccess.belongsToRoot(file.uri, Uri.parse(rootUri))) {
                            throw MusicProviderException("文件不在所选目录中")
                        }
                        val existing = mutableSettings.value.localTracks
                        val identity = LocalAudioAccess.identity(file.uri)
                        if (existing.any { it.localReference?.uri?.let { uri -> LocalAudioAccess.identity(Uri.parse(uri)) } == identity }) {
                            duplicates++
                            duplicateKeys += identity
                        }
                        else {
                            val track = prepared.getOrThrow()
                            val digest = requireNotNull(track.localReference).digest
                            if (existing.any { digests[it.key] == digest } ||
                                pending.any { it.localReference?.digest == digest }) {
                                duplicates++
                                duplicateKeys += identity
                            }
                            else pending += track
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failures += LocalMusicImportFailure(file.name,
                            (error as? MusicProviderException)?.message ?: "无法读取音频文件", file.uri.toString())
                    }
                    processed++
                    if (pending.size >= 20) flush()
                    onProgress(LocalMusicImportProgress(processed, files.size, file.name))
                }
            } catch (_: LocalAudioImportStopped) {
                stopped = true
            } finally { flush() }
            LocalMusicImportResult(imported, duplicates, failures, files.size - processed, stopped, duplicateKeys)
        }
    }

    internal suspend fun relocateLocal(key: String, file: io.legado.app.utils.FileDoc, rootUri: String,
        shouldStop: () -> Boolean = { false }): Boolean =
        withContext(Dispatchers.IO) {
            ready.await()
            importMutex.withLock {
                val old = mutableSettings.value.localTracks.firstOrNull { it.key == key && it.localReference != null }
                    ?: throw MusicProviderException("音乐已移出曲库")
                if (!LocalAudioAccess.belongsToRoot(file.uri, Uri.parse(rootUri))) {
                    throw MusicProviderException("文件不在所选目录中")
                }
                val current = LocalAudioAccess.document(file.uri)
                val metadata = LocalAudioAccess.inspect(file.uri, current.name, current.size,
                    current.lastModified, loadCover = true)
                val digest = LocalAudioAccess.digest(file.uri, shouldStop)
                val after = LocalAudioAccess.document(file.uri)
                if (after.size != current.size || after.lastModified != current.lastModified) {
                    throw MusicProviderException("文件已变化，请重新扫描")
                }
                if (shouldStop()) throw LocalAudioImportStopped()
                val replacement = LocalAudioAccess.track(current, rootUri, metadata, digest, old.id)
                edit { settings ->
                    if (settings.localTracks.none { it.key == key }) throw MusicProviderException("音乐已移出曲库")
                    if (settings.localTracks.any { it.key != key && it.localReference?.uri?.let { uri ->
                        LocalAudioAccess.identity(Uri.parse(uri)) } == LocalAudioAccess.identity(file.uri) }) {
                        throw MusicProviderException("此文件已在曲库中")
                    }
                    settings.copy(localTracks = settings.localTracks.map { if (it.key == key) replacement else it },
                        queue = settings.queue.map { if (it.key == key) replacement else it },
                        selectionRevision = settings.selectionRevision + if (settings.currentTrack?.key == key) 1 else 0)
                }
            }
        }

    suspend fun localStorageBytes(): Long = withContext(Dispatchers.IO) {
        ready.await()
        LocalMusicLibrary.storageBytes(mutableSettings.value.localTracks)
    }

    fun removeLocal(track: MusicTrack) = removeLocal(listOf(track))

    fun removeLocal(tracks: List<MusicTrack>): CompletableDeferred<Boolean> {
        val keys = tracks.filter { it.platform == MusicPlatform.LOCAL }.mapTo(HashSet(), MusicTrack::key)
        val completion = CompletableDeferred<Boolean>()
        var removed = emptyList<MusicTrack>()
        val sent = mutations.trySend(Mutation(transform = { current ->
                // Remove only files selected from the authoritative catalogue, never caller paths.
                removed = current.localTracks.filter { it.key in keys }
                val queue = current.queue.filterNot { it.key in keys }
                val currentKey = current.currentTrack?.key
                val retainedIndex = queue.indexOfFirst { it.key == currentKey }
                val nextIndex = if (retainedIndex >= 0) retainedIndex else {
                    current.queue.take(current.index).count { it.key !in keys }.coerceAtMost(queue.lastIndex)
                }
                current.copy(localTracks = current.localTracks.filterNot { it.key in keys },
                    queue = queue, index = nextIndex.coerceAtLeast(0),
                    enabled = current.enabled && queue.isNotEmpty(),
                    selectionRevision = current.selectionRevision +
                        if (currentKey in keys) 1 else 0)
            }, completion = completion, afterCommit = {
                var failure: Exception? = null
                removed.forEach { track ->
                    try { LocalMusicLibrary.remove(track) } catch (error: Exception) { failure = error }
                }
                failure?.let { throw it }
            }))
        if (sent.isFailure) completion.complete(false)
        return completion
    }

    suspend fun resolve(track: MusicTrack): MusicPlaybackSource = withContext(Dispatchers.IO) {
        if (track.platform == MusicPlatform.LOCAL) return@withContext LocalMusicLibrary.source(track)
        val result = MusicProviders.get(track.platform).resolve(track)
        if (result.preview == true) throw MusicProviderException("此曲目仅提供试听")
        validatePlaybackSource(track, result)
    }

    suspend fun resolvePreview(track: MusicTrack): MusicPlaybackSource = withContext(Dispatchers.IO) {
        if (track.platform == MusicPlatform.LOCAL) return@withContext LocalMusicLibrary.source(track)
        val result = MusicProviders.get(track.platform).resolvePreview(track)
        // Only QQ's declared RS02 resource currently has a verified standalone media timeline.
        if (result.preview == true &&
            !(track.platform == MusicPlatform.QQ && result.standalonePreview)) {
            throw MusicProviderException("此曲目仅提供试听")
        }
        validatePlaybackSource(track, result)
    }

    private fun validatePlaybackSource(track: MusicTrack, result: MusicPlaybackSource): MusicPlaybackSource {
        val uri = runCatching { URI(result.url) }.getOrNull()
            ?: throw MusicProviderException("音乐地址不可用")
        val host = uri.host.orEmpty().lowercase()
        val allowed = when (track.platform) {
            MusicPlatform.NETEASE -> host == "music.126.net" || host.endsWith(".music.126.net")
                || host == "music.163.com" || host.endsWith(".music.163.com")
            MusicPlatform.QQ -> host.endsWith(".qq.com") || host.endsWith(".qqmusic.com")
            MusicPlatform.LOCAL -> false
        }
        if (!allowed || uri.scheme !in listOf("http", "https") || uri.userInfo != null
            || uri.port !in listOf(-1, 80, 443)) throw MusicProviderException("音乐地址不可用")
        return result.copy(headers = result.headers.filterKeys { key ->
            key.equals("Referer", true) || key.equals("User-Agent", true) || key.equals("Origin", true)
        })
    }

    @Synchronized
    fun claimPlaybackOwner(): Long {
        owner++
        mutableProgress.value = BgmPlaybackProgress()
        return owner
    }

    @Synchronized
    fun isPlaybackOwner(candidate: Long): Boolean = candidate == owner

    @Synchronized
    fun publishPlayback(candidate: Long, state: BgmPlaybackState) {
        if (candidate == owner) mutablePlayback.value = state
    }

    @Synchronized
    internal fun publishProgress(candidate: Long, state: BgmPlaybackProgress) {
        if (candidate == owner) mutableProgress.value = state
    }

    @Synchronized
    fun releasePlaybackOwner(candidate: Long) {
        if (candidate == owner) {
            owner++
            mutablePlayback.value = BgmPlaybackState()
            mutableProgress.value = BgmPlaybackProgress()
        }
    }

    private fun change(
        operation: BgmQueueOperation? = null,
        transform: (BgmSettings) -> BgmSettings
    ): CompletableDeferred<Boolean> {
        val completion = CompletableDeferred<Boolean>()
        if (mutations.trySend(Mutation(transform, completion = completion, operation = operation)).isFailure) {
            completion.complete(false)
        }
        return completion
    }

    private suspend fun edit(
        expectedOwner: Long? = null,
        expectedRevision: Long? = null,
        operation: BgmQueueOperation? = null,
        transform: (BgmSettings) -> BgmSettings
    ): Boolean = mutex.withLock {
        fun expectedStateMatches(): Boolean =
            (expectedOwner == null || isPlaybackOwner(expectedOwner)) &&
                (expectedRevision == null || mutableSettings.value.selectionRevision == expectedRevision) &&
                operation?.isCancelled != true
        if (!expectedStateMatches()) return@withLock false
        val next = normalize(transform(mutableSettings.value))
        if (next == mutableSettings.value) {
            return@withLock operation?.commitIfActive { true } ?: true
        }
        val target = file
        val temporary = File(target.parentFile, "settings.json.tmp")
        try {
            target.parentFile?.mkdirs()
            temporary.writeText(gson.toJson(next))
            // Serialize the large payload off the main thread; the small commit is atomic
            // with host handoff so an old callback cannot publish after claimPlaybackOwner.
            synchronized(this@BgmRepository) {
                if (!expectedStateMatches()) false else {
                    fun commit(): Boolean {
                        check(temporary.renameTo(target))
                        mutableSettings.value = next
                        return true
                    }
                    operation?.commitIfActive(::commit) ?: commit()
                }
            }
        } catch (_: Exception) {
            mutablePlayback.value = BgmPlaybackState(BgmPlaybackStatus.ERROR, message = "无法保存背景音乐设置")
            false
        } finally {
            temporary.delete()
        }
    }

    private fun normalize(value: BgmSettings): BgmSettings {
        val queue = value.queue.filter { track -> runCatching {
            track.id.isNotBlank() && track.title.isNotBlank() && track.platform in MusicPlatform.entries
        }.getOrDefault(false) }.distinctBy(MusicTrack::key)
        val local = value.localTracks.filter { runCatching {
            it.platform == MusicPlatform.LOCAL && it.id.isNotBlank() &&
                (it.localReference?.let(LocalAudioAccess::referenceIsValid) ?: it.localPath.isNotBlank())
        }.getOrDefault(false) }.distinctBy(MusicTrack::key)
        return value.copy(queue = queue, localTracks = local,
            index = value.index.coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
            volume = value.volume.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.15f)
    }
}
