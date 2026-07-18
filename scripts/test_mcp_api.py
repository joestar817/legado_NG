#!/usr/bin/env python3
"""Integration-style unittest runner for the native Legado MCP HTTP endpoint."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import unittest
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_ENDPOINT = os.environ.get("LEGADO_MCP_ENDPOINT", "http://192.168.11.13:1124/mcp")
EXPECTED_TOOLS = {
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
    "network_log_list",
    "network_log_get",
    "network_log_clear",
    "ai_chat_conversation_list",
    "ai_chat_conversation_get",
    "agent_memory_status_get",
    "agent_memory_search",
    "agent_memory_upsert",
    "agent_memory_batch_upsert",
    "debug_log_list",
    "debug_log_get",
    "debug_log_clear",
}
EXPECTED_RESOURCES = {
    "legado://api/mcp",
    "legado://schema/book-source",
    "legado://schema/bookshelf",
    "legado://schema/settings",
}
BOOK_SOURCE_LIST_FIELDS = {
    "bookSourceComment",
    "bookSourceGroup",
    "bookSourceName",
    "bookSourceType",
    "bookSourceUrl",
    "enabled",
}


def find_replacement_chars(value: Any, path: str = "") -> list[str]:
    if isinstance(value, str):
        return [path] if "\ufffd" in value else []
    if isinstance(value, dict):
        paths: list[str] = []
        for key, item in value.items():
            paths.extend(find_replacement_chars(item, f"{path}.{key}" if path else str(key)))
        return paths
    if isinstance(value, list):
        paths: list[str] = []
        for index, item in enumerate(value):
            paths.extend(find_replacement_chars(item, f"{path}[{index}]"))
        return paths
    return []


class Config:
    endpoint = DEFAULT_ENDPOINT
    timeout = 45.0
    search_key = "斗破苍穹"
    debug_key = "斗破苍穹"
    debug_timeout_seconds = 12.0
    search_timeout_seconds = 20.0
    expected_search_min = 1
    allow_empty_search = False
    write = False
    clear_network_log = False
    no_slow = False


class McpClient:
    def __init__(self, endpoint: str, timeout: float) -> None:
        self.endpoint = endpoint
        self.timeout = timeout
        self.next_id = int(time.time() * 1000)

    def post(self, payload: Any) -> Any:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = Request(
            self.endpoint,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urlopen(request, timeout=self.timeout) as response:
                text = response.read().decode("utf-8")
        except HTTPError as exc:
            text = exc.read().decode("utf-8", errors="replace")
            raise AssertionError(f"HTTP {exc.code}: {text}") from exc
        except URLError as exc:
            raise AssertionError(f"Cannot reach MCP endpoint {self.endpoint}: {exc}") from exc
        return json.loads(text)

    def rpc(self, method: str, params: dict[str, Any] | None = None) -> Any:
        self.next_id += 1
        request_id = self.next_id
        payload = {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
            "params": params or {},
        }
        response = self.post(payload)
        if not isinstance(response, dict):
            raise AssertionError(f"RPC response is not an object: {response!r}")
        if response.get("jsonrpc") != "2.0":
            raise AssertionError(f"Invalid jsonrpc field: {response!r}")
        if response.get("id") != request_id:
            raise AssertionError(f"Unexpected id in response: {response!r}")
        if "error" in response:
            raise AssertionError(f"RPC {method} failed: {response['error']}")
        return response.get("result")

    def call_tool(self, name: str, arguments: dict[str, Any] | None = None) -> dict[str, Any]:
        result = self.rpc("tools/call", {"name": name, "arguments": arguments or {}})
        if not isinstance(result, dict):
            raise AssertionError(f"Tool result is not an object: {result!r}")
        if "structuredContent" not in result:
            raise AssertionError(f"Tool result misses structuredContent: {result!r}")
        structured = result["structuredContent"]
        if not isinstance(structured, dict):
            raise AssertionError(f"structuredContent is not an object: {structured!r}")
        for field in ("ok", "upstream_endpoint", "warnings"):
            if field not in structured:
                raise AssertionError(f"structuredContent misses {field}: {structured!r}")
        if structured.get("ok") and "normalized_data" not in structured:
            raise AssertionError(f"structuredContent misses normalized_data: {structured!r}")
        content = result.get("content")
        if not isinstance(content, list) or not content:
            raise AssertionError(f"Tool result misses content text: {result!r}")
        if content[0].get("type") != "text":
            raise AssertionError(f"Tool content is not text: {content!r}")
        return structured


class NativeMcpApiTest(unittest.TestCase):
    client: McpClient
    tools: set[str]
    resources: set[str]
    first_source_url: str | None
    first_full_source: dict[str, Any] | None
    first_book_url: str | None
    first_book_work_key: str | None
    first_book_name: str | None
    first_book_author: str | None

    @classmethod
    def setUpClass(cls) -> None:
        cls.client = McpClient(Config.endpoint, Config.timeout)
        cls.tools = set()
        cls.resources = set()
        cls.first_source_url = None
        cls.first_full_source = None
        cls.first_book_url = None
        cls.first_book_work_key = None
        cls.first_book_name = None
        cls.first_book_author = None

        initialize = cls.client.rpc(
            "initialize",
            {
                "protocolVersion": "2025-06-18",
                "capabilities": {},
                "clientInfo": {"name": "legado-mcp-unittest", "version": "0.1"},
            },
        )
        if initialize.get("serverInfo", {}).get("name") != "Legado Native MCP":
            raise AssertionError(f"Unexpected server info: {initialize!r}")

        tools_result = cls.client.rpc("tools/list")
        cls.tools = {item.get("name") for item in tools_result.get("tools", [])}

        resources_result = cls.client.rpc("resources/list")
        cls.resources = {item.get("uri") for item in resources_result.get("resources", [])}

        list_result = cls.client.call_tool("book_source_list")
        source_data = list_result.get("normalized_data")
        sources = source_data.get("sources") if isinstance(source_data, dict) else None
        if isinstance(sources, list) and sources:
            cls.first_source_url = sources[0].get("bookSourceUrl")

        books_result = cls.client.call_tool("bookshelf_book_list", {"limit": 1})
        books_data = books_result.get("normalized_data")
        books = books_data.get("books") if isinstance(books_data, dict) else None
        if isinstance(books, list) and books:
            cls.first_book_url = books[0].get("book_url")
            cls.first_book_work_key = books[0].get("work_key")
            cls.first_book_name = books[0].get("name")
            cls.first_book_author = books[0].get("author")

    def assert_tool_ok_shape(self, structured: dict[str, Any]) -> None:
        self.assertIsInstance(structured["ok"], bool)
        self.assertIsInstance(structured["warnings"], list)
        self.assertIsInstance(structured["upstream_endpoint"], str)

    def test_01_ping_method(self) -> None:
        result = self.client.rpc("ping")
        self.assertEqual(result, {})

    def test_02_tools_list(self) -> None:
        self.assertTrue(EXPECTED_TOOLS.issubset(self.tools), self.tools)

    def test_03_resources_list(self) -> None:
        self.assertTrue(EXPECTED_RESOURCES.issubset(self.resources), self.resources)

    def test_04_resources_templates_and_prompts(self) -> None:
        templates = self.client.rpc("resources/templates/list")
        prompts = self.client.rpc("prompts/list")
        self.assertEqual(templates.get("resourceTemplates"), [])
        self.assertEqual(prompts.get("prompts"), [])

    def test_05_resources_read(self) -> None:
        for uri in EXPECTED_RESOURCES:
            result = self.client.rpc("resources/read", {"uri": uri})
            contents = result.get("contents")
            self.assertIsInstance(contents, list)
            self.assertEqual(contents[0].get("uri"), uri)
            self.assertTrue(contents[0].get("text"))

    def test_06_batch_request(self) -> None:
        batch = [
            {"jsonrpc": "2.0", "id": 101, "method": "ping", "params": {}},
            {"jsonrpc": "2.0", "id": 102, "method": "tools/list", "params": {}},
        ]
        response = self.client.post(batch)
        self.assertIsInstance(response, list)
        self.assertEqual({item.get("id") for item in response}, {101, 102})
        self.assertFalse(any("error" in item for item in response))

    def test_07_tool_legado_ping(self) -> None:
        structured = self.client.call_tool("legado_ping")
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"])
        data = structured["normalized_data"]
        self.assertTrue(data.get("mcp_service_enabled"))
        self.assertEqual(data.get("endpoint"), "/mcp")

    def test_08_tool_api_summary(self) -> None:
        structured = self.client.call_tool("legado_get_api_summary")
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"])
        data = structured["normalized_data"]
        self.assertEqual(data.get("endpoint"), "/mcp")
        self.assertTrue(EXPECTED_TOOLS.issubset(set(data.get("tools", []))))

    def test_09_tool_book_source_list_is_compact(self) -> None:
        structured = self.client.call_tool("book_source_list")
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"], structured.get("warnings"))
        data = structured["normalized_data"]
        self.assertIsInstance(data, dict)
        self.assertIn("sources", data)
        self.assertIn("offset", data)
        self.assertIn("limit", data)
        self.assertIn("total", data)
        self.assertIn("has_more", data)
        sources = data["sources"]
        self.assertIsInstance(sources, list)
        self.assertGreater(len(sources), 0)
        for source in sources[:20]:
            self.assertTrue(set(source.keys()).issubset(BOOK_SOURCE_LIST_FIELDS), source)
            self.assertIn("bookSourceName", source)
            self.assertIn("bookSourceUrl", source)
        self.assertEqual(find_replacement_chars(sources), [])
        self.assertTrue(self.first_source_url)

        stats = self.client.call_tool("book_source_stats_get")
        self.assert_tool_ok_shape(stats)
        self.assertTrue(stats["ok"], stats.get("warnings"))
        stats_data = stats["normalized_data"]
        self.assertIn("total", stats_data)
        self.assertIn("enabled", stats_data)
        self.assertIn("type_counts", stats_data)
        self.assertIn("group_counts", stats_data)
        self.assertIn("capability_counts", stats_data)
        self.assertGreaterEqual(stats_data["total"], data["total"])

    def test_10_tool_book_source_get(self) -> None:
        if not self.first_source_url:
            self.skipTest("No book source available")
        structured = self.client.call_tool("book_source_get", {"url": self.first_source_url})
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"], structured.get("warnings"))
        source = structured["normalized_data"]
        self.assertEqual(source.get("bookSourceUrl"), self.first_source_url)
        self.assertEqual(find_replacement_chars(source), [])
        self.__class__.first_full_source = source

    def test_11_tool_book_source_save_roundtrip(self) -> None:
        if not Config.write:
            self.skipTest("--write was not set")
        if not self.first_source_url:
            self.skipTest("No book source available")
        if self.first_full_source is None:
            structured = self.client.call_tool("book_source_get", {"url": self.first_source_url})
            self.__class__.first_full_source = structured["normalized_data"]
        structured = self.client.call_tool("book_source_save", {"source": self.first_full_source})
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"], structured.get("warnings"))

    def test_12_tool_book_search(self) -> None:
        if Config.no_slow:
            self.skipTest("--no-slow was set")
        structured = self.client.call_tool(
            "book_search",
            {
                "key": Config.search_key,
                "min_results": 1,
                "timeout_seconds": Config.search_timeout_seconds,
                "limit": max(Config.expected_search_min, 10),
            },
        )
        self.assert_tool_ok_shape(structured)
        data = structured["normalized_data"]
        self.assertIn("books", data)
        self.assertIn("done", data)
        self.assertIn("source_count", data)
        self.assertIn("batch_count", data)
        self.assertIn("offset", data)
        self.assertIn("limit", data)
        self.assertIn("total", data)
        self.assertIn("has_more", data)
        self.assertTrue(data.get("compact"))
        self.assertIsInstance(data["books"], list)
        self.assertGreater(data["source_count"], 0)
        self.assertGreaterEqual(data["batch_count"], 1)
        self.assertIsNone(data.get("error_message"))
        if not Config.allow_empty_search:
            self.assertGreaterEqual(
                len(data["books"]),
                Config.expected_search_min,
                f"book_search returned too few books: {data!r}",
            )
            joined = "\n".join(
                " ".join(
                    str(book.get(field) or "")
                    for field in ("name", "author", "kind", "latest_chapter_title")
                )
                for book in data["books"]
            )
            self.assertNotIn("\ufffd", joined, "book_search result contains replacement characters")
            self.assertIn(
                Config.search_key,
                joined,
                f"book_search results do not contain search key {Config.search_key!r}",
            )

    def test_13_tool_book_source_debug(self) -> None:
        if Config.no_slow:
            self.skipTest("--no-slow was set")
        if not self.first_source_url:
            self.skipTest("No book source available")
        structured = self.client.call_tool(
            "book_source_debug",
            {
                "tag": self.first_source_url,
                "key": Config.debug_key,
                "mode": "search",
                "timeout_seconds": Config.debug_timeout_seconds,
            },
        )
        self.assert_tool_ok_shape(structured)
        data = structured["normalized_data"]
        self.assertIn("logs", data)
        self.assertIn("done", data)
        self.assertIsInstance(data["logs"], list)
        self.assertGreater(len(data["logs"]), 0, "book_source_debug returned no logs")
        transcript = "\n".join(str(line) for line in data["logs"])
        self.assertNotIn("\ufffd", transcript, "debug logs contain replacement characters")
        self.assertIn(Config.debug_key, transcript)
        self.assertIn("获取书籍列表", transcript)

    def test_14_tool_network_log_list_and_get(self) -> None:
        structured = self.client.call_tool("network_log_list", {"limit": 5})
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"], structured.get("warnings"))
        data = structured["normalized_data"]
        for field in (
            "logs",
            "offset",
            "limit",
            "total",
            "filtered_total",
            "has_more",
            "recording_enabled",
            "max_log_size",
            "body_preview_size",
        ):
            self.assertIn(field, data)
        self.assertLessEqual(data["limit"], 50)
        self.assertIsInstance(data["logs"], list)
        if not data["logs"]:
            missing = self.client.call_tool("network_log_get", {"id": -1})
            self.assert_tool_ok_shape(missing)
            self.assertFalse(missing["ok"])
            return
        item = data["logs"][0]
        self.assertIn("id", item)
        self.assertIn("url", item)
        self.assertNotIn("request_body", item)
        self.assertNotIn("response_body", item)
        detail = self.client.call_tool(
            "network_log_get",
            {
                "id": item["id"],
                "include_headers": False,
                "include_body": False,
            },
        )
        self.assert_tool_ok_shape(detail)
        self.assertTrue(detail["ok"], detail.get("warnings"))
        detail_data = detail["normalized_data"]
        self.assertEqual(detail_data.get("id"), item["id"])
        self.assertNotIn("request_headers", detail_data)
        self.assertNotIn("request_body", detail_data)

    def test_15_tool_debug_log_list_and_get(self) -> None:
        structured = self.client.call_tool("debug_log_list", {"limit": 5})
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"], structured.get("warnings"))
        data = structured["normalized_data"]
        for field in ("logs", "offset", "limit", "total", "filtered_total", "has_more", "max_log_size"):
            self.assertIn(field, data)
        self.assertLessEqual(data["limit"], 100)
        self.assertIsInstance(data["logs"], list)
        if not data["logs"]:
            missing = self.client.call_tool("debug_log_get", {"id": -1})
            self.assert_tool_ok_shape(missing)
            self.assertFalse(missing["ok"])
            return
        item = data["logs"][0]
        self.assertIn("id", item)
        self.assertIn("message_preview", item)
        detail = self.client.call_tool(
            "debug_log_get",
            {
                "id": item["id"],
                "include_stack": False,
            },
        )
        self.assert_tool_ok_shape(detail)
        self.assertTrue(detail["ok"], detail.get("warnings"))
        detail_data = detail["normalized_data"]
        self.assertEqual(detail_data.get("id"), item["id"])
        self.assertNotIn("stack", detail_data)

    def test_16_tool_ai_chat_conversation_list_and_get(self) -> None:
        structured = self.client.call_tool("ai_chat_conversation_list", {"limit": 5})
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"], structured.get("warnings"))
        data = structured["normalized_data"]
        for field in ("conversations", "offset", "limit", "total", "filtered_total", "has_more"):
            self.assertIn(field, data)
        self.assertLessEqual(data["limit"], 50)
        self.assertIsInstance(data["conversations"], list)
        if not data["conversations"]:
            missing = self.client.call_tool("ai_chat_conversation_get", {"id": "__missing__"})
            self.assert_tool_ok_shape(missing)
            self.assertFalse(missing["ok"])
            return
        item = data["conversations"][0]
        self.assertIn("id", item)
        self.assertIn("title", item)
        self.assertIn("message_count", item)
        detail = self.client.call_tool(
            "ai_chat_conversation_get",
            {
                "id": item["id"],
                "message_limit": 3,
                "text_char_limit": 2048,
            },
        )
        self.assert_tool_ok_shape(detail)
        self.assertTrue(detail["ok"], detail.get("warnings"))
        detail_data = detail["normalized_data"]
        self.assertIn("conversation", detail_data)
        self.assertIn("messages", detail_data)
        self.assertEqual(detail_data["conversation"].get("id"), item["id"])

    def test_17_tool_agent_memory_status_and_search(self) -> None:
        status = self.client.call_tool("agent_memory_status_get")
        self.assert_tool_ok_shape(status)
        self.assertTrue(status["ok"], status.get("warnings"))
        status_data = status["normalized_data"]
        self.assertIn("enabled", status_data)
        self.assertIsInstance(status_data["enabled"], bool)
        structured = self.client.call_tool(
            "agent_memory_search",
            {
                "scope_type": "book",
                "scope_key": "__mcp_test_missing__",
                "domain": "character_card",
                "limit": 5,
            },
        )
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"], structured.get("warnings"))
        data = structured["normalized_data"]
        for field in ("enabled", "memories", "offset", "limit", "total", "has_more"):
            self.assertIn(field, data)
        self.assertIsInstance(data["memories"], list)

    def test_18_tool_network_log_clear(self) -> None:
        if not Config.clear_network_log:
            self.skipTest("--clear-network-log was not set")
        structured = self.client.call_tool("network_log_clear")
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"], structured.get("warnings"))
        data = structured["normalized_data"]
        self.assertIn("cleared", data)
        self.assertEqual(data.get("remaining"), 0)

    def test_18_bookshelf_read_only_tools(self) -> None:
        groups = self.client.call_tool("bookshelf_group_list")
        self.assert_tool_ok_shape(groups)
        self.assertTrue(groups["ok"], groups.get("warnings"))
        self.assertIn("groups", groups["normalized_data"])

        stats = self.client.call_tool("bookshelf_stats_get")
        self.assert_tool_ok_shape(stats)
        self.assertTrue(stats["ok"], stats.get("warnings"))
        stats_data = stats["normalized_data"]
        self.assertIn("total", stats_data)
        self.assertIn("type_counts", stats_data)
        self.assertIn("groups", stats_data)

        books = self.client.call_tool("bookshelf_book_list", {"limit": 3})
        self.assert_tool_ok_shape(books)
        self.assertTrue(books["ok"], books.get("warnings"))
        books_data = books["normalized_data"]
        self.assertIn("books", books_data)
        self.assertIn("total", books_data)

        current = self.client.call_tool("bookshelf_current_book_get")
        self.assert_tool_ok_shape(current)

        rules = self.client.call_tool("bookshelf_replace_rule_list", {"limit": 3})
        self.assert_tool_ok_shape(rules)
        self.assertTrue(rules["ok"], rules.get("warnings"))
        self.assertIn("rules", rules["normalized_data"])
        self.assertTrue(rules["normalized_data"].get("compact"))

        bookmarks = self.client.call_tool("bookshelf_bookmark_list", {"limit": 3})
        self.assert_tool_ok_shape(bookmarks)
        self.assertTrue(bookmarks["ok"], bookmarks.get("warnings"))
        self.assertIn("bookmarks", bookmarks["normalized_data"])

        read_records = self.client.call_tool("bookshelf_read_record_list", {"limit": 3})
        self.assert_tool_ok_shape(read_records)
        self.assertTrue(read_records["ok"], read_records.get("warnings"))
        self.assertIn("records", read_records["normalized_data"])

        if not self.first_book_url:
            self.skipTest("No bookshelf book available")

        book = self.client.call_tool("bookshelf_book_get", {"book_url": self.first_book_url})
        self.assert_tool_ok_shape(book)
        self.assertTrue(book["ok"], book.get("warnings"))
        self.assertEqual(book["normalized_data"].get("book_url"), self.first_book_url)
        if self.first_book_work_key:
            book_by_work = self.client.call_tool("bookshelf_book_get", {"work_key": self.first_book_work_key})
            self.assert_tool_ok_shape(book_by_work)
            self.assertTrue(book_by_work["ok"], book_by_work.get("warnings"))
            self.assertEqual(book_by_work["normalized_data"].get("work_key"), self.first_book_work_key)

        book_identity = {"work_key": self.first_book_work_key} if self.first_book_work_key else {"book_url": self.first_book_url}
        chapters = self.client.call_tool(
            "bookshelf_chapter_list",
            {**book_identity, "start": 0, "end": 5, "include_cache_status": True},
        )
        self.assert_tool_ok_shape(chapters)
        self.assertTrue(chapters["ok"], chapters.get("warnings"))
        chapter_data = chapters["normalized_data"]
        self.assertIn("chapters", chapter_data)
        self.assertIn("total", chapter_data)
        self.assertIn("limit", chapter_data)
        self.assertIn("has_more", chapter_data)
        self.assertTrue(chapter_data.get("compact"))
        for chapter in chapter_data["chapters"]:
            self.assertIn("index", chapter)
            self.assertIn("title", chapter)
            self.assertNotIn("url", chapter)

        detailed_chapters = self.client.call_tool(
            "bookshelf_chapter_list",
            {**book_identity, "start": 0, "limit": 1, "include_detail": True},
        )
        self.assert_tool_ok_shape(detailed_chapters)
        self.assertTrue(detailed_chapters["ok"], detailed_chapters.get("warnings"))
        detailed_data = detailed_chapters["normalized_data"]
        self.assertFalse(detailed_data.get("compact"))
        if detailed_data.get("chapters"):
            self.assertIn("url", detailed_data["chapters"][0])

        cache = self.client.call_tool(
            "bookshelf_cache_status_get",
            {**book_identity, "start": 0, "end": 5},
        )
        self.assert_tool_ok_shape(cache)
        self.assertTrue(cache["ok"], cache.get("warnings"))
        self.assertIn("chapters", cache["normalized_data"])

        sources = self.client.call_tool("bookshelf_book_sources_get", {"book_url": self.first_book_url})
        self.assert_tool_ok_shape(sources)
        self.assertTrue(sources["ok"], sources.get("warnings"))
        self.assertIn("sources", sources["normalized_data"])

        preview = self.client.call_tool("bookshelf_change_source_preview", {"book_url": self.first_book_url})
        self.assert_tool_ok_shape(preview)
        self.assertTrue(preview["ok"], preview.get("warnings"))
        self.assertIn("sources", preview["normalized_data"])

        profile = self.client.call_tool("bookshelf_character_profile_get", {"book_url": self.first_book_url})
        self.assert_tool_ok_shape(profile)
        self.assertIn("work_key", profile["normalized_data"])

        characters = self.client.call_tool("bookshelf_character_list", {"book_url": self.first_book_url})
        self.assert_tool_ok_shape(characters)
        self.assertTrue(characters["ok"], characters.get("warnings"))
        self.assertIn("characters", characters["normalized_data"])

        book_bookmarks = self.client.call_tool("bookshelf_bookmark_list", {"book_url": self.first_book_url, "limit": 3})
        self.assert_tool_ok_shape(book_bookmarks)
        self.assertTrue(book_bookmarks["ok"], book_bookmarks.get("warnings"))
        self.assertIn("bookmarks", book_bookmarks["normalized_data"])

        if chapter_data.get("chapters"):
            first_chapter = chapter_data["chapters"][0]
            chapter_index = first_chapter["index"]
            content = self.client.call_tool(
                "bookshelf_chapter_content_get",
                {"book_url": self.first_book_url, "chapter_index": chapter_index, "char_limit": 1024},
            )
            self.assert_tool_ok_shape(content)
            self.assertIn("has_content", content["normalized_data"])

            window = self.client.call_tool(
                "bookshelf_text_window_get",
                {
                    "book_url": self.first_book_url,
                    "start_chapter_index": chapter_index,
                    "chapter_count": 1,
                    "char_limit": 1024,
                },
            )
            self.assert_tool_ok_shape(window)
            self.assertIn("text", window["normalized_data"])

    def test_19_bookshelf_write_tools(self) -> None:
        if not Config.write:
            self.skipTest("--write was not set")
        if not self.first_book_url:
            self.skipTest("No bookshelf book available")

        stamp = int(time.time())
        character = self.client.call_tool(
            "bookshelf_character_upsert",
            {
                "book_url": self.first_book_url,
                "character": {
                    "name": f"MCP测试角色{stamp}",
                    "gender": "unknown",
                    "role_tag": "other",
                    "intro": "MCP接口外部验证临时角色",
                    "source": "ai",
                    "confidence": 0.1,
                    "enabled": False,
                },
            },
        )
        self.assert_tool_ok_shape(character)
        self.assertTrue(character["ok"], character.get("warnings"))
        character_id = character["normalized_data"]["character"]["id"]

        character_enabled = self.client.call_tool(
            "bookshelf_character_set_enabled",
            {"ids": [character_id], "enabled": True},
        )
        self.assert_tool_ok_shape(character_enabled)
        self.assertTrue(character_enabled["ok"], character_enabled.get("warnings"))
        self.assertEqual(character_enabled["normalized_data"].get("updated_count"), 1)

        character_delete = self.client.call_tool(
            "bookshelf_character_delete",
            {"ids": [character_id]},
        )
        self.assert_tool_ok_shape(character_delete)
        self.assertTrue(character_delete["ok"], character_delete.get("warnings"))
        self.assertEqual(character_rollback["normalized_data"].get("deleted_count"), 1)

        rule = self.client.call_tool(
            "bookshelf_replace_rule_draft_upsert",
            {
                "book_url": self.first_book_url,
                "rule": {
                    "name": f"MCP测试替换规则{stamp}",
                    "pattern": f"MCP_TEST_{stamp}",
                    "replacement": "",
                    "group": "AI草稿",
                    "enabled": False,
                    "is_regex": False,
                },
            },
        )
        self.assert_tool_ok_shape(rule)
        self.assertTrue(rule["ok"], rule.get("warnings"))
        ids = rule["normalized_data"]["ids"]
        self.assertTrue(ids)

        rule_apply = self.client.call_tool(
            "bookshelf_replace_rule_draft_apply",
            {"ids": ids, "enabled": True},
        )
        self.assert_tool_ok_shape(rule_apply)
        self.assertTrue(rule_apply["ok"], rule_apply.get("warnings"))
        self.assertEqual(rule_apply["normalized_data"].get("updated_count"), len(ids))

        rollback = self.client.call_tool("bookshelf_replace_rule_rollback", {"ids": ids})
        self.assert_tool_ok_shape(rollback)
        self.assertTrue(rollback["ok"], rollback.get("warnings"))
        self.assertEqual(rollback["normalized_data"].get("deleted_count"), len(ids))

        bookmark_time = int(time.time() * 1000)
        bookmark = self.client.call_tool(
            "bookshelf_bookmark_upsert",
            {
                "book_url": self.first_book_url,
                "bookmark": {
                    "time": bookmark_time,
                    "chapter_index": 0,
                    "chapter_pos": 0,
                    "chapter_name": "MCP测试章节",
                    "book_text": "MCP测试书签正文",
                    "content": "MCP接口外部验证临时书签",
                },
            },
        )
        self.assert_tool_ok_shape(bookmark)
        self.assertTrue(bookmark["ok"], bookmark.get("warnings"))
        self.assertEqual(bookmark["normalized_data"]["bookmark"]["time"], bookmark_time)

        bookmark_detail = self.client.call_tool("bookshelf_bookmark_get", {"time": bookmark_time})
        self.assert_tool_ok_shape(bookmark_detail)
        self.assertTrue(bookmark_detail["ok"], bookmark_detail.get("warnings"))
        self.assertEqual(bookmark_detail["normalized_data"].get("time"), bookmark_time)

        bookmark_delete = self.client.call_tool("bookshelf_bookmark_delete", {"times": [bookmark_time]})
        self.assert_tool_ok_shape(bookmark_delete)
        self.assertTrue(bookmark_delete["ok"], bookmark_delete.get("warnings"))
        self.assertEqual(bookmark_delete["normalized_data"].get("deleted"), 1)

        record_book_name = f"MCP测试阅读记录{stamp}"
        read_record = self.client.call_tool(
            "bookshelf_read_record_upsert",
            {
                "record": {
                    "book_name": record_book_name,
                    "read_time": 1234,
                    "last_read": bookmark_time,
                }
            },
        )
        self.assert_tool_ok_shape(read_record)
        self.assertTrue(read_record["ok"], read_record.get("warnings"))
        self.assertEqual(read_record["normalized_data"]["record"]["book_name"], record_book_name)

        read_record_detail = self.client.call_tool("bookshelf_read_record_get", {"book_name": record_book_name})
        self.assert_tool_ok_shape(read_record_detail)
        self.assertTrue(read_record_detail["ok"], read_record_detail.get("warnings"))
        self.assertEqual(read_record_detail["normalized_data"].get("book_name"), record_book_name)

        read_record_delete = self.client.call_tool("bookshelf_read_record_delete", {"book_name": record_book_name})
        self.assert_tool_ok_shape(read_record_delete)
        self.assertTrue(read_record_delete["ok"], read_record_delete.get("warnings"))
        self.assertGreaterEqual(read_record_delete["normalized_data"].get("deleted"), 1)

    def test_20_settings_read_only_tools(self) -> None:
        stats = self.client.call_tool("settings_rule_stats_get")
        self.assert_tool_ok_shape(stats)
        self.assertTrue(stats["ok"], stats.get("warnings"))
        stats_data = stats["normalized_data"]
        self.assertIn("txt_toc_rules", stats_data)
        self.assertIn("replace_rules", stats_data)
        self.assertIn("dict_rules", stats_data)

        toc = self.client.call_tool("settings_txt_toc_rule_list", {"limit": 3})
        self.assert_tool_ok_shape(toc)
        self.assertTrue(toc["ok"], toc.get("warnings"))
        self.assertIn("rules", toc["normalized_data"])
        self.assertTrue(toc["normalized_data"].get("compact"))

        toc_rules = toc["normalized_data"]["rules"]
        if toc_rules:
            detail = self.client.call_tool("settings_txt_toc_rule_get", {"id": toc_rules[0]["id"]})
            self.assert_tool_ok_shape(detail)
            self.assertTrue(detail["ok"], detail.get("warnings"))
            self.assertEqual(detail["normalized_data"].get("id"), toc_rules[0]["id"])

        replace = self.client.call_tool("settings_replace_rule_list", {"limit": 3})
        self.assert_tool_ok_shape(replace)
        self.assertTrue(replace["ok"], replace.get("warnings"))
        self.assertIn("rules", replace["normalized_data"])
        self.assertTrue(replace["normalized_data"].get("compact"))

        replace_rules = replace["normalized_data"]["rules"]
        if replace_rules:
            detail = self.client.call_tool("settings_replace_rule_get", {"id": replace_rules[0]["id"]})
            self.assert_tool_ok_shape(detail)
            self.assertTrue(detail["ok"], detail.get("warnings"))
            self.assertEqual(detail["normalized_data"].get("id"), replace_rules[0]["id"])

        dict_rules = self.client.call_tool("settings_dict_rule_list", {"limit": 3})
        self.assert_tool_ok_shape(dict_rules)
        self.assertTrue(dict_rules["ok"], dict_rules.get("warnings"))
        self.assertIn("rules", dict_rules["normalized_data"])
        self.assertTrue(dict_rules["normalized_data"].get("compact"))

        rules = dict_rules["normalized_data"]["rules"]
        if rules:
            detail = self.client.call_tool("settings_dict_rule_get", {"name": rules[0]["name"]})
            self.assert_tool_ok_shape(detail)
            self.assertTrue(detail["ok"], detail.get("warnings"))
            self.assertEqual(detail["normalized_data"].get("name"), rules[0]["name"])

    def test_21_settings_write_tools(self) -> None:
        if not Config.write:
            self.skipTest("--write was not set")

        stamp = int(time.time())

        toc = self.client.call_tool(
            "settings_txt_toc_rule_upsert",
            {
                "rule": {
                    "name": f"MCP测试目录规则{stamp}",
                    "rule": r"^MCP测试目录(\d+)$",
                    "replacement": "第$1章",
                    "example": "MCP测试目录1",
                    "enabled": False,
                }
            },
        )
        self.assert_tool_ok_shape(toc)
        self.assertTrue(toc["ok"], toc.get("warnings"))
        toc_id = toc["normalized_data"]["rule"]["id"]

        toc_enable = self.client.call_tool(
            "settings_txt_toc_rule_set_enabled",
            {"ids": [toc_id], "enabled": True},
        )
        self.assert_tool_ok_shape(toc_enable)
        self.assertTrue(toc_enable["ok"], toc_enable.get("warnings"))
        self.assertEqual(toc_enable["normalized_data"].get("updated"), 1)

        toc_delete = self.client.call_tool("settings_txt_toc_rule_delete", {"ids": [toc_id]})
        self.assert_tool_ok_shape(toc_delete)
        self.assertTrue(toc_delete["ok"], toc_delete.get("warnings"))
        self.assertEqual(toc_delete["normalized_data"].get("deleted"), 1)

        replace = self.client.call_tool(
            "settings_replace_rule_upsert",
            {
                "rule": {
                    "name": f"MCP测试净化规则{stamp}",
                    "group": "MCP测试",
                    "pattern": f"MCP_REPLACE_{stamp}",
                    "replacement": "",
                    "enabled": False,
                    "is_regex": False,
                }
            },
        )
        self.assert_tool_ok_shape(replace)
        self.assertTrue(replace["ok"], replace.get("warnings"))
        replace_id = replace["normalized_data"]["rule"]["id"]

        replace_enable = self.client.call_tool(
            "settings_replace_rule_set_enabled",
            {"ids": [replace_id], "enabled": True},
        )
        self.assert_tool_ok_shape(replace_enable)
        self.assertTrue(replace_enable["ok"], replace_enable.get("warnings"))
        self.assertEqual(replace_enable["normalized_data"].get("updated"), 1)

        replace_delete = self.client.call_tool("settings_replace_rule_delete", {"ids": [replace_id]})
        self.assert_tool_ok_shape(replace_delete)
        self.assertTrue(replace_delete["ok"], replace_delete.get("warnings"))
        self.assertEqual(replace_delete["normalized_data"].get("deleted"), 1)

        dict_rule_name = f"MCP测试字典规则{stamp}"
        dict_rule = self.client.call_tool(
            "settings_dict_rule_upsert",
            {
                "rule": {
                    "name": dict_rule_name,
                    "url_rule": "https://example.invalid/dict?word={{key}}",
                    "show_rule": "",
                    "enabled": False,
                }
            },
        )
        self.assert_tool_ok_shape(dict_rule)
        self.assertTrue(dict_rule["ok"], dict_rule.get("warnings"))
        self.assertEqual(dict_rule["normalized_data"]["rule"]["name"], dict_rule_name)

        dict_enable = self.client.call_tool(
            "settings_dict_rule_set_enabled",
            {"names": [dict_rule_name], "enabled": True},
        )
        self.assert_tool_ok_shape(dict_enable)
        self.assertTrue(dict_enable["ok"], dict_enable.get("warnings"))
        self.assertEqual(dict_enable["normalized_data"].get("updated"), 1)

        dict_delete = self.client.call_tool("settings_dict_rule_delete", {"names": [dict_rule_name]})
        self.assert_tool_ok_shape(dict_delete)
        self.assertTrue(dict_delete["ok"], dict_delete.get("warnings"))
        self.assertEqual(dict_delete["normalized_data"].get("deleted"), 1)

    def test_22_unknown_method_error(self) -> None:
        request_id = 404
        response = self.client.post(
            {"jsonrpc": "2.0", "id": request_id, "method": "unknown/method", "params": {}}
        )
        self.assertEqual(response.get("id"), request_id)
        self.assertEqual(response.get("error", {}).get("code"), -32601)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run native Legado MCP HTTP API tests.")
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT, help="MCP endpoint URL")
    parser.add_argument("--timeout", type=float, default=45.0, help="HTTP timeout in seconds")
    parser.add_argument("--search-key", default="斗破苍穹", help="Keyword for book_search")
    parser.add_argument("--debug-key", default="斗破苍穹", help="Keyword for book_source_debug")
    parser.add_argument("--debug-timeout-seconds", type=float, default=12.0)
    parser.add_argument("--search-timeout-seconds", type=float, default=20.0)
    parser.add_argument("--expected-search-min", type=int, default=1)
    parser.add_argument(
        "--allow-empty-search",
        action="store_true",
        help="Do not fail when book_search returns zero books",
    )
    parser.add_argument(
        "--write",
        action="store_true",
        help="Run book_source_save by reading the first source and saving it back unchanged",
    )
    parser.add_argument(
        "--clear-network-log",
        action="store_true",
        help="Run network_log_clear. This clears the app's current in-memory network log window.",
    )
    parser.add_argument("--no-slow", action="store_true", help="Skip book_search and book_source_debug")
    parser.add_argument("unittest_args", nargs="*", help="Extra unittest arguments, for example -v")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    Config.endpoint = args.endpoint
    Config.timeout = args.timeout
    Config.search_key = args.search_key
    Config.debug_key = args.debug_key
    Config.debug_timeout_seconds = args.debug_timeout_seconds
    Config.search_timeout_seconds = args.search_timeout_seconds
    Config.expected_search_min = args.expected_search_min
    Config.allow_empty_search = args.allow_empty_search
    Config.write = args.write
    Config.clear_network_log = args.clear_network_log
    Config.no_slow = args.no_slow

    unittest_argv = [sys.argv[0], *args.unittest_args]
    if len(unittest_argv) == 1:
        unittest_argv.append("-v")
    return 0 if unittest.main(argv=unittest_argv, exit=False).result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
