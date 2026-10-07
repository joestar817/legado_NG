package io.legado.app.web.mcp

import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.isOpenableExploreCategory
import io.legado.app.model.webBook.WebBook
import io.legado.app.web.mcp.McpModuleSupport.bool
import io.legado.app.web.mcp.McpModuleSupport.field
import io.legado.app.web.mcp.McpModuleSupport.identifiers
import io.legado.app.web.mcp.McpModuleSupport.int
import io.legado.app.web.mcp.McpModuleSupport.page
import io.legado.app.web.mcp.McpModuleSupport.result
import io.legado.app.web.mcp.McpModuleSupport.string
import io.legado.app.web.mcp.McpModuleSupport.timed
import io.legado.app.web.mcp.McpModuleSupport.tool

/** Discovery uses the same native parser (including single-file JS sources) as the page. */
object ExploreMcpTools {
    private val sourceArgument = mapOf("source_url" to field("string", "Exact bookSourceUrl"))

    fun tools(): List<Map<String, Any>> = listOf(
        tool("explore_source_list", "List discovery sources, independently of search enablement.",
            McpModuleSupport.paging + mapOf("keyword" to field("string", "Name/URL keyword"),
                "group" to field("string", "Exact comma-delimited group"),
                "enabled" to field("boolean", "Discovery enabled filter; omitted means all"))),
        tool("explore_source_set_enabled", "Enable/disable discovery only; leave search state unchanged.",
            McpModuleSupport.urls + mapOf("enabled" to field("boolean", "Required discovery state")), "urls", "enabled"),
        tool("explore_books", "Fetch one remote discovery page without adding books to the shelf. Use category_index from book_source_explore_kinds_get or an explicit explore_url. UI controls are not categories.",
            sourceArgument + McpModuleSupport.timeout + McpModuleSupport.paging + mapOf(
                "category_index" to field("integer", "Index of an openable category"),
                "explore_url" to field("string", "Explicit category URL/rule; mutually exclusive with category_index"),
                "page" to field("integer", "Remote page, starts at 1")), "source_url"),
        tool("explore_book_info", "Parse book details with the selected source without adding to the shelf.",
            sourceArgument + McpModuleSupport.timeout + mapOf("book_url" to field("string", "Book detail URL")),
            "source_url", "book_url")
    )

    fun call(name: String, args: JsonObject): Map<String, Any?>? {
        if (tools().none { it["name"] == name }) return null
        return try {
            McpModuleSupport.validate(name, args, tools())
            val data: Any = when (name) {
                "explore_source_list" -> {
                    val enabled = args.bool("enabled")
                    val keyword = args.string("keyword").orEmpty()
                    val group = args.string("group")
                    val sources = appDb.bookSourceDao.allPart.filter {
                        it.hasExploreUrl && (enabled == null || it.enabledExplore == enabled) &&
                            (group == null || group in it.bookSourceGroup.orEmpty().split(',')) &&
                            (it.bookSourceName.contains(keyword, true) || it.bookSourceUrl.contains(keyword, true))
                    }.map { mapOf("source_url" to it.bookSourceUrl, "name" to it.bookSourceName,
                        "group" to it.bookSourceGroup, "enabled" to it.enabledExplore,
                        "search_enabled" to it.enabled, "type" to it.bookSourceType) }
                    page(sources, args, "sources")
                }
                "explore_source_set_enabled" -> {
                    val urls = args.identifiers()
                    val enabled = requireNotNull(args.bool("enabled")) { "enabled is required" }
                    appDb.runInTransaction {
                        require(urls.all { appDb.bookSourceDao.getBookSource(it) != null }) { "Unknown book source; nothing changed" }
                        urls.forEach { appDb.bookSourceDao.enableExplore(it, enabled) }
                    }
                    mapOf("updated" to urls.size, "enabled" to enabled)
                }
                else -> {
                    val source = requireNotNull(appDb.bookSourceDao.getBookSource(args.string("source_url", true)!!)) { "Book source not found" }
                    timed(args) {
                        if (name == "explore_book_info") {
                            val book = WebBook.getBookInfoAwait(source,
                                Book(bookUrl = args.string("book_url", true)!!, origin = source.bookSourceUrl))
                            mapOf("book_url" to book.bookUrl, "source_url" to book.origin,
                                "name" to book.name, "author" to book.author, "intro" to book.intro,
                                "cover_url" to book.coverUrl, "toc_url" to book.tocUrl,
                                "kind" to book.kind, "latest_chapter_title" to book.latestChapterTitle,
                                "variable" to book.variable)
                        } else {
                            val explicit = args.string("explore_url")
                            require(explicit == null || !args.has("category_index")) { "Choose category_index or explore_url" }
                            val url = explicit?.also { require(it.isNotBlank()) { "explore_url must not be blank" } } ?: run {
                                val kinds = source.exploreKinds()
                                require(kinds.none { it.title.startsWith("ERROR:") }) { "Discovery category parsing failed" }
                                val index = args.int("category_index", -1)
                                val kind = if (index >= 0) kinds.getOrNull(index) else kinds.firstOrNull { it.isOpenableExploreCategory() }
                                require(kind?.isOpenableExploreCategory() == true) { "No openable category; interactive controls require the App UI" }
                                kind.url!!
                            }
                            val remotePage = args.int("page", 1, 1)
                            val books = WebBook.exploreBookAwait(source, url, remotePage).map {
                                mapOf("book_url" to it.bookUrl, "source_url" to it.origin, "name" to it.name,
                                    "author" to it.author, "cover_url" to it.coverUrl, "intro" to it.intro,
                                    "kind" to it.kind, "latest_chapter_title" to it.latestChapterTitle,
                                    "toc_url" to it.tocUrl, "variable" to it.variable)
                            }
                            page(books, args, "books") + mapOf("page" to remotePage, "explore_url" to url,
                                "next_page_candidate" to (remotePage.toLong() + 1).takeIf { books.isNotEmpty() },
                                "end_confirmed" to books.isEmpty())
                        }
                    }
                }
            }
            result(name, data)
        } catch (e: Exception) {
            result(name, null, e.localizedMessage ?: e.javaClass.simpleName)
        }
    }
}
