package io.legado.app.ui.book.bgm

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.BuildConfig
import io.legado.app.help.bgm.*
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.FileDoc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal data class BgmLocalImportState(
    val root: FileDoc? = null,
    val path: List<FileDoc> = emptyList(),
    val items: List<LocalAudioEntry> = emptyList(),
    val selectedKeys: Set<String> = emptySet(),
    val addedUris: Set<String> = emptySet(),
    val query: String = "",
    val searching: Boolean = false,
    val sort: Int = 0,
    val recursive: Boolean = false,
    val scanning: Boolean = false,
    val reading: Boolean = false,
    val showHidden: Boolean = false,
    val includeRecordings: Boolean = true,
    val minDuration: Long = 0,
    val discovered: Int = 0,
    val scanDirectory: String = "",
    val directoryFailures: List<String> = emptyList(),
    val error: String = "",
    val adding: Boolean = false,
    val stopping: Boolean = false,
    val progress: LocalMusicImportProgress? = null,
    val result: LocalMusicImportResult? = null,
    val relocating: Boolean = false,
) {
    fun canSelect(item: LocalAudioEntry) = !item.file.isDir && item.error.isBlank() && item.key !in addedUris
}

internal class BgmLocalImportViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("bgm-local-import", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(BgmLocalImportState(sort = preferences.getInt("sort", 0)))
    val state = mutableState.asStateFlow()
    private val entries = linkedMapOf<String, LocalAudioEntry>()
    private val selections = linkedMapOf<String, LocalAudioEntry>()
    private val knownDuplicates = HashSet<String>()
    private var visibleKeys = emptySet<String>()
    private var scanJob: Job? = null
    private var metadataJob: Job? = null
    private var publishJob: Job? = null
    private var addJob: Job? = null
    private var generation = 0L
    private var renderVersion = 0L
    private var metadataPaused = false
    private val stopAdding = AtomicBoolean(false)
    private var initialized = false
    private var relocateKey: String? = null
    private var lastFiles = emptyList<FileDoc>()
    private var lastCopies = emptyList<Uri>()
    private var scanStartedAt: Long? = null

    init {
        viewModelScope.launch {
            BgmRepository.settings.map { it.localTracks }.distinctUntilChanged().collect { tracks ->
                val added = tracks.filterNot { it.key == relocateKey }
                    .mapNotNull { it.localReference?.uri }.mapTo(HashSet()) { LocalAudioAccess.identity(Uri.parse(it)) }
                    .apply { addAll(knownDuplicates) }
                selections.keys.removeAll(added)
                mutableState.update { it.copy(addedUris = added, selectedKeys = selections.keys.toSet()) }
            }
        }
    }

    private fun savedSourceKeys(): Set<String> = BgmRepository.settings.value.localTracks
        .filterNot { it.key == relocateKey }.mapNotNull { it.localReference?.uri }
        .mapTo(HashSet()) { LocalAudioAccess.identity(Uri.parse(it)) }

    fun initialize(initialRoot: String?, relocatingKey: String?, recursive: Boolean) {
        if (initialized) return
        initialized = true
        relocateKey = relocatingKey
        mutableState.update { it.copy(relocating = relocatingKey != null, addedUris = savedSourceKeys()) }
        val last = initialRoot ?: preferences.getString("directory", null)
        if (!last.isNullOrBlank()) chooseRoot(Uri.parse(last), recursive)
    }

    fun chooseRoot(uri: Uri, recursive: Boolean = false) {
        if (state.value.adding) return
        stopScan()
        val token = ++generation
        selections.clear()
        knownDuplicates.clear()
        entries.clear()
        mutableState.update { it.copy(root = null, path = emptyList(), selectedKeys = emptySet(), error = "",
            items = emptyList(), scanning = true, recursive = recursive, addedUris = savedSourceKeys()) }
        scanJob = viewModelScope.launch {
            try {
                val root = withContext(Dispatchers.IO) { LocalAudioAccess.root(uri) }
                ensureActive()
                if (token != generation) return@launch
                preferences.edit().putString("directory", root.uri.toString()).apply()
                mutableState.update { it.copy(root = root, path = listOf(root), scanning = false) }
                load(recursive)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (token == generation) mutableState.update { it.copy(scanning = false,
                    error = (error as? MusicProviderException)?.message ?: "目录不可访问，请重新选择") }
            }
        }
    }

    fun openDirectory(entry: LocalAudioEntry) {
        if (!entry.file.isDir || state.value.adding) return
        mutableState.update { it.copy(path = it.path + entry.file) }
        load(false)
    }

    fun goUp(): Boolean {
        val current = state.value
        if (current.adding) { stopImport(); return true }
        if (current.searching) { search(false); return true }
        if (current.recursive) { load(false); return true }
        if (current.path.size <= 1) return false
        mutableState.update { it.copy(path = it.path.dropLast(1)) }
        load(false)
        return true
    }

    fun scanChildren() = load(true)
    fun reload() = load(state.value.recursive)

    private fun load(recursive: Boolean) {
        val root = state.value.root ?: return
        val directory = state.value.path.lastOrNull() ?: root
        stopScan()
        val token = ++generation
        metadataPaused = false
        scanStartedAt = SystemClock.elapsedRealtime()
        entries.clear()
        knownDuplicates.clear()
        visibleKeys = emptySet()
        mutableState.update { it.copy(items = emptyList(), discovered = 0, scanning = true, recursive = recursive,
            directoryFailures = emptyList(), scanDirectory = directory.name, error = "", query = "", addedUris = savedSourceKeys()) }
        scanJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    LocalAudioScanner.scan(root, directory, recursive, state.value.showHidden,
                        onBatch = { batch, path -> withContext(Dispatchers.Main) {
                            if (token == generation) {
                                batch.forEach { entries[it.key] = it }
                                mutableState.update { it.copy(discovered = entries.values.count { item -> !item.file.isDir },
                                    scanDirectory = path) }
                                publish()
                                readMetadata()
                            }
                        } },
                        onFailure = { path -> withContext(Dispatchers.Main) { mutableState.update {
                            if (token == generation) it.copy(directoryFailures = it.directoryFailures + path) else it
                        } } })
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (token == generation) mutableState.update {
                    it.copy(directoryFailures = it.directoryFailures + directory.name)
                }
            } finally {
                if (token == generation) {
                    mutableState.update { it.copy(scanning = false, error = if (entries.isEmpty() &&
                        it.directoryFailures.isNotEmpty()) "目录不可访问，请重新选择或授权" else "") }
                    publish()
                    readMetadata()
                }
            }
        }
    }

    fun stopScan() {
        scanStartedAt = null
        generation++
        metadataPaused = true
        scanJob?.cancel()
        metadataJob?.cancel()
        metadataJob = null
        mutableState.update { it.copy(scanning = false, reading = false) }
    }

    fun visible(keys: Set<String>) { visibleKeys = keys; readMetadata() }

    private fun readMetadata() {
        if (metadataPaused || metadataJob?.isActive == true || state.value.adding || entries.values.none {
            !it.file.isDir && it.error.isBlank() && (it.metadata == null || (it.key in visibleKeys && !it.coverChecked))
        }) return
        val token = generation
        metadataJob = viewModelScope.launch {
            mutableState.update { it.copy(reading = true) }
            try {
                while (token == generation) {
                    ensureActive()
                    val pending = entries.values.filter { !it.file.isDir && it.error.isBlank() &&
                        (it.metadata == null || (it.key in visibleKeys && !it.coverChecked)) }
                    if (pending.isEmpty()) break
                    val tasks = pending.filter { it.key in visibleKeys } + pending.filterNot { it.key in visibleKeys }
                    // Claim work without a main-thread round trip per song. The bounded queue lets
                    // file reads continue while Compose renders the latest published batch.
                    val next = AtomicInteger()
                    coroutineScope {
                        val completed = Channel<LocalAudioEntry>(32)
                        val workers = List(minOf(LocalAudioAccess.MAX_CONCURRENT_PROBES, tasks.size)) {
                            launch(Dispatchers.IO) {
                                while (true) {
                                    ensureActive()
                                    val entry = tasks.getOrNull(next.getAndIncrement()) ?: break
                                    val updated = try {
                                        // Tags and artwork share the same read; no second open on add/scroll.
                                        entry.copy(metadata = LocalAudioAccess.inspect(entry.file.uri, entry.file.name,
                                            entry.file.size, entry.file.lastModified, loadCover = true), coverChecked = true)
                                    } catch (error: Exception) {
                                        if (error is CancellationException) throw error
                                        entry.copy(error = (error as? MusicProviderException)?.message ?: "无法读取音频")
                                    }
                                    completed.send(updated)
                                }
                            }
                        }
                        launch { workers.joinAll(); completed.close() }
                        try {
                            for (updated in completed) {
                                if (token == generation) {
                                    entries[updated.key] = updated
                                    if (updated.error.isNotBlank()) selections.remove(updated.key)
                                    else if (updated.key in selections) selections[updated.key] = updated
                                    publish()
                                }
                            }
                        } finally { completed.cancel() }
                    }
                }
            } finally { if (token == generation) {
                mutableState.update { it.copy(reading = false) }
                metadataJob = null
                publish()
                viewModelScope.launch { readMetadata() }
            } }
        }
    }

    private fun publish() {
        renderVersion++
        if (publishJob?.isActive == true) return
        publishJob = viewModelScope.launch {
            var renderedVersion: Long
            do {
                delay(80)
                renderedVersion = renderVersion
                val token = generation
                val all = entries.values.toList()
                val current = state.value
                val list = withContext(Dispatchers.Default) {
                    val query = current.query.trim()
                    val filtered = all.filter { entry ->
                        (current.includeRecordings || !LocalAudioFormat.isRecording(entry.file.name)) &&
                            (entry.file.isDir || current.minDuration == 0L || entry.metadata?.durationMs?.let { it >= current.minDuration } == true) &&
                            (query.isBlank() || entry.title.contains(query, true) ||
                                entry.metadata?.artist?.contains(query, true) == true || entry.file.name.contains(query, true))
                    }
                    val order: Comparator<LocalAudioEntry> = when (current.sort) {
                        1 -> compareBy<LocalAudioEntry> { it.metadata?.artist?.takeIf(String::isNotBlank) ?: "\uffff" }
                        2 -> compareBy { it.metadata?.durationMs?.takeIf { time -> time > 0 }?.let { time -> -time } ?: Long.MAX_VALUE }
                        3 -> compareBy { -it.file.size }
                        4 -> compareBy { -it.file.lastModified }
                        else -> compareBy(AlphanumComparator) { it.title }
                    }
                    filtered.sortedWith(compareBy<LocalAudioEntry> { !it.file.isDir }.then(order)
                        .thenBy(AlphanumComparator) { it.file.name })
                }
                if (token == generation) mutableState.update { it.copy(items = list, selectedKeys = selections.keys.toSet()) }
            } while (renderedVersion != renderVersion)
            val started = scanStartedAt
            val current = state.value
            if (started != null && !current.scanning && !current.reading &&
                entries.values.none { !it.file.isDir && it.metadata == null && it.error.isBlank() }) {
                scanStartedAt = null
                if (BuildConfig.DEBUG) Log.d("LocalAudioPerformance",
                    "scan_ready count=${current.discovered} elapsedMs=${SystemClock.elapsedRealtime() - started} " +
                        LocalAudioAccess.debugProbeCounts())
            }
        }
    }

    fun query(value: String) { mutableState.update { it.copy(query = value) }; publish() }
    fun search(value: Boolean) { mutableState.update { it.copy(searching = value, query = if (value) it.query else "") }; publish() }
    fun sort(value: Int) { preferences.edit().putInt("sort", value).apply(); mutableState.update { it.copy(sort = value) }; publish() }
    fun toggleHidden() { mutableState.update { it.copy(showHidden = !it.showHidden) }; reload() }
    fun toggleRecordings() { mutableState.update { it.copy(includeRecordings = !it.includeRecordings) }; publish() }
    fun duration(value: Long) { mutableState.update { it.copy(minDuration = value) }; publish() }

    fun toggle(entry: LocalAudioEntry) {
        if (state.value.adding || !state.value.canSelect(entry)) return
        if (entry.key in selections) selections.remove(entry.key)
        else { if (state.value.relocating) selections.clear(); selections[entry.key] = entry }
        mutableState.update { it.copy(selectedKeys = selections.keys.toSet()) }
    }

    fun selectAll(invert: Boolean = false) {
        if (state.value.adding || state.value.relocating) return
        val selectable = state.value.items.filter(state.value::canSelect)
        val clear = !invert && selectable.isNotEmpty() && selectable.all { it.key in selections }
        selectable.forEach { if (clear || (invert && it.key in selections)) selections.remove(it.key) else selections[it.key] = it }
        mutableState.update { it.copy(selectedKeys = selections.keys.toSet()) }
    }

    fun preview(entry: LocalAudioEntry): MusicTrack? {
        val root = state.value.root ?: return null
        if (entry.file.isDir || entry.error.isNotBlank()) return null
        return LocalAudioAccess.track(entry.file, root.uri.toString(), entry.metadata ?:
            LocalAudioMetadata(entry.title, mime = ""))
    }

    fun addSelected() {
        val files = selections.values.map { it.file }
        if (files.isEmpty()) return
        beginImport(files, emptyList())
    }

    fun importCopies(uris: List<Uri>) { if (uris.isNotEmpty()) beginImport(emptyList(), uris) }

    private fun beginImport(files: List<FileDoc>, copies: List<Uri>) {
        if (state.value.adding) return
        stopScan()
        val started = SystemClock.elapsedRealtime()
        lastFiles = files
        lastCopies = copies
        stopAdding.set(false)
        mutableState.update { it.copy(adding = true, stopping = false, result = null,
            progress = LocalMusicImportProgress(0, files.size + copies.size, "")) }
        addJob = viewModelScope.launch {
            val result = try {
                val onProgress: (LocalMusicImportProgress) -> Unit = { progress -> mutableState.update { it.copy(progress = progress) } }
                if (copies.isNotEmpty()) BgmRepository.importLocalBatch(copies, stopAdding::get, onProgress)
                else {
                    val rootUri = state.value.root?.uri?.toString() ?: throw MusicProviderException("请选择音乐目录")
                    val key = relocateKey
                    if (key != null) {
                        if (BgmRepository.relocateLocal(key, files.first(), rootUri, stopAdding::get)) LocalMusicImportResult(1, 0, emptyList())
                        else throw MusicProviderException("无法保存音乐位置")
                    } else BgmRepository.importLocalReferences(files, rootUri, stopAdding::get, onProgress)
                }
            } catch (_: LocalAudioImportStopped) {
                LocalMusicImportResult(0, 0, emptyList(), files.size + copies.size, stopped = true)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                LocalMusicImportResult(0, 0, listOf(LocalMusicImportFailure("本地音乐",
                    (error as? MusicProviderException)?.message ?: "添加失败，请重试")))
            }
            selections.clear()
            knownDuplicates += result.duplicateKeys
            mutableState.update { it.copy(adding = false, stopping = false, selectedKeys = emptySet(), result = result,
                addedUris = it.addedUris + knownDuplicates) }
            if (BuildConfig.DEBUG) Log.d("LocalAudioPerformance",
                "add_ready count=${files.size + copies.size} imported=${result.imported} " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - started} stopped=${result.stopped}")
            metadataPaused = false
            readMetadata()
        }
    }

    fun stopImport() { stopAdding.set(true); mutableState.update { it.copy(stopping = true) } }
    fun dismissResult() { mutableState.update { it.copy(result = null) } }
    fun retry() {
        val keys = state.value.result?.failures?.mapNotNull { it.sourceKey.takeIf(String::isNotBlank) }?.toSet().orEmpty()
        beginImport(if (keys.isEmpty()) lastFiles else lastFiles.filter { it.uri.toString() in keys },
            if (keys.isEmpty()) lastCopies else lastCopies.filter { it.toString() in keys })
    }
}
