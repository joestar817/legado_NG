package io.legado.app.help.resource

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** 仅当前浏览会话使用，不参与Gson或磁盘持久化。 */
internal data class ResourceCatalogLoadState(
    val snapshot: ResourceSnapshot? = null,
    val checking: Boolean = false,
    val loaded: Set<String> = emptySet(),
    val pending: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val accessDenied: Boolean = false,
    val cached: CachedResourceCatalog? = null,
)

internal interface ResourceCatalogSource {
    suspend fun latestCommit(): String
    suspend fun catalog(commit: String): ResourceCatalog
    suspend fun index(commit: String, catalog: ResourceCatalog, category: ResourceCategory): ResourceIndex
}

internal class ResourceHttpException(val status: Int, val authoritative: Boolean) :
    IOException("获取资源失败（HTTP $status）") {
    val accessDenied: Boolean get() = authoritative && (status == 401 || status == 403)
}

/** 两个索引工作槽；当前分类优先，按目录顺序发布，任何迟到结果均受轮次校验。 */
internal class ResourceCatalogLoader(
    parentScope: CoroutineScope,
    private val source: ResourceCatalogSource,
    private val message: (Exception) -> String = { it.message ?: "暂时无法获取资源" },
    private val parallelism: Int = 2,
    private val cache: ResourceCatalogCache? = null,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val lock = Any()
    private val mutableState = MutableStateFlow(ResourceCatalogLoadState())
    val state = mutableState.asStateFlow()
    private var checkingJob: Job? = null
    private val indexJobs = mutableMapOf<String, Job>()
    private var priorityTypes: Set<String>? = null
    private var generation = 0L
    private var closed = false
    private var cacheRestored = false

    init { require(parallelism > 0) }

    fun prioritize(types: Set<String>?) = synchronized(lock) {
        if (closed) return@synchronized
        priorityTypes = types?.toSet()
        scheduleLocked()
    }

    fun refresh(force: Boolean = true): Unit = synchronized(lock) {
        if (closed || checkingJob?.isActive == true) return@synchronized
        val round = generation
        val restoreCache = !cacheRestored
        cacheRestored = true
        mutableState.value = mutableState.value.copy(checking = true, error = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (restoreCache) {
                    val cached = cache?.load()
                    synchronized(lock) {
                        if (closed || round != generation) return@launch
                        mutableState.value = mutableState.value.copy(cached = cached)
                    }
                }
                val commit = source.latestCommit()
                require(ResourceProtocol.validCommit(commit)) { "目录提交不合法" }
                val previous = synchronized(lock) {
                    if (closed || round != generation) return@launch
                    mutableState.value
                }
                val cachedCatalog = previous.cached?.snapshot?.takeIf { it.catalogCommit == commit }?.catalog
                val catalog = if (previous.snapshot?.catalogCommit == commit) null
                    else cachedCatalog ?: source.catalog(commit).also(ResourceProtocol::validateCatalog)
                synchronized(lock) {
                    if (closed || round != generation) return@launch
                    if (catalog != null) adoptCatalogLocked(commit, catalog)
                    else {
                        val current = mutableState.value
                        mutableState.value = current.copy(
                            // Manual refresh also retries failed previews; successful images stay in memory.
                            snapshot = if (force) current.snapshot?.let {
                                it.copy(checkedAt = maxOf(System.currentTimeMillis(), it.checkedAt + 1))
                            } else current.snapshot,
                            checking = false, error = null,
                            pending = if (force) current.pending + current.errors.keys else current.pending,
                            errors = if (force) emptyMap() else current.errors,
                        )
                    }
                    scheduleLocked()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                synchronized(lock) {
                    if (!closed && round == generation) {
                        if (error is ResourceHttpException && error.accessDenied) denyLocked(error)
                        else {
                            val cached = lastVerified(mutableState.value)
                            cancelIndexesLocked()
                            mutableState.value = ResourceCatalogLoadState(cached = cached, error = message(error))
                        }
                    }
                }
            } finally {
                synchronized(lock) {
                    if (checkingJob === coroutineContext[Job]) {
                        checkingJob = null
                        if (!closed) mutableState.value = mutableState.value.copy(checking = false)
                    }
                }
            }
        }
        checkingJob = job
        job.start()
    }

    private fun adoptCatalogLocked(commit: String, catalog: ResourceCatalog) {
        val previous = lastVerified(mutableState.value)
        var snapshot = ResourceSnapshot(catalogCommit = commit, catalog = catalog,
            checkedAt = System.currentTimeMillis())
        val loaded = mutableSetOf<String>()
        if (previous != null) {
            val oldCategories = previous.snapshot.catalog.categories.associateBy { it.id }
            val oldResources = previous.snapshot.entries.groupBy { it.category.id }
            for (category in catalog.categories) {
                val old = oldCategories[category.id] ?: continue
                if (category.id !in previous.loaded || category.type != old.type) continue
                val unchanged = commit == previous.snapshot.catalogCommit ||
                    (category.indexSha256 != null && category.indexSha256 == old.indexSha256)
                if (!unchanged) continue
                val next = snapshot.withResources(category, oldResources[category.id].orEmpty().map { it.item })
                if (runCatching { ResourceProtocol.validateLoadedSnapshot(next, loaded + category.id) }.isSuccess) {
                    snapshot = next
                    loaded += category.id
                }
            }
        }
        val pending = catalog.categories.mapTo(mutableSetOf()) { it.id } - loaded
        cancelIndexesLocked()
        mutableState.value = ResourceCatalogLoadState(
            snapshot = snapshot, loaded = loaded.toSet(), pending = pending,
            cached = previous.takeUnless { pending.isEmpty() },
        )
        cache?.remember(snapshot, complete = pending.isEmpty())
    }

    private fun lastVerified(state: ResourceCatalogLoadState): CachedResourceCatalog? =
        state.snapshot?.takeIf { state.loaded.isNotEmpty() || it.catalog.categories.isEmpty() }
            ?.let { CachedResourceCatalog(it, state.loaded) } ?: state.cached

    fun retry(categories: Set<String>) = synchronized(lock) {
        if (closed) return@synchronized
        val current = mutableState.value
        if (current.snapshot == null || current.accessDenied) {
            refresh()
        } else {
            val failed = current.errors.keys.intersect(categories)
            mutableState.value = current.copy(pending = current.pending + failed, errors = current.errors - failed)
            scheduleLocked()
        }
    }

    private fun scheduleLocked() {
        if (closed) return
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        val waiting = snapshot.catalog.categories.filter { it.id in current.pending }
        val foreground = waiting.filter { priorityTypes == null || it.type in priorityTypes.orEmpty() }
        val candidates = if (foreground.isNotEmpty()) foreground else waiting
        for (category in candidates) {
            if (indexJobs.size >= parallelism) break
            if (category.id in indexJobs) continue
            val round = generation
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val index = source.index(snapshot.catalogCommit, snapshot.catalog, category)
                    ResourceProtocol.validateIndex(snapshot.catalog, category, index)
                    synchronized(lock) {
                        if (closed || round != generation) return@launch
                        val latest = mutableState.value
                        val next = requireNotNull(latest.snapshot).withResources(category, index.resources)
                        val loaded = latest.loaded + category.id
                        ResourceProtocol.validateLoadedSnapshot(next, loaded)
                        val complete = loaded.size == next.catalog.categories.size
                        mutableState.value = latest.copy(snapshot = next, loaded = loaded,
                            pending = latest.pending - category.id, errors = latest.errors - category.id,
                            cached = latest.cached.takeUnless { complete })
                        if (complete) cache?.remember(next, complete = true)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    synchronized(lock) {
                        if (!closed && round == generation) {
                            if (error is ResourceHttpException && error.accessDenied) denyLocked(error)
                            else {
                                val latest = mutableState.value
                                mutableState.value = latest.copy(pending = latest.pending - category.id,
                                    errors = latest.errors + (category.id to message(error)))
                            }
                        }
                    }
                } finally {
                    synchronized(lock) {
                        if (!closed && round == generation && indexJobs[category.id] === coroutineContext[Job]) {
                            indexJobs.remove(category.id)
                            scheduleLocked()
                        }
                    }
                }
            }
            indexJobs[category.id] = job
            job.start()
        }
    }

    private fun cancelIndexesLocked() {
        generation++
        indexJobs.values.forEach { it.cancel() }
        indexJobs.clear()
    }

    private fun denyLocked(error: Exception) {
        cancelIndexesLocked()
        mutableState.value = ResourceCatalogLoadState(error = message(error), accessDenied = true)
        runCatching { cache?.invalidate() }
    }

    fun denyAccess(error: ResourceHttpException) = synchronized(lock) {
        if (!closed && error.accessDenied) denyLocked(error)
    }

    fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        generation++
        scope.cancel()
        indexJobs.clear()
        mutableState.value = ResourceCatalogLoadState()
    }
}
