package io.legado.features.aniyomi

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.*
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.legado.features.api.source.*
import io.legado.features.runtime.ExtensionRuntime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AniyomiProvider(
    private val resolve: suspend (Long) -> AnimeSource? = { ExtensionRuntime.awaitInitialized(); ExtensionRuntime.getAnimeSource(it) },
    private val list: suspend () -> List<AnimeSource> = { ExtensionRuntime.awaitInitialized(); ExtensionRuntime.getAllAnimeSources() },
) : SourceProvider {
    override val provider = "aniyomi"
    private data class UpdateLock(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val locks = mutableMapOf<Pair<Long, String>, UpdateLock>()
    private suspend fun source(key: SourceKey): AnimeSource {
        require(key.provider == provider && key.contentKind == ContentKind.VIDEO)
        currentCoroutineContext().ensureActive()
        return resolve(key.nativeId.toLong()) ?: error("视频扩展未加载：${key.nativeId}")
    }
    override suspend fun sources() = list().map { s -> SourceDescriptor(SourceKey(provider, ContentKind.VIDEO, s.id.toString()), s.name, s.lang, ExtensionRuntime.isAnimeSourceEnabled(s.id), s.supportsLatest) }
    override suspend fun filters(source: SourceKey): FilterSession = AnimeFilters(source, source(source).getFilterList())
    override suspend fun preferences(source: SourceKey): SourcePreferenceSession? {
        val configurable = source(source) as? eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            io.legado.features.runtime.ExtensionPreferences(io.legado.features.extensioncompat.ExtensionServices.require().application, source, configurable::setupPreferenceScreen)
        }
    }
    private fun filters(key: SourceKey, session: FilterSession): AnimeFilters {
        require(session.source == key && session is AnimeFilters) { "筛选会话与源不匹配" }
        return session
    }
    override suspend fun search(source: SourceKey, query: String, page: Int, filters: FilterSession): CatalogPage {
        require(page > 0)
        val s = source(source)
        return filters(source, filters).use { s.getSearchAnime(page, query, it).catalog() }
    }
    override suspend fun discover(source: SourceKey, section: CatalogSection, page: Int, filters: FilterSession): CatalogPage {
        require(page > 0)
        val s = source(source)
        return filters(source, filters).use {
            when (section) {
                CatalogSection.POPULAR -> s.getPopularAnime(page)
                CatalogSection.LATEST -> if (s.supportsLatest) s.getLatestUpdates(page) else AnimesPage(emptyList(), false)
                CatalogSection.FILTER -> s.getSearchAnime(page, "", it)
            }.catalog()
        }
    }
    private fun AnimesPage.catalog() = CatalogPage(animes.map { it.catalog() }, hasNextPage)
    private fun SAnime.catalog() = CatalogItem(url, title, author, thumbnail_url, description, genre, AnimeCatalogMetadata.encode(this))
    private fun CatalogItem.anime() = SAnime.create().also {
        it.url = id; it.title = title; it.author = author; it.description = description; it.genre = genre; it.thumbnail_url = cover
        AnimeCatalogMetadata.restore(it, metadata)
    }
    private suspend fun update(key: SourceKey, item: CatalogItem, details: Boolean, episodes: Boolean): SAnimeEpisodeUpdate {
        return withUpdateLock(key, item) { s -> s.getAnimeEpisodeUpdate(item.anime(), emptyList(), details, episodes) }
    }
    private suspend fun <T> withUpdateLock(key: SourceKey, item: CatalogItem, action: suspend (AnimeSource) -> T): T {
        val s = source(key)
        val lockKey = s.id to item.id
        val lock = synchronized(locks) { locks.getOrPut(lockKey) { UpdateLock() }.also { it.users++ } }
        try { return lock.mutex.withLock { action(s) } }
        finally { synchronized(locks) { if (--lock.users == 0) locks.remove(lockKey, lock) } }
    }
    override suspend fun details(source: SourceKey, item: CatalogItem) = update(source, item, true, false).anime.catalog()
    override suspend fun chapters(source: SourceKey, item: CatalogItem): List<ChapterDescriptor> {
        val seasons = if (item.anime().fetch_type != FetchType.Seasons) emptyList() else
            try { withUpdateLock(source, item) { it.getAnimeSeasonUpdate(item.anime(), emptyList(), false, true).seasons } }
            catch (_: UnsupportedOperationException) { emptyList() }
            catch (e: IllegalStateException) { if (e.message == "Not used") emptyList() else throw e }
        if (seasons.isEmpty()) return update(source, item, false, true).episodes.mapIndexed { i, c -> ChapterDescriptor(c.url, c.name, i, c.episode_number, c.date_upload, c.memo.toString()) }
        require(seasons.size <= 100) { "扩展返回季度过多" }
        val result = mutableListOf<ChapterDescriptor>()
        seasons.forEach { season ->
            currentCoroutineContext().ensureActive()
            val parent = season.catalog()
            val prefix = "feature-season:" + java.net.URLEncoder.encode(parent.id, "UTF-8") + ":"
            result += ChapterDescriptor(prefix, parent.title, result.size, volume = true, parentItem = parent)
            update(source, parent, false, true).episodes.forEach { c ->
                require(result.size < 20000) { "扩展剧集数量过多" }
                result += ChapterDescriptor(prefix + java.net.URLEncoder.encode(c.url, "UTF-8"), c.name, result.size, c.episode_number, c.date_upload, c.memo.toString(), parentItem = parent, originalId = c.url)
            }
        }
        return result
    }
    override suspend fun content(source: SourceKey, item: CatalogItem, chapter: ChapterDescriptor): ContentResult {
        val s = source(source)
        require(!chapter.volume) { "季度标题不是可播放剧集" }
        val episode = update(source, chapter.parentItem ?: item, false, true).episodes.firstOrNull { it.url == (chapter.originalId ?: chapter.id) } ?: error("剧集已失效，请刷新目录")
        // Legacy fallback is selected only when the source declares the API unsupported.
        val hosters = try { s.getHosterList(episode) } catch (_: UnsupportedOperationException) { null } catch (e: IllegalStateException) { if (e.message == "Not used") null else throw e }
        val resources = mutableListOf<VideoResource>()
        var firstError: Exception? = null
        suspend fun collect(lineId: String, lineTitle: String, load: suspend () -> List<Video>) {
            try {
                for (video in load()) {
                    currentCoroutineContext().ensureActive()
                    try {
                        resources += AnimeVideoResources.resolve(s, video, lineId, lineTitle)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { if (firstError == null) firstError = e }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (firstError == null) firstError = e }
        }
        @Suppress("DEPRECATION")
        if (hosters == null) collect("default", "默认线路") {
            val videos = s.getVideoList(episode)
            if (s is AnimeHttpSource) s.run { videos.sortVideos() } else videos
        }
        else {
            val ordered = if (s is AnimeHttpSource) s.run { hosters.sortHosters() } else hosters
            ordered.forEachIndexed { index, hoster ->
                collect(hoster.hosterUrl.ifBlank { "hoster:$index:${hoster.hosterName}" },
                    hoster.hosterName.ifBlank { "线路 ${index + 1}" }) {
                    val videos = hoster.videoList ?: s.getVideoList(hoster)
                    if (s is AnimeHttpSource) s.run { videos.sortVideos() } else videos
                }
            }
        }
        currentCoroutineContext().ensureActive()
        if (resources.isEmpty()) throw firstError ?: IllegalStateException("扩展未返回播放资源，请尝试其他线路或更新扩展")
        return ContentResult.Videos(resources)
    }
}
