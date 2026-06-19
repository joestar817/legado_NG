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
    "book_source_get",
    "book_source_save",
    "book_source_debug",
    "book_search",
    "network_log_list",
    "network_log_get",
    "network_log_clear",
}
EXPECTED_RESOURCES = {
    "legado://api/mcp",
    "legado://schema/book-source",
}
BOOK_SOURCE_LIST_FIELDS = {
    "bookSourceComment",
    "bookSourceGroup",
    "bookSourceName",
    "bookSourceType",
    "bookSourceUrl",
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
        for field in ("ok", "upstream_endpoint", "normalized_data", "warnings"):
            if field not in structured:
                raise AssertionError(f"structuredContent misses {field}: {structured!r}")
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

    @classmethod
    def setUpClass(cls) -> None:
        cls.client = McpClient(Config.endpoint, Config.timeout)
        cls.tools = set()
        cls.resources = set()
        cls.first_source_url = None
        cls.first_full_source = None

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
        sources = list_result.get("normalized_data")
        if isinstance(sources, list) and sources:
            cls.first_source_url = sources[0].get("bookSourceUrl")

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
        sources = structured["normalized_data"]
        self.assertIsInstance(sources, list)
        self.assertGreater(len(sources), 0)
        for source in sources[:20]:
            self.assertEqual(set(source.keys()), BOOK_SOURCE_LIST_FIELDS)
        self.assertEqual(find_replacement_chars(sources), [])
        self.assertTrue(self.first_source_url)

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
            },
        )
        self.assert_tool_ok_shape(structured)
        data = structured["normalized_data"]
        self.assertIn("books", data)
        self.assertIn("done", data)
        self.assertIn("source_count", data)
        self.assertIn("batch_count", data)
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
                    for field in ("name", "author", "kind", "intro")
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

    def test_15_tool_network_log_clear(self) -> None:
        if not Config.clear_network_log:
            self.skipTest("--clear-network-log was not set")
        structured = self.client.call_tool("network_log_clear")
        self.assert_tool_ok_shape(structured)
        self.assertTrue(structured["ok"], structured.get("warnings"))
        data = structured["normalized_data"]
        self.assertIn("cleared", data)
        self.assertEqual(data.get("remaining"), 0)

    def test_16_unknown_method_error(self) -> None:
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
