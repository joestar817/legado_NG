package io.legado.app.help.resource

import android.graphics.Bitmap
import io.legado.app.help.home.HomeWidgetSkinPackage
import io.legado.app.help.home.HomeWidgetSkinStore
import io.legado.app.help.home.HomeWidgetSkinRecord
import io.legado.app.help.pet.PetPluginStore
import io.legado.app.help.pet.PetLocalManager
import io.legado.app.help.pet.PetLocalRecord
import io.legado.app.help.pet.PetPackageInfo
import io.legado.app.help.pet.PetPackageDisplay
import io.legado.app.help.pet.PetInstallOrigin
import io.legado.app.help.pet.PetImportCandidate
import io.legado.app.help.pet.PetImportResult
import android.graphics.BitmapFactory
import io.legado.app.help.background.BackgroundDeletionResult
import io.legado.app.help.background.BackgroundDeletionTarget
import io.legado.app.help.background.ReadingBackgroundLibrary
import io.legado.app.BuildConfig
import io.legado.app.help.http.await
import io.legado.app.help.http.GitHubRelay
import io.legado.app.help.font.FontLibraryStore
import io.legado.app.help.font.FontUsageInspector
import io.legado.app.help.font.LocalFontAsset
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import splitties.init.appCtx
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** 页面退出不持有 Activity；网络候选可取消，最终入库串行且可恢复。 */
internal object ResourceRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(ResourceCenterState())
    val state = mutableState.asStateFlow()
    private val sessionLock = Any()
    private val catalogCache by lazy { ResourceCatalogDiskCache(File(appCtx.cacheDir, "resource-index-cache")) }
    private val sessionClients = mutableSetOf<Long>()
    private var nextSessionClient = 0L
    private var catalogLoader: ResourceCatalogLoader? = null
    private var catalogObserver: Job? = null
    private val installMutex = Mutex()
    private val updateAllMutex = Mutex()
    private val previewMutex = Mutex()
    private val fontPreviewPermits = Semaphore(2)
    private val fontPreviewLocks = Array(32) { Mutex() }
    private val fontPreviewCacheMutex = Mutex()
    private val fontPreviewCacheName = Regex("(?:[a-f0-9]{64}\\.png|\\.[a-f0-9-]{36}\\.part)")
    private val jobs = ConcurrentHashMap<String, Job>()
    private val payloadLeaseLock = Any()
    private val payloadLeases = mutableMapOf<String, Int>()
    // installMutex protects this retry set; cache bookkeeping must not fail a completed install.
    private val pendingFontPayloadCleanup = mutableSetOf<String>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(GitHubRelay.METADATA_ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.MINUTES).build()
    private val metadataClient = client.newBuilder()
        .readTimeout(GitHubRelay.METADATA_ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(GitHubRelay.METADATA_ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    private val fontPreviewClient = client.newBuilder()
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val cacheDir get() = File(ResourceFiles.root, "payload-cache").apply { mkdirs() }
    private val fontPreviewCacheDir get() = File(appCtx.cacheDir, "resource-previews").apply { mkdirs() }

    init {
        scope.launch { PetPluginStore.revision.collect { reconcile() } }
        scope.launch { HomeWidgetSkinStore.revision.collect { reconcile() } }
    }

    /** ViewModel持有租约，配置变化不重开会话，最后一个页面退出才释放远端索引。 */
    fun openSession(): Long = synchronized(sessionLock) {
        val token = ++nextSessionClient
        sessionClients.add(token)
        if (catalogLoader == null) {
            mutableState.update { clearCatalog(it).copy(localChecked = false, tasks = it.tasks.filterValues { task -> task.running }) }
            val loader = ResourceCatalogLoader(scope, object : ResourceCatalogSource {
                override suspend fun latestCommit(): String = metadata(
                    "https://github.com/${ResourceProtocol.REPOSITORY}.git/info/refs?service=git-upload-pack",
                    originFirst = true, decode = ResourceProtocol::catalogCommit,
                )
                override suspend fun catalog(commit: String): ResourceCatalog {
                    val catalog = json(rawUrl(commit, "catalog.json"), ResourceCatalog::class.java, ResourceProtocol::validateCatalog)
                    return catalog.copy(categories = catalog.categories.filter { ResourceProtocol.isBrowsableType(it.type) })
                }
                override suspend fun index(commit: String, catalog: ResourceCatalog, category: ResourceCategory): ResourceIndex {
                    val hash = category.indexSha256
                    if (hash == null) {
                        return json(rawUrl(commit, category.index), ResourceIndex::class.java) {
                            ResourceProtocol.validateIndex(catalog, category, it)
                        }
                    }
                    catalogCache.readIndex(catalog, category)?.let { return it }
                    val path = "resources/${category.id}/index.content.json"
                    val (bytes, index) = metadata(rawUrl(commit, path)) {
                        it to decodeResourceContentIndex(it, catalog, category)
                    }
                    currentCoroutineContext().ensureActive()
                    catalogCache.rememberIndex(hash, bytes)
                    return index
                }
            }, ::resourceNetworkMessage, cache = catalogCache)
            catalogLoader = loader
            catalogObserver = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                loader.state.collect { remote ->
                    synchronized(sessionLock) {
                        if (catalogLoader === loader) mutableState.update {
                            it.copy(snapshot = remote.snapshot, loading = remote.checking,
                                loadedCategories = remote.loaded, pendingCategories = remote.pending,
                                categoryErrors = remote.errors, error = remote.error, accessDenied = remote.accessDenied,
                                cachedSnapshot = remote.cached?.snapshot)
                        }
                    }
                }
            }
        }
        checkLocalFiles()
        token
    }

    fun closeSession(token: Long) = synchronized(sessionLock) {
        sessionClients.remove(token)
        if (sessionClients.isEmpty()) {
            catalogObserver?.cancel()
            catalogObserver = null
            catalogLoader?.close()
            catalogLoader = null
            mutableState.update(::clearCatalog)
        }
    }

    private fun clearCatalog(value: ResourceCenterState) = value.copy(
        snapshot = null, cachedSnapshot = null, loading = false, error = null, accessDenied = false,
        loadedCategories = emptySet(), pendingCategories = emptySet(), categoryErrors = emptyMap(),
    )

    fun prioritize(types: Set<String>?) { synchronized(sessionLock) { catalogLoader?.prioritize(types) } }
    fun revalidate() { synchronized(sessionLock) { catalogLoader?.refresh(force = false) } }
    fun retryCategories(ids: Set<String>) { synchronized(sessionLock) { catalogLoader?.retry(ids) } }

    fun checkLocalFiles() { scope.launch { reconcile() } }

    /** Keep installation and deletion serialized; a removed image is MISSING in the existing ledger. */
    suspend fun deleteReadingBackgrounds(targets: List<BackgroundDeletionTarget>): BackgroundDeletionResult =
        withContext(Dispatchers.IO) {
            installMutex.withLock {
                currentCoroutineContext().ensureActive()
                val records = recoverFontDeletionLocked(recoverInstallationLocked(ResourceInstallStore.load()))
                withContext(NonCancellable) {
                    try {
                        ReadingBackgroundLibrary.deleteLocked(targets)
                    } finally {
                        val locals = records.associate { it.entry.item.id to ResourceInstaller.localState(it) }
                        mutableState.update { it.withLocalInstallations(records, locals, it.tasks) }
                    }
                }
            }
        }

    /** Resolve the current installed font again when a use request crosses an Activity boundary. */
    suspend fun installedFontPath(id: String): String? = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val record = ResourceInstallStore.load().firstOrNull {
                it.entry.item.id == id && it.entry.category.type == "font"
            } ?: return@withLock null
            if (ResourceInstallStore.pendingFontDeletion()?.paths?.contains(record.target) == true ||
                ResourceInstaller.localState(record) != ResourceLocalState.PRESENT
            ) return@withLock null
            ResourceInstaller.fileFor(record)?.takeIf { it.isFile }?.absolutePath
        }
    }

    suspend fun homeWidgetSkinPreview(id: String, detail: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val file = HomeWidgetSkinStore.file(id) ?: return@withContext null
        runCatching { HomeWidgetSkinPackage.previewBitmap(file, if (detail) 1 else 4) }.getOrNull()
    }

    /** Installed receipts are local data; this projection never opens a catalog session. */
    private fun localPetRecordsLocked(records: List<ResourceInstallation>): List<PetLocalRecord> =
        PetLocalManager.records().map { pet ->
            val receipt = PetResourcePolicy.matchingReceipt(pet, records) ?: return@map pet
            val entry = receipt.entry
            val origin = if (ResourceProtocol.validCommit(entry.sourceCommit) &&
                ResourceProtocol.validCommit(entry.catalogCommit)) PetInstallOrigin(
                channel = "resource", resourceId = entry.item.id, repository = receipt.repository,
                sourceCommit = entry.sourceCommit, catalogCommit = entry.catalogCommit, sha256 = pet.sha256,
            ) else null
            pet.copy(info = pet.info ?: PetPackageInfo(
                versionName = entry.item.versionName,
                author = entry.item.author.takeIf { it.isNotBlank() },
                description = entry.item.description.takeIf { it.isNotBlank() }, license = entry.item.license,
            ), origin = pet.origin ?: origin)
        }

    suspend fun localPetRecords(): List<PetLocalRecord> = withContext(Dispatchers.IO) {
        installMutex.withLock {
            // Optional display enrichment must not make the local manager depend on a healthy online ledger.
            localPetRecordsLocked(runCatching { ResourceInstallStore.load() }.getOrDefault(emptyList()))
        }
    }

    suspend fun localPetPreview(id: String): ByteArray? = withContext(Dispatchers.IO) {
        installMutex.withLock {
            runCatching {
                PetPluginStore.readPreview(id)?.let { return@withLock it }
                val pet = PetPluginStore.record(id) ?: return@withLock null
                val receipt = PetResourcePolicy.matchingReceipt(pet, ResourceInstallStore.load())
                    ?: return@withLock null
                ResourceProtocol.validatePreview(receipt.entry.item.preview, "pet")
                val preview = receipt.entry.item.preview?.detail ?: return@withLock null
                val file = File(fontPreviewCacheDir, preview.sha256 + ".png")
                if (!file.isFile || file.length() != preview.size) return@withLock null
                val bytes = PetPackageDisplay.readBounded(file.inputStream(), PetPackageDisplay.MAX_PREVIEW_BYTES)
                require(ResourceProtocol.hash(bytes) == preview.sha256)
                PetPackageDisplay.validatePng(bytes)
                bytes
            }.getOrNull()
        }
    }

    suspend fun localPetCatalogPreview(pet: PetLocalRecord): Bitmap? = withContext(Dispatchers.IO) {
        val entry = installMutex.withLock {
            val receipts = runCatching { ResourceInstallStore.load() }.getOrDefault(emptyList())
            PetResourcePolicy.matchingReceipt(pet, receipts)?.entry
        } ?: return@withContext null
        // Release the install lock before downloading an optional display asset.
        catalogPreview(entry, detail = true)
    }

    private fun publishLocalPetsLocked(records: List<ResourceInstallation>?) {
        val pets = localPetRecordsLocked(records.orEmpty()).associateBy { it.id }
        val locals = records?.let { runCatching {
            it.associate { record -> record.entry.item.id to ResourceInstaller.localState(record) }
        }.getOrNull() }
        mutableState.update {
            if (records != null && locals != null) it.withLocalInstallations(records, locals).copy(petInstallations = pets)
            else it.copy(petInstallations = pets, storageError = "资源中心安装记录暂不可读，本地宠物仍可管理")
        }
    }

    suspend fun commitLocalPet(candidate: PetImportCandidate, allowReplace: Boolean,
        allowDowngrade: Boolean): PetImportResult = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val records = runCatching { recoverInstallationLocked(ResourceInstallStore.load()) }.getOrNull()
            withContext(NonCancellable) {
                val result = PetLocalManager.commit(candidate, allowReplace, allowDowngrade)
                // The installation has committed; an unrelated resource file must not turn success into failure.
                runCatching { publishLocalPetsLocked(records) }.onFailure {
                    mutableState.update { state -> state.copy(storageError = "宠物已导入，资源列表待刷新") }
                }
                result
            }
        }
    }

    suspend fun removeLocalPet(id: String): Unit = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val records = runCatching { recoverInstallationLocked(ResourceInstallStore.load()) }.getOrNull()
            val related = records.orEmpty().filter { it.entry.category.type == "pet" && it.target == id }
            val remoteIds = related.map { it.entry.item.id } + state.value.snapshot?.entries.orEmpty()
                .filter { it.item.payload.pluginId == id }.map { it.item.id }
            check(remoteIds.none { jobs[it]?.isActive == true } &&
                (records != null || jobs.values.none { it.isActive })) { "插件正在下载或安装，请先取消" }
            withContext(NonCancellable) {
                var failure: Exception? = null
                try { PetPluginStore.remove(id) } catch (error: Exception) { failure = error }
                val removed = PetPluginStore.record(id) == null
                if (removed) {
                    val updated = records?.filterNot { it in related }
                    if (updated != null) runCatching { ResourceInstallStore.save(updated) }.onFailure {
                        failure = failure ?: (it as? Exception ?: IllegalStateException("无法更新资源记录", it))
                    }
                    publishLocalPetsLocked(if (failure == null) updated else null)
                }
                failure?.let { throw IllegalStateException(
                    if (removed) "宠物已卸载，但部分文件或资源记录清理失败" else "宠物卸载失败，请重试", it,
                ) }
            }
        }
    }

    private suspend fun reconcile() = installMutex.withLock {
        val checkedTasks = state.value.tasks
        try {
            val records = recoverFontDeletionLocked(recoverInstallationLocked(ResourceInstallStore.load()))
            val locals = records.associate { it.entry.item.id to ResourceInstaller.localState(it) }
            val pets = localPetRecordsLocked(records).associateBy { it.id }
            mutableState.update { it.withLocalInstallations(records, locals, checkedTasks).copy(petInstallations = pets) }
            HomeWidgetSkinStore.refresh()
            mutableState.update { it.copy(widgetSkins = HomeWidgetSkinStore.installed.value.associateBy { skin -> skin.id }) }
            runCatching { cleanupFontPayloadsLocked(records) }
        } catch (error: Exception) {
            mutableState.update { it.copy(storageError = "无法读取安装记录：${error.message}") }
        }
    }

    private fun recoverInstallationLocked(initial: List<ResourceInstallation>): List<ResourceInstallation> {
        var records = initial
        ResourceInstallStore.pending()?.let { pending ->
            if (ResourceInstaller.fingerprint(pending) == pending.baseline) {
                records = records.filterNot { it.entry.item.id == pending.entry.item.id } + pending
                ResourceInstallStore.save(records)
            }
            ResourceInstallStore.clearPending()
        }
        return records
    }

    private suspend fun commitInstallationLocked(
        records: List<ResourceInstallation>,
        record: ResourceInstallation,
        candidate: PreparedResource,
        onCommitted: () -> Unit,
    ): List<ResourceInstallation> {
        ResourceInstallStore.prepare(record)
        try {
            candidate.commit()
        } catch (conflict: ResourceConflict) {
            // A confirmation can become stale before the final write.
            ResourceInstallStore.clearPending()
            throw conflict
        }
        onCommitted()
        val verified = record.copy(baseline = ResourceInstaller.fingerprint(record) ?: error("入库后资源不存在"))
        ResourceInstallStore.prepare(verified)
        val updated = records.filterNot { it.entry.item.id == record.entry.item.id } + verified
        ResourceInstallStore.save(updated)
        ResourceInstallStore.clearPending()
        return updated
    }

    /** 删除托管字体及其台账。用中的字体需要单独确认其引用。 */
    suspend fun deleteFont(asset: LocalFontAsset, confirmedUsages: List<String>? = null): Unit = withContext(Dispatchers.IO) {
        val selectedPath = ResourceFontDeletionPolicy.singlePath(asset)
        installMutex.withLock {
            currentCoroutineContext().ensureActive()
            try {
                withContext(NonCancellable) {
                    synchronized(FontLibraryStore.lock) {
                        var records = recoverFontDeletionLocked(recoverInstallationLocked(ResourceInstallStore.load()))
                        check(jobs.values.none { it.isActive }) { "资源正在下载或安装，请稍后删除" }
                        // Locate the current path without rehashing every unrelated font in the library.
                        // The selected file is always hashed below and again by deletion recovery.
                        val currentGroup = FontLibraryStore.list().firstOrNull { it.id == asset.id }
                            ?: error("字体已变化，请刷新列表")
                        check(currentGroup.paths.any { File(it).canonicalPath == selectedPath }) { "字体文件已变化，请刷新后重试" }
                        val selectedFile = requireNotNull(FontLibraryStore.managedFile(selectedPath)) { "字体删除路径越界" }
                        check(selectedFile.isFile && ResourceFiles.hash(selectedFile) == asset.id) { "字体文件已变化，请刷新后重试" }
                        val current = currentGroup.copy(path = selectedPath, paths = listOf(selectedPath), size = selectedFile.length())
                        FontUsageInspector.verifyDeletion(current.paths, confirmedUsages)
                        val related = ResourceFontDeletionPolicy.recordsToRemove(records, current.paths)
                        ResourceInstallStore.prepareFontDeletion(ResourceFontDeletion(
                            id = current.id, name = current.name, path = current.path,
                            paths = current.paths, size = current.size,
                            payloadHashes = (related.map { it.entry.item.payload.sha256 } + current.id).distinct(),
                            confirmedUsages = confirmedUsages,
                        ))
                        records = recoverFontDeletionLocked(records)
                        check(!selectedFile.exists()) { "字体正在使用或文件已变化，请刷新后重试" }
                        val locals = records.associate { it.entry.item.id to ResourceInstaller.localState(it) }
                        mutableState.update { it.withLocalInstallations(records, locals) }
                        runCatching { cleanupFontPayloadsLocked(records) }
                    }
                }
            } catch (error: Exception) {
                if (runCatching { ResourceInstallStore.pendingFontDeletion() != null }.getOrDefault(true)) {
                    mutableState.update { it.copy(storageError = "字体删除未完成，请重新进入后检查") }
                }
                throw error
            }
        }
    }

    /** Called with installMutex; the font-library monitor is reentrant and always second in lock order. */
    private fun recoverFontDeletionLocked(records: List<ResourceInstallation>): List<ResourceInstallation> {
        val deletion = ResourceInstallStore.pendingFontDeletion() ?: return records
        return synchronized(FontLibraryStore.lock) {
            val files = deletion.paths.map { path ->
                requireNotNull(FontLibraryStore.managedFile(path)) { "字体删除路径越界" }
            }
            val fingerprints = files.map { file ->
                when {
                    !file.exists() -> null
                    file.isFile -> ResourceFiles.hash(file)
                    else -> ""
                }
            }
            val cancellation = ResourceFontDeletionPolicy.recoveryCancellation(
                deletion.id, fingerprints, deletion.confirmedUsages,
            ) {
                FontUsageInspector.deletionUsages(deletion.paths)
            }
            if (cancellation != null) {
                // The old confirmation no longer applies. Keep remaining files/configuration and reconcile normally.
                ResourceInstallStore.clearFontDeletion()
                mutableState.update { it.copy(error = cancellation) }
                return@synchronized records
            }
            val removed = ResourceFontDeletionPolicy.recordsToRemove(records, deletion.paths)
            val removedIds = removed.map { it.entry.item.id }.toSet()
            val updated = records.filterNot { it.entry.item.id in removedIds }
            // Any interruption leaves this exact path set in the deletion journal for a guarded retry.
            FontLibraryStore.deleteFiles(deletion.asset())
            if (deletion.confirmedUsages != null) FontUsageInspector.resetAfterDeletion(deletion.paths)
            ResourceInstallStore.save(updated)
            ResourceInstallStore.saveFontCacheCleanup(ResourceInstallStore.fontCacheCleanup() + deletion.payloadHashes)
            ResourceInstallStore.clearFontDeletion()
            mutableState.update { it.copy(tasks = it.tasks - removedIds) }
            updated
        }
    }

    private fun acquirePayloadLease(hash: String) = synchronized(payloadLeaseLock) {
        payloadLeases[hash] = (payloadLeases[hash] ?: 0) + 1
    }

    private fun releasePayloadLease(hash: String, type: String) {
        synchronized(payloadLeaseLock) {
            val count = (payloadLeases[hash] ?: 1) - 1
            if (count == 0) payloadLeases.remove(hash) else payloadLeases[hash] = count
        }
        scope.launch {
            installMutex.withLock {
                if (type == "font") queueFontPayloadCleanupLocked(listOf(hash))
                if (type == HOME_WIDGET_SKIN_TYPE) synchronized(payloadLeaseLock) {
                    if (hash !in payloadLeases) File(cacheDir, hash).delete()
                }
                // Cache failures never turn a successful install into a failed resource operation.
                runCatching { cleanupFontPayloadsLocked(ResourceInstallStore.load()) }
            }
        }
    }

    private fun cleanupFontPayloadsLocked(records: List<ResourceInstallation>) = synchronized(payloadLeaseLock) {
        // Installed fonts already have a real file. Re-derive their cache keys after a process exit,
        // including an exit between saving the installation and enqueueing its temporary payload.
        val queued = runCatching { ResourceInstallStore.fontCacheCleanup() }.getOrNull()
        val candidates = queued.orEmpty() + pendingFontPayloadCleanup +
            records.filter { it.entry.category.type == "font" }.map { it.entry.item.payload.sha256 }
        if (candidates.isEmpty()) return@synchronized
        val retained = ResourceFontDeletionPolicy.payloadsNeededByOtherResources(
            records, state.value.snapshot?.entries.orEmpty(),
        )
        val cleanable = ResourceFontDeletionPolicy.cleanablePayloads(candidates, retained, payloadLeases.keys)
        val deleted = cleanable.filter { hash ->
            val file = File(cacheDir, hash)
            file.canonicalFile.parentFile == cacheDir.canonicalFile && (!file.exists() || file.delete())
        }.toSet()
        pendingFontPayloadCleanup.removeAll(deleted)
        // A damaged queue does not prevent deleting these independently known font payloads,
        // and is not silently overwritten with an assumed empty queue.
        if (queued != null) ResourceInstallStore.saveFontCacheCleanup(candidates - deleted)
    }

    private fun queueFontPayloadCleanupLocked(hashes: Collection<String>) {
        pendingFontPayloadCleanup += hashes
        runCatching {
            ResourceInstallStore.saveFontCacheCleanup(ResourceInstallStore.fontCacheCleanup() + pendingFontPayloadCleanup)
        }
    }

    fun refresh() {
        synchronized(sessionLock) { catalogLoader?.refresh() }
    }

    private fun rawUrl(commit: String, path: String): String {
        require(ResourceProtocol.validCommit(commit) && ResourceProtocol.safePath(path)) { "资源地址不合法" }
        return "https://raw.githubusercontent.com".toHttpUrl().newBuilder()
            .addPathSegments(ResourceProtocol.REPOSITORY).addPathSegment(commit).addPathSegments(path).build().toString()
    }

    private suspend fun <T> json(url: String, type: Class<T>, validate: (T) -> Unit = {}): T =
        metadata(url) {
            (GSON.fromJson(it.toString(Charsets.UTF_8), type) ?: error("资源索引为空")).also(validate)
        }

    private suspend fun <T> metadata(url: String, originFirst: Boolean = false, decode: (ByteArray) -> T): T =
        relayResponse(url, metadataClient, originFirst) { response ->
            val bytes = response.body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= ResourceProtocol.MAX_INDEX_BYTES) { "资源索引过大" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            decode(bytes)
        }

    /** 目录版本先核对源站；索引/载荷沿用代理顺序。解析/摘要检查也是成功条件。 */
    private suspend fun <T> relayResponse(
        url: String,
        networkClient: OkHttpClient,
        originFirst: Boolean = false,
        read: suspend (Response) -> T,
    ): T {
        var lastError: Exception? = null
        val browsingSession = synchronized(sessionLock) { catalogLoader }
        val routes = GitHubRelay.proxyFirst(url)
        val candidates = if (originFirst) listOf(url) + routes.filter { it != url } else routes
        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            val request = Request.Builder().url(candidate).header("Git-Protocol", "version=0")
            if (originFirst) request.header("Cache-Control", "no-cache")
            val call = networkClient.newCall(request.build())
            try {
                return call.await().use { response ->
                    if (!response.isSuccessful) throw ResourceHttpException(response.code, authoritative = candidate == url)
                    read(response)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 源站明确拒绝访问时不再借镜像或旧结果回退。
                if (error is ResourceHttpException && error.accessDenied) {
                    synchronized(sessionLock) {
                        if (catalogLoader === browsingSession) browsingSession?.denyAccess(error)
                    }
                    throw error
                }
                lastError = error
            } finally {
                call.cancel()
            }
        }
        throw lastError ?: IOException("无法连接资源仓库")
    }

    private fun cachedPayload(entry: ResourceEntry, file: File): File? =
        file.takeIf { it.isFile && it.length() == entry.item.payload.size && ResourceFiles.hash(it) == entry.item.payload.sha256 }

    private suspend fun payload(entry: ResourceEntry, progress: (Float) -> Unit = {}): File {
        val cached = File(cacheDir, entry.item.payload.sha256)
        cachedPayload(entry, cached)?.let { return it }
        val temp = File(cacheDir, ".${UUID.randomUUID()}.part")
        try {
            relayResponse(rawUrl(entry.sourceCommit, entry.item.payload.path), client) { response ->
                progress(0f)
                var received = 0L
                var lastPercent = -1
                response.body.byteStream().use { input ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            require(received <= entry.item.payload.size && received <= ResourceProtocol.MAX_PAYLOAD_BYTES) { "下载大小超过目录声明" }
                            output.write(buffer, 0, count)
                            val percent = (received * 100 / entry.item.payload.size).toInt()
                            if (percent != lastPercent) {
                                progress(percent / 100f)
                                lastPercent = percent
                            }
                        }
                    }
                }
                require(received == entry.item.payload.size && ResourceFiles.hash(temp) == entry.item.payload.sha256) {
                    "资源大小或 SHA-256 不一致，请重试（暂不支持 LFS 指针下载）"
                }
            }
            currentCoroutineContext().ensureActive()
            if (!temp.renameTo(cached)) {
                if (cached.isFile && ResourceFiles.hash(cached) == entry.item.payload.sha256) return cached
                error("无法保存资源缓存")
            }
            return cached
        } finally {
            temp.delete()
        }
    }

    /** 图片预览只读；下载预览不会产生安装记录或启用资源。 */
    suspend fun backgroundPreview(entry: ResourceEntry, allowDownload: Boolean = true): File? = withContext(Dispatchers.IO) {
        if (entry.category.type != "background") return@withContext null
        state.value.installed(entry.item.id)?.let { installed ->
            if (installed.entry.item.payload.sha256 == entry.item.payload.sha256 &&
                state.value.localStates[entry.item.id] == ResourceLocalState.PRESENT) {
                ResourceInstaller.fileFor(installed)?.takeIf { it.isFile }?.let { return@withContext it }
            }
        }
        if (entry.item.payload.size > 8 * 1024 * 1024) return@withContext null
        previewMutex.withLock {
            try {
                if (allowDownload) payload(entry) else cachedPayload(entry, File(cacheDir, entry.item.payload.sha256))
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        }
    }

    /** Fetch only the catalog image, independently of resource installation or plugin execution. */
    suspend fun catalogPreview(entry: ResourceEntry, detail: Boolean, allowDownload: Boolean = true): Bitmap? = withContext(Dispatchers.IO) {
        val preview = entry.item.preview
        if (entry.category.type !in setOf("font", "pet", "read_preset") || preview == null) return@withContext null
        try {
            ResourceProtocol.validatePreviewSource(entry)
            val image = requireNotNull(if (detail) preview.detail else preview.thumbnail)
            val lock = fontPreviewLocks[(image.sha256.hashCode() and Int.MAX_VALUE) % fontPreviewLocks.size]
            lock.withLock {
                fontPreviewPermits.withPermit {
                    val directory = fontPreviewCacheDir
                    val cached = File(directory, "${image.sha256}.png")
                    val cachedBitmap = fontPreviewCacheMutex.withLock {
                        if (!cached.isFile) null else try {
                            require(cached.length() == image.size) { "预览缓存大小不一致" }
                            val bitmap = cached.inputStream().use { decodeFontPreview(readFontPreviewBytes(it, image.size), image) }
                            cached.setLastModified(System.currentTimeMillis())
                            bitmap
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            cached.delete()
                            null
                        }
                    }
                    if (cachedBitmap != null || !allowDownload) return@withPermit cachedBitmap
                    val (bytes, bitmap) = relayResponse(rawUrl(entry.catalogCommit, image.path), fontPreviewClient) { response ->
                        val bytes = response.body.byteStream().use { readFontPreviewBytes(it, image.size) }
                        bytes to decodeFontPreview(bytes, image)
                    }
                    currentCoroutineContext().ensureActive()
                    try {
                        fontPreviewCacheMutex.withLock {
                            trimFontPreviewCache(directory, bytes.size.toLong())
                            val temp = File(directory, ".${UUID.randomUUID()}.part")
                            try {
                                temp.outputStream().use { it.write(bytes) }
                                check(temp.renameTo(cached)) { "无法保存预览缓存" }
                            } finally { temp.delete() }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // 缓存空间不可用时仍可展示这次已验证的图片。
                    }
                    bitmap
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun readFontPreviewBytes(input: InputStream, expectedSize: Long): ByteArray {
        require(expectedSize in 1..ResourceProtocol.MAX_PREVIEW_BYTES) { "预览图片过大" }
        val output = ByteArrayOutputStream(minOf(expectedSize, 8192).toInt())
        val buffer = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= expectedSize) { "预览图片超过目录声明" }
            output.write(buffer, 0, count)
        }
        require(output.size().toLong() == expectedSize) { "预览图片未完整下载" }
        return output.toByteArray()
    }

    private fun decodeFontPreview(bytes: ByteArray, image: ResourcePreviewImage): Bitmap {
        ResourceProtocol.validatePreviewPng(bytes, image)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType == "image/png" && bounds.outWidth == image.width && bounds.outHeight == image.height) {
            "预览图片实际尺寸不符"
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("预览图片无法解码")
        if (bitmap.width != image.width || bitmap.height != image.height) {
            bitmap.recycle()
            error("预览图片解码尺寸不符")
        }
        return bitmap
    }

    private fun trimFontPreviewCache(directory: File, incomingBytes: Long) {
        val files = directory.listFiles().orEmpty()
            .filter { it.isFile && fontPreviewCacheName.matches(it.name) }
            .sortedBy { it.lastModified() }
        var bytes = files.sumOf { it.length() }
        var count = files.size
        for (file in files) {
            if (!file.name.endsWith(".part") && bytes + incomingBytes <= 32L * 1024 * 1024 && count < 256) continue
            val size = file.length()
            if (file.delete()) { bytes -= size; count-- }
        }
        check(bytes + incomingBytes <= 32L * 1024 * 1024 && count < 256) { "预览缓存空间不足" }
    }

    fun install(
        entry: ResourceEntry,
        mode: ResourceInstallMode = ResourceInstallMode.NORMAL,
        confirmedThemeConflict: ThemeResourceConflict? = null,
        confirmedReadPresetConflict: io.legado.app.help.config.ReadPresetResourceConflict? = null,
        readPresetReplacement: io.legado.app.help.config.ReadPresetResourceCandidate? = null,
        confirmedWidgetSkinReplacement: HomeWidgetSkinRecord? = null,
    ) {
        val current = state.value.confirmedEntry(entry) ?: return
        startInstall(current, mode, confirmedThemeConflict = confirmedThemeConflict,
            confirmedReadPresetConflict = confirmedReadPresetConflict, readPresetReplacement = readPresetReplacement,
            confirmedWidgetSkinReplacement = confirmedWidgetSkinReplacement)
    }

    fun updateAll() {
        if (!state.value.updatesReady() || state.value.loading) return
        if (!updateAllMutex.tryLock()) return
        val candidates = state.value.updatableEntries().map { it to jobs[it.item.id] }
        if (candidates.isEmpty()) {
            updateAllMutex.unlock()
            return
        }
        mutableState.update { it.copy(updatingAll = true) }
        scope.launch {
            try {
                // 同时固定版本和已有任务；即使任务在轮到它前失败/取消并移除，也不会自动重试。
                for ((entry, existing) in candidates) {
                    currentCoroutineContext().ensureActive()
                    (existing ?: startInstall(entry, ResourceInstallMode.NORMAL, updateOnly = true)).join()
                }
            } finally {
                mutableState.update { it.copy(updatingAll = false) }
                updateAllMutex.unlock()
            }
        }
    }

    private fun startInstall(
        entry: ResourceEntry,
        mode: ResourceInstallMode,
        updateOnly: Boolean = false,
        confirmedThemeConflict: ThemeResourceConflict? = null,
        confirmedReadPresetConflict: io.legado.app.help.config.ReadPresetResourceConflict? = null,
        readPresetReplacement: io.legado.app.help.config.ReadPresetResourceCandidate? = null,
        confirmedWidgetSkinReplacement: HomeWidgetSkinRecord? = null,
    ): Job {
        val id = entry.item.id
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var prepared: PreparedResource? = null
            var committed = false
            var leasedPayload: String? = null
            var completionMessage = "已加入资源库"
            try {
                // 等待前序资源期间，单项更新可能已经完成；批量操作不因此重新安装。
                if (updateOnly) {
                    val current = state.value
                    if (current.confirmedEntry(entry) == null || current.updatableEntries().none { it.item.id == id }) return@launch
                }
                val petBefore = if (entry.category.type == "pet")
                    entry.item.payload.pluginId?.let(PetPluginStore::record) else null
                check(state.value.storageError == null) { "安装记录异常，请重新进入后重试" }
                check(!state.value.accessDenied) { "资源库暂不可访问，请重试" }
                ResourceProtocol.incompatibility(entry, BuildConfig.VERSION_CODE.toLong())?.let { error(it) }
                val widgetSkinBefore = if (entry.category.type == HOME_WIDGET_SKIN_TYPE) HomeWidgetSkinStore.current(id) else null
                val previous = state.value.installed(id)
                if (previous != null && entry.category.type != "pet") {
                    val version = ResourceProtocol.versionState(entry, previous)
                    require(version != "来源版本异常" && version != "本地版本较新") { version }
                }
                task(id, ResourceTask(message = "下载中", progress = 0f))
                acquirePayloadLease(entry.item.payload.sha256)
                leasedPayload = entry.item.payload.sha256
                val file = payload(entry) { task(id, ResourceTask(progress = it, message = "下载中")) }
                installMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    check(!state.value.accessDenied) { "资源库暂不可访问，请重试" }
                    val records = recoverFontDeletionLocked(recoverInstallationLocked(ResourceInstallStore.load()))
                    val old = records.firstOrNull { it.entry.item.id == id }
                    val oldState = old?.let(ResourceInstaller::localState)
                    // 缺失资源不受旧版本号限制；复用原目标前仍校验身份，且保护检查后恢复的资源。
                    old?.takeIf { entry.category.type != "pet" }?.let {
                        val version = ResourceProtocol.versionState(entry, it)
                        if (oldState != ResourceLocalState.MISSING) {
                            require(version != "来源版本异常" && version != "本地版本较新") { version }
                        }
                    }
                    val petNow = if (entry.category.type == "pet")
                        entry.item.payload.pluginId?.let(PetPluginStore::record) else null
                    if (entry.category.type == "pet" && petNow?.sha256 != petBefore?.sha256) {
                        throw ResourceConflict("本地宠物已变化，请重新确认")
                    }
                    val stillUpdate = if (entry.category.type == "pet") PetResourcePolicy.autoUpdate(entry, petNow, old)
                        else ResourceProtocol.isUpdate(entry, old)
                    if (updateOnly && (oldState == ResourceLocalState.MISSING || !stillUpdate)) {
                        if (oldState == ResourceLocalState.MISSING) {
                            mutableState.update { it.copy(localStates = it.localStates + (id to ResourceLocalState.MISSING)) }
                        }
                        mutableState.update { it.copy(tasks = it.tasks - id) }
                        return@launch
                    }
                    val otherThemeTargets = if (entry.category.type == "theme") {
                        records.filter { it.entry.category.type == entry.category.type && it.entry.item.id != id }
                            .mapTo(hashSetOf()) { it.target }
                    } else emptySet()
                    val candidate = ResourceInstaller.prepare(entry, file, old, mode, confirmedThemeConflict, otherThemeTargets,
                        confirmedReadPresetConflict, readPresetReplacement, confirmedWidgetSkinReplacement, widgetSkinBefore)
                    prepared = candidate
                    currentCoroutineContext().ensureActive()
                    task(id, ResourceTask(message = "正在入库"))
                    // 事务段不可取消；不会留下已经替换业务对象却声称取消的状态。
                    withContext(NonCancellable) {
                        val record = ResourceInstallation(
                            entry = entry, target = candidate.target, baseline = candidate.baseline,
                            installedAt = System.currentTimeMillis(),
                        )
                        val updated = commitInstallationLocked(records, record, candidate) { committed = true }
                        val installedPets = if (entry.category.type == "pet")
                            localPetRecordsLocked(updated).associateBy { pet -> pet.id } else null
                        val invalidatedPresets = if (entry.category.type == "read_preset") {
                            updated.filter { it.entry.item.id != id && it.entry.category.type == "read_preset" &&
                                ResourceInstaller.fingerprint(it) == null }
                                .associate { it.entry.item.id to ResourceLocalState.MISSING }
                        } else emptyMap()
                        mutableState.update {
                            it.copy(installations = updated, localStates = it.localStates + invalidatedPresets + (id to ResourceLocalState.PRESENT),
                                petInstallations = installedPets ?: it.petInstallations,
                                widgetSkins = HomeWidgetSkinStore.installed.value.associateBy { skin -> skin.id })
                        }
                        if (entry.category.type == "font") {
                            queueFontPayloadCleanupLocked(listOfNotNull(
                                entry.item.payload.sha256,
                                old?.takeIf { it.entry.category.type == "font" }?.entry?.item?.payload?.sha256,
                            ))
                        }
                        completionMessage = try {
                            candidate.afterCommit() ?: completionMessage
                        } catch (_: Exception) {
                            "资源已入库，当前外观刷新失败。"
                        }
                    }
                }
                task(id, ResourceTask(message = completionMessage, running = false))
            } catch (conflict: ResourceConflict) {
                task(id, ResourceTask(message = conflict.message ?: "本地资源冲突", running = false, conflict = true,
                    themeConflict = conflict.themeConflict, readPresetConflict = conflict.readPresetConflict,
                    widgetSkinConflict = conflict.widgetSkinConflict))
            } catch (cancelled: CancellationException) {
                task(id, ResourceTask(message = if (committed) "已加入资源库" else "已取消", running = false))
                throw cancelled
            } catch (error: Exception) {
                if (committed) mutableState.update { it.copy(storageError = "资源已入库，版本记录待恢复；重新进入后检查") }
                task(id, ResourceTask(message = resourceNetworkMessage(error), running = false))
            } finally {
                if (!committed) prepared?.discard?.invoke()
                leasedPayload?.let { releasePayloadLease(it, entry.category.type) }
            }
        }
        // LAZY 任务若在 start 前被取消，协程体不会执行，仍必须释放对应 ID。
        job.invokeOnCompletion { jobs.remove(id, job) }
        val existing = jobs.putIfAbsent(id, job)
        return if (existing == null) {
            job.start()
            job
        } else {
            job.cancel()
            existing
        }
    }

    fun cancel(id: String) { jobs[id]?.cancel() }
    fun dismissTask(id: String) {
        if (jobs[id]?.isActive != true) mutableState.update { it.copy(tasks = it.tasks - id) }
    }
    private fun task(id: String, value: ResourceTask) { mutableState.update { it.copy(tasks = it.tasks + (id to value)) } }

}

internal fun resourceNetworkMessage(error: Exception): String = when (error) {
    is ResourceHttpException -> error.message ?: "资源库暂不可访问"
    is InterruptedIOException -> "连接超时，请重试"
    is IOException -> "网络连接失败，请重试"
    else -> error.message ?: "暂时无法获取资源"
}
