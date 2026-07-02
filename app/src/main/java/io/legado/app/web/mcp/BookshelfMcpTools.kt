package io.legado.app.web.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.constant.BookType
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.ReadRecord
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isUpError
import io.legado.app.help.book.isVideo
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import kotlin.math.max
import kotlin.math.min

object BookshelfMcpTools {

    private const val DEFAULT_LIST_LIMIT = 50
    private const val MAX_LIST_LIMIT = 200
    private const val DEFAULT_CONTENT_LIMIT = 20_000
    private const val MAX_CONTENT_LIMIT = 120_000
    private const val DEFAULT_WINDOW_CHAPTERS = 1
    private const val MAX_WINDOW_CHAPTERS = 20
    private const val DEFAULT_CHAPTER_LIST_LIMIT = 100
    private const val MAX_CHAPTER_LIST_LIMIT = 300
    private const val MAX_CACHE_STATUS_CHAPTERS = 500
    private const val DRAFT_REPLACE_RULE_GROUP = "AI草稿"

    fun tools(): List<Map<String, Any>> {
        return listOf(
            tool(
                name = "bookshelf_group_list",
                description = "List bookshelf groups, including built-in group ids and current book counts.",
                properties = emptyMap()
            ),
            tool(
                name = "bookshelf_group_get",
                description = "Get one bookshelf group by group_id or group_name.",
                properties = mapOf(
                    "group_id" to numberSchema("Optional BookGroup.groupId"),
                    "group_name" to stringSchema("Optional BookGroup.groupName")
                )
            ),
            tool(
                name = "bookshelf_group_upsert",
                description = "Create or update one custom bookshelf group. Built-in groups cannot be changed through MCP.",
                properties = mapOf(
                    "group" to mapOf("type" to "object", "description" to "BookGroup fields")
                ),
                required = listOf("group")
            ),
            tool(
                name = "bookshelf_group_delete",
                description = "Delete custom bookshelf groups by id or name. Default only deletes empty groups.",
                properties = mapOf(
                    "group_ids" to arraySchema("BookGroup.groupId list"),
                    "group_names" to arraySchema("BookGroup.groupName list"),
                    "only_empty" to booleanSchema("Default true. When false, remove group bits from books before deleting.")
                )
            ),
            tool(
                name = "bookshelf_stats_get",
                description = "Return lightweight bookshelf book counts and group counts without listing books.",
                properties = mapOf(
                    "include_not_shelf" to booleanSchema("Include temporary books not formally added to shelf")
                )
            ),
            tool(
                name = "bookshelf_book_list",
                description = "List bookshelf books with optional group and keyword filters.",
                properties = mapOf(
                    "group_id" to numberSchema("Optional BookGroup.groupId filter"),
                    "keyword" to stringSchema("Optional keyword matched against name, author, originName, origin, kind, or intro"),
                    "offset" to numberSchema("Default 0"),
                    "limit" to numberSchema("Default 50, max 200"),
                    "include_not_shelf" to booleanSchema("Include temporary books not formally added to shelf")
                )
            ),
            tool(
                name = "bookshelf_book_get",
                description = "Get one bookshelf book by stable work_key, by name and author, or by current source-specific book_url.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity, usually 'book name + newline + author'. Prefer this over book_url for AI workflows."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author")
                )
            ),
            tool(
                name = "bookshelf_book_upsert",
                description = "Create or replace one bookshelf book from a complete Book JSON object. Book.bookUrl is still required as the current source instance address; existing books are detected by bookUrl or by name+author.",
                properties = mapOf(
                    "book" to mapOf("type" to "object", "description" to "Complete Book fields. Must include current source-specific bookUrl, name, and author.")
                ),
                required = listOf("book")
            ),
            tool(
                name = "bookshelf_book_delete",
                description = "Delete bookshelf books by book_url.",
                properties = mapOf("book_urls" to arraySchema("Book.bookUrl list")),
                required = listOf("book_urls")
            ),
            tool(
                name = "bookshelf_book_group_update",
                description = "Batch add, remove, or replace custom bookshelf group membership for books.",
                properties = mapOf(
                    "book_urls" to arraySchema("Book.bookUrl list"),
                    "group_ids" to arraySchema("BookGroup.groupId list"),
                    "group_names" to arraySchema("BookGroup.groupName list"),
                    "mode" to mapOf(
                        "type" to "string",
                        "enum" to listOf("add", "remove", "replace"),
                        "default" to "add"
                    ),
                    "create_missing_groups" to booleanSchema("Default true when group_names are supplied")
                ),
                required = listOf("book_urls")
            ),
            tool(
                name = "bookshelf_current_book_get",
                description = "Return the active reading book when available, otherwise the most recently read text book.",
                properties = emptyMap()
            ),
            tool(
                name = "bookshelf_chapter_list",
                description = "List chapters for one book. Default returns a paged compact list only; pass start/limit/end and include_detail=true only when full chapter fields are needed. This only reads local toc/cache state and never fetches remote content.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity. Prefer this when available."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author"),
                    "start" to numberSchema("Optional inclusive chapter index"),
                    "end" to numberSchema("Optional exclusive chapter index. Output is still capped by limit/max 300 unless include_all=true"),
                    "limit" to numberSchema("Default 100, max 300. Prefer paging for long books"),
                    "keyword" to stringSchema("Optional chapter title keyword"),
                    "include_cache_status" to booleanSchema("Include per-chapter cached/local availability"),
                    "include_detail" to booleanSchema("Default false. When true includes url/base_url/resource_url/start/end/variable/tag fields for the returned page"),
                    "include_all" to booleanSchema("Default false. Avoid in AI chat; allows returning the whole requested range")
                )
            ),
            tool(
                name = "bookshelf_chapter_content_get",
                description = "Read cached or local chapter content for one chapter. This never triggers network fetching.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity. Prefer this when available."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author"),
                    "chapter_index" to numberSchema("BookChapter.index"),
                    "include_title" to booleanSchema("Prefix title before content, default true"),
                    "char_limit" to numberSchema("Default 20000, max 120000")
                ),
                required = listOf("chapter_index")
            ),
            tool(
                name = "bookshelf_text_window_get",
                description = "Read a cached/local text window across adjacent chapters for AI context.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity. Prefer this when available."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author"),
                    "start_chapter_index" to numberSchema("Inclusive chapter index"),
                    "chapter_count" to numberSchema("Default 1, max 20"),
                    "char_limit" to numberSchema("Default 20000, max 120000")
                ),
                required = listOf("start_chapter_index")
            ),
            tool(
                name = "bookshelf_cache_status_get",
                description = "Return cached/local availability for a chapter range without reading full bodies.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity. Prefer this when available."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author"),
                    "start" to numberSchema("Optional inclusive chapter index"),
                    "end" to numberSchema("Optional exclusive chapter index")
                )
            ),
            tool(
                name = "bookshelf_bookmark_list",
                description = "List bookmarks globally or for one book.",
                properties = mapOf(
                    "work_key" to stringSchema("Optional stable work identity used to resolve name/author"),
                    "book_url" to stringSchema("Optional current Book.bookUrl used to resolve name/author"),
                    "name" to stringSchema("Optional Book.name filter"),
                    "author" to stringSchema("Optional Book.author filter"),
                    "keyword" to stringSchema("Optional keyword matched against chapterName, bookText, or content"),
                    "offset" to numberSchema("Default 0"),
                    "limit" to numberSchema("Default 50, max 200")
                )
            ),
            tool(
                name = "bookshelf_bookmark_get",
                description = "Get one bookmark by primary time.",
                properties = mapOf("time" to numberSchema("Bookmark.time")),
                required = listOf("time")
            ),
            tool(
                name = "bookshelf_bookmark_upsert",
                description = "Create or update one bookmark. Supplying work_key, name/author, or book_url fills bookName/bookAuthor when omitted.",
                properties = mapOf(
                    "work_key" to stringSchema("Optional stable work identity"),
                    "book_url" to stringSchema("Optional current Book.bookUrl"),
                    "name" to stringSchema("Optional Book.name"),
                    "author" to stringSchema("Optional Book.author"),
                    "bookmark" to mapOf("type" to "object")
                ),
                required = listOf("bookmark")
            ),
            tool(
                name = "bookshelf_bookmark_delete",
                description = "Delete bookmarks by primary time.",
                properties = mapOf("times" to arraySchema("Bookmark.time list")),
                required = listOf("times")
            ),
            tool(
                name = "bookshelf_read_record_list",
                description = "List aggregated read records grouped by book name.",
                properties = mapOf(
                    "keyword" to stringSchema("Optional keyword matched against book name"),
                    "sort" to mapOf(
                        "type" to "string",
                        "enum" to listOf("name", "read_time", "last_read"),
                        "default" to "name"
                    ),
                    "offset" to numberSchema("Default 0"),
                    "limit" to numberSchema("Default 50, max 200")
                )
            ),
            tool(
                name = "bookshelf_read_record_get",
                description = "Get read record aggregate and per-device rows by book name.",
                properties = mapOf("book_name" to stringSchema("ReadRecord.bookName")),
                required = listOf("book_name")
            ),
            tool(
                name = "bookshelf_read_record_upsert",
                description = "Create or update one read record row. Defaults device_id to current device id.",
                properties = mapOf("record" to mapOf("type" to "object")),
                required = listOf("record")
            ),
            tool(
                name = "bookshelf_read_record_delete",
                description = "Delete all read record rows for one book name.",
                properties = mapOf("book_name" to stringSchema("ReadRecord.bookName")),
                required = listOf("book_name")
            ),
            tool(
                name = "bookshelf_search",
                description = "Alias of book_search, kept under the bookshelf module namespace.",
                properties = mapOf(
                    "key" to stringSchema("Book name, author, or keyword"),
                    "scope" to stringSchema("Optional Legado SearchScope string. Empty means all enabled sources."),
                    "wait_for_finish" to booleanSchema("Default false"),
                    "min_results" to numberSchema("Default 1"),
                    "timeout_seconds" to numberSchema("Default 30")
                ),
                required = listOf("key")
            ),
            tool(
                name = "bookshelf_book_sources_get",
                description = "List cached enabled source candidates for a book, usually produced by search/change-source flows.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity. Prefer this when available."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author")
                )
            ),
            tool(
                name = "bookshelf_change_source_preview",
                description = "Preview cached source candidates for a book. This does not apply source changes.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity. Prefer this when available."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author")
                )
            ),
            tool(
                name = "bookshelf_character_profile_get",
                description = "Get the character profile for one book/work. If create=true is supplied this is a write operation because it creates a profile row.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity. Prefer this over book_url for character profiles."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author"),
                    "create" to booleanSchema("Create profile when book identity is available")
                )
            ),
            tool(
                name = "bookshelf_character_list",
                description = "List character cards for one book/work.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity. Prefer this over book_url for character profiles."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author")
                )
            ),
            tool(
                name = "bookshelf_character_get",
                description = "Get one character card by id.",
                properties = mapOf("id" to numberSchema("BookCharacter.id")),
                required = listOf("id")
            ),
            tool(
                name = "bookshelf_character_upsert",
                description = "Create or update one character card for a book/work.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity. Prefer this over book_url for character profiles."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author"),
                    "character" to mapOf("type" to "object")
                ),
                required = listOf("character")
            ),
            tool(
                name = "bookshelf_character_delete",
                description = "Delete character cards by id.",
                properties = mapOf("ids" to arraySchema("BookCharacter.id list")),
                required = listOf("ids")
            ),
            tool(
                name = "bookshelf_character_set_enabled",
                description = "Enable or disable character cards by id.",
                properties = mapOf(
                    "ids" to arraySchema("BookCharacter.id list"),
                    "enabled" to booleanSchema("Default true")
                ),
                required = listOf("ids")
            ),
            tool(
                name = "bookshelf_character_draft_upsert",
                description = "Write an AI/imported character record in draft state for one book/work. This persists data and is not a chat preview cache.",
                properties = mapOf(
                    "work_key" to stringSchema("Stable work identity. Prefer this over book_url for character profiles."),
                    "book_url" to stringSchema("Current Book.bookUrl. Source-specific and may change after changing source."),
                    "name" to stringSchema("Book.name"),
                    "author" to stringSchema("Book.author"),
                    "character" to mapOf("type" to "object")
                ),
                required = listOf("character")
            ),
            tool(
                name = "bookshelf_character_draft_apply",
                description = "Enable existing character draft records by id. This is a write operation and should only be called after explicit user confirmation.",
                properties = mapOf(
                    "ids" to arraySchema("BookCharacter.id list"),
                    "enabled" to booleanSchema("Default true")
                ),
                required = listOf("ids")
            ),
            tool(
                name = "bookshelf_character_draft_rollback",
                description = "Delete character draft records by id. This is a write operation intended for rollback after explicit user request.",
                properties = mapOf("ids" to arraySchema("BookCharacter.id list")),
                required = listOf("ids")
            ),
            tool(
                name = "bookshelf_replace_rule_list",
                description = "List replace rules, optionally filtered by book scope, group, enabled state, or text scope.",
                properties = mapOf(
                    "work_key" to stringSchema("Optional stable work identity used to resolve a book scope"),
                    "book_url" to stringSchema("Optional current Book.bookUrl used to resolve a book scope"),
                    "name" to stringSchema("Optional Book.name used with author to resolve a book scope"),
                    "author" to stringSchema("Optional Book.author used with name to resolve a book scope"),
                    "group" to stringSchema("Optional group keyword"),
                    "scope" to stringSchema("Optional scope keyword"),
                    "enabled" to booleanSchema("Optional enabled filter"),
                    "offset" to numberSchema("Default 0"),
                    "limit" to numberSchema("Default 50, max 200")
                )
            ),
            tool(
                name = "bookshelf_replace_rule_get",
                description = "Get one replacement rule by id.",
                properties = mapOf("id" to numberSchema("ReplaceRule.id")),
                required = listOf("id")
            ),
            tool(
                name = "bookshelf_replace_rule_upsert",
                description = "Create or update replacement rules, optionally scoped to one book.",
                properties = mapOf(
                    "work_key" to stringSchema("Optional stable work identity used to resolve the book name scope"),
                    "book_url" to stringSchema("Optional current Book.bookUrl used to resolve the book name scope"),
                    "name" to stringSchema("Optional Book.name used with author to resolve the book name scope"),
                    "author" to stringSchema("Optional Book.author used with name to resolve the book name scope"),
                    "rule" to mapOf("type" to "object"),
                    "rules" to arraySchema("ReplaceRule objects")
                )
            ),
            tool(
                name = "bookshelf_replace_rule_delete",
                description = "Delete replacement rules by id.",
                properties = mapOf("ids" to arraySchema("ReplaceRule.id list")),
                required = listOf("ids")
            ),
            tool(
                name = "bookshelf_replace_rule_set_enabled",
                description = "Enable or disable replacement rules by id.",
                properties = mapOf(
                    "ids" to arraySchema("ReplaceRule.id list"),
                    "enabled" to booleanSchema("Default true")
                ),
                required = listOf("ids")
            ),
            tool(
                name = "bookshelf_replace_rule_draft_upsert",
                description = "Write replacement rule records in draft group. This persists data and is not a chat preview cache. When book_url is supplied and scope is omitted, the book name is used as scope.",
                properties = mapOf(
                    "work_key" to stringSchema("Optional stable work identity used to resolve the book name scope"),
                    "book_url" to stringSchema("Optional current Book.bookUrl used to resolve the book name scope"),
                    "name" to stringSchema("Optional Book.name used with author to resolve the book name scope"),
                    "author" to stringSchema("Optional Book.author used with name to resolve the book name scope"),
                    "rule" to mapOf("type" to "object"),
                    "rules" to arraySchema("ReplaceRule objects")
                )
            ),
            tool(
                name = "bookshelf_replace_rule_draft_apply",
                description = "Enable or disable existing replacement rule draft records by id. This is a write operation.",
                properties = mapOf(
                    "ids" to arraySchema("ReplaceRule.id list"),
                    "enabled" to booleanSchema("Default true")
                ),
                required = listOf("ids")
            ),
            tool(
                name = "bookshelf_replace_rule_rollback",
                description = "Delete replacement rule draft records by id. This is a write operation intended for rollback after explicit user request.",
                properties = mapOf("ids" to arraySchema("ReplaceRule.id list")),
                required = listOf("ids")
            )
        )
    }

    fun resources(): List<Map<String, String>> {
        return listOf(
            mapOf(
                "uri" to "legado://schema/bookshelf",
                "name" to "bookshelf-mcp-schema",
                "description" to "Bookshelf MCP module interface summary",
                "mimeType" to "application/json"
            )
        )
    }

    fun readResource(uri: String): String? {
        if (uri != "legado://schema/bookshelf") return null
        return GSON.toJson(
            mapOf(
                "module" to "bookshelf",
                "submodules" to listOf(
                    "books_and_groups",
                    "chapters_content_cache",
                    "bookmarks",
                    "read_records",
                    "search_and_source_preview",
                    "characters",
                    "replace_rules"
                ),
                "notes" to listOf(
                    "Chapter content tools only read cached or local book content.",
                    "Source change preview does not apply a source migration.",
                    "Draft tools are still write tools: they persist rows in the existing Legado tables and are not chat preview storage."
                ),
                "tools" to tools().mapNotNull { it["name"] }
            )
        )
    }

    fun call(name: String, arguments: JsonObject): Map<String, Any?>? {
        return when (name) {
            "bookshelf_group_list" -> listGroups()
            "bookshelf_group_get" -> getGroup(arguments)
            "bookshelf_group_upsert" -> upsertGroup(arguments)
            "bookshelf_group_delete" -> deleteGroups(arguments)
            "bookshelf_stats_get" -> getStats(arguments)
            "bookshelf_book_list" -> listBooks(arguments)
            "bookshelf_book_get" -> getBook(arguments)
            "bookshelf_book_upsert" -> upsertBook(arguments)
            "bookshelf_book_delete" -> deleteBooks(arguments)
            "bookshelf_book_group_update" -> updateBookGroups(arguments)
            "bookshelf_current_book_get" -> getCurrentBook()
            "bookshelf_chapter_list" -> listChapters(arguments)
            "bookshelf_chapter_content_get" -> getChapterContent(arguments)
            "bookshelf_text_window_get" -> getTextWindow(arguments)
            "bookshelf_cache_status_get" -> getCacheStatus(arguments)
            "bookshelf_bookmark_list" -> listBookmarks(arguments)
            "bookshelf_bookmark_get" -> getBookmark(arguments)
            "bookshelf_bookmark_upsert" -> upsertBookmark(arguments)
            "bookshelf_bookmark_delete" -> deleteBookmarks(arguments)
            "bookshelf_read_record_list" -> listReadRecords(arguments)
            "bookshelf_read_record_get" -> getReadRecord(arguments)
            "bookshelf_read_record_upsert" -> upsertReadRecord(arguments)
            "bookshelf_read_record_delete" -> deleteReadRecord(arguments)
            "bookshelf_book_sources_get" -> getBookSources(arguments)
            "bookshelf_change_source_preview" -> getSourcePreview(arguments)
            "bookshelf_character_profile_get" -> getCharacterProfile(arguments)
            "bookshelf_character_list" -> listCharacters(arguments)
            "bookshelf_character_get" -> getCharacter(arguments)
            "bookshelf_character_upsert" -> upsertCharacter(arguments)
            "bookshelf_character_delete" -> deleteCharacters(arguments)
            "bookshelf_character_set_enabled" -> setCharactersEnabled(arguments)
            "bookshelf_character_draft_upsert" -> upsertCharacterDraft(arguments)
            "bookshelf_character_draft_apply" -> applyCharacterDraft(arguments)
            "bookshelf_character_draft_rollback" -> rollbackCharacterDraft(arguments)
            "bookshelf_replace_rule_list" -> listReplaceRules(arguments)
            "bookshelf_replace_rule_get" -> getReplaceRule(arguments)
            "bookshelf_replace_rule_upsert" -> upsertReplaceRule(arguments)
            "bookshelf_replace_rule_delete" -> deleteReplaceRules(arguments)
            "bookshelf_replace_rule_set_enabled" -> setReplaceRulesEnabled(arguments)
            "bookshelf_replace_rule_draft_upsert" -> upsertReplaceRuleDraft(arguments)
            "bookshelf_replace_rule_draft_apply" -> applyReplaceRuleDraft(arguments)
            "bookshelf_replace_rule_rollback" -> rollbackReplaceRuleDraft(arguments)
            else -> null
        }
    }

    private fun listGroups(): Map<String, Any?> {
        val groups = appDb.bookGroupDao.all.map { group ->
            group.toMcpMap(bookCount = booksForGroup(group.groupId, includeNotShelf = false).size)
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/groups",
            normalizedData = mapOf(
                "groups" to groups,
                "total" to groups.size
            )
        )
    }

    private fun getGroup(arguments: JsonObject): Map<String, Any?> {
        val group = arguments.get("group_id").asLongOrNull()?.let { appDb.bookGroupDao.getByID(it) }
            ?: arguments.get("group_name").asStringOrNull()?.takeIf { it.isNotBlank() }
                ?.let { appDb.bookGroupDao.getByName(it) }
            ?: return notFound("native://bookshelf/group", "未找到分组，请检查 group_id 或 group_name")
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/group",
            normalizedData = group.toMcpMap(bookCount = booksForGroup(group.groupId, includeNotShelf = false).size)
        )
    }

    private fun upsertGroup(arguments: JsonObject): Map<String, Any?> {
        val input = arguments.get("group")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("group is required")
        val requestedId = input.get("group_id").asLongOrNull()
            ?: input.get("groupId").asLongOrNull()
        if (requestedId != null && requestedId < 0L) {
            throw IllegalArgumentException("built-in groups cannot be changed through MCP")
        }
        val groupName = input.get("group_name").asStringOrNull()
            ?: input.get("groupName").asStringOrNull()
        val existing = requestedId?.takeIf { it > 0L }?.let { appDb.bookGroupDao.getByID(it) }
            ?: groupName?.takeIf { it.isNotBlank() }?.let { appDb.bookGroupDao.getByName(it) }
        if (existing?.groupId != null && existing.groupId < 0L) {
            throw IllegalArgumentException("built-in groups cannot be changed through MCP")
        }
        if (existing == null && !appDb.bookGroupDao.canAddGroup) {
            throw IllegalStateException("book group limit reached")
        }
        val group = (existing ?: BookGroup(groupId = requestedId?.takeIf { it > 0L } ?: appDb.bookGroupDao.getUnusedId())).apply {
            this.groupName = groupName?.takeIf { it.isNotBlank() } ?: this.groupName
            if (this.groupName.isBlank()) throw IllegalArgumentException("group.group_name is required")
            cover = input.get("cover").asStringOrNull() ?: cover
            order = input.get("order").asIntOrNull() ?: order.takeIf { existing != null } ?: (appDb.bookGroupDao.maxOrder + 1)
            enableRefresh = input.get("enable_refresh").asBooleanOrNull()
                ?: input.get("enableRefresh").asBooleanOrNull()
                        ?: enableRefresh
            show = input.get("show").asBooleanOrNull() ?: show
            bookSort = input.get("book_sort").asIntOrNull()
                ?: input.get("bookSort").asIntOrNull()
                        ?: bookSort
            onlyUpdateRead = input.get("only_update_read").asBooleanOrNull()
                ?: input.get("onlyUpdateRead").asBooleanOrNull()
                        ?: onlyUpdateRead
        }
        appDb.bookGroupDao.insert(group)
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/groupUpsert",
            normalizedData = mapOf(
                "group" to group.toMcpMap(bookCount = booksForGroup(group.groupId, includeNotShelf = false).size),
                "created" to (existing == null)
            )
        )
    }

    private fun deleteGroups(arguments: JsonObject): Map<String, Any?> {
        val ids = arguments.get("group_ids").asLongListOrEmpty()
        val names = arguments.get("group_names").asStringListOrNull().orEmpty()
        if (ids.isEmpty() && names.isEmpty()) {
            throw IllegalArgumentException("group_ids or group_names is required")
        }
        val onlyEmpty = arguments.get("only_empty").asBooleanOrNull() ?: true
        val groups = (ids.mapNotNull { appDb.bookGroupDao.getByID(it) } +
                names.mapNotNull { appDb.bookGroupDao.getByName(it) })
            .distinctBy { it.groupId }
        val deleted = mutableListOf<BookGroup>()
        val skipped = mutableListOf<Map<String, Any?>>()
        groups.forEach { group ->
            val count = booksForGroup(group.groupId, includeNotShelf = true).size
            when {
                group.groupId < 0L -> skipped.add(mapOf("group_id" to group.groupId, "reason" to "built_in_group"))
                onlyEmpty && count > 0 -> skipped.add(mapOf("group_id" to group.groupId, "reason" to "not_empty", "book_count" to count))
                else -> {
                    appDb.bookDao.removeGroup(group.groupId)
                    appDb.bookGroupDao.delete(group)
                    deleted.add(group)
                }
            }
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/groupDelete",
            normalizedData = mapOf(
                "requested" to (ids.size + names.size),
                "deleted_count" to deleted.size,
                "deleted" to deleted.map { it.toMcpMap(bookCount = 0) },
                "skipped" to skipped
            )
        )
    }

    private fun getStats(arguments: JsonObject): Map<String, Any?> {
        val includeNotShelf = arguments.get("include_not_shelf").asBooleanOrNull() ?: false
        val books = booksForGroup(null, includeNotShelf)
        val allBooks = appDb.bookDao.all
        val groups = appDb.bookGroupDao.all.map { group ->
            group.toMcpMap(bookCount = booksForGroup(group.groupId, includeNotShelf).size)
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/stats",
            normalizedData = mapOf(
                "total" to books.size,
                "include_not_shelf" to includeNotShelf,
                "not_shelf" to allBooks.count { it.isNotShelf },
                "type_counts" to mapOf(
                    "text" to books.count { it.type and BookType.text > 0 },
                    "image" to books.count { it.isImage },
                    "audio" to books.count { it.isAudio },
                    "video" to books.count { it.isVideo },
                    "local" to books.count { it.isLocal },
                    "network_text" to books.count { !it.isLocal && !it.isAudio && !it.isVideo },
                    "update_error" to books.count { it.isUpError }
                ),
                "group_total" to groups.size,
                "groups" to groups
            )
        )
    }

    private fun listBooks(arguments: JsonObject): Map<String, Any?> {
        val groupId = arguments.get("group_id").asLongOrNull()
        val keyword = arguments.get("keyword").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_LIST_LIMIT)
            .coerceIn(1, MAX_LIST_LIMIT)
        val includeNotShelf = arguments.get("include_not_shelf").asBooleanOrNull() ?: false
        val filtered = booksForGroup(groupId, includeNotShelf)
            .filter { keyword == null || it.matchesBookKeyword(keyword) }
            .sortedWith(compareByDescending<Book> { it.durChapterTime }.thenBy { it.name })
        val page = filtered.drop(offset).take(limit).map { it.toMcpSummary() }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/books",
            normalizedData = mapOf(
                "books" to page,
                "offset" to offset,
                "limit" to limit,
                "total" to filtered.size,
                "has_more" to (offset + page.size < filtered.size)
            )
        )
    }

    private fun getBook(arguments: JsonObject): Map<String, Any?> {
        val book = resolveBook(arguments)
            ?: return notFound("native://bookshelf/book", "未找到书籍，请检查 work_key、book_url 或 name/author")
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/book",
            normalizedData = book.toMcpDetail()
        )
    }

    private fun upsertBook(arguments: JsonObject): Map<String, Any?> {
        val input = arguments.get("book")?.takeIf { it.isJsonObject }
            ?: throw IllegalArgumentException("book is required")
        val book = runCatching { GSON.fromJson(input, Book::class.java) }.getOrNull()
            ?: throw IllegalArgumentException("book cannot be parsed")
        if (book.bookUrl.isBlank()) throw IllegalArgumentException("book.book_url/bookUrl is required")
        if (book.name.isBlank()) throw IllegalArgumentException("book.name is required")
        if (book.author.isBlank()) throw IllegalArgumentException("book.author is required")
        val existing = appDb.bookDao.getBook(book.bookUrl)
            ?: appDb.bookDao.getBook(book.name, book.author)
        appDb.bookDao.insert(book)
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/bookUpsert",
            normalizedData = mapOf(
                "book" to appDb.bookDao.getBook(book.bookUrl)?.toMcpDetail(),
                "created" to (existing == null),
                "replaced_book_url" to existing?.bookUrl?.takeIf { it != book.bookUrl }
            )
        )
    }

    private fun deleteBooks(arguments: JsonObject): Map<String, Any?> {
        val urls = arguments.get("book_urls").asStringListOrNull().orEmpty()
        if (urls.isEmpty()) throw IllegalArgumentException("book_urls is required")
        val books = urls.mapNotNull { appDb.bookDao.getBook(it) }
        if (books.isNotEmpty()) {
            appDb.bookDao.delete(*books.toTypedArray())
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/bookDelete",
            normalizedData = mapOf(
                "requested" to urls.size,
                "deleted_count" to books.size,
                "deleted" to books.map { it.toMcpSummary() }
            ),
            warnings = if (books.size == urls.size) emptyList() else listOf("部分 book_url 未找到")
        )
    }

    private fun updateBookGroups(arguments: JsonObject): Map<String, Any?> {
        val urls = arguments.get("book_urls").asStringListOrNull().orEmpty()
        if (urls.isEmpty()) throw IllegalArgumentException("book_urls is required")
        val mode = arguments.get("mode").asStringOrNull()?.trim()?.lowercase()
            ?.takeIf { it in setOf("add", "remove", "replace") }
            ?: "add"
        val createMissingGroups = arguments.get("create_missing_groups").asBooleanOrNull() ?: true
        val groupIds = linkedSetOf<Long>()
        arguments.get("group_ids").asLongListOrEmpty()
            .filter { it > 0L }
            .forEach { groupIds.add(it) }
        arguments.get("group_names").asStringListOrNull().orEmpty().forEach { name ->
            val existing = appDb.bookGroupDao.getByName(name)
            val group = existing ?: if (createMissingGroups) {
                val created = BookGroup(
                    groupId = appDb.bookGroupDao.getUnusedId(),
                    groupName = name,
                    order = appDb.bookGroupDao.maxOrder + 1
                )
                appDb.bookGroupDao.insert(created)
                created
            } else {
                null
            }
            group?.takeIf { it.groupId > 0L }?.let { groupIds.add(it.groupId) }
        }
        if (mode != "replace" && groupIds.isEmpty()) {
            throw IllegalArgumentException("group_ids or group_names is required")
        }
        val mask = groupIds.fold(0L) { acc, id -> acc or id }
        val books = urls.mapNotNull { appDb.bookDao.getBook(it) }
        val updated = books.map { book ->
            val oldGroup = book.group
            book.group = when (mode) {
                "remove" -> book.group and mask.inv()
                "replace" -> mask
                else -> book.group or mask
            }
            appDb.bookDao.update(book)
            mapOf(
                "book_url" to book.bookUrl,
                "name" to book.name,
                "old_group" to oldGroup,
                "new_group" to book.group,
                "group_names" to appDb.bookGroupDao.getGroupNames(book.group)
            )
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/bookGroupUpdate",
            normalizedData = mapOf(
                "mode" to mode,
                "group_ids" to groupIds.toList(),
                "requested_books" to urls.size,
                "updated_count" to updated.size,
                "updated" to updated
            ),
            warnings = if (books.size == urls.size) emptyList() else listOf("部分 book_url 未找到")
        )
    }

    private fun getCurrentBook(): Map<String, Any?> {
        val activeBook = ReadBook.book
        val book = activeBook ?: appDb.bookDao.lastReadBook
        if (book == null) {
            return notFound("native://bookshelf/currentBook", "当前没有正在阅读或最近阅读的文本书籍")
        }
        val isActive = activeBook?.bookUrl == book.bookUrl
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/currentBook",
            normalizedData = mapOf(
                "is_reading_active" to isActive,
                "book" to book.toMcpDetail(),
                "runtime_progress" to if (isActive) {
                    mapOf(
                        "dur_chapter_index" to ReadBook.durChapterIndex,
                        "dur_chapter_pos" to ReadBook.durChapterPos,
                        "chapter_size" to ReadBook.chapterSize,
                        "simulated_chapter_size" to ReadBook.simulatedChapterSize
                    )
                } else {
                    null
                }
            )
        )
    }

    private fun listChapters(arguments: JsonObject): Map<String, Any?> {
        val book = resolveBook(arguments)
            ?: return notFound("native://bookshelf/chapters", "未找到书籍，请检查 work_key、book_url 或 name/author")
        val all = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val start = (arguments.get("start").asIntOrNull() ?: 0).coerceAtLeast(0)
        val includeAll = arguments.get("include_all").asBooleanOrNull() ?: false
        val limit = if (includeAll) {
            Int.MAX_VALUE
        } else {
            (arguments.get("limit").asIntOrNull() ?: DEFAULT_CHAPTER_LIST_LIMIT)
                .coerceIn(1, MAX_CHAPTER_LIST_LIMIT)
        }
        val requestedEnd = arguments.get("end").asIntOrNull()
            ?: min(all.size, start + limit)
        val end = min(requestedEnd.coerceIn(start, all.size), start + limit)
        val keyword = arguments.get("keyword").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val includeCacheStatus = arguments.get("include_cache_status").asBooleanOrNull() ?: false
        val includeDetail = arguments.get("include_detail").asBooleanOrNull() ?: false
        val filtered = all.asSequence()
            .filter { it.index in start until end }
            .filter { keyword == null || it.title.contains(keyword, ignoreCase = true) }
            .map { it.toMcpMap(book, includeCacheStatus, includeDetail) }
            .toList()
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/chapters",
            normalizedData = mapOf(
                "book" to book.toMcpSummary(),
                "chapters" to filtered,
                "start" to start,
                "end" to end,
                "limit" to limit.takeIf { it != Int.MAX_VALUE },
                "total" to all.size,
                "filtered_total" to filtered.size,
                "has_more" to (end < all.size),
                "compact" to !includeDetail,
                "truncated_by_mcp" to (!includeAll && requestedEnd > end)
            ),
            warnings = if (!includeAll && (requestedEnd > end || end < all.size)) {
                listOf("章节列表默认分页并使用精简字段；需要更多章节请用 start/limit 继续分页，确需完整字段再传 include_detail=true")
            } else {
                emptyList()
            }
        )
    }

    private fun getChapterContent(arguments: JsonObject): Map<String, Any?> {
        val book = resolveBook(arguments)
            ?: return notFound("native://bookshelf/chapterContent", "未找到书籍，请检查 work_key、book_url 或 name/author")
        val chapterIndex = arguments.get("chapter_index").asIntOrNull()
            ?: throw IllegalArgumentException("chapter_index is required")
        val includeTitle = arguments.get("include_title").asBooleanOrNull() ?: true
        val charLimit = (arguments.get("char_limit").asIntOrNull() ?: DEFAULT_CONTENT_LIMIT)
            .coerceIn(0, MAX_CONTENT_LIMIT)
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
            ?: return notFound("native://bookshelf/chapterContent", "未找到章节，请检查 chapter_index")
        val rawContent = BookHelp.getContent(book, chapter)
        val hasContent = rawContent != null
        val fullText = if (rawContent != null && includeTitle) {
            chapter.title + "\n" + rawContent
        } else {
            rawContent
        }
        val limited = fullText.limitText(charLimit)
        return toolResult(
            ok = hasContent,
            upstreamEndpoint = "native://bookshelf/chapterContent",
            normalizedData = mapOf(
                "book" to book.toMcpSummary(),
                "chapter" to chapter.toMcpMap(book, includeCacheStatus = true, includeDetail = true),
                "has_content" to hasContent,
                "content" to limited.text,
                "content_chars" to (fullText?.length ?: 0),
                "truncated_by_mcp" to limited.truncated
            ),
            warnings = if (hasContent) {
                emptyList()
            } else {
                listOf("章节正文未缓存且不是可直接读取的本地书籍；MCP 不会主动联网抓取正文")
            }
        )
    }

    private fun getTextWindow(arguments: JsonObject): Map<String, Any?> {
        val book = resolveBook(arguments)
            ?: return notFound("native://bookshelf/textWindow", "未找到书籍，请检查 work_key、book_url 或 name/author")
        val start = arguments.get("start_chapter_index").asIntOrNull()
            ?: throw IllegalArgumentException("start_chapter_index is required")
        val chapterCount = (arguments.get("chapter_count").asIntOrNull() ?: DEFAULT_WINDOW_CHAPTERS)
            .coerceIn(1, MAX_WINDOW_CHAPTERS)
        val charLimit = (arguments.get("char_limit").asIntOrNull() ?: DEFAULT_CONTENT_LIMIT)
            .coerceIn(0, MAX_CONTENT_LIMIT)
        val chapters = appDb.bookChapterDao.getChapterList(
            book.bookUrl,
            start.coerceAtLeast(0),
            start.coerceAtLeast(0) + chapterCount
        )
        var remaining = charLimit
        val items = mutableListOf<Map<String, Any?>>()
        val textParts = mutableListOf<String>()
        chapters.forEach { chapter ->
            val content = BookHelp.getContent(book, chapter)
            val block = if (content == null) {
                null
            } else {
                chapter.title + "\n" + content
            }
            val limited = block.limitText(remaining)
            if (limited.text != null) {
                textParts.add(limited.text)
                remaining = max(0, remaining - limited.text.length)
            }
            items.add(
                mapOf(
                    "index" to chapter.index,
                    "title" to chapter.title,
                    "has_content" to (content != null),
                    "content_chars" to (block?.length ?: 0),
                    "included_chars" to (limited.text?.length ?: 0),
                    "truncated_by_mcp" to limited.truncated
                )
            )
        }
        val missing = items.filter { it["has_content"] != true }.map { it["index"] }
        return toolResult(
            ok = chapters.isNotEmpty(),
            upstreamEndpoint = "native://bookshelf/textWindow",
            normalizedData = mapOf(
                "book" to book.toMcpSummary(),
                "start_chapter_index" to start,
                "chapter_count" to chapterCount,
                "chapters" to items,
                "missing_chapter_indexes" to missing,
                "text" to textParts.joinToString("\n\n"),
                "char_limit" to charLimit,
                "truncated_by_mcp" to chapters.any { BookHelp.getContent(book, it)?.length ?: 0 > charLimit }
            ),
            warnings = if (missing.isEmpty()) {
                emptyList()
            } else {
                listOf("部分章节未缓存且不是可直接读取的本地书籍；MCP 不会主动联网抓取正文")
            }
        )
    }

    private fun getCacheStatus(arguments: JsonObject): Map<String, Any?> {
        val book = resolveBook(arguments)
            ?: return notFound("native://bookshelf/cacheStatus", "未找到书籍，请检查 work_key、book_url 或 name/author")
        val all = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val start = (arguments.get("start").asIntOrNull() ?: 0).coerceAtLeast(0)
        val requestedEnd = arguments.get("end").asIntOrNull() ?: min(all.size, start + MAX_CACHE_STATUS_CHAPTERS)
        val end = requestedEnd.coerceIn(start, all.size)
        val page = all.filter { it.index in start until end }.take(MAX_CACHE_STATUS_CHAPTERS)
        val statuses = page.map { chapter ->
            mapOf(
                "index" to chapter.index,
                "title" to chapter.title,
                "has_content" to BookHelp.hasContent(book, chapter),
                "is_volume" to chapter.isVolume
            )
        }
        val cachedCount = statuses.count { it["has_content"] == true }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/cacheStatus",
            normalizedData = mapOf(
                "book" to book.toMcpSummary(),
                "start" to start,
                "end" to end,
                "total_chapters" to all.size,
                "returned" to statuses.size,
                "cached_count" to cachedCount,
                "missing_count" to (statuses.size - cachedCount),
                "truncated_by_mcp" to (end - start > statuses.size),
                "chapters" to statuses
            )
        )
    }

    private fun listBookmarks(arguments: JsonObject): Map<String, Any?> {
        val book = resolveBook(arguments)
        val name = book?.name ?: arguments.get("name").asStringOrNull()?.takeIf { it.isNotBlank() }
        val author = book?.author ?: arguments.get("author").asStringOrNull()?.takeIf { it.isNotBlank() }
        val keyword = arguments.get("keyword").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_LIST_LIMIT)
            .coerceIn(1, MAX_LIST_LIMIT)
        val filtered = appDb.bookmarkDao.all
            .asSequence()
            .filter { name == null || it.bookName == name }
            .filter { author == null || it.bookAuthor == author }
            .filter { keyword == null || it.matchesBookmarkKeyword(keyword) }
            .toList()
        val page = filtered.drop(offset).take(limit).map { it.toMcpMap() }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/bookmarks",
            normalizedData = mapOf(
                "book" to book?.toMcpSummary(),
                "book_name" to name,
                "book_author" to author,
                "bookmarks" to page,
                "offset" to offset,
                "limit" to limit,
                "total" to filtered.size,
                "has_more" to (offset + page.size < filtered.size)
            )
        )
    }

    private fun getBookmark(arguments: JsonObject): Map<String, Any?> {
        val time = arguments.get("time").asLongOrNull() ?: throw IllegalArgumentException("time is required")
        val bookmark = appDb.bookmarkDao.all.firstOrNull { it.time == time }
            ?: return notFound("native://bookshelf/bookmark", "未找到书签: $time")
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/bookmark",
            normalizedData = bookmark.toMcpMap()
        )
    }

    private fun upsertBookmark(arguments: JsonObject): Map<String, Any?> {
        val input = arguments.get("bookmark")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("bookmark is required")
        val book = resolveBook(arguments)
        val time = input.get("time").asLongOrNull() ?: System.currentTimeMillis()
        val existing = appDb.bookmarkDao.all.firstOrNull { it.time == time }
        val bookmark = Bookmark(
            time = time,
            bookName = input.get("book_name").asStringOrNull()
                ?: input.get("bookName").asStringOrNull()
                ?: book?.name
                ?: existing?.bookName
                ?: "",
            bookAuthor = input.get("book_author").asStringOrNull()
                ?: input.get("bookAuthor").asStringOrNull()
                ?: book?.author
                ?: existing?.bookAuthor
                ?: "",
            chapterIndex = input.get("chapter_index").asIntOrNull()
                ?: input.get("chapterIndex").asIntOrNull()
                ?: existing?.chapterIndex
                ?: 0,
            chapterPos = input.get("chapter_pos").asIntOrNull()
                ?: input.get("chapterPos").asIntOrNull()
                ?: existing?.chapterPos
                ?: 0,
            chapterName = input.get("chapter_name").asStringOrNull()
                ?: input.get("chapterName").asStringOrNull()
                ?: existing?.chapterName
                ?: "",
            bookText = input.get("book_text").asStringOrNull()
                ?: input.get("bookText").asStringOrNull()
                ?: existing?.bookText
                ?: "",
            content = input.get("content").asStringOrNull() ?: existing?.content ?: ""
        )
        if (bookmark.bookName.isBlank()) throw IllegalArgumentException("bookmark.book_name is required")
        appDb.bookmarkDao.insert(bookmark)
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/bookmarkUpsert",
            normalizedData = mapOf(
                "bookmark" to appDb.bookmarkDao.all.firstOrNull { it.time == bookmark.time }?.toMcpMap(),
                "created" to (existing == null)
            )
        )
    }

    private fun deleteBookmarks(arguments: JsonObject): Map<String, Any?> {
        val times = arguments.get("times").asLongList()
        val bookmarks = appDb.bookmarkDao.all.filter { it.time in times }
        if (bookmarks.isNotEmpty()) {
            appDb.bookmarkDao.delete(*bookmarks.toTypedArray())
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/bookmarkDelete",
            normalizedData = mapOf(
                "requested" to times.size,
                "deleted" to bookmarks.size,
                "times" to bookmarks.map { it.time }
            )
        )
    }

    private fun listReadRecords(arguments: JsonObject): Map<String, Any?> {
        val keyword = arguments.get("keyword").asStringOrNull()?.trim().orEmpty()
        val sort = arguments.get("sort").asStringOrNull()?.takeIf { it.isNotBlank() } ?: "name"
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_LIST_LIMIT)
            .coerceIn(1, MAX_LIST_LIMIT)
        val filtered = appDb.readRecordDao.search(keyword).let { records ->
            when (sort) {
                "read_time" -> records.sortedByDescending { it.readTime }
                "last_read" -> records.sortedByDescending { it.lastRead }
                else -> records.sortedBy { it.bookName }
            }
        }
        val page = filtered.drop(offset).take(limit).map { it.toMcpMap() }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/readRecords",
            normalizedData = mapOf(
                "records" to page,
                "offset" to offset,
                "limit" to limit,
                "total" to filtered.size,
                "has_more" to (offset + page.size < filtered.size),
                "all_time" to appDb.readRecordDao.allTime
            )
        )
    }

    private fun getReadRecord(arguments: JsonObject): Map<String, Any?> {
        val bookName = arguments.get("book_name").asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: arguments.get("bookName").asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("book_name is required")
        val rows = appDb.readRecordDao.all.filter { it.bookName == bookName }
        if (rows.isEmpty()) {
            return notFound("native://bookshelf/readRecord", "未找到阅读记录: $bookName")
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/readRecord",
            normalizedData = mapOf(
                "book_name" to bookName,
                "read_time" to rows.sumOf { it.readTime },
                "last_read" to (rows.maxOfOrNull { it.lastRead } ?: 0L),
                "rows" to rows.map { it.toMcpMap() }
            )
        )
    }

    private fun upsertReadRecord(arguments: JsonObject): Map<String, Any?> {
        val input = arguments.get("record")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("record is required")
        val bookName = input.get("book_name").asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: input.get("bookName").asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("record.book_name is required")
        val deviceId = input.get("device_id").asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: input.get("deviceId").asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: AppConst.androidId
        val existing = appDb.readRecordDao.all.firstOrNull { it.deviceId == deviceId && it.bookName == bookName }
        val record = ReadRecord(
            deviceId = deviceId,
            bookName = bookName,
            readTime = input.get("read_time").asLongOrNull()
                ?: input.get("readTime").asLongOrNull()
                ?: existing?.readTime
                ?: 0L,
            lastRead = input.get("last_read").asLongOrNull()
                ?: input.get("lastRead").asLongOrNull()
                ?: existing?.lastRead
                ?: System.currentTimeMillis()
        )
        appDb.readRecordDao.insert(record)
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/readRecordUpsert",
            normalizedData = mapOf(
                "record" to record.toMcpMap(),
                "created" to (existing == null)
            )
        )
    }

    private fun deleteReadRecord(arguments: JsonObject): Map<String, Any?> {
        val bookName = arguments.get("book_name").asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: arguments.get("bookName").asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("book_name is required")
        val before = appDb.readRecordDao.all.count { it.bookName == bookName }
        appDb.readRecordDao.deleteByName(bookName)
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/readRecordDelete",
            normalizedData = mapOf(
                "book_name" to bookName,
                "deleted" to before
            )
        )
    }

    private fun getBookSources(arguments: JsonObject): Map<String, Any?> {
        val book = resolveBook(arguments)
            ?: return notFound("native://bookshelf/bookSources", "未找到书籍，请检查 work_key、book_url 或 name/author")
        val sources = appDb.searchBookDao.getEnabledByNameAuthor(book.name, book.author)
            .map { it.toMcpSourceCandidate(currentBook = book) }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/bookSources",
            normalizedData = mapOf(
                "book" to book.toMcpSummary(),
                "sources" to sources,
                "total" to sources.size
            ),
            warnings = if (sources.isEmpty()) {
                listOf("未找到缓存的可换源候选；请先通过 bookshelf_search/book_search 搜索或在换源页刷新候选")
            } else {
                emptyList()
            }
        )
    }

    private fun getSourcePreview(arguments: JsonObject): Map<String, Any?> {
        val result = getBookSources(arguments).toMutableMap()
        result["upstream_endpoint"] = "native://bookshelf/changeSourcePreview"
        result["warnings"] = ((result["warnings"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()) +
                "当前接口只返回换源候选预览，不执行书籍迁移或应用换源"
        return result
    }

    private fun getCharacterProfile(arguments: JsonObject): Map<String, Any?> {
        val identity = resolveWorkIdentity(arguments)
        val create = arguments.get("create").asBooleanOrNull() ?: false
        val profile = if (create && identity.book != null) {
            appDb.bookCharacterDao.getOrCreateProfile(
                identity.book.name,
                identity.book.author,
                identity.book.bookUrl
            )
        } else {
            appDb.bookCharacterDao.getProfile(identity.workKey)
        }
        return toolResult(
            ok = profile != null,
            upstreamEndpoint = "native://bookshelf/characterProfile",
            normalizedData = mapOf(
                "profile" to profile?.toMcpMap(),
                "work_key" to identity.workKey
            ),
            warnings = if (profile == null) {
                listOf("未找到角色档案；传入 create=true 且提供 work_key、book_url 或 name/author 可创建")
            } else {
                emptyList()
            }
        )
    }

    private fun listCharacters(arguments: JsonObject): Map<String, Any?> {
        val identity = resolveWorkIdentity(arguments)
        val profile = appDb.bookCharacterDao.getProfile(identity.workKey)
        val characters = if (profile == null) {
            emptyList()
        } else {
            appDb.bookCharacterDao.getCharacters(identity.workKey).map { it.toMcpMap() }
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/characters",
            normalizedData = mapOf(
                "profile" to profile?.toMcpMap(),
                "work_key" to identity.workKey,
                "characters" to characters,
                "total" to characters.size
            ),
            warnings = if (profile == null) listOf("未找到角色档案") else emptyList()
        )
    }

    private fun getCharacter(arguments: JsonObject): Map<String, Any?> {
        val id = arguments.get("id").asLongOrNull()
            ?: throw IllegalArgumentException("id is required")
        val character = appDb.bookCharacterDao.getCharacter(id)
            ?: return notFound("native://bookshelf/character", "未找到角色卡")
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/character",
            normalizedData = character.toMcpMap()
        )
    }

    private fun upsertCharacter(arguments: JsonObject): Map<String, Any?> {
        return withEndpoint(upsertCharacterDraft(arguments), "native://bookshelf/characterUpsert")
    }

    private fun upsertCharacterDraft(arguments: JsonObject): Map<String, Any?> {
        val characterJson = arguments.get("character")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("character is required")
        val identity = resolveWorkIdentity(arguments)
        if (identity.book != null) {
            appDb.bookCharacterDao.getOrCreateProfile(
                identity.book.name,
                identity.book.author,
                identity.book.bookUrl
            )
        } else if (appDb.bookCharacterDao.getProfile(identity.workKey) == null) {
            throw IllegalArgumentException("profile is missing; provide book_url or call bookshelf_character_profile_get with create=true first")
        }
        val now = System.currentTimeMillis()
        val id = characterJson.get("id").asLongOrNull() ?: 0L
        val old = when {
            id > 0L -> appDb.bookCharacterDao.getCharacter(id)
            else -> {
                val name = characterJson.get("name").asStringOrNull()
                    ?: throw IllegalArgumentException("character.name is required")
                appDb.bookCharacterDao.getCharacters(identity.workKey)
                    .firstOrNull { it.name == name }
            }
        }
        val saved = (old ?: BookCharacter(
            workKey = identity.workKey,
            createdAt = now
        )).apply {
            workKey = identity.workKey
            name = characterJson.get("name").asStringOrNull() ?: name
            if (name.isBlank()) throw IllegalArgumentException("character.name is required")
            gender = characterJson.get("gender").asStringOrNull() ?: gender
            roleTag = characterJson.get("role_tag").asStringOrNull()
                ?: characterJson.get("roleTag").asStringOrNull()
                        ?: roleTag
            aliasesJson = characterJson.get("aliases").asStringListOrNull()?.let { GSON.toJson(it) }
                ?: characterJson.get("aliases_json").asStringOrNull()
                        ?: characterJson.get("aliasesJson").asStringOrNull()
                        ?: aliasesJson
            intro = characterJson.get("intro").asStringOrNull()
                ?: characterJson.get("short_intro").asStringOrNull()
                ?: characterJson.get("shortIntro").asStringOrNull()
                ?: characterJson.get("identity").asStringOrNull()
                        ?: intro
            this.identity = null
            shortIntro = null
            avatarUri = characterJson.get("avatar_uri").asStringOrNull()
                ?: characterJson.get("avatarUri").asStringOrNull()
                        ?: avatarUri
            portraitUri = characterJson.get("portrait_uri").asStringOrNull()
                ?: characterJson.get("portraitUri").asStringOrNull()
                        ?: portraitUri
            imagePrompt = characterJson.get("image_prompt").asStringOrNull()
                ?: characterJson.get("imagePrompt").asStringOrNull()
                        ?: imagePrompt
            enabled = characterJson.get("enabled").asBooleanOrNull() ?: enabled
            sortOrder = characterJson.get("sort_order").asIntOrNull()
                ?: characterJson.get("sortOrder").asIntOrNull()
                        ?: sortOrder
            source = characterJson.get("source").asStringOrNull() ?: BookCharacter.Source.AI
            confidence = characterJson.get("confidence").asFloatOrNull() ?: confidence
            updatedAt = now
        }
        val savedId = if (saved.id == 0L) {
            appDb.bookCharacterDao.insertCharacter(saved)
        } else {
            appDb.bookCharacterDao.updateCharacter(saved)
            saved.id
        }
        appDb.bookCharacterDao.updateCharacterCount(identity.workKey)
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/characterDraftUpsert",
            normalizedData = mapOf(
                "character" to appDb.bookCharacterDao.getCharacter(savedId)?.toMcpMap(),
                "work_key" to identity.workKey
            )
        )
    }

    private fun setCharactersEnabled(arguments: JsonObject): Map<String, Any?> {
        return withEndpoint(applyCharacterDraft(arguments), "native://bookshelf/characterSetEnabled")
    }

    private fun applyCharacterDraft(arguments: JsonObject): Map<String, Any?> {
        val ids = arguments.get("ids").asLongList()
        val enabled = arguments.get("enabled").asBooleanOrNull() ?: true
        val now = System.currentTimeMillis()
        val changed = ids.mapNotNull { appDb.bookCharacterDao.getCharacter(it) }
            .onEach {
                it.enabled = enabled
                it.updatedAt = now
                appDb.bookCharacterDao.updateCharacter(it)
                appDb.bookCharacterDao.updateCharacterCount(it.workKey)
            }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/characterDraftApply",
            normalizedData = mapOf(
                "requested_ids" to ids,
                "updated" to changed.map { it.toMcpMap() },
                "updated_count" to changed.size
            ),
            warnings = if (changed.size == ids.size) emptyList() else listOf("部分角色卡 id 未找到")
        )
    }

    private fun deleteCharacters(arguments: JsonObject): Map<String, Any?> {
        return withEndpoint(rollbackCharacterDraft(arguments), "native://bookshelf/characterDelete")
    }

    private fun rollbackCharacterDraft(arguments: JsonObject): Map<String, Any?> {
        val ids = arguments.get("ids").asLongList()
        val characters = ids.mapNotNull { appDb.bookCharacterDao.getCharacter(it) }
        val workKeys = characters.map { it.workKey }.toSet()
        characters.forEach {
            appDb.bookCharacterDao.deleteCharacter(it)
        }
        workKeys.forEach {
            appDb.bookCharacterDao.updateCharacterCount(it)
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/characterDraftRollback",
            normalizedData = mapOf(
                "requested_ids" to ids,
                "deleted_count" to characters.size,
                "deleted" to characters.map { it.toMcpMap() }
            ),
            warnings = if (characters.size == ids.size) emptyList() else listOf("部分角色卡 id 未找到")
        )
    }

    private fun listReplaceRules(arguments: JsonObject): Map<String, Any?> {
        val book = resolveBook(arguments)
        val group = arguments.get("group").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val scope = arguments.get("scope").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val enabled = arguments.get("enabled").asBooleanOrNull()
        val includeDetail = arguments.get("include_detail").asBooleanOrNull() ?: false
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_LIST_LIMIT)
            .coerceIn(1, MAX_LIST_LIMIT)
        val filtered = appDb.replaceRuleDao.all.asSequence()
            .filter { enabled == null || it.isEnabled == enabled }
            .filter { group == null || it.group?.contains(group, ignoreCase = true) == true }
            .filter { scope == null || it.scope?.contains(scope, ignoreCase = true) == true }
            .filter { book == null || it.matchesBookScope(book) }
            .toList()
        val page = filtered.drop(offset).take(limit).map { rule ->
            if (includeDetail) rule.toMcpMap() else rule.toMcpSummary()
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/replaceRules",
            normalizedData = mapOf(
                "rules" to page,
                "offset" to offset,
                "limit" to limit,
                "total" to filtered.size,
                "has_more" to (offset + page.size < filtered.size),
                "compact" to !includeDetail
            )
        )
    }

    private fun getReplaceRule(arguments: JsonObject): Map<String, Any?> {
        val id = arguments.get("id").asLongOrNull()
            ?: throw IllegalArgumentException("id is required")
        val rule = appDb.replaceRuleDao.findById(id)
            ?: return notFound("native://bookshelf/replaceRule", "未找到替换规则: $id")
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/replaceRule",
            normalizedData = rule.toMcpMap()
        )
    }

    private fun upsertReplaceRule(arguments: JsonObject): Map<String, Any?> {
        return upsertReplaceRules(arguments, draft = false)
    }

    private fun upsertReplaceRuleDraft(arguments: JsonObject): Map<String, Any?> {
        return upsertReplaceRules(arguments, draft = true)
    }

    private fun upsertReplaceRules(arguments: JsonObject, draft: Boolean): Map<String, Any?> {
        val book = resolveBook(arguments)
        val ruleJsons = buildList {
            arguments.get("rule")?.takeIf { it.isJsonObject }?.asJsonObject?.let { add(it) }
            arguments.get("rules")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach {
                if (it.isJsonObject) add(it.asJsonObject)
            }
        }
        if (ruleJsons.isEmpty()) {
            throw IllegalArgumentException("rule or rules is required")
        }
        val warnings = mutableListOf<String>()
        val rules = ruleJsons.map { json ->
            val old = json.get("id").asLongOrNull()?.takeIf { it > 0L }
                ?.let { appDb.replaceRuleDao.findById(it) }
            val rule = (old ?: ReplaceRule()).apply {
                id = json.get("id").asLongOrNull()?.takeIf { it > 0L } ?: id
                name = json.get("name").asStringOrNull() ?: name
                pattern = json.get("pattern").asStringOrNull() ?: pattern
                replacement = json.get("replacement").asStringOrNull() ?: replacement
                group = json.get("group").asStringOrNull() ?: group ?: DRAFT_REPLACE_RULE_GROUP.takeIf { draft }
                scope = json.get("scope").asStringOrNull() ?: scope ?: book?.name
                scopeTitle = json.get("scope_title").asBooleanOrNull()
                    ?: json.get("scopeTitle").asBooleanOrNull()
                            ?: scopeTitle
                scopeContent = json.get("scope_content").asBooleanOrNull()
                    ?: json.get("scopeContent").asBooleanOrNull()
                            ?: scopeContent
                excludeScope = json.get("exclude_scope").asStringOrNull()
                    ?: json.get("excludeScope").asStringOrNull()
                            ?: excludeScope
                isEnabled = json.get("enabled").asBooleanOrNull()
                    ?: json.get("isEnabled").asBooleanOrNull()
                            ?: isEnabled
                isRegex = json.get("is_regex").asBooleanOrNull()
                    ?: json.get("isRegex").asBooleanOrNull()
                            ?: isRegex
                timeoutMillisecond = json.get("timeout_millisecond").asLongOrNull()
                    ?: json.get("timeoutMillisecond").asLongOrNull()
                            ?: timeoutMillisecond
                order = json.get("order").asIntOrNull()
                    ?: json.get("sort_order").asIntOrNull()
                            ?: json.get("sortOrder").asIntOrNull()
                            ?: order
            }
            if (rule.name.isBlank()) {
                rule.name = rule.pattern.take(40).ifBlank { "MCP替换规则" }
            }
            if (!rule.isValid()) {
                warnings.add("规则无效: ${rule.name}")
            }
            rule
        }
        val validRules = rules.filter { it.isValid() }
        val insertedIds = if (validRules.isNotEmpty()) {
            appDb.replaceRuleDao.insert(*validRules.toTypedArray())
        } else {
            emptyList()
        }
        return toolResult(
            ok = validRules.isNotEmpty(),
            upstreamEndpoint = if (draft) {
                "native://bookshelf/replaceRuleDraftUpsert"
            } else {
                "native://bookshelf/replaceRuleUpsert"
            },
            normalizedData = mapOf(
                "ids" to insertedIds,
                "rules" to insertedIds.mapNotNull { appDb.replaceRuleDao.findById(it) }.map { it.toMcpMap() },
                "invalid_count" to (rules.size - validRules.size)
            ),
            warnings = warnings
        )
    }

    private fun setReplaceRulesEnabled(arguments: JsonObject): Map<String, Any?> {
        return withEndpoint(applyReplaceRuleDraft(arguments), "native://bookshelf/replaceRuleSetEnabled")
    }

    private fun applyReplaceRuleDraft(arguments: JsonObject): Map<String, Any?> {
        val ids = arguments.get("ids").asLongList()
        val enabled = arguments.get("enabled").asBooleanOrNull() ?: true
        val rules = appDb.replaceRuleDao.findByIds(*ids.toLongArray())
        rules.forEach {
            it.isEnabled = enabled
            appDb.replaceRuleDao.update(it)
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/replaceRuleDraftApply",
            normalizedData = mapOf(
                "requested_ids" to ids,
                "updated" to rules.map { it.toMcpMap() },
                "updated_count" to rules.size
            ),
            warnings = if (rules.size == ids.size) emptyList() else listOf("部分替换规则 id 未找到")
        )
    }

    private fun deleteReplaceRules(arguments: JsonObject): Map<String, Any?> {
        return withEndpoint(rollbackReplaceRuleDraft(arguments), "native://bookshelf/replaceRuleDelete")
    }

    private fun rollbackReplaceRuleDraft(arguments: JsonObject): Map<String, Any?> {
        val ids = arguments.get("ids").asLongList()
        val rules = appDb.replaceRuleDao.findByIds(*ids.toLongArray())
        if (rules.isNotEmpty()) {
            appDb.replaceRuleDao.delete(*rules.toTypedArray())
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookshelf/replaceRuleRollback",
            normalizedData = mapOf(
                "requested_ids" to ids,
                "deleted_count" to rules.size,
                "deleted" to rules.map { it.toMcpMap() }
            ),
            warnings = if (rules.size == ids.size) emptyList() else listOf("部分替换规则 id 未找到")
        )
    }

    private fun booksForGroup(groupId: Long?, includeNotShelf: Boolean): List<Book> {
        val customGroupSum = appDb.bookGroupDao.idsSum
        return appDb.bookDao.all.filter { book ->
            (includeNotShelf || !book.isNotShelf) && when (groupId) {
                null, BookGroup.IdAll -> true
                BookGroup.IdRoot -> !book.isLocal && !book.isAudio && !book.isVideo &&
                        (customGroupSum == 0L || (customGroupSum and book.group) == 0L)
                BookGroup.IdLocal -> book.isLocal
                BookGroup.IdAudio -> book.isAudio
                BookGroup.IdVideo -> book.isVideo
                BookGroup.IdError -> book.isUpError
                BookGroup.IdNetNone -> !book.isLocal && !book.isAudio && !book.isVideo &&
                        (customGroupSum == 0L || (customGroupSum and book.group) == 0L)
                BookGroup.IdLocalNone -> book.isLocal && !book.isAudio && !book.isVideo &&
                        (customGroupSum == 0L || (customGroupSum and book.group) == 0L)
                else -> groupId > 0 && (book.group and groupId) > 0
            }
        }
    }

    private fun resolveBook(arguments: JsonObject): Book? {
        arguments.identityString("work_key", "workKey")?.let { workKey ->
            return appDb.bookDao.all.firstOrNull { book -> book.matchesWorkKey(workKey) }
        }
        arguments.identityString("book_url", "bookUrl")?.let { bookUrl ->
            return appDb.bookDao.getBook(bookUrl)
                ?: appDb.bookDao.all.firstOrNull { book -> book.bookUrl.sameIdentity(bookUrl) }
        }
        val name = arguments.identityString("name")
        val author = arguments.identityString("author")
        if (name != null && author != null) {
            return appDb.bookDao.getBook(name, author)
                ?: appDb.bookDao.all.firstOrNull { book ->
                    book.matchesWorkKey(BookCharacterProfile.workKey(name, author)) ||
                            (book.name.sameIdentity(name) && book.author.sameIdentity(author))
                }
        }
        return null
    }

    private fun resolveWorkIdentity(arguments: JsonObject): WorkIdentity {
        val book = resolveBook(arguments)
        if (book != null) {
            return WorkIdentity(
                workKey = BookCharacterProfile.workKey(book.name, book.author),
                book = book
            )
        }
        arguments.identityString("work_key", "workKey")?.let {
            return WorkIdentity(workKey = it, book = null)
        }
        val name = arguments.identityString("name")
        val author = arguments.identityString("author")
        if (name != null && author != null) {
            return WorkIdentity(
                workKey = BookCharacterProfile.workKey(name, author),
                book = null
            )
        }
        throw IllegalArgumentException("work_key, book_url, or name/author is required")
    }

    private fun JsonObject.identityString(vararg names: String): String? {
        return names.asSequence()
            .mapNotNull { get(it).asStringOrNull()?.normalizeIdentityInput() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun Book.matchesWorkKey(workKey: String): Boolean {
        return BookCharacterProfile.workKey(name, author).sameIdentity(workKey)
    }

    private fun String.sameIdentity(other: String): Boolean {
        return normalizeIdentityInput() == other.normalizeIdentityInput()
    }

    private fun String.normalizeIdentityInput(): String {
        return trim()
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\r", "\n")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
    }

    private data class WorkIdentity(
        val workKey: String,
        val book: Book?
    )

    private data class LimitedText(
        val text: String?,
        val truncated: Boolean
    )

    private fun Book.matchesBookKeyword(keyword: String): Boolean {
        return sequenceOf(name, author, originName, origin, kind, intro, customTag, latestChapterTitle)
            .filterNotNull()
            .any { it.contains(keyword, ignoreCase = true) }
    }

    private fun ReplaceRule.matchesBookScope(book: Book): Boolean {
        val scopeValue = scope
        val excludeValue = excludeScope
        val included = scopeValue.isNullOrBlank() ||
                scopeValue.contains(book.name, ignoreCase = true) ||
                scopeValue.contains(book.origin, ignoreCase = true) ||
                scopeValue.contains(book.originName, ignoreCase = true)
        val excluded = !excludeValue.isNullOrBlank() && (
                excludeValue.contains(book.name, ignoreCase = true) ||
                        excludeValue.contains(book.origin, ignoreCase = true) ||
                        excludeValue.contains(book.originName, ignoreCase = true)
                )
        return included && !excluded
    }

    private fun Book.toMcpSummary(): Map<String, Any?> {
        return mapOf(
            "book_url" to bookUrl,
            "name" to name,
            "author" to author,
            "origin" to origin,
            "origin_name" to originName,
            "type" to type,
            "type_flags" to typeFlags(),
            "group" to group,
            "group_names" to appDb.bookGroupDao.getGroupNames(group),
            "kind" to kind,
            "latest_chapter_title" to latestChapterTitle,
            "total_chapter_num" to totalChapterNum,
            "dur_chapter_index" to durChapterIndex,
            "dur_chapter_title" to durChapterTitle,
            "dur_chapter_pos" to durChapterPos,
            "dur_chapter_time" to durChapterTime,
            "word_count" to wordCount,
            "can_update" to canUpdate,
            "is_not_shelf" to isNotShelf,
            "work_key" to BookCharacterProfile.workKey(name, author)
        )
    }

    private fun Book.toMcpDetail(
        includeIntro: Boolean = true,
        includeReadConfig: Boolean = false
    ): Map<String, Any?> {
        return toMcpSummary() + mapOf(
            "toc_url" to tocUrl,
            "cover_url" to coverUrl,
            "custom_cover_url" to customCoverUrl,
            "intro" to intro.takeIf { includeIntro },
            "custom_intro" to customIntro.takeIf { includeIntro },
            "custom_tag" to customTag,
            "latest_chapter_time" to latestChapterTime,
            "last_check_time" to lastCheckTime,
            "last_check_count" to lastCheckCount,
            "order" to order,
            "origin_order" to originOrder,
            "sync_time" to syncTime,
            "read_config" to readConfig.takeIf { includeReadConfig }
        )
    }

    private fun Book.typeFlags(): List<String> {
        return buildList {
            if (type and BookType.text > 0) add("text")
            if (isImage) add("image")
            if (isAudio) add("audio")
            if (isVideo) add("video")
            if (type and BookType.webFile > 0) add("web_file")
            if (isLocal) add("local")
            if (isUpError) add("update_error")
            if (isNotShelf) add("not_shelf")
        }
    }

    private fun BookChapter.toMcpMap(
        book: Book,
        includeCacheStatus: Boolean,
        includeDetail: Boolean
    ): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "index" to index,
            "title" to title,
            "is_volume" to isVolume,
            "is_vip" to isVip,
            "word_count" to wordCount
        )
        if (includeDetail) {
            map["url"] = url
            map["book_url"] = bookUrl
            map["base_url"] = baseUrl
            map["tag"] = tag
            map["resource_url"] = resourceUrl
            map["start"] = start
            map["end"] = end
            map["variable"] = variable
        }
        if (includeCacheStatus) {
            map["has_content"] = BookHelp.hasContent(book, this)
        }
        return map
    }

    private fun BookGroup.toMcpMap(bookCount: Int): Map<String, Any?> {
        return mapOf(
            "group_id" to groupId,
            "group_name" to groupName,
            "cover" to cover,
            "order" to order,
            "enable_refresh" to enableRefresh,
            "show" to show,
            "book_sort" to bookSort,
            "only_update_read" to onlyUpdateRead,
            "book_count" to bookCount,
            "is_builtin" to (groupId < 0)
        )
    }

    private fun Bookmark.matchesBookmarkKeyword(keyword: String): Boolean {
        return sequenceOf(bookName, bookAuthor, chapterName, bookText, content)
            .any { it.contains(keyword, ignoreCase = true) }
    }

    private fun Bookmark.toMcpMap(): Map<String, Any?> {
        return mapOf(
            "time" to time,
            "book_name" to bookName,
            "book_author" to bookAuthor,
            "chapter_index" to chapterIndex,
            "chapter_pos" to chapterPos,
            "chapter_name" to chapterName,
            "book_text" to bookText,
            "content" to content
        )
    }

    private fun ReadRecordShow.toMcpMap(): Map<String, Any?> {
        return mapOf(
            "book_name" to bookName,
            "read_time" to readTime,
            "last_read" to lastRead
        )
    }

    private fun ReadRecord.toMcpMap(): Map<String, Any?> {
        return mapOf(
            "device_id" to deviceId,
            "book_name" to bookName,
            "read_time" to readTime,
            "last_read" to lastRead
        )
    }

    private fun SearchBook.toMcpSourceCandidate(currentBook: Book): Map<String, Any?> {
        return mapOf(
            "book_url" to bookUrl,
            "name" to name,
            "author" to author,
            "origin" to origin,
            "origin_name" to originName,
            "type" to type,
            "cover_url" to coverUrl,
            "kind" to kind,
            "intro" to intro,
            "word_count" to wordCount,
            "latest_chapter_title" to latestChapterTitle,
            "toc_url" to tocUrl,
            "origin_order" to originOrder,
            "respond_time" to respondTime,
            "chapter_word_count" to chapterWordCount,
            "chapter_word_count_text" to chapterWordCountText,
            "is_current_source" to (bookUrl == currentBook.bookUrl || origin == currentBook.origin)
        )
    }

    private fun BookCharacterProfile.toMcpMap(): Map<String, Any?> {
        return mapOf(
            "work_key" to workKey,
            "book_name" to bookName,
            "book_author" to bookAuthor,
            "latest_book_url" to latestBookUrl,
            "character_count" to characterCount,
            "enabled" to enabled,
            "created_at" to createdAt,
            "updated_at" to updatedAt
        )
    }

    private fun BookCharacter.toMcpMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "work_key" to workKey,
            "name" to name,
            "gender" to gender,
            "role_tag" to roleTag,
            "aliases" to aliasesJson.parseStringList(),
            "aliases_json" to aliasesJson,
            "intro" to displayIntro(),
            "avatar_uri" to avatarUri,
            "portrait_uri" to portraitUri,
            "image_prompt" to imagePrompt,
            "enabled" to enabled,
            "sort_order" to sortOrder,
            "source" to source,
            "confidence" to confidence,
            "created_at" to createdAt,
            "updated_at" to updatedAt
        )
    }

    private fun ReplaceRule.toMcpMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "group" to group,
            "pattern" to pattern,
            "replacement" to replacement,
            "scope" to scope,
            "scope_title" to scopeTitle,
            "scope_content" to scopeContent,
            "exclude_scope" to excludeScope,
            "enabled" to isEnabled,
            "is_regex" to isRegex,
            "timeout_millisecond" to timeoutMillisecond,
            "order" to order,
            "is_valid" to isValid()
        )
    }

    private fun ReplaceRule.toMcpSummary(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "group" to group,
            "scope" to scope,
            "scope_title" to scopeTitle,
            "scope_content" to scopeContent,
            "exclude_scope" to excludeScope,
            "enabled" to isEnabled,
            "is_regex" to isRegex,
            "order" to order,
            "is_valid" to isValid(),
            "pattern_preview" to pattern.limitText(120).text,
            "replacement_preview" to replacement.limitText(120).text
        )
    }

    private fun String?.parseStringList(): List<String> {
        val value = this?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            JsonParser.parseString(value).asStringListOrNull() ?: emptyList()
        }.getOrElse {
            value.split(',', '，').map { item -> item.trim() }.filter { it.isNotEmpty() }
        }
    }

    private fun String?.limitText(maxChars: Int): LimitedText {
        val value = this ?: return LimitedText(null, truncated = false)
        if (value.length <= maxChars) {
            return LimitedText(value, truncated = false)
        }
        return LimitedText(value.take(maxChars) + "\n[truncated by MCP at $maxChars chars]", true)
    }

    private fun notFound(upstreamEndpoint: String, warning: String): Map<String, Any?> {
        return toolResult(
            ok = false,
            upstreamEndpoint = upstreamEndpoint,
            normalizedData = null,
            warnings = listOf(warning)
        )
    }

    private fun toolResult(
        ok: Boolean,
        upstreamEndpoint: String,
        normalizedData: Any?,
        rawUpstream: Any? = null,
        warnings: List<String> = emptyList(),
        sessionId: String? = null
    ): Map<String, Any?> {
        return mapOf(
            "ok" to ok,
            "upstream_endpoint" to upstreamEndpoint,
            "normalized_data" to normalizedData,
            "raw_upstream" to rawUpstream,
            "warnings" to warnings,
            "session_id" to sessionId
        )
    }

    private fun withEndpoint(result: Map<String, Any?>, upstreamEndpoint: String): Map<String, Any?> {
        return result.toMutableMap().apply {
            put("upstream_endpoint", upstreamEndpoint)
        }
    }

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, Any>,
        required: List<String> = emptyList()
    ): Map<String, Any> {
        return mapOf(
            "name" to name,
            "description" to description,
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to properties,
                "required" to required
            )
        )
    }

    private fun stringSchema(description: String): Map<String, String> {
        return mapOf("type" to "string", "description" to description)
    }

    private fun numberSchema(description: String): Map<String, String> {
        return mapOf("type" to "number", "description" to description)
    }

    private fun booleanSchema(description: String): Map<String, Any> {
        return mapOf("type" to "boolean", "description" to description)
    }

    private fun arraySchema(description: String): Map<String, Any> {
        return mapOf("type" to "array", "description" to description)
    }

    private fun JsonElement?.asStringOrNull(): String? {
        return this?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        return runCatching { this?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
    }

    private fun JsonElement?.asLongOrNull(): Long? {
        return runCatching { this?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull()
    }

    private fun JsonElement?.asFloatOrNull(): Float? {
        return runCatching { this?.takeIf { it.isJsonPrimitive }?.asFloat }.getOrNull()
    }

    private fun JsonElement?.asBooleanOrNull(): Boolean? {
        return runCatching { this?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull()
    }

    private fun JsonElement?.asStringListOrNull(): List<String>? {
        val element = this ?: return null
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.asStringOrNull()?.trim() }
                .filter { it.isNotEmpty() }

            element.isJsonPrimitive -> element.asString.split(',', '，')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            else -> null
        }
    }

    private fun JsonElement?.asLongList(): List<Long> {
        val element = this ?: throw IllegalArgumentException("ids is required")
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.asLongOrNull() }
            element.isJsonPrimitive -> listOfNotNull(element.asLongOrNull())
            else -> emptyList()
        }.also {
            if (it.isEmpty()) throw IllegalArgumentException("ids is required")
        }
    }

    private fun JsonElement?.asLongListOrEmpty(): List<Long> {
        val element = this ?: return emptyList()
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.asLongOrNull() }
            element.isJsonPrimitive -> listOfNotNull(element.asLongOrNull())
            else -> emptyList()
        }
    }
}
