package io.legado.app.web.mcp

data class McpInternalToolModule(
    val id: String,
    val title: String,
    val capabilities: List<McpInternalToolCapability>
)

data class McpInternalToolCapability(
    val id: String,
    val title: String,
    val description: String,
    val toolNames: Set<String>,
    val sideEffect: McpToolSideEffect = McpToolSideEffect.READ
) {
    val requiresUserConfirmation: Boolean
        get() = sideEffect.requiresUserConfirmation
}

enum class McpToolSideEffect(val requiresUserConfirmation: Boolean) {
    READ(false),
    AGENT_INTERNAL_WRITE(false),
    APP_WRITE(true),
    DESTRUCTIVE(true)
}

/**
 * Internal-only capability catalog used to limit the tool definitions sent to AI models.
 * External MCP clients continue to receive the complete tools/list response.
 */
object McpInternalToolCatalog {

    val modules: List<McpInternalToolModule> = listOf(
        module(
            id = "general",
            title = "通用",
            capability(
                id = "general.service_info",
                title = "MCP 服务信息",
                description = "检查服务状态并读取接口摘要",
                "legado_ping",
                "legado_get_api_summary"
            )
        ),
        module(
            id = "book_source",
            title = "书源",
            capability(
                id = "book_source.query",
                title = "查询书源",
                description = "列出、统计并读取书源规则",
                "book_source_list",
                "book_source_stats_get",
                "book_source_get"
            ),
            writeCapability(
                id = "book_source.manage",
                title = "管理书源",
                description = "保存、删除以及启停书源",
                "book_source_save",
                "book_source_delete",
                "book_source_set_enabled"
            ),
            capability(
                id = "book_source.search",
                title = "跨源搜书",
                description = "通过已启用书源搜索书籍",
                "book_search"
            ),
            capability(
                id = "book_source.debug",
                title = "调试书源",
                description = "运行书源规则调试并读取过程日志",
                "book_source_debug"
            )
        ),
        module(
            id = "bookshelf",
            title = "书架",
            capability(
                id = "bookshelf.query",
                title = "查询书架书籍",
                description = "读取书架统计、书籍列表和当前书籍",
                "bookshelf_stats_get",
                "bookshelf_book_list",
                "bookshelf_book_get",
                "bookshelf_current_book_get"
            ),
            writeCapability(
                id = "bookshelf.manage_books",
                title = "管理书架书籍",
                description = "新增、更新或删除书架书籍",
                "bookshelf_book_upsert",
                "bookshelf_book_delete"
            ),
            writeCapability(
                id = "bookshelf.manage_groups",
                title = "管理书架分组",
                description = "查询和维护分组及书籍分组归属",
                "bookshelf_group_list",
                "bookshelf_group_get",
                "bookshelf_group_upsert",
                "bookshelf_group_delete",
                "bookshelf_book_group_update"
            ),
            capability(
                id = "bookshelf.read_content",
                title = "读取目录与正文",
                description = "读取章节目录、章节正文和正文窗口",
                "bookshelf_chapter_list",
                "bookshelf_chapter_content_get",
                "bookshelf_text_window_get",
                "bookshelf_chapter_snippets_get"
            ),
            capability(
                id = "bookshelf.cache_status",
                title = "查询章节缓存",
                description = "只读查询章节正文是否已经缓存",
                "bookshelf_cache_status_get"
            ),
            writeCapability(
                id = "bookshelf.manage_cache",
                title = "管理章节缓存",
                description = "查询、下载或清理章节缓存",
                "bookshelf_cache_status_get",
                "bookshelf_cache_download",
                "bookshelf_cache_clear"
            ),
            writeCapability(
                id = "bookshelf.manage_bookmarks",
                title = "管理书签",
                description = "查询、新增、更新或删除书签",
                "bookshelf_bookmark_list",
                "bookshelf_bookmark_get",
                "bookshelf_bookmark_upsert",
                "bookshelf_bookmark_delete"
            ),
            writeCapability(
                id = "bookshelf.manage_read_records",
                title = "管理阅读记录",
                description = "查询、新增、更新或删除阅读记录",
                "bookshelf_read_record_list",
                "bookshelf_read_record_get",
                "bookshelf_read_record_upsert",
                "bookshelf_read_record_delete"
            ),
            capability(
                id = "bookshelf.search_and_change_source",
                title = "搜书与换源预览",
                description = "搜索书籍、查询来源并预览换源结果",
                "bookshelf_search",
                "bookshelf_book_sources_get",
                "bookshelf_change_source_preview"
            ),
            writeCapability(
                id = "bookshelf.manage_characters",
                title = "管理角色卡",
                description = "查询和维护当前书籍的角色资料",
                "bookshelf_character_profile_get",
                "bookshelf_character_list",
                "bookshelf_character_get",
                "bookshelf_character_upsert",
                "bookshelf_character_delete",
                "bookshelf_character_set_enabled"
            ),
            writeCapability(
                id = "bookshelf.manage_replace_rules",
                title = "管理本书净化规则",
                description = "查询、维护、应用或回滚本书替换规则",
                "bookshelf_replace_rule_list",
                "bookshelf_replace_rule_get",
                "bookshelf_replace_rule_upsert",
                "bookshelf_replace_rule_delete",
                "bookshelf_replace_rule_set_enabled",
                "bookshelf_replace_rule_draft_upsert",
                "bookshelf_replace_rule_draft_apply",
                "bookshelf_replace_rule_rollback"
            )
        ),
        module(
            id = "settings",
            title = "设置与规则",
            capability(
                id = "settings.rule_stats",
                title = "查询规则统计",
                description = "读取目录、净化和字典规则数量",
                "settings_rule_stats_get"
            ),
            writeCapability(
                id = "settings.manage_toc_rules",
                title = "管理 TXT 目录规则",
                description = "查询和维护本地 TXT 目录识别规则",
                "settings_txt_toc_rule_list",
                "settings_txt_toc_rule_get",
                "settings_txt_toc_rule_upsert",
                "settings_txt_toc_rule_delete",
                "settings_txt_toc_rule_set_enabled"
            ),
            writeCapability(
                id = "settings.manage_replace_rules",
                title = "管理全局净化规则",
                description = "查询和维护全局替换净化规则",
                "settings_replace_rule_list",
                "settings_replace_rule_get",
                "settings_replace_rule_upsert",
                "settings_replace_rule_delete",
                "settings_replace_rule_set_enabled"
            ),
            writeCapability(
                id = "settings.manage_dict_rules",
                title = "管理字典规则",
                description = "查询和维护字典查询规则",
                "settings_dict_rule_list",
                "settings_dict_rule_get",
                "settings_dict_rule_upsert",
                "settings_dict_rule_delete",
                "settings_dict_rule_set_enabled"
            )
        ),
        module(
            id = "ai",
            title = "AI 数据",
            capability(
                id = "ai.memory_read",
                title = "查询 AI 记忆",
                description = "只读检查和检索 AI 记忆",
                "agent_memory_status_get",
                "agent_memory_search"
            ),
            capability(
                id = "ai.chat_history",
                title = "查询 AI 聊天历史",
                description = "读取 AI 助手会话和消息详情",
                "ai_chat_conversation_list",
                "ai_chat_conversation_get"
            ),
            internalWriteCapability(
                id = "ai.memory",
                title = "管理 AI 记忆",
                description = "检查、检索或写入 AI 记忆",
                "agent_memory_status_get",
                "agent_memory_search",
                "agent_memory_upsert",
                "agent_memory_batch_upsert"
            )
        ),
        module(
            id = "developer",
            title = "开发调试",
            writeCapability(
                id = "developer.network_logs",
                title = "网络请求日志",
                description = "查询或清空运行时网络日志",
                "network_log_list",
                "network_log_get",
                "network_log_clear"
            ),
            writeCapability(
                id = "developer.app_logs",
                title = "App 调试日志",
                description = "查询或清空 App 内存调试日志",
                "debug_log_list",
                "debug_log_get",
                "debug_log_clear"
            ),
            capability(
                id = "developer.read_aloud_storyboard",
                title = "听书分镜调试",
                description = "读取当前章节的 AI 听书分镜快照",
                "read_aloud_storyboard_debug_get"
            )
        )
    )

    val capabilities: List<McpInternalToolCapability> = modules.flatMap { it.capabilities }
    val allCapabilityIds: List<String> = capabilities.map { it.id }
    val allToolNames: Set<String> = capabilities.flatMapTo(linkedSetOf()) { it.toolNames }

    private val capabilityById = capabilities.associateBy { it.id }

    fun normalizeCapabilityIds(capabilityIds: Collection<String>): List<String> {
        val selected = capabilityIds.mapTo(hashSetOf()) { it.trim() }
        return allCapabilityIds.filter { it in selected }
    }

    fun resolveToolNames(capabilityIds: Collection<String>): Set<String> {
        return normalizeCapabilityIds(capabilityIds)
            .flatMapTo(linkedSetOf()) { capabilityById.getValue(it).toolNames }
    }

    fun capability(id: String): McpInternalToolCapability? = capabilityById[id]

    fun sideEffectOf(
        toolName: String,
        argumentsDeclareWrite: Boolean = false
    ): McpToolSideEffect {
        if (toolName in agentInternalWriteToolNames) {
            return McpToolSideEffect.AGENT_INTERNAL_WRITE
        }
        if (toolName in destructiveToolNames || destructiveFallbackSuffixes.any(toolName::endsWith)) {
            return McpToolSideEffect.DESTRUCTIVE
        }
        if (toolName in appWriteToolNames || appWriteFallbackSuffixes.any(toolName::endsWith) ||
            argumentsDeclareWrite
        ) {
            return McpToolSideEffect.APP_WRITE
        }
        return McpToolSideEffect.READ
    }

    fun requiresUserConfirmation(
        toolName: String,
        argumentsDeclareWrite: Boolean = false
    ): Boolean {
        return sideEffectOf(toolName, argumentsDeclareWrite).requiresUserConfirmation
    }

    private fun module(
        id: String,
        title: String,
        vararg capabilities: McpInternalToolCapability
    ) = McpInternalToolModule(id, title, capabilities.toList())

    private fun capability(
        id: String,
        title: String,
        description: String,
        vararg toolNames: String
    ) = McpInternalToolCapability(
        id = id,
        title = title,
        description = description,
        toolNames = toolNames.toCollection(linkedSetOf()),
        sideEffect = McpToolSideEffect.READ
    )

    private fun internalWriteCapability(
        id: String,
        title: String,
        description: String,
        vararg toolNames: String
    ) = McpInternalToolCapability(
        id = id,
        title = title,
        description = description,
        toolNames = toolNames.toCollection(linkedSetOf()),
        sideEffect = McpToolSideEffect.AGENT_INTERNAL_WRITE
    )

    private fun writeCapability(
        id: String,
        title: String,
        description: String,
        vararg toolNames: String
    ) = McpInternalToolCapability(
        id = id,
        title = title,
        description = description,
        toolNames = toolNames.toCollection(linkedSetOf()),
        sideEffect = McpToolSideEffect.APP_WRITE
    )

    private val agentInternalWriteToolNames = setOf(
        "agent_memory_upsert",
        "agent_memory_batch_upsert"
    )

    private val appWriteToolNames = setOf(
        "book_source_save",
        "book_source_set_enabled",
        "bookshelf_book_upsert",
        "bookshelf_group_upsert",
        "bookshelf_book_group_update",
        "bookshelf_cache_download",
        "bookshelf_bookmark_upsert",
        "bookshelf_read_record_upsert",
        "bookshelf_character_upsert",
        "bookshelf_character_set_enabled",
        "bookshelf_replace_rule_upsert",
        "bookshelf_replace_rule_set_enabled",
        "bookshelf_replace_rule_draft_upsert",
        "bookshelf_replace_rule_draft_apply",
        "settings_txt_toc_rule_upsert",
        "settings_txt_toc_rule_set_enabled",
        "settings_replace_rule_upsert",
        "settings_replace_rule_set_enabled",
        "settings_dict_rule_upsert",
        "settings_dict_rule_set_enabled"
    )

    private val destructiveToolNames = setOf(
        "book_source_delete",
        "bookshelf_book_delete",
        "bookshelf_group_delete",
        "bookshelf_cache_clear",
        "bookshelf_bookmark_delete",
        "bookshelf_read_record_delete",
        "bookshelf_character_delete",
        "bookshelf_replace_rule_delete",
        "bookshelf_replace_rule_rollback",
        "settings_txt_toc_rule_delete",
        "settings_replace_rule_delete",
        "settings_dict_rule_delete",
        "network_log_clear",
        "debug_log_clear"
    )

    // Unknown future tools fail closed by conventional mutating suffixes. Registered tools
    // should still be added to the explicit sets above so review semantics remain auditable.
    private val destructiveFallbackSuffixes = listOf(
        "_delete",
        "_rollback",
        "_archive",
        "_clear"
    )

    private val appWriteFallbackSuffixes = listOf(
        "_upsert",
        "_set_enabled",
        "_apply",
        "_save",
        "_download",
        "_group_update"
    )
}
