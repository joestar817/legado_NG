package io.legado.app.web.mcp

import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.help.ConcurrentRateLimiter.Companion.concurrentRecordMap
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.removeSortCache
import io.legado.app.help.source.sortUrls
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.GSON
import io.legado.app.web.mcp.McpModuleSupport.bool
import io.legado.app.web.mcp.McpModuleSupport.field
import io.legado.app.web.mcp.McpModuleSupport.identifiers
import io.legado.app.web.mcp.McpModuleSupport.obj
import io.legado.app.web.mcp.McpModuleSupport.page
import io.legado.app.web.mcp.McpModuleSupport.result
import io.legado.app.web.mcp.McpModuleSupport.string
import io.legado.app.web.mcp.McpModuleSupport.timed
import io.legado.app.web.mcp.McpModuleSupport.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object RssSourceMcpTools {
    internal val sourceArgument = mapOf("source_url" to field("string", "Exact RssSource.sourceUrl"))
    private val sourceField = mapOf("source" to field("object", "RssSource fields; partial edits preserve omitted fields"))

    fun tools(): List<Map<String, Any>> = listOf(
        tool("rss_source_list", "List subscription sources; full rules are opt-in.", McpModuleSupport.paging + mapOf(
            "keyword" to field("string", "Name, URL or comment keyword"), "group" to field("string", "Exact group"),
            "enabled" to field("boolean", "Optional enabled filter"), "include_detail" to field("boolean", "Full definitions, default false"))),
        tool("rss_source_stats_get", "Count subscription sources and groups.", emptyMap()),
        tool("rss_source_get", "Read a complete subscription source for editing/export.", sourceArgument, "source_url"),
        tool("rss_source_save", "Create or patch one subscription source. sourceUrl is immutable identity; omitted fields are preserved. Refreshes rule caches.", sourceField, "source"),
        tool("rss_source_import", "Import 1..200 source objects atomically after validation. Existing identities default to skip; overwrite=true replaces their definitions while preserving customOrder. Returns skipped identities.",
            mapOf("sources" to mapOf("type" to "array", "items" to field("object", "Full RssSource"), "minItems" to 1, "maxItems" to 200),
                "overwrite" to field("boolean", "Replace existing definitions, default false")), "sources"),
        tool("rss_source_export", "Export exact selected source definitions as JSON objects; no file writes.", McpModuleSupport.urls, "urls"),
        tool("rss_source_delete", "Delete selected sources through SourceHelp, including their cached articles and variables; retain favorites/history as the App does.", McpModuleSupport.urls, "urls"),
        tool("rss_source_set_enabled", "Enable/disable selected subscription sources.", McpModuleSupport.urls +
            mapOf("enabled" to field("boolean", "Required enabled state")), "urls", "enabled"),
        tool("rss_source_categories", "Resolve native subscription categories. singleUrl sources open a web page, not an article list.",
            sourceArgument + McpModuleSupport.timeout + mapOf("refresh" to field("boolean", "Refresh cached category script, default false")), "source_url"),
        tool("rss_source_debug", "Run the native subscription debugger. key accepts categoryName::URL, article URL or search keyword; omit for first category. source_override is a temporary patch, never saved. Fails if another native debugger is active.",
            sourceArgument + McpModuleSupport.timeout + mapOf("key" to field("string", "Native debugger key"),
                "source_override" to field("object", "Temporary rule fields; identity cannot change")), "source_url")
    )

    internal fun source(args: JsonObject): RssSource =
        requireNotNull(appDb.rssSourceDao.getByKey(args.string("source_url", true)!!)) { "Subscription source not found" }

    // Existing entity wire names are already protected by the project's entities R8 keep rule.
    // No host methods/setters or new reflection DTOs are added by these tools.
    internal fun mergeSource(patch: JsonObject, current: RssSource? = null): RssSource {
        require(patch.toString().length <= 2_000_000) { "Source definition exceeds size limit" }
        val fields = sourceFields
        patch.entrySet().forEach { (key, value) ->
            require(key in fields) { "Unknown subscription field: $key" }
            val kind = fields.getValue(key)
            if (value.isJsonNull) {
                require(key !in nonNullFields) { "$key cannot be null" }
            } else when (kind) {
                "boolean" -> require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$key must be boolean" }
                "integer" -> {
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$key must be integer" }
                    val number = value.asBigDecimal.longValueExact()
                    require(key == "lastUpdateTime" || number in Int.MIN_VALUE..Int.MAX_VALUE) { "$key exceeds integer range" }
                }
                else -> require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$key must be string" }
            }
        }
        val merged = GSON.toJsonTree(current ?: RssSource()).asJsonObject
        patch.entrySet().forEach { (key, value) -> merged.add(key, value.deepCopy()) }
        val source = GSON.fromJson(merged, RssSource::class.java)
        require(source.sourceUrl.isNotBlank() && source.sourceName.isNotBlank()) { "sourceUrl and sourceName must not be blank" }
        require(current == null || source.sourceUrl == current.sourceUrl) { "sourceUrl cannot be changed by a patch" }
        require(source.type in 0..2 && source.articleStyle in 0..4) { "Unsupported source type/articleStyle" }
        return source
    }

    private fun invalidate(old: RssSource?, saved: RssSource) = runBlocking(Dispatchers.IO) {
        old?.removeSortCache()
        saved.removeSortCache()
        old?.jsLib?.let { SharedJsScope.remove(it) }
        saved.jsLib?.let { SharedJsScope.remove(it) }
        concurrentRecordMap.remove(saved.sourceUrl)
    }

    fun call(name: String, args: JsonObject): Map<String, Any?>? {
        if (name == "rss_source_debug" || tools().none { it["name"] == name }) return null
        return try {
            McpModuleSupport.validate(name, args, tools())
            val data: Any = when (name) {
                "rss_source_list" -> {
                    val keyword = args.string("keyword").orEmpty()
                    val enabled = args.bool("enabled")
                    val group = args.string("group")
                    val detail = args.bool("include_detail", false)!!
                    val items = appDb.rssSourceDao.all.filter {
                        (enabled == null || it.enabled == enabled) && (group == null || group in it.sourceGroup.orEmpty().split(',')) &&
                            listOf(it.sourceName, it.sourceUrl, it.sourceComment.orEmpty()).any { text -> text.contains(keyword, true) }
                    }.map { if (detail) GSON.toJsonTree(it) else summary(it) }
                    page(items, args, "sources")
                }
                "rss_source_stats_get" -> {
                    val sources = appDb.rssSourceDao.all
                    mapOf("total" to sources.size, "enabled" to sources.count { it.enabled },
                        "disabled" to sources.count { !it.enabled }, "groups" to appDb.rssSourceDao.allGroups(),
                        "searchable" to sources.count { !it.searchUrl.isNullOrBlank() },
                        "single_url" to sources.count { it.singleUrl })
                }
                "rss_source_get" -> GSON.toJsonTree(source(args))
                "rss_source_save" -> {
                    val patch = args.obj("source")
                    val url = patch.string("sourceUrl", true)!!
                    var old: RssSource? = null
                    lateinit var saved: RssSource
                    appDb.runInTransaction {
                        old = appDb.rssSourceDao.getByKey(url)
                        saved = mergeSource(patch, old).apply { lastUpdateTime = System.currentTimeMillis() }
                        appDb.rssSourceDao.insert(saved)
                    }
                    invalidate(old, saved)
                    GSON.toJsonTree(saved)
                }
                "rss_source_import" -> importSources(args)
                "rss_source_categories" -> {
                    val source = source(args)
                    val kinds = timed(args) {
                        if (args.bool("refresh", false)!!) source.removeSortCache()
                        source.sortUrls()
                    }
                    require(kinds.isNotEmpty()) { "Category script returned no categories or failed; check native debug logs" }
                    mapOf("source_url" to source.sourceUrl, "single_url" to source.singleUrl,
                        "categories" to kinds.mapIndexed { index, pair -> mapOf("index" to index, "name" to pair.first, "url" to pair.second) })
                }
                else -> {
                    val urls = args.identifiers()
                    val enabled = if (name == "rss_source_set_enabled") requireNotNull(args.bool("enabled")) { "enabled is required" } else false
                    var selected = emptyList<RssSource>()
                    appDb.runInTransaction {
                        selected = urls.map { requireNotNull(appDb.rssSourceDao.getByKey(it)) { "Subscription source not found: $it" } }
                        when (name) {
                            "rss_source_set_enabled" -> urls.forEach { appDb.rssSourceDao.enable(it, enabled) }
                            "rss_source_delete" -> SourceHelp.deleteRssSources(selected)
                        }
                    }
                    if (name == "rss_source_export") mapOf("sources" to selected.map { GSON.toJsonTree(it) })
                    else mapOf("affected" to selected.size, "urls" to urls)
                }
            }
            result(name, data)
        } catch (e: Exception) {
            result(name, null, e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    private fun importSources(args: JsonObject): Map<String, Any?> {
        val array = args.get("sources")
        require(array?.isJsonArray == true && array.asJsonArray.size() in 1..200) { "sources must contain 1..200 objects" }
        require(array.toString().length <= 4_000_000) { "Import exceeds size limit" }
        val sources = array.asJsonArray.map { require(it.isJsonObject) { "Each source must be an object" }; mergeSource(it.asJsonObject) }
        require(sources.map { it.sourceUrl }.distinct().size == sources.size) { "Duplicate sourceUrl in import" }
        val overwrite = args.bool("overwrite", false)!!
        val imported = mutableListOf<RssSource>()
        val skipped = mutableListOf<String>()
        val prior = mutableMapOf<String, RssSource?>()
        appDb.runInTransaction {
            sources.forEach { source ->
                val old = appDb.rssSourceDao.getByKey(source.sourceUrl)
                if (old != null && !overwrite) skipped.add(source.sourceUrl) else {
                    prior[source.sourceUrl] = old
                    old?.let { source.customOrder = it.customOrder }
                    SourceHelp.insertRssSource(source)
                    val stored = appDb.rssSourceDao.getByKey(source.sourceUrl)
                    if (stored != null && GSON.toJsonTree(stored) == GSON.toJsonTree(source)) imported.add(source)
                    else skipped.add(source.sourceUrl)
                }
            }
        }
        imported.forEach { invalidate(prior[it.sourceUrl], it) }
        return mapOf("imported" to imported.size, "imported_urls" to imported.map { it.sourceUrl }, "skipped_urls" to skipped)
    }

    private fun summary(source: RssSource): Map<String, Any?> = mapOf(
        "sourceUrl" to source.sourceUrl, "sourceName" to source.sourceName, "sourceGroup" to source.sourceGroup,
        "enabled" to source.enabled, "type" to source.type, "singleUrl" to source.singleUrl,
        "searchable" to !source.searchUrl.isNullOrBlank(), "customOrder" to source.customOrder
    )

    internal val sourceFields: Map<String, String> = buildMap {
        "sourceUrl sourceName sourceIcon sourceGroup sourceComment variableComment jsLib concurrentRate header loginUrl loginUi loginCheckJs coverDecodeJs sortUrl ruleArticles ruleNextPage ruleTitle rulePubDate ruleDescription ruleImage ruleLink ruleContent contentWhitelist contentBlacklist shouldOverrideUrlLoading style injectJs preloadJs startHtml startStyle startJs searchUrl".split(' ').forEach { put(it, "string") }
        "enabled enabledCookieJar singleUrl enableJs loadWithBaseUrl showWebLog preload cacheFirst".split(' ').forEach { put(it, "boolean") }
        "articleStyle lastUpdateTime customOrder type".split(' ').forEach { put(it, "integer") }
    }
    private val nonNullFields = setOf("sourceUrl", "sourceName", "sourceIcon", "enabled", "singleUrl",
        "enableJs", "loadWithBaseUrl", "showWebLog", "preload", "cacheFirst", "articleStyle", "lastUpdateTime", "customOrder", "type")
}
