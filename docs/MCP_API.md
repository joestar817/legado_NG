# 原生 MCP 接口手动测试说明

本文档说明阅读 NG 原生 MCP P0 接口的手动测试方式。当前 MCP 是开发调试功能，未加入 token、配对码、只读/写入分级和 SSE 流式会话。

## 启动方式

在 App 中打开：

1. `我的`
2. `服务管理`
3. 设置 `MCP 端口`，默认 `1124`
4. 开启 `MCP 服务`

开启后服务地址格式：

```text
http://设备IP:MCP端口/mcp
```

示例：

```text
http://192.168.11.13:1124/mcp
```

说明：

- MCP 服务和 Web 服务已经分开，MCP 不依赖 Web 服务开启。
- `Web 端口` 默认 `1122`，`MCP 端口` 默认 `1124`。
- 当前也接受 `POST /`，但手动测试和客户端配置统一使用 `POST /mcp`。
- 当前只实现 JSON 响应，不实现 SSE 流式响应；`GET /mcp` 会返回不支持。
- 如果关闭 `MCP 服务`，接口返回 disabled。

## 自动测试脚本

仓库内提供了一个标准库 Python 测试脚本，用于覆盖 MCP P0 的 JSON-RPC 方法、资源接口和所有工具：

```powershell
python scripts\test_mcp_api.py --endpoint http://192.168.11.13:1124/mcp
```

默认会执行：

- `initialize`、`ping`
- `tools/list`
- `resources/list`、`resources/templates/list`、`resources/read`
- `prompts/list`
- JSON-RPC batch request
- `legado_ping`
- `legado_get_api_summary`
- `book_source_list`
- `book_source_get`
- `book_search`
- `book_source_debug`
- 未知 method 错误返回

其中 `book_search` 默认会要求至少返回 1 条结果，并校验搜索结果中包含搜索关键词；`book_source_debug` 会校验调试日志包含搜索关键词和“获取书籍列表”。这能覆盖中文 POST body 编码、搜索结果为空、调试未真正进入搜索解析等问题。

网络日志测试默认只验证 `network_log_list` 和 `network_log_get` 的结构；`network_log_clear` 会清空 App 当前内存日志窗口，只有显式传 `--clear-network-log` 才会执行。

可选参数：

```powershell
python scripts\test_mcp_api.py --endpoint http://192.168.11.13:1124/mcp --no-slow
python scripts\test_mcp_api.py --endpoint http://192.168.11.13:1124/mcp --search-key 斗破苍穹 --debug-key 斗破苍穹
python scripts\test_mcp_api.py --endpoint http://192.168.11.13:1124/mcp --allow-empty-search
python scripts\test_mcp_api.py --endpoint http://192.168.11.13:1124/mcp --write
python scripts\test_mcp_api.py --endpoint http://192.168.11.13:1124/mcp --clear-network-log
```

说明：

- 默认不执行 `book_source_save`，避免测试脚本覆盖真实书源数据。
- `--write` 会先取第一个完整书源，再原样保存回去，用于显式验证写接口。
- `--no-slow` 会跳过 `book_search` 和 `book_source_debug`。
- `--allow-empty-search` 会关闭搜索非空断言，只保留协议和字段结构验证。
- 也可以通过环境变量 `LEGADO_MCP_ENDPOINT` 指定默认 endpoint。
- `--clear-network-log` 会额外调用 `network_log_clear`，清空当前内存网络日志窗口。

注意：MCP HTTP 服务必须按 UTF-8 解析 POST body，否则中文关键词会变成 `�`，表现为搜索 URL 中出现 `%EF%BF%BD` 并返回空结果。

## 基础请求格式

所有 MCP 请求都是 JSON-RPC 2.0：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

Windows PowerShell 建议使用 `curl.exe`，避免 PowerShell 自带 `curl` 别名行为差异。

## 初始化

PowerShell：

```powershell
curl.exe -s http://192.168.11.13:1124/mcp `
  -H "Content-Type: application/json" `
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"manual-test","version":"0.1"}}}'
```

PowerShell 里不要用 bash 风格的 `\"` 转义 JSON 双引号；推荐像上面一样用单引号包住整段 JSON。

WSL / Linux / macOS：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"manual-test","version":"0.1"}}}'
```

期望返回中包含：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-06-18",
    "serverInfo": {
      "name": "Legado Native MCP",
      "version": "0.1.0"
    }
  }
}
```

## Ping

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"ping","params":{}}'
```

## 列出工具

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}'
```

当前 P0 工具：

- `legado_ping`
- `legado_get_api_summary`
- `book_source_list`
- `book_source_get`
- `book_source_save`
- `book_source_debug`
- `book_search`
- `network_log_list`
- `network_log_get`
- `network_log_clear`

## 调用工具

MCP 工具调用统一使用 `tools/call`：

```json
{
  "jsonrpc": "2.0",
  "id": 10,
  "method": "tools/call",
  "params": {
    "name": "工具名",
    "arguments": {}
  }
}
```

工具返回结构：

```json
{
  "content": [
    {
      "type": "text",
      "text": "{...}"
    }
  ],
  "structuredContent": {
    "ok": true,
    "upstream_endpoint": "native://mcp",
    "normalized_data": {},
    "raw_upstream": null,
    "warnings": [],
    "session_id": null
  },
  "isError": false
}
```

### legado_ping

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"legado_ping","arguments":{}}}'
```

### legado_get_api_summary

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"legado_get_api_summary","arguments":{}}}'
```

### book_source_list

列出全部书源的基础字段。为了避免几百或几千个书源撑爆上下文，这个接口不会返回完整规则字段；需要完整书源内容时，再用 `book_source_get` 按 `bookSourceUrl` 获取单个书源。

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"book_source_list","arguments":{}}}'
```

`normalized_data` 中每个书源只包含：

```json
{
  "bookSourceComment": "",
  "bookSourceGroup": "NovelHub",
  "bookSourceName": "NovelHub · 聚合",
  "bookSourceType": 0,
  "bookSourceUrl": "https://novel-joestar.ccwu.cc#default"
}
```

### book_source_get

按 `bookSourceUrl` 获取单个书源：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"book_source_get","arguments":{"url":"https://novel-joestar.ccwu.cc#default"}}}'
```

### book_source_save

保存或覆盖单个书源。最小有效字段是 `bookSourceUrl` 和 `bookSourceName`：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"name":"book_source_save","arguments":{"source":{"bookSourceUrl":"https://novel-joestar.ccwu.cc#default","bookSourceName":"测试书源","enabled":true,"enabledExplore":false}}}}'
```

注意：这是写操作，当前 P0 不区分只读/写入权限。

### book_source_debug

调试书源。`tag` 必须是 `BookSource.bookSourceUrl`，不是书源名称。

搜索模式：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":15,"method":"tools/call","params":{"name":"book_source_debug","arguments":{"tag":"https://novel-joestar.ccwu.cc#default","key":"斗破苍穹","mode":"search","timeout_seconds":30}}}'
```

详情页模式：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":16,"method":"tools/call","params":{"name":"book_source_debug","arguments":{"tag":"https://novel-joestar.ccwu.cc#default","key":"https://example.com/book/1","mode":"detail","timeout_seconds":30}}}'
```

发现页模式：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":17,"method":"tools/call","params":{"name":"book_source_debug","arguments":{"tag":"https://novel-joestar.ccwu.cc#default","key":"https://example.com/list","mode":"explore","timeout_seconds":30}}}'
```

目录页模式：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":18,"method":"tools/call","params":{"name":"book_source_debug","arguments":{"tag":"https://novel-joestar.ccwu.cc#default","key":"https://example.com/toc","mode":"toc","timeout_seconds":30}}}'
```

正文页模式：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":19,"method":"tools/call","params":{"name":"book_source_debug","arguments":{"tag":"https://novel-joestar.ccwu.cc#default","key":"https://example.com/chapter/1","mode":"content","timeout_seconds":30}}}'
```

返回的 `normalized_data.logs` 是调试日志数组。

注意：

- 当前调试复用 Legado 现有全局 `Debug.callback`。
- MCP 侧用互斥锁限制同一时间只跑一个书源调试。
- 超时后 `ok=false`，`warnings` 会包含超时说明。

### book_search

跨启用书源搜索图书。

默认行为：

- `scope` 为空，表示搜索全部已启用书源，不继承 App 搜索页当前分组或单书源范围。
- `wait_for_finish=false`，拿到第一批满足 `min_results` 的结果后就返回，避免被慢书源拖到整体超时。
- 返回中 `done=false` 表示这是部分结果；需要等待全部书源完成时传 `wait_for_finish=true`。
- `source_count` 是本次纳入搜索的书源数量，`batch_count` 是已经收到的结果批次数。

PowerShell：

```powershell
curl.exe -s http://192.168.11.13:1124/mcp -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":20,"method":"tools/call","params":{"name":"book_search","arguments":{"key":"斗破苍穹","timeout_seconds":30}}}'
```

WSL / Linux / macOS：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":20,"method":"tools/call","params":{"name":"book_search","arguments":{"key":"斗破苍穹","timeout_seconds":30}}}'
```

等待全部书源完成：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":21,"method":"tools/call","params":{"name":"book_search","arguments":{"key":"斗破苍穹","wait_for_finish":true,"timeout_seconds":60}}}'
```

指定搜索范围：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":22,"method":"tools/call","params":{"name":"book_search","arguments":{"key":"斗破苍穹","scope":"玄幻","timeout_seconds":30}}}'
```

返回的 `normalized_data.books` 是搜索结果数组。

### network_log_list

分页获取运行时网络请求日志摘要。这个接口不会返回 headers/body，只返回足够定位单条请求的摘要字段，避免一次把网络日志撑爆 MCP 上下文。

建议调试流程：

1. 需要干净现场时先调用 `network_log_clear`。
2. 在 App 内复现问题，或通过 MCP 调用 `book_source_debug` / `book_search` 触发请求。
3. 调用 `network_log_list` 按摘要找目标请求。
4. 调用 `network_log_get` 按 `id` 获取单条详情。

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":23,"method":"tools/call","params":{"name":"network_log_list","arguments":{"limit":20}}}'
```

支持参数：

- `offset`：分页偏移，默认 `0`。
- `limit`：返回条数，默认 `20`，最大 `50`。
- `type`：可选，`OkHttp`、`JS`、`WebView`。
- `keyword`：可选，匹配 URL、source、method、status 或 error 首行。
- `only_errors`：可选，只看异常或非 2xx/3xx 状态。

`normalized_data.logs` 每条只包含摘要，例如：

```json
{
  "id": 12,
  "time": 1781840000000,
  "time_text": "12:00:00.000",
  "type": "OkHttp",
  "method": "GET",
  "url": "https://example.com/api",
  "status_code": 200,
  "took_ms": 123,
  "source": "NovelHub / 斗破苍穹 / 第一章",
  "ok_status": true,
  "has_request_headers": true,
  "has_request_body": false,
  "has_response_headers": true,
  "has_response_body": true,
  "has_error": false,
  "error_preview": null
}
```

`normalized_data` 还会返回：

- `total`：当前内存窗口内总日志条数。
- `filtered_total`：过滤后的总条数。
- `has_more`：是否还有下一页。
- `recording_enabled`：`记录网络请求` 开关是否开启。
- `max_log_size`：当前内存窗口上限，当前为 `500`。
- `body_preview_size`：App 内部 body 预览上限，当前为 `512KB`。

### network_log_get

按 `network_log_list` 返回的 `id` 获取单条日志详情。默认包含 headers 和 body，但 body 会被 MCP 二次限制，默认最多返回 `16KB`，最大 `64KB`。

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":24,"method":"tools/call","params":{"name":"network_log_get","arguments":{"id":12,"body_char_limit":16384}}}'
```

支持参数：

- `id`：必填，来自 `network_log_list`。
- `include_headers`：默认 `true`。
- `include_body`：默认 `true`。
- `body_char_limit`：默认 `16384`，最大 `65536`。

如果只想看请求概况，不要 body：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":25,"method":"tools/call","params":{"name":"network_log_get","arguments":{"id":12,"include_headers":false,"include_body":false}}}'
```

注意：

- App 内部已经只保存 body 预览，不是完整抓包。
- MCP 详情接口还会按 `body_char_limit` 二次截断，避免单条响应过大。
- headers/body 不做脱敏，只在开发调试环境使用。
- 日志窗口在内存中，应用重启或被系统杀掉后会丢失；窗口满 500 条后旧日志会被挤出。

### network_log_clear

清空当前内存网络日志窗口，适合复现前清现场：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":26,"method":"tools/call","params":{"name":"network_log_clear","arguments":{}}}'
```

返回的 `normalized_data.cleared` 是被清掉的日志条数。

## Resources

### resources/list

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":30,"method":"resources/list","params":{}}'
```

当前资源：

- `legado://api/mcp`
- `legado://schema/book-source`

### resources/read

读取 MCP 概要：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":31,"method":"resources/read","params":{"uri":"legado://api/mcp"}}'
```

读取书源 schema：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":32,"method":"resources/read","params":{"uri":"legado://schema/book-source"}}'
```

## 批量请求

支持 JSON-RPC batch：

```bash
curl -s http://192.168.11.13:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '[{"jsonrpc":"2.0","id":40,"method":"ping","params":{}},{"jsonrpc":"2.0","id":41,"method":"tools/list","params":{}}]'
```

## 常见错误

### MCP 服务未开启

```text
MCP service is disabled
```

处理：在 `我的 -> 服务管理` 中开启 `MCP 服务`。

### 请求路径不对

```text
Not found
```

处理：使用 `POST /mcp`。

### GET 不支持

```text
MCP stream is not supported in this version
```

处理：当前版本只支持 `POST /mcp` 的 JSON 响应。

### JSON-RPC 方法不存在

```json
{
  "error": {
    "code": -32601,
    "message": "Method not found"
  }
}
```

处理：检查 `method`，当前只支持文档列出的基础 MCP 方法。

### 工具不存在

```json
{
  "structuredContent": {
    "ok": false
  },
  "isError": true
}
```

处理：先调用 `tools/list` 确认工具名。
