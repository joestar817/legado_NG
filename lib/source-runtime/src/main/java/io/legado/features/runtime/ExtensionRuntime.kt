package io.legado.features.runtime

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import io.legado.features.runtime.RuntimeLog
import io.legado.features.runtime.ExtensionException
import kotlinx.coroutines.Dispatchers
import io.legado.features.runtime.model.InstallStep
import io.legado.features.runtime.model.MangaExtension
import io.legado.features.runtime.model.MangaExtensionRepo
import io.legado.features.runtime.repo.RepoHelper
import io.legado.features.runtime.trust.TrustHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object ExtensionRuntime {

    data class RegisteredMangaSource(
        val source: Source,
        val pkgName: String,
    )

    data class RegisteredAnimeSource(
        val source: AnimeSource,
        val pkgName: String,
    )

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val initialized = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _loadedExtensionsFlow = MutableStateFlow<Map<String, MangaExtension.Loaded>>(emptyMap())
    private val _notLoadedExtensionsFlow = MutableStateFlow<Map<String, MangaExtension.NotLoaded>>(emptyMap())
    private val _availableExtensionsFlow = MutableStateFlow<List<MangaExtension.Available>>(emptyList())
    private val _reposFlow = MutableStateFlow<List<MangaExtensionRepo>>(emptyList())
    private val _sourcesFlow = MutableStateFlow<List<RegisteredMangaSource>>(emptyList())
    private val _animeSourcesFlow = MutableStateFlow<List<RegisteredAnimeSource>>(emptyList())

    val loadedExtensions: StateFlow<Map<String, MangaExtension.Loaded>> = _loadedExtensionsFlow
    val notLoadedExtensions: StateFlow<Map<String, MangaExtension.NotLoaded>> = _notLoadedExtensionsFlow
    val availableExtensions: StateFlow<List<MangaExtension.Available>> = _availableExtensionsFlow
    val repos: StateFlow<List<MangaExtensionRepo>> = _reposFlow
    private val _repoStatuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val repoStatuses: StateFlow<Map<String, String>> = _repoStatuses
    private val indexRefreshMutex = Mutex()
    val sources: StateFlow<List<RegisteredMangaSource>> = _sourcesFlow
    val animeSources: StateFlow<List<RegisteredAnimeSource>> = _animeSourcesFlow

    private val _restoreGenerationFlow = MutableStateFlow(0)
    val restoreGeneration: StateFlow<Int> = _restoreGenerationFlow

    fun init() {
        check(!closed.get()) { "扩展运行时已释放" }
        if (!started.compareAndSet(false, true)) return
        _reposFlow.value = ExtensionPrefs.getRepos()

        
        runCatching { ExtensionHostProviders.getOrNull()?.onInitialized() }
            .onFailure { RuntimeLog.put("扩展宿主初始化失败", it) }
        scope.launch {
            reloadMutex.withLock {

                _availableExtensionsFlow.value = RepoHelper.loadCachedIndex().filter { entry ->
                    _reposFlow.value.any { it.indexUrl == entry.repo.indexUrl }
                }
                refreshStatuses()
                loadExtensionsLocked()
            }

            
            // Repository network access is an explicit management action, never an app startup side effect.
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        ExtensionHostProviders.getOrNull()?.close()
        initialized.cancel()
        _sourcesFlow.value = emptyList()
        _animeSourcesFlow.value = emptyList()
        _loadedExtensionsFlow.value = emptyMap()
    }

    suspend fun awaitInitialized(): Boolean {
        if (!started.get()) return false
        initialized.await()
        return true
    }

    suspend fun getLoadedExtensions(): List<MangaExtension.Loaded> {
        if (!awaitInitialized()) return emptyList()
        return _loadedExtensionsFlow.value.values.sortedBy { it.name }
    }

    suspend fun getNotLoadedExtensions(): List<MangaExtension.NotLoaded> {
        if (!awaitInitialized()) return emptyList()
        return _notLoadedExtensionsFlow.value.values.sortedBy { it.name }
    }

    fun hasExtension(pkgName: String): Boolean {
        return _loadedExtensionsFlow.value.containsKey(pkgName) ||
            _notLoadedExtensionsFlow.value.containsKey(pkgName)
    }

    fun getSource(sourceId: Long): Source? {
        return _sourcesFlow.value.firstOrNull { it.source.id == sourceId }?.source
    }

    fun getAllSources(): List<Source> {
        return _sourcesFlow.value.map { it.source }
    }

    fun getEnabledSources(): List<Source> {
        return _sourcesFlow.value
            .filter { ExtensionPrefs.isSourceEnabled(it.source.id) }
            .map { it.source }
    }

    fun getPackageBySource(sourceId: Long): String? {
        return _sourcesFlow.value.firstOrNull { it.source.id == sourceId }?.pkgName
    }

    fun getAnimeSource(sourceId: Long): AnimeSource? {
        return _animeSourcesFlow.value.firstOrNull { it.source.id == sourceId }?.source
    }

    fun getAllAnimeSources(): List<AnimeSource> {
        return _animeSourcesFlow.value.map { it.source }
    }

    fun getPackageByAnimeSource(sourceId: Long): String? {
        return _animeSourcesFlow.value.firstOrNull { it.source.id == sourceId }?.pkgName
    }

    fun isAnimeSourceEnabled(sourceId: Long): Boolean = ExtensionPrefs.isSourceEnabled(sourceId, "aniyomi")

    fun setAnimeSourceEnabled(sourceId: Long, enabled: Boolean) {
        ExtensionPrefs.setSourceEnabled(sourceId, enabled, "aniyomi")
    }

    fun firstSourceOf(pkgName: String): Any? =
        _sourcesFlow.value.firstOrNull { it.pkgName == pkgName }?.source
            ?: _animeSourcesFlow.value.firstOrNull { it.pkgName == pkgName }?.source

    fun isPkgConfigurable(pkgName: String): Boolean {
        val source = firstSourceOf(pkgName) ?: return false
        return source is ConfigurableSource || source is ConfigurableAnimeSource
    }

    fun getRepos(): List<MangaExtensionRepo> = _reposFlow.value

    fun isSourceEnabled(sourceId: Long): Boolean = ExtensionPrefs.isSourceEnabled(sourceId)

    fun setSourceEnabled(sourceId: Long, enabled: Boolean) {
        ExtensionPrefs.setSourceEnabled(sourceId, enabled)
    }

    private val reloadMutex = Mutex()

    fun reloadExtensions() {
        scope.launch { loadExtensions() }
    }

    private suspend fun loadExtensions() {
        reloadMutex.withLock { loadExtensionsLocked() }
    }

    private suspend fun loadExtensionsLocked() {
        try {
            val host = ExtensionHostProviders.getOrNull()
                ?: run {
                    RuntimeLog.put("扩展宿主未注册 (ExtensionHostProviders), 跳过装载")
                    return
                }
            val extensions = host.loadExtensions(_loadedExtensionsFlow.value)
            _loadedExtensionsFlow.value = extensions
                .filterIsInstance<MangaExtension.Loaded>()
                .associateBy { it.pkgName }
            _notLoadedExtensionsFlow.value = extensions
                .filterIsInstance<MangaExtension.NotLoaded>()
                .associateBy { it.pkgName }
            _sourcesFlow.value = _loadedExtensionsFlow.value.values
                .flatMap { ext -> ext.sources.map { RegisteredMangaSource(it, ext.pkgName) } }
                .distinctBy { it.source.id }
            _animeSourcesFlow.value = _loadedExtensionsFlow.value.values
                .flatMap { ext -> ext.animeSources.map { RegisteredAnimeSource(it, ext.pkgName) } }
                .distinctBy { it.source.id }

            
            refreshStatuses()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            RuntimeLog.put("漫画扩展加载失败", e)
        } finally {
            initialized.complete(Unit)
        }
    }

    fun installExtension(extension: MangaExtension.Available): Flow<InstallStep> {
        return ExtensionHostProviders.getOrNull()?.install(extension) ?: emptyFlow()
    }

    fun updateExtension(extension: MangaExtension.Installed): Flow<InstallStep> {
        val update = extension.findUpdate(_availableExtensionsFlow.value) ?: return emptyFlow()
        return installExtension(update)
    }

    fun cancelInstallUpdateExtension(pkgName: String) {
        ExtensionHostProviders.getOrNull()?.cancelInstall(pkgName)
    }

    fun uninstallExtension(extension: MangaExtension.Installed) {
        ExtensionHostProviders.getOrNull()?.uninstall(extension)
    }

    fun trust(extension: MangaExtension.NotLoaded) {
        val reason = extension.reason as? MangaExtension.NotLoaded.Reason.Untrusted ?: return
        if (_notLoadedExtensionsFlow.value[extension.pkgName] == null) return
        TrustHelper.trust(extension.pkgName, extension.versionCode, reason.signatureHash)
        reloadExtensions()
    }

    fun revokeAllTrusted() {
        TrustHelper.revokeAll()
        reloadExtensions()
    }

    suspend fun addRepo(rawUrl: String): Result<MangaExtensionRepo> = withContext(Dispatchers.IO) {
        runCatching {
            val indexUrl = RepoHelper.normalizeIndexUrl(rawUrl)
            check(_reposFlow.value.none { it.indexUrl == indexUrl }) { "仓库已存在: $indexUrl" }
            val (name, fingerprint) = RepoHelper.fetchRepoMeta(indexUrl)
            val repo = MangaExtensionRepo(
                indexUrl = indexUrl,
                name = name,
                signingKeyFingerprint = fingerprint,
            )
            val entries = RepoHelper.fetchIndex(repo)
            ExtensionPrefs.addUserRepo(repo)
            _reposFlow.value = ExtensionPrefs.getRepos()
            _availableExtensionsFlow.update { it.filterNot { entry -> entry.repo.indexUrl == indexUrl } + entries }
            RepoHelper.saveCachedIndex(_availableExtensionsFlow.value)
            _repoStatuses.update { it + (indexUrl to "已验证，${entries.size} 个扩展") }
            refreshStatuses()
            repo
        }.onFailure { if (it is CancellationException) throw it }
    }

    fun removeRepo(indexUrl: String) {
        ExtensionPrefs.removeRepo(indexUrl)
        _reposFlow.value = ExtensionPrefs.getRepos()
        _repoStatuses.update { it - indexUrl }
        _availableExtensionsFlow.update { list ->
            list.filterNot { it.repo.indexUrl == indexUrl }
        }
        RepoHelper.saveCachedIndex(_availableExtensionsFlow.value)
        refreshStatuses()
    }

    suspend fun findAvailableExtensions(indexUrl: String? = null): List<MangaExtension.Available> = withContext(Dispatchers.IO) {
        // Separate management pages and update checks share the same index/cache writer.
        indexRefreshMutex.withLock { refreshAvailableExtensions(indexUrl) }
    }

    private suspend fun refreshAvailableExtensions(indexUrl: String?): List<MangaExtension.Available> = coroutineScope {
        val repoList = _reposFlow.value.filter { indexUrl == null || it.indexUrl == indexUrl }
        val previousStatuses = _repoStatuses.value
        _repoStatuses.update { it + repoList.associate { repo -> repo.indexUrl to "正在刷新…" } }
        val results = try {
            repoList.map { repo ->
                async { repo to runCatching { RepoHelper.fetchIndex(repo) } }
            }.awaitAll().also { ensureActive() }
        } catch (e: CancellationException) {
            _repoStatuses.update { current ->
                current + repoList.filter { repo -> current[repo.indexUrl] == "正在刷新…" }
                    .associate { repo -> repo.indexUrl to (previousStatuses[repo.indexUrl] ?: "刷新已取消，保留上次索引") }
            }
            throw e
        }

        val failures = mutableListOf<Throwable>()
        val listings = results.flatMap { (repo, result) ->
            result.onSuccess { entries ->
                _repoStatuses.update { it + (repo.indexUrl to "刷新成功，${entries.size} 个扩展") }
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                _repoStatuses.update { it + (repo.indexUrl to "刷新失败：${e.message ?: e.javaClass.simpleName}（保留上次索引）") }
                failures += e
                RuntimeLog.put("扩展仓库拉取失败: ${repo.name}", e)
                _availableExtensionsFlow.value.filter { it.repo.indexUrl == repo.indexUrl }
            }
        }
        if (repoList.isNotEmpty() && failures.size == repoList.size) {
            throw ExtensionException(failures.last().message ?: "扩展仓库拉取失败")
        }

        val retained = _availableExtensionsFlow.value.filter { old -> repoList.none { it.indexUrl == old.repo.indexUrl } }
        val merged = (retained + listings).filter { entry -> _reposFlow.value.any { it.indexUrl == entry.repo.indexUrl } }
            .groupBy { it.pkgName to it.repo.signingKeyFingerprint.lowercase() }
            .values.map { entries ->
                entries.maxWith(
                    compareBy<MangaExtension.Available> { it.versionCode }.thenBy { it.libVersion }
                )
            }
            .sortedBy { it.name }
        _availableExtensionsFlow.value = merged
        RepoHelper.saveCachedIndex(merged)
        refreshStatuses()
        merged
    }

    suspend fun checkForUpdates(): List<String> {
        try {
            findAvailableExtensions()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            RuntimeLog.put("扩展更新检查失败", e)
            return emptyList()
        }
        return (_loadedExtensionsFlow.value.values + _notLoadedExtensionsFlow.value.values)
            .filter { it.hasUpdate }
            .map { it.name }
            .distinct()
    }

    suspend fun onRestoreFinished() {
        if (!started.get()) return
        reloadMutex.withLock {
            _reposFlow.value = ExtensionPrefs.getRepos()
            _availableExtensionsFlow.value = RepoHelper.loadCachedIndex().filter { entry ->
                _reposFlow.value.any { it.indexUrl == entry.repo.indexUrl }
            }
            loadExtensionsLocked()
        }
        _restoreGenerationFlow.value++
    }

    private fun refreshStatuses() {
        val available = _availableExtensionsFlow.value
        if (available.isEmpty()) return
        _loadedExtensionsFlow.update { loaded ->
            loaded.mapValues { (_, ext) ->
                ext.copy(
                    hasUpdate = ext.findUpdate(available) != null,
                    isObsolete = ext.findListing(available) == null,
                    repo = pickRepo(ext.pkgName, ext.signatures, available),
                )
            }
        }
        _notLoadedExtensionsFlow.update { notLoaded ->
            notLoaded.mapValues { (_, ext) ->
                ext.copy(
                    hasUpdate = ext.findUpdate(available) != null,
                    repo = pickRepo(ext.pkgName, ext.signatures, available),
                )
            }
        }
    }

    private fun pickRepo(
        pkgName: String,
        signatures: List<String>,
        available: List<MangaExtension.Available>,
    ): MangaExtensionRepo? {
        val signatureSet = signatures.mapTo(HashSet()) { it.lowercase() }
        val signingRepos = _reposFlow.value.filter { it.signingKeyFingerprint.lowercase() in signatureSet }
        if (signingRepos.isEmpty()) return null
        val listedBy = available.filter { it.pkgName == pkgName }.mapTo(HashSet()) { it.repo.indexUrl }
        return signingRepos.firstOrNull { it.indexUrl in listedBy } ?: signingRepos.first()
    }
}

