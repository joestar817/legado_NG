#!/usr/bin/env python3
"""Read-only smoke checks for discovery/RSS MCP. Never creates, edits or deletes device data."""
import argparse
import json
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--endpoint", required=True)
    args = parser.parse_args()
    count = 0

    def rpc(method, params=None):
        nonlocal count
        count += 1
        request = Request(args.endpoint, json.dumps({"jsonrpc": "2.0", "id": count,
                          "method": method, "params": params or {}}).encode(),
                          {"Content-Type": "application/json"})
        with urlopen(request, timeout=20) as response:
            result = json.load(response)
        assert "error" not in result, (method, result)
        return result["result"]

    def call(name, arguments, ok=True):
        result = rpc("tools/call", {"name": name, "arguments": arguments})
        assert result["isError"] is not ok, (name, result)
        data = result["structuredContent"]
        assert data["ok"] is ok, (name, data)
        return data.get("normalized_data")

    names = {tool["name"] for tool in rpc("tools/list")["tools"]}
    required = {"explore_source_list", "explore_source_set_enabled", "explore_books", "explore_book_info",
                "book_source_explore_kinds_get", "rss_source_list", "rss_source_stats_get", "rss_source_get",
                "rss_source_save", "rss_source_import", "rss_source_export", "rss_source_delete",
                "rss_source_set_enabled", "rss_source_categories", "rss_source_debug", "rss_articles_fetch",
                "rss_article_list", "rss_article_get", "rss_article_content_get", "rss_star_list",
                "rss_star_get", "rss_star_save", "rss_star_delete", "rss_read_record_list", "rss_read_record_get",
                "rss_read_record_save", "rss_read_record_delete", "rss_rule_subscription_list",
                "rss_rule_subscription_get", "rss_rule_subscription_save", "rss_rule_subscription_delete",
                "rss_rule_subscription_refresh"}
    assert required <= names, required - names
    resource = rpc("resources/read", {"uri": "legado://schema/discovery-subscription"})
    schema = json.loads(resource["contents"][0]["text"])
    assert {tool["name"] for tool in schema["tools"]} == required - {"book_source_explore_kinds_get"}
    sources = call("rss_source_list", {"limit": 1})
    stats = call("rss_source_stats_get", {})
    assert sources["total"] == stats["total"]
    if sources["sources"]:
        url = sources["sources"][0]["sourceUrl"]
        source = call("rss_source_get", {"source_url": url})
        exported = call("rss_source_export", {"urls": [url]})
        assert exported["sources"] == [source]
    for name in ("explore_source_list", "rss_star_list", "rss_read_record_list", "rss_rule_subscription_list"):
        call(name, {"limit": 1})
        call(name, {"limit": 0}, ok=False)
        call(name, {"offset": 1.5}, ok=False)
        call(name, {"unexpected": True}, ok=False)
    call("rss_source_list", {"enabled": "false"}, ok=False)
    call("rss_source_get", {"source_url": ""}, ok=False)
    call("rss_source_export", {"urls": []}, ok=False)
    print(json.dumps({"rpc_checks": count, "registered_tools": len(names), "result": "passed"}))


if __name__ == "__main__":
    main()
