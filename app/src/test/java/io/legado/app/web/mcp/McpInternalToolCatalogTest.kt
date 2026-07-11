package io.legado.app.web.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpInternalToolCatalogTest {

    @Test
    fun catalogCoversEveryRegisteredMcpTool() {
        assertEquals(EXPECTED_TOOL_NAMES, McpInternalToolCatalog.allToolNames)
        assertEquals(
            McpInternalToolCatalog.allCapabilityIds.size,
            McpInternalToolCatalog.allCapabilityIds.distinct().size
        )
    }

    @Test
    fun capabilitiesCanReuseToolsAcrossModules() {
        val tools = McpInternalToolCatalog.resolveToolNames(
            listOf("book_source.query", "bookshelf.search_and_change_source")
        )

        assertTrue("book_source_get" in tools)
        assertTrue("bookshelf_search" in tools)
        assertFalse("bookshelf_book_delete" in tools)
    }

    @Test
    fun unknownCapabilitiesDoNotExposeTools() {
        assertTrue(McpInternalToolCatalog.resolveToolNames(listOf("missing")).isEmpty())
    }

    companion object {
        private val EXPECTED_TOOL_NAMES = setOf(
            "legado_ping",
            "legado_get_api_summary",
            "book_source_list",
            "book_source_stats_get",
            "book_source_get",
            "book_source_save",
            "book_source_delete",
            "book_source_set_enabled",
            "book_source_debug",
            "book_search",
            "network_log_list",
            "network_log_get",
            "network_log_clear",
            "read_aloud_storyboard_debug_get",
            "ai_chat_conversation_list",
            "ai_chat_conversation_get",
            "debug_log_list",
            "debug_log_get",
            "debug_log_clear",
            "bookshelf_group_list",
            "bookshelf_group_get",
            "bookshelf_group_upsert",
            "bookshelf_group_delete",
            "bookshelf_stats_get",
            "bookshelf_book_list",
            "bookshelf_book_get",
            "bookshelf_book_upsert",
            "bookshelf_book_delete",
            "bookshelf_book_group_update",
            "bookshelf_current_book_get",
            "bookshelf_chapter_list",
            "bookshelf_chapter_content_get",
            "bookshelf_text_window_get",
            "bookshelf_cache_status_get",
            "bookshelf_cache_download",
            "bookshelf_cache_clear",
            "bookshelf_bookmark_list",
            "bookshelf_bookmark_get",
            "bookshelf_bookmark_upsert",
            "bookshelf_bookmark_delete",
            "bookshelf_read_record_list",
            "bookshelf_read_record_get",
            "bookshelf_read_record_upsert",
            "bookshelf_read_record_delete",
            "bookshelf_search",
            "bookshelf_book_sources_get",
            "bookshelf_change_source_preview",
            "bookshelf_character_profile_get",
            "bookshelf_character_list",
            "bookshelf_character_get",
            "bookshelf_character_upsert",
            "bookshelf_character_delete",
            "bookshelf_character_set_enabled",
            "bookshelf_replace_rule_list",
            "bookshelf_replace_rule_get",
            "bookshelf_replace_rule_upsert",
            "bookshelf_replace_rule_delete",
            "bookshelf_replace_rule_set_enabled",
            "bookshelf_replace_rule_draft_upsert",
            "bookshelf_replace_rule_draft_apply",
            "bookshelf_replace_rule_rollback",
            "settings_rule_stats_get",
            "settings_txt_toc_rule_list",
            "settings_txt_toc_rule_get",
            "settings_txt_toc_rule_upsert",
            "settings_txt_toc_rule_delete",
            "settings_txt_toc_rule_set_enabled",
            "settings_replace_rule_list",
            "settings_replace_rule_get",
            "settings_replace_rule_upsert",
            "settings_replace_rule_delete",
            "settings_replace_rule_set_enabled",
            "settings_dict_rule_list",
            "settings_dict_rule_get",
            "settings_dict_rule_upsert",
            "settings_dict_rule_delete",
            "settings_dict_rule_set_enabled",
            "agent_memory_status_get",
            "agent_memory_search",
            "agent_memory_upsert",
            "agent_memory_archive"
        )
    }
}
