package io.legado.app.web.mcp

import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RuleSub
import io.legado.app.help.source.sortUrls
import io.legado.app.model.rss.Rss
import io.legado.app.model.RuleUpdate
import io.legado.app.utils.GSON
import io.legado.app.web.mcp.McpModuleSupport.bool
import io.legado.app.web.mcp.McpModuleSupport.field
import io.legado.app.web.mcp.McpModuleSupport.int
import io.legado.app.web.mcp.McpModuleSupport.obj
import io.legado.app.web.mcp.McpModuleSupport.page
import io.legado.app.web.mcp.McpModuleSupport.result
import io.legado.app.web.mcp.McpModuleSupport.string
import io.legado.app.web.mcp.McpModuleSupport.timed
import io.legado.app.web.mcp.McpModuleSupport.tool
import kotlinx.coroutines.flow.first

/** Stateless native RSS parsing; reading through MCP does not change the user's read/star state. */
object RssContentMcpTools {
    private val identity = RssSourceMcpTools.sourceArgument + mapOf("link" to field("string", "Exact article link"),
        "sort" to field("string", "Exact article category; required when selecting cached articles"))
    private val articleInput = identity + mapOf("article" to field("object", "Complete article returned by rss_articles_fetch; alternatively identify a cached article/favorite"))
    private val filters = McpModuleSupport.paging + mapOf("source_url" to field("string", "Optional source filter"),
        "keyword" to field("string", "Title keyword"))

    fun tools(): List<Map<String, Any>> = listOf(
        tool("rss_articles_fetch", "Parse one remote category/search page without saving articles. Pass next_page_url as sort_url on the next call and increment page. PAGE rules may intentionally reuse the URL.",
            RssSourceMcpTools.sourceArgument + McpModuleSupport.paging + McpModuleSupport.timeout + mapOf(
                "sort_url" to field("string", "Category URL/rule or previous next_page_url; omit for first category/search"),
                "sort" to field("string", "Category name, kept across pages"), "key" to field("string", "Search keyword; source must support search"),
                "page" to field("integer", "Remote page, starts at 1")), "source_url"),
        tool("rss_article_list", "Read locally cached articles in one source/category, without network requests.",
            RssSourceMcpTools.sourceArgument + McpModuleSupport.paging + mapOf("sort" to field("string", "Exact category name, including empty name")), "source_url", "sort"),
        tool("rss_article_get", "Read one cached article or favorite by exact identity.", identity, "source_url", "link", "sort"),
        tool("rss_article_content_get", "Return feed description or parse the source's content rule. No read markers or cache writes. WebView-only pages return requires_ui rather than invented content.",
            articleInput + McpModuleSupport.timeout + mapOf("offset" to field("integer", "Content character offset"),
                "max_chars" to field("integer", "Content window, 1..65536, default 16000"),
                "refresh" to field("boolean", "Reparse ruleContent instead of using description, default false")), "source_url"),
        tool("rss_star_list", "List subscription favorites.", filters + mapOf("group" to field("string", "Exact favorite group"))),
        tool("rss_star_get", "Read one subscription favorite.", identity, "source_url", "link"),
        tool("rss_star_save", "Favorite an article or update its favorite group; preserve existing favorite time.",
            articleInput + mapOf("group" to field("string", "Favorite group")), "source_url"),
        tool("rss_star_delete", "Remove one favorite, preserving article cache/history.", identity, "source_url", "link"),
        tool("rss_read_record_list", "Read subscription browsing history.", filters),
        tool("rss_read_record_get", "Read one subscription history entry.", identity, "source_url", "link"),
        tool("rss_read_record_save", "Explicitly mark an article read and save progress using the existing record model.",
            articleInput + mapOf("position" to field("integer", "Non-negative reading position")), "source_url"),
        tool("rss_read_record_delete", "Remove one exact history entry; no bulk history clear.", identity, "source_url", "link"),
        tool("rss_rule_subscription_list", "List rule subscriptions (book sources=0, RSS sources=1, replacement rules=2).", McpModuleSupport.paging),
        tool("rss_rule_subscription_get", "Get a rule subscription by URL.", mapOf("url" to field("string", "Exact subscription URL")), "url"),
        tool("rss_rule_subscription_save", "Create/patch a rule subscription by URL. Does not fetch/import its remote contents or execute subscription scripts.",
            mapOf("subscription" to field("object", "Editable RuleSub fields; URL is identity, id is managed by the App")), "subscription"),
        tool("rss_rule_subscription_refresh", "Run the existing rule subscription updater. Honors update interval unless force=true. silentUpdate may overwrite local rules; otherwise returns the pending import preview without applying it.",
            McpModuleSupport.timeout + McpModuleSupport.paging + mapOf("url" to field("string", "Exact subscription URL"),
                "force" to field("boolean", "Ignore update interval, default false")), "url"),
        tool("rss_rule_subscription_delete", "Delete one rule subscription; leave previously imported rules intact.",
            mapOf("url" to field("string", "Exact subscription URL")), "url")
    )

    fun call(name: String, args: JsonObject): Map<String, Any?>? {
        if (tools().none { it["name"] == name }) return null
        return try {
            McpModuleSupport.validate(name, args, tools())
            val data: Any = when (name) {
                "rss_articles_fetch" -> fetch(args)
                "rss_article_list" -> {
                    val origin = args.string("source_url", true)!!
                    val sort = args.string("sort") ?: error("sort is required")
                    val articles = timed(args) { appDb.rssArticleDao.flowByOriginSort(origin, sort).first() }
                    page(articles.map { GSON.toJsonTree(it) }, args, "articles")
                }
                "rss_article_get" -> GSON.toJsonTree(article(args))
                "rss_article_content_get" -> content(args)
                "rss_star_list" -> {
                    val source = args.string("source_url")
                    val keyword = args.string("keyword").orEmpty()
                    val group = args.string("group")
                    page(appDb.rssStarDao.all.filter {
                        (source == null || source == it.origin) && (group == null || group == it.group) && it.title.contains(keyword, true)
                    }.map { GSON.toJsonTree(it) }, args, "stars")
                }
                "rss_star_get" -> GSON.toJsonTree(requireNotNull(appDb.rssStarDao.get(args.string("source_url", true)!!,
                    args.string("link", true)!!)) { "Favorite not found" })
                "rss_star_save" -> {
                    val article = article(args)
                    val group = args.string("group")
                    var stored = article.toStar()
                    appDb.runInTransaction {
                        val old = appDb.rssStarDao.get(article.origin, article.link)
                        stored = stored.copy(starTime = old?.starTime ?: stored.starTime, group = group ?: old?.group ?: stored.group)
                        appDb.rssStarDao.insert(stored)
                    }
                    GSON.toJsonTree(stored)
                }
                "rss_star_delete" -> {
                    val origin = args.string("source_url", true)!!
                    val link = args.string("link", true)!!
                    var deleted = false
                    appDb.runInTransaction {
                        deleted = appDb.rssStarDao.get(origin, link) != null
                        appDb.rssStarDao.delete(origin, link)
                    }
                    mapOf("deleted" to deleted)
                }
                "rss_read_record_list" -> {
                    val origin = args.string("source_url")
                    val keyword = args.string("keyword").orEmpty()
                    val records = if (origin == null) appDb.rssReadRecordDao.getRecords() else appDb.rssReadRecordDao.getRecordsByOrigin(origin)
                    page(records.filter { it.title.orEmpty().contains(keyword, true) }.map { GSON.toJsonTree(it) }, args, "records")
                }
                "rss_read_record_get" -> GSON.toJsonTree(requireNotNull(appDb.rssReadRecordDao.getRecord(
                    args.string("link", true)!!, args.string("source_url", true)!!)) { "Read record not found" })
                "rss_read_record_save" -> {
                    val article = article(args)
                    val record = article.toRecord().apply { durPos = args.int("position", article.durPos.coerceAtLeast(0)) }
                    appDb.runInTransaction {
                        // The legacy record primary key is only the link; never overwrite another source's history.
                        val existing = appDb.rssReadRecordDao.getRecords().firstOrNull { it.record == record.record }
                        require(existing == null || existing.origin == record.origin) { "This link belongs to another source's history" }
                        if (existing == null) appDb.rssReadRecordDao.insertRecord(record) else appDb.rssReadRecordDao.update(record)
                    }
                    GSON.toJsonTree(record)
                }
                "rss_read_record_delete" -> mapOf("deleted" to appDb.rssReadRecordDao.deleteRecord(
                    args.string("source_url", true)!!, args.string("link", true)!!))
                "rss_rule_subscription_list" -> page(appDb.ruleSubDao.all.map { GSON.toJsonTree(it) }, args, "subscriptions")
                "rss_rule_subscription_get" -> GSON.toJsonTree(requireNotNull(appDb.ruleSubDao.findByUrl(args.string("url", true)!!)) { "Rule subscription not found" })
                "rss_rule_subscription_save" -> saveSubscription(args.obj("subscription"))
                "rss_rule_subscription_refresh" -> {
                    val subscription = requireNotNull(appDb.ruleSubDao.findByUrl(args.string("url", true)!!)) { "Rule subscription not found" }.copy()
                    val forced = args.bool("force", false)!!
                    val due = forced || subscription.update + subscription.updateInterval * 3600_000L <= System.currentTimeMillis()
                    if (forced) subscription.update = 0
                    val pending = timed(args) { RuleUpdate.cacheSource(subscription) }
                    val rules = when (subscription.type) {
                        0 -> RuleUpdate.cacheBookSourceMap[subscription.url].orEmpty().map { GSON.toJsonTree(it) }
                        1 -> RuleUpdate.cacheRssSourceMap[subscription.url].orEmpty().map { GSON.toJsonTree(it) }
                        2 -> RuleUpdate.cacheReplaceRuleMap[subscription.url].orEmpty().map { GSON.toJsonTree(it) }
                        else -> error("Unsupported subscription type")
                    }
                    page(if (pending) rules else emptyList(), args, "pending_rules") + mapOf(
                        "checked" to due, "preview_available" to pending, "silent_update" to subscription.silentUpdate,
                        "subscription" to GSON.toJsonTree(appDb.ruleSubDao.findByUrl(subscription.url)))
                }
                else -> {
                    val url = args.string("url", true)!!
                    var deleted = false
                    appDb.runInTransaction {
                        appDb.ruleSubDao.findByUrl(url)?.let { appDb.ruleSubDao.delete(it); deleted = true }
                    }
                    mapOf("deleted" to deleted)
                }
            }
            result(name, data)
        } catch (e: Exception) {
            result(name, null, e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    private fun fetch(args: JsonObject): Map<String, Any?> {
        val source = RssSourceMcpTools.source(args)
        require(!source.singleUrl) { "Single-URL subscription requires the App web page" }
        val key = args.string("key")
        require(key == null || !source.searchUrl.isNullOrBlank()) { "This subscription source does not support search" }
        val remotePage = args.int("page", 1, 1)
        return timed(args) {
            var sort = args.string("sort") ?: if (key == null) "" else "搜索"
            val explicit = args.string("sort_url")
            require(remotePage == 1 || !explicit.isNullOrBlank()) { "Subsequent pages require sort_url from next_page_url" }
            val url = explicit ?: if (key != null) source.searchUrl!! else {
                val category = source.sortUrls().firstOrNull() ?: error("No subscription category")
                if (!args.has("sort")) sort = category.first
                category.second
            }
            require(url.isNotBlank()) { "sort_url must not be blank" }
            val (articles, next) = Rss.getArticlesAwait(sort, url, source, remotePage, key)
            page(articles.map { GSON.toJsonTree(it) }, args, "articles") + mapOf(
                "source_url" to source.sourceUrl, "sort" to sort, "page" to remotePage,
                "next_page_url" to next?.takeIf { it.isNotBlank() && articles.isNotEmpty() },
                "has_more" to (articles.isNotEmpty() && !next.isNullOrBlank()))
        }
    }

    private fun article(args: JsonObject): RssArticle {
        val origin = args.string("source_url", true)!!
        val supplied = args.get("article")
        if (supplied != null) {
            require(supplied.isJsonObject && supplied.toString().length <= 1_000_000) { "Invalid or oversized article object" }
            val parsed = decodeArticle(supplied.asJsonObject, origin)
            args.string("link")?.let { require(it == parsed.link) { "Article link mismatch" } }
            args.string("sort")?.let { require(it == parsed.sort) { "Article category mismatch" } }
            return parsed
        }
        val link = args.string("link", true)!!
        val sort = args.string("sort") ?: error("sort is required for cached article lookup")
        return appDb.rssArticleDao.get(origin, link, sort)
            ?: appDb.rssStarDao.get(origin, link)?.takeIf { it.sort == sort }?.toRssArticle()
            ?: error("Article not found; pass the article object returned by rss_articles_fetch")
    }

    internal fun decodeArticle(value: JsonObject, origin: String): RssArticle {
        val strings = setOf("origin", "sort", "title", "link", "pubDate", "description", "content", "image", "group", "variable")
        val nullable = setOf("pubDate", "description", "content", "image", "variable")
        value.entrySet().forEach { (key, item) ->
            when {
                key in strings -> require((item.isJsonNull && key in nullable) ||
                    (item.isJsonPrimitive && item.asJsonPrimitive.isString)) { "$key must be a valid string" }
                key == "order" -> {
                    require(item.isJsonPrimitive && item.asJsonPrimitive.isNumber) { "order must be an integer" }
                    item.asBigDecimal.longValueExact()
                }
                key == "type" -> value.int(key, 0, 0, 2)
                key == "durPos" -> value.int(key, 0)
                key == "read" -> value.bool(key)
                else -> error("Unknown article field: $key")
            }
        }
        require(value.string("origin", true) == origin) { "Article identity does not match source_url" }
        value.string("link", true)
        return GSON.fromJson(value, RssArticle::class.java)
    }

    private fun content(args: JsonObject): Map<String, Any?> {
        val article = article(args)
        val offset = args.int("offset", 0)
        val maxChars = args.int("max_chars", 16000, 1, 65536)
        val refresh = args.bool("refresh", false)!!
        val body = article.description?.takeIf { it.isNotBlank() && !refresh } ?: run {
            val source = RssSourceMcpTools.source(args)
            if (source.ruleContent.isNullOrBlank() || source.singleUrl) return mapOf(
                "requires_ui" to true, "url" to article.link, "reason" to "This source delegates content to WebView")
            timed(args) { Rss.getContentAwait(article, source.ruleContent!!, source) }
        }
        return mapOf("requires_ui" to false) + mcpTextWindow(body, offset, maxChars)
    }

    private fun saveSubscription(patch: JsonObject): Any {
        val url = patch.string("url", true)!!
        val allowed = setOf("url", "name", "type", "customOrder", "autoUpdate", "updateInterval", "silentUpdate", "js", "showRule", "sourceUrl")
        require(patch.keySet().all { it in allowed }) { "Unknown or immutable subscription field" }
        require(patch.toString().length <= 1_000_000) { "Subscription exceeds size limit" }
        lateinit var saved: RuleSub
        appDb.runInTransaction {
            val old = appDb.ruleSubDao.findByUrl(url)
            val base = old ?: RuleSub(id = maxOf(System.currentTimeMillis(), (appDb.ruleSubDao.all.maxOfOrNull { it.id } ?: 0) + 1), url = url)
            saved = base.copy(
                name = patch.string("name") ?: base.name,
                type = patch.int("type", base.type, 0, 2),
                customOrder = patch.int("customOrder", base.customOrder, Int.MIN_VALUE),
                autoUpdate = patch.bool("autoUpdate", base.autoUpdate)!!,
                updateInterval = patch.int("updateInterval", base.updateInterval),
                silentUpdate = patch.bool("silentUpdate", base.silentUpdate)!!,
                js = nullableText(patch, "js", base.js),
                showRule = nullableText(patch, "showRule", base.showRule),
                sourceUrl = nullableText(patch, "sourceUrl", base.sourceUrl)
            )
            appDb.ruleSubDao.insert(saved)
        }
        return GSON.toJsonTree(saved)
    }

    private fun nullableText(args: JsonObject, key: String, fallback: String?): String? =
        if (!args.has(key)) fallback else if (args.get(key).isJsonNull) null else args.string(key)
}
