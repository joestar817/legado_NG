# 原生 MCP 接口手动测试说明

本文档说明阅读 NG 原生 MCP P0 接口的手动测试方式。当前 MCP 是开发调试功能，未加入 token、配对码、只读/写入分级和 SSE 流式会话。

MCP 现在有两条通道：

- 外部 HTTP 通道：在 `我的 -> 服务管理` 中开启 `MCP 服务` 后，供局域网 PC agent 调试连接。
- 内置通道：在 `我的 -> AI 设置` 中开启 `内置 MCP` 后，供 App 内置 AI 直接调用同一套 MCP 工具；这个通道不启动 HTTP 服务，也不开放局域网端口。

## 接口总览

| 模块 | 接口 | 类型 | 说明 |
| --- | --- | --- | --- |
| 基础协议 | `initialize` | JSON-RPC method | 初始化 MCP 会话，返回服务端信息和能力声明。 |
| 基础协议 | `ping` | JSON-RPC method | 检查 MCP 服务是否可用。 |
| 基础协议 | `tools/list` | JSON-RPC method | 列出当前可调用工具。 |
| 基础协议 | `tools/call` | JSON-RPC method | 调用指定 MCP 工具。 |
| 基础协议 | `resources/list` | JSON-RPC method | 列出当前资源 URI。 |
| 基础协议 | `resources/templates/list` | JSON-RPC method | 列出资源模板；当前返回空模板列表。 |
| 基础协议 | `resources/read` | JSON-RPC method | 读取指定资源内容。 |
| 基础协议 | `prompts/list` | JSON-RPC method | 列出 prompt；当前返回空列表。 |
| 资源 | `legado://api/mcp` | resource | 返回 MCP 接口摘要。 |
| 资源 | `legado://schema/book-source` | resource | 返回书源工具相关字段说明。 |
| 资源 | `legado://schema/bookshelf` | resource | 返回书架、章节、角色卡、替换规则工具字段说明。 |
| 资源 | `legado://schema/settings` | resource | 返回设置规则工具字段说明。 |
| 通用 | `legado_ping` | tool | 返回 Legado MCP 服务状态。 |
| 通用 | `legado_get_api_summary` | tool | 返回 MCP 工具和资源摘要。 |
| 书源 | `book_source_list` | tool | 分页列出书源基础信息。 |
| 书源 | `book_source_stats_get` | tool | 获取书源数量、分组、类型和能力统计。 |
| 书源 | `book_source_get` | tool | 按 `bookSourceUrl` 获取完整书源规则。 |
| 书源 | `book_source_save` | tool | 新增或覆盖保存书源规则；写接口，测试脚本默认跳过。 |
| 书源 | `book_source_delete` | tool | 按 `bookSourceUrl` 删除书源；写接口，测试脚本默认跳过。 |
| 书源 | `book_source_set_enabled` | tool | 按 `bookSourceUrl` 启用或停用书源；写接口。 |
| 书源 | `book_source_debug` | tool | 运行书源搜索调试并返回调试日志。 |
| 书源 | `book_search` | tool | 跨书源搜索书籍。 |
| 书架 | `bookshelf_group_list` | tool | 列出书架分组。 |
| 书架 | `bookshelf_group_get` | tool | 按 ID 或名称获取单个书架分组。 |
| 书架 | `bookshelf_group_upsert` | tool | 新建或更新自定义书架分组；写接口。 |
| 书架 | `bookshelf_group_delete` | tool | 删除自定义书架分组；默认只删除空分组。 |
| 书架 | `bookshelf_stats_get` | tool | 获取书架图书数量、类型和分组统计。 |
| 书架 | `bookshelf_book_list` | tool | 分页列出书架书籍，可按分组过滤。 |
| 书架 | `bookshelf_book_get` | tool | 按 `book_url` 获取书籍详情。 |
| 书架 | `bookshelf_book_upsert` | tool | 新增或覆盖书架图书；写接口。 |
| 书架 | `bookshelf_book_delete` | tool | 按 `book_url` 删除书架图书；写接口。 |
| 书架 | `bookshelf_book_group_update` | tool | 批量添加、移除或替换图书自定义分组归属；写接口。 |
| 书架 | `bookshelf_current_book_get` | tool | 获取当前阅读书籍。 |
| 书架 | `bookshelf_chapter_list` | tool | 获取书籍目录列表。 |
| 书架 | `bookshelf_chapter_content_get` | tool | 读取本地或已缓存章节正文，不主动联网抓取。 |
| 书架 | `bookshelf_text_window_get` | tool | 按当前位置读取章节正文窗口。 |
| 书架 | `bookshelf_cache_status_get` | tool | 获取书籍章节缓存状态。 |
| 书架 | `bookshelf_cache_download` | tool | 批量触发指定章节离线缓存；写/联网接口。 |
| 书架 | `bookshelf_cache_clear` | tool | 清理指定章节或整本书正文缓存；写接口。 |
| 书架/书签 | `bookshelf_bookmark_list` | tool | 分页列出全局或指定书籍的书签。 |
| 书架/书签 | `bookshelf_bookmark_get` | tool | 按书签时间主键获取书签详情。 |
| 书架/书签 | `bookshelf_bookmark_upsert` | tool | 新增或更新书签；写接口。 |
| 书架/书签 | `bookshelf_bookmark_delete` | tool | 按时间主键删除书签；写接口。 |
| 书架/阅读记录 | `bookshelf_read_record_list` | tool | 分页列出按书名聚合的阅读记录。 |
| 书架/阅读记录 | `bookshelf_read_record_get` | tool | 按书名获取阅读记录聚合和设备明细。 |
| 书架/阅读记录 | `bookshelf_read_record_upsert` | tool | 新增或更新阅读记录；写接口。 |
| 书架/阅读记录 | `bookshelf_read_record_delete` | tool | 按书名删除阅读记录；写接口。 |
| 书架 | `bookshelf_search` | tool | 书架模块下的跨源搜索别名，等价于 `book_search`。 |
| 书架 | `bookshelf_book_sources_get` | tool | 获取当前书名/作者的可用来源候选。 |
| 书架 | `bookshelf_change_source_preview` | tool | 预览换源候选，不直接应用换源。 |
| 书架/角色卡 | `bookshelf_character_profile_get` | tool | 获取当前书籍角色资料集信息；只读，不创建档案。 |
| 书架/角色卡 | `bookshelf_character_list` | tool | 列出当前书籍角色。 |
| 书架/角色卡 | `bookshelf_character_get` | tool | 按角色 ID 获取角色详情。 |
| 书架/角色卡 | `bookshelf_character_upsert` | tool | 新增或更新角色卡；写接口。 |
| 书架/角色卡 | `bookshelf_character_delete` | tool | 按角色 ID 删除角色卡；写接口。 |
| 书架/角色卡 | `bookshelf_character_set_enabled` | tool | 按角色 ID 启用或停用角色卡；写接口。 |
| 书架/替换规则 | `bookshelf_replace_rule_list` | tool | 列出当前书籍相关替换规则。 |
| 书架/替换规则 | `bookshelf_replace_rule_get` | tool | 按 ID 获取替换规则详情。 |
| 书架/替换规则 | `bookshelf_replace_rule_upsert` | tool | 新增或更新替换规则；写接口。 |
| 书架/替换规则 | `bookshelf_replace_rule_delete` | tool | 按 ID 删除替换规则；写接口。 |
| 书架/替换规则 | `bookshelf_replace_rule_set_enabled` | tool | 按 ID 启用或停用替换规则；写接口。 |
| 书架/替换规则 | `bookshelf_replace_rule_draft_upsert` | tool | 写入草稿分组替换规则；不是聊天预览缓存，属于写接口。 |
| 书架/替换规则 | `bookshelf_replace_rule_draft_apply` | tool | 启用或禁用草稿分组替换规则；写接口。 |
| 书架/替换规则 | `bookshelf_replace_rule_rollback` | tool | 删除草稿分组替换规则；写接口。 |
| 设置/规则统计 | `settings_rule_stats_get` | tool | 获取目录规则、净化规则、字典规则的轻量统计。 |
| 设置/目录规则 | `settings_txt_toc_rule_list` | tool | 分页列出 TXT 本地书目录识别规则。 |
| 设置/目录规则 | `settings_txt_toc_rule_get` | tool | 按 ID 获取目录规则详情。 |
| 设置/目录规则 | `settings_txt_toc_rule_upsert` | tool | 新增或更新目录规则；写接口。 |
| 设置/目录规则 | `settings_txt_toc_rule_delete` | tool | 按 ID 删除目录规则；写接口。 |
| 设置/目录规则 | `settings_txt_toc_rule_set_enabled` | tool | 按 ID 启用或停用目录规则；写接口。 |
| 设置/净化规则 | `settings_replace_rule_list` | tool | 分页列出全局替换净化规则。 |
| 设置/净化规则 | `settings_replace_rule_get` | tool | 按 ID 获取净化规则详情。 |
| 设置/净化规则 | `settings_replace_rule_upsert` | tool | 新增或更新净化规则；写接口。 |
| 设置/净化规则 | `settings_replace_rule_delete` | tool | 按 ID 删除净化规则；写接口。 |
| 设置/净化规则 | `settings_replace_rule_set_enabled` | tool | 按 ID 启用或停用净化规则；写接口。 |
| 设置/字典规则 | `settings_dict_rule_list` | tool | 分页列出字典查询规则。 |
| 设置/字典规则 | `settings_dict_rule_get` | tool | 按名称获取字典规则详情。 |
| 设置/字典规则 | `settings_dict_rule_upsert` | tool | 新增或更新字典规则；写接口。 |
| 设置/字典规则 | `settings_dict_rule_delete` | tool | 按名称删除字典规则；写接口。 |
| 设置/字典规则 | `settings_dict_rule_set_enabled` | tool | 按名称启用或停用字典规则；写接口。 |
| 网络日志 | `network_log_list` | tool | 分页列出内存网络请求日志摘要。 |
| 网络日志 | `network_log_get` | tool | 按 ID 获取单条网络请求日志详情。 |
| 网络日志 | `network_log_clear` | tool | 清空当前内存网络日志窗口；测试脚本默认跳过。 |
| 听书调试 | `read_aloud_storyboard_debug_get` | tool | 读取当前章节的 AI 听书分镜调试快照。 |
| AI 聊天历史 | `ai_chat_conversation_list` | tool | 分页列出 AI 助手聊天会话摘要。 |
| AI 聊天历史 | `ai_chat_conversation_get` | tool | 按会话 ID 获取聊天消息、思考和工具轨迹。 |
| AI 记忆 | `agent_memory_status_get` | tool | 检查 AI 助手记忆系统开关状态。 |
| AI 记忆 | `agent_memory_search` | tool | 按具体对象、业务域、类型和关键词检索记忆。 |
| AI 记忆 | `agent_memory_upsert` | tool | 写入或更新一条 AI 助手记忆；写接口。 |
| AI 记忆 | `agent_memory_archive` | tool | 归档指定 AI 助手记忆；写接口。 |
| 调试日志 | `debug_log_list` | tool | 分页列出 App 内存调试日志摘要。 |
| 调试日志 | `debug_log_get` | tool | 按 ID 获取单条调试日志详情。 |
| 调试日志 | `debug_log_clear` | tool | 清空当前内存调试日志窗口。 |

## 启动方式

### 外部 HTTP 通道

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
http://192.0.2.10:1124/mcp
```

说明：

- MCP 服务和 Web 服务已经分开，MCP 不依赖 Web 服务开启。
- `Web 端口` 默认 `1122`，`MCP 端口` 默认 `1124`。
- 当前也接受 `POST /`，但手动测试和客户端配置统一使用 `POST /mcp`。
- 当前只实现 JSON 响应，不实现 SSE 流式响应；`GET /mcp` 会返回不支持。
- 如果关闭 `MCP 服务`，接口返回 disabled。
- Debug 构建会在 App 启动时自动启动外部 MCP 服务，便于开发调试；Release 仍按服务管理开关控制。

### 内置通道

在 App 中打开：

1. `我的`
2. `AI 设置`
3. 开启 `内置 MCP`

说明：

- 内置通道不依赖 `服务管理 -> MCP 服务`，关闭外部 MCP 服务后仍可被 App 内 AI 调用。
- 内置通道复用外部 HTTP MCP 的工具定义和调用实现；AI 助手按当前会话选择的内部能力分组过滤工具定义。
- 外部 HTTP MCP 的 `tools/list` 始终返回全量工具，不受 AI 助手内部能力选择影响。
- 当前只提供 App 内代码入口 `McpInternalChannel`，后续 AI 功能调用工具时接入该入口。
- `AI 设置 -> AI助理 -> 记忆系统` 单独控制 `agent_memory_*` 工具是否实际读写记忆；关闭时搜索返回空列表，写入不会执行。
- `AI 设置 -> AI助理 -> 操作权限` 控制内置 AI 助理调用 MCP 写工具时的执行权限；默认 `写操作确认` 会在真实执行前弹出本地确认窗，`完全信任` 会允许 AI 直接执行写操作。
- 当前 P0 暂不做 token、配对码和读写权限分级；内置通道由 AI 设置页开关控制。
- 列表类接口默认返回分页后的精简字段，长文本、规则详情、章节 URL 等字段需要通过 `include_detail=true` 或单条 `*_get` 接口显式获取。AI 助理不要为了计数、定位或粗略判断一次性拉取全量明细。
- 书籍作品身份优先使用 `work_key` 或 `name + author`。`book_url` 是当前书源实例地址，换源后可能变化，只适合作为当前实例的辅助定位参数。

### 内置 AI 能力分组

能力分组只用于 App 内置 AI 助手。模块负责界面归类，能力项负责组合实际工具；同一个能力项可以按需求引用其他模块的工具。

| 模块 | 能力项 | 包含工具 |
| --- | --- | --- |
| 通用 | `general.service_info` | `legado_ping`、`legado_get_api_summary` |
| 书源 | `book_source.query` | `book_source_list`、`book_source_stats_get`、`book_source_get` |
| 书源 | `book_source.manage` | `book_source_save`、`book_source_delete`、`book_source_set_enabled` |
| 书源 | `book_source.search` | `book_search` |
| 书源 | `book_source.debug` | `book_source_debug` |
| 书架 | `bookshelf.query` | `bookshelf_stats_get`、`bookshelf_book_list`、`bookshelf_book_get`、`bookshelf_current_book_get` |
| 书架 | `bookshelf.manage_books` | `bookshelf_book_upsert`、`bookshelf_book_delete` |
| 书架 | `bookshelf.manage_groups` | `bookshelf_group_list`、`bookshelf_group_get`、`bookshelf_group_upsert`、`bookshelf_group_delete`、`bookshelf_book_group_update` |
| 书架 | `bookshelf.read_content` | `bookshelf_chapter_list`、`bookshelf_chapter_content_get`、`bookshelf_text_window_get` |
| 书架 | `bookshelf.manage_cache` | `bookshelf_cache_status_get`、`bookshelf_cache_download`、`bookshelf_cache_clear` |
| 书架 | `bookshelf.manage_bookmarks` | `bookshelf_bookmark_list`、`bookshelf_bookmark_get`、`bookshelf_bookmark_upsert`、`bookshelf_bookmark_delete` |
| 书架 | `bookshelf.manage_read_records` | `bookshelf_read_record_list`、`bookshelf_read_record_get`、`bookshelf_read_record_upsert`、`bookshelf_read_record_delete` |
| 书架 | `bookshelf.search_and_change_source` | `bookshelf_search`、`bookshelf_book_sources_get`、`bookshelf_change_source_preview` |
| 书架 | `bookshelf.manage_characters` | `bookshelf_character_profile_get`、`bookshelf_character_list`、`bookshelf_character_get`、`bookshelf_character_upsert`、`bookshelf_character_delete`、`bookshelf_character_set_enabled` |
| 书架 | `bookshelf.manage_replace_rules` | `bookshelf_replace_rule_list`、`bookshelf_replace_rule_get`、`bookshelf_replace_rule_upsert`、`bookshelf_replace_rule_delete`、`bookshelf_replace_rule_set_enabled`、`bookshelf_replace_rule_draft_upsert`、`bookshelf_replace_rule_draft_apply`、`bookshelf_replace_rule_rollback` |
| 设置与规则 | `settings.rule_stats` | `settings_rule_stats_get` |
| 设置与规则 | `settings.manage_toc_rules` | `settings_txt_toc_rule_list`、`settings_txt_toc_rule_get`、`settings_txt_toc_rule_upsert`、`settings_txt_toc_rule_delete`、`settings_txt_toc_rule_set_enabled` |
| 设置与规则 | `settings.manage_replace_rules` | `settings_replace_rule_list`、`settings_replace_rule_get`、`settings_replace_rule_upsert`、`settings_replace_rule_delete`、`settings_replace_rule_set_enabled` |
| 设置与规则 | `settings.manage_dict_rules` | `settings_dict_rule_list`、`settings_dict_rule_get`、`settings_dict_rule_upsert`、`settings_dict_rule_delete`、`settings_dict_rule_set_enabled` |
| AI 数据 | `ai.chat_history` | `ai_chat_conversation_list`、`ai_chat_conversation_get` |
| AI 数据 | `ai.memory` | `agent_memory_status_get`、`agent_memory_search`、`agent_memory_upsert`、`agent_memory_archive` |
| 开发调试 | `developer.network_logs` | `network_log_list`、`network_log_get`、`network_log_clear` |
| 开发调试 | `developer.app_logs` | `debug_log_list`、`debug_log_get`、`debug_log_clear` |
| 开发调试 | `developer.read_aloud_storyboard` | `read_aloud_storyboard_debug_get` |

## 自动测试脚本

仓库内提供了一个标准库 Python 测试脚本，用于覆盖 MCP P0 的 JSON-RPC 方法、资源接口和所有工具：

```powershell
python scripts\test_mcp_api.py --endpoint http://192.0.2.10:1124/mcp
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
- `book_source_stats_get`
- `book_source_get`
- `book_search`
- `book_source_debug`
- 默认只跑安全读取工具：`bookshelf_group_list/get`、`bookshelf_stats_get`、`bookshelf_book_list/get`、`bookshelf_current_book_get`、`bookshelf_chapter_list`、`bookshelf_chapter_content_get`、`bookshelf_text_window_get`、`bookshelf_cache_status_get`、`bookshelf_bookmark_list/get`、`bookshelf_read_record_list/get`、`bookshelf_book_sources_get`、`bookshelf_change_source_preview`、`bookshelf_character_profile_get/list/get`、`bookshelf_replace_rule_list/get`
- 书源、书架、角色卡、替换规则和设置规则的写入/删除/启用停用接口会检查工具是否存在；实际写入用例只在传入 `--write` 时执行。
- 设置只读工具：`settings_rule_stats_get`、`settings_txt_toc_rule_list/get`、`settings_replace_rule_list/get`、`settings_dict_rule_list/get`
- 调试日志只读工具：`debug_log_list/get`
- 未知 method 错误返回

其中 `book_search` 默认会要求至少返回 1 条结果，并校验搜索结果中包含搜索关键词；`book_source_debug` 会校验调试日志包含搜索关键词和“获取书籍列表”。这能覆盖中文 POST body 编码、搜索结果为空、调试未真正进入搜索解析等问题。

网络日志测试默认只验证 `network_log_list` 和 `network_log_get` 的结构；`network_log_clear` 会清空 App 当前内存日志窗口，只有显式传 `--clear-network-log` 才会执行。

可选参数：

```powershell
python scripts\test_mcp_api.py --endpoint http://192.0.2.10:1124/mcp --no-slow
python scripts\test_mcp_api.py --endpoint http://192.0.2.10:1124/mcp --search-key 斗破苍穹 --debug-key 斗破苍穹
python scripts\test_mcp_api.py --endpoint http://192.0.2.10:1124/mcp --allow-empty-search
python scripts\test_mcp_api.py --endpoint http://192.0.2.10:1124/mcp --write
python scripts\test_mcp_api.py --endpoint http://192.0.2.10:1124/mcp --clear-network-log
```

说明：

- 默认不执行 `book_source_save`，避免测试脚本覆盖真实书源数据。
- 默认不执行角色卡/替换规则草稿写入，避免测试脚本污染真实书架数据。
- 默认不执行设置类规则写入，避免测试脚本污染目录规则、净化规则和字典规则。
- `--write` 会先取第一个完整书源并原样保存回去，还会创建临时角色草稿、书架替换规则草稿、书签、阅读记录、目录规则、净化规则和字典规则；临时数据都会通过 rollback 或 delete 接口清理。
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
curl.exe -s http://192.0.2.10:1124/mcp `
  -H "Content-Type: application/json" `
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"manual-test","version":"0.1"}}}'
```

PowerShell 里不要用 bash 风格的 `\"` 转义 JSON 双引号；推荐像上面一样用单引号包住整段 JSON。

WSL / Linux / macOS：

```bash
curl -s http://192.0.2.10:1124/mcp \
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
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"ping","params":{}}'
```

## 列出工具

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}'
```

当前 P0 工具：

- `legado_ping`
- `legado_get_api_summary`
- `book_source_list`
- `book_source_stats_get`
- `book_source_get`
- `book_source_save`
- `book_source_delete`
- `book_source_set_enabled`
- `book_source_debug`
- `book_search`
- `bookshelf_group_list`
- `bookshelf_group_get`
- `bookshelf_group_upsert`
- `bookshelf_group_delete`
- `bookshelf_stats_get`
- `bookshelf_book_list`
- `bookshelf_book_get`
- `bookshelf_book_upsert`
- `bookshelf_book_delete`
- `bookshelf_book_group_update`
- `bookshelf_current_book_get`
- `bookshelf_chapter_list`
- `bookshelf_chapter_content_get`
- `bookshelf_text_window_get`
- `bookshelf_cache_status_get`
- `bookshelf_bookmark_list`
- `bookshelf_bookmark_get`
- `bookshelf_bookmark_upsert`
- `bookshelf_bookmark_delete`
- `bookshelf_read_record_list`
- `bookshelf_read_record_get`
- `bookshelf_read_record_upsert`
- `bookshelf_read_record_delete`
- `bookshelf_search`
- `bookshelf_book_sources_get`
- `bookshelf_change_source_preview`
- `bookshelf_character_profile_get`
- `bookshelf_character_list`
- `bookshelf_character_get`
- `bookshelf_character_upsert`
- `bookshelf_character_delete`
- `bookshelf_character_set_enabled`
- `bookshelf_replace_rule_list`
- `bookshelf_replace_rule_get`
- `bookshelf_replace_rule_upsert`
- `bookshelf_replace_rule_delete`
- `bookshelf_replace_rule_set_enabled`
- `bookshelf_replace_rule_draft_upsert`
- `bookshelf_replace_rule_draft_apply`
- `bookshelf_replace_rule_rollback`
- `settings_rule_stats_get`
- `settings_txt_toc_rule_list`
- `settings_txt_toc_rule_get`
- `settings_txt_toc_rule_upsert`
- `settings_txt_toc_rule_delete`
- `settings_txt_toc_rule_set_enabled`
- `settings_replace_rule_list`
- `settings_replace_rule_get`
- `settings_replace_rule_upsert`
- `settings_replace_rule_delete`
- `settings_replace_rule_set_enabled`
- `settings_dict_rule_list`
- `settings_dict_rule_get`
- `settings_dict_rule_upsert`
- `settings_dict_rule_delete`
- `settings_dict_rule_set_enabled`
- `network_log_list`
- `network_log_get`
- `network_log_clear`
- `read_aloud_storyboard_debug_get`
- `ai_chat_conversation_list`
- `ai_chat_conversation_get`
- `agent_memory_status_get`
- `agent_memory_search`
- `agent_memory_upsert`
- `agent_memory_archive`
- `debug_log_list`
- `debug_log_get`
- `debug_log_clear`

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
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"legado_ping","arguments":{}}}'
```

### legado_get_api_summary

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"legado_get_api_summary","arguments":{}}}'
```

### book_source_list

分页列出书源的基础字段。为了避免几百或几千个书源撑爆上下文，这个接口不会返回完整规则字段；需要完整书源内容时，再用 `book_source_get` 按 `bookSourceUrl` 获取单个书源。

参数：

- `offset`：起始偏移，默认 `0`。
- `limit`：返回数量，默认 `100`，最大 `300`。
- `keyword`：可选，匹配书源名称、URL、分组或备注。
- `enabled`：可选，只返回启用或停用书源。

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"book_source_list","arguments":{}}}'
```

`normalized_data` 返回分页对象：

```json
{
  "sources": [],
  "offset": 0,
  "limit": 100,
  "total": 123,
  "has_more": true
}
```

其中 `sources` 中每个书源只包含：

```json
{
  "bookSourceComment": "",
  "bookSourceGroup": "示例分组",
  "bookSourceName": "示例书源",
  "bookSourceType": 0,
  "bookSourceUrl": "https://example.com/source#default",
  "enabled": true
}
```

### book_source_stats_get

获取书源轻量统计。回答“当前有多少书源、多少分组、各类型/能力数量”这类问题时应优先调用该接口，不要拉取 `book_source_list` 后让模型自行计数。

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"book_source_stats_get","arguments":{}}}'
```

返回字段包括：

- `total/enabled/disabled`：书源总数和启用状态统计。
- `enabled_explore/disabled_explore`：发现页启用状态统计。
- `type_counts`：按 `bookSourceType` 聚合。
- `group_counts`：按书源分组聚合；多分组书源会计入多个分组，空分组归入 `未分组`。
- `capability_counts`：搜索、发现、登录、事件监听、启用文本源等能力数量。

### book_source_get

按 `bookSourceUrl` 获取单个书源：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"book_source_get","arguments":{"url":"https://example.com/source#default"}}}'
```

### book_source_save

保存或覆盖单个书源。最小有效字段是 `bookSourceUrl` 和 `bookSourceName`：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"name":"book_source_save","arguments":{"source":{"bookSourceUrl":"https://example.com/source#default","bookSourceName":"测试书源","enabled":true,"enabledExplore":false}}}}'
```

注意：这是写操作，当前 P0 不区分只读/写入权限。

### book_source_debug

调试书源。`tag` 必须是 `BookSource.bookSourceUrl`，不是书源名称。

搜索模式：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":15,"method":"tools/call","params":{"name":"book_source_debug","arguments":{"tag":"https://example.com/source#default","key":"斗破苍穹","mode":"search","timeout_seconds":30}}}'
```

详情页模式：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":16,"method":"tools/call","params":{"name":"book_source_debug","arguments":{"tag":"https://example.com/source#default","key":"https://example.com/book/1","mode":"detail","timeout_seconds":30}}}'
```

发现页模式：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":17,"method":"tools/call","params":{"name":"book_source_debug","arguments":{"tag":"https://example.com/source#default","key":"https://example.com/list","mode":"explore","timeout_seconds":30}}}'
```

目录页模式：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":18,"method":"tools/call","params":{"name":"book_source_debug","arguments":{"tag":"https://example.com/source#default","key":"https://example.com/toc","mode":"toc","timeout_seconds":30}}}'
```

正文页模式：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":19,"method":"tools/call","params":{"name":"book_source_debug","arguments":{"tag":"https://example.com/source#default","key":"https://example.com/chapter/1","mode":"content","timeout_seconds":30}}}'
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
- 返回结果默认分页且使用精简字段，`offset` 默认 `0`，`limit` 默认 `50`、最大 `200`。
- 默认不返回简介、目录 URL 等较长字段；确实需要这些字段时传 `include_detail=true`。

PowerShell：

```powershell
curl.exe -s http://192.0.2.10:1124/mcp -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":20,"method":"tools/call","params":{"name":"book_search","arguments":{"key":"斗破苍穹","timeout_seconds":30}}}'
```

WSL / Linux / macOS：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":20,"method":"tools/call","params":{"name":"book_search","arguments":{"key":"斗破苍穹","timeout_seconds":30}}}'
```

等待全部书源完成：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":21,"method":"tools/call","params":{"name":"book_search","arguments":{"key":"斗破苍穹","wait_for_finish":true,"timeout_seconds":60}}}'
```

指定搜索范围：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":22,"method":"tools/call","params":{"name":"book_search","arguments":{"key":"斗破苍穹","scope":"玄幻","timeout_seconds":30}}}'
```

返回的 `normalized_data.books` 是当前页搜索结果数组；`normalized_data.total` 是本次已收集结果总数，`has_more=true` 时继续用 `offset/limit` 翻页。

默认精简结果字段包含：

```json
{
  "book_url": "https://example.com/book",
  "name": "示例书名",
  "author": "作者",
  "origin": "https://example.com/source",
  "origin_name": "示例书源",
  "type": 0,
  "kind": "玄幻",
  "word_count": "100万字",
  "latest_chapter_title": "最新章节",
  "respond_time": 123
}
```

### 书架模块工具

书架模块按子模块拆成：书籍/分组、目录/正文/缓存、书签、阅读记录、搜索/换源预览、角色卡、替换规则。所有正文类接口只读取本地书籍或已缓存正文，不会通过 MCP 主动联网抓取章节。写接口应在 AI 回复中先说明影响范围并等待用户确认。

#### 书籍/分组

- `bookshelf_group_list`：列出书架分组和每个分组当前书籍数。
- `bookshelf_group_get`：按 `group_id` 或 `group_name` 获取单个分组。
- `bookshelf_group_upsert`：新建或更新自定义分组；内置分组不能通过 MCP 修改。
- `bookshelf_group_delete`：按 `group_ids` 或 `group_names` 删除自定义分组；默认 `only_empty=true`，只删除空分组；传 `only_empty=false` 会先移除图书的该分组归属再删除分组。
- `bookshelf_stats_get`：获取书架图书总数、类型数量、未入书架数量和分组统计；回答“书架上有多少本书、多少个分组”时优先使用。
- `bookshelf_book_list`：分页列出书架书籍，支持 `group_id`、`keyword`、`offset`、`limit`、`include_not_shelf`。
- `bookshelf_book_get`：按 `work_key`，或 `name` + `author`，或当前实例 `book_url` 获取单本书详情。
- `bookshelf_book_upsert`：按完整 `Book` JSON 新增或覆盖书籍；`bookUrl` 仍是当前书源实例地址且必须提供，但已有书会按 `bookUrl` 或 `name+author` 判断，换源替换时结果会返回 `replaced_book_url`。
- `bookshelf_book_delete`：按 `book_urls` 删除书籍。
- `bookshelf_book_group_update`：批量添加、移除或替换图书自定义分组归属；`mode` 支持 `add/remove/replace`，可用 `group_ids` 或 `group_names` 指定目标分组。
- `bookshelf_current_book_get`：获取当前阅读书籍；没有活动阅读状态时返回最近阅读文本书。

示例：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":40,"method":"tools/call","params":{"name":"bookshelf_book_list","arguments":{"limit":10}}}'
```

统计示例：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":401,"method":"tools/call","params":{"name":"bookshelf_stats_get","arguments":{}}}'
```

`bookshelf_stats_get` 返回 `total`、`not_shelf`、`type_counts`、`group_total` 和 `groups`，不会返回书籍明细。

#### 目录/正文/缓存

- `bookshelf_chapter_list`：分页列出目录，默认只返回精简字段；书籍可用 `work_key`、`name + author` 或 `book_url` 定位，支持 `start`、`end`、`limit`、`keyword`、`include_cache_status`、`include_detail`、`include_all`。
- `bookshelf_chapter_content_get`：读取单章已缓存/本地正文，书籍可用 `work_key`、`name + author` 或 `book_url` 定位，支持 `char_limit`。
- `bookshelf_text_window_get`：读取连续章节窗口，适合给 AI 取上下文，书籍可用 `work_key`、`name + author` 或 `book_url` 定位。
- `bookshelf_cache_status_get`：检查章节范围的正文缓存状态，书籍可用 `work_key`、`name + author` 或 `book_url` 定位。
- `bookshelf_cache_download`：批量触发指定章节离线缓存，支持 `chapter_indexes`、`ranges[{start,end}]` 或 `start/end`；`start` 包含、`end` 不包含。`refresh_existing=true` 会先删除选中章节正文缓存再重新入队下载。
- `bookshelf_cache_clear`：清理指定章节正文缓存，支持 `chapter_indexes`、`ranges[{start,end}]` 或 `start/end`；如需清理整本书缓存，必须显式传 `clear_book=true`。

`bookshelf_chapter_list` 体积约束：

- 默认 `start=0`、`limit=100`，单次最多 `300` 条。
- 默认目录项只包含 `index`、`title`、`is_volume`、`is_vip`、`word_count`，传 `include_cache_status=true` 时额外返回是否有本地正文。
- 只有需要章节 URL、资源 URL、起止位置、变量等完整字段时，才传 `include_detail=true`。
- `include_all=true` 会返回请求范围内全部目录，AI 聊天中应避免使用；长书请用 `start/limit` 分页。

示例：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":41,"method":"tools/call","params":{"name":"bookshelf_chapter_list","arguments":{"work_key":"书名\n作者","start":0,"limit":20,"include_cache_status":true}}}'
```

重新缓存一组异常章节：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":411,"method":"tools/call","params":{"name":"bookshelf_cache_download","arguments":{"work_key":"书名\n作者","chapter_indexes":[2,5,7],"refresh_existing":true}}}'
```

#### 书签

- `bookshelf_bookmark_list`：列出全局或指定书籍书签，支持 `book_url`、`name`、`author`、`keyword`、`offset`、`limit`。
- `bookshelf_bookmark_get`：按 `time` 获取单条书签。
- `bookshelf_bookmark_upsert`：新增或更新书签；传 `book_url` 时可自动补 `book_name/book_author`。
- `bookshelf_bookmark_delete`：按 `times` 删除书签。

示例：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":42,"method":"tools/call","params":{"name":"bookshelf_bookmark_list","arguments":{"book_url":"BOOK_URL","limit":20}}}'
```

#### 阅读记录

- `bookshelf_read_record_list`：列出按书名聚合的阅读记录，支持 `keyword`、`sort=name/read_time/last_read`、`offset`、`limit`。
- `bookshelf_read_record_get`：按 `book_name` 获取聚合阅读时长、最近阅读时间和设备明细。
- `bookshelf_read_record_upsert`：新增或更新阅读记录；未传 `device_id` 时使用当前设备 ID。
- `bookshelf_read_record_delete`：按 `book_name` 删除阅读记录。

#### 搜索/换源预览

- `bookshelf_search`：`book_search` 的书架模块别名。
- `bookshelf_book_sources_get`：获取某本书当前缓存的可用候选源。
- `bookshelf_change_source_preview`：只预览候选源，不执行换源迁移。

说明：真正换源应用需要复用阅读页现有确认和迁移流程，当前 MCP 不直接绕过 UI 修改书籍来源。

#### 角色卡

- `bookshelf_character_profile_get`：获取角色档案；只读，不创建档案。
- `bookshelf_character_list`：列出一本书的角色卡。
- `bookshelf_character_get`：按角色 `id` 获取单张角色卡。
- `bookshelf_character_upsert`：创建或更新角色卡。
- `bookshelf_character_delete`：按 `ids` 删除角色卡。
- `bookshelf_character_set_enabled`：按 `ids` 启用或禁用角色卡。

#### 替换规则

- `bookshelf_replace_rule_list`：分页列出替换规则，默认返回精简字段；支持按书籍作用域、分组、启用状态过滤，传 `include_detail=true` 返回完整规则字段。
- `bookshelf_replace_rule_get`：按 `id` 获取替换规则。
- `bookshelf_replace_rule_upsert`：创建或更新替换规则；传 `work_key`、`name + author` 或 `book_url` 且未传 `scope` 时默认作用于书名。
- `bookshelf_replace_rule_delete`：按 `ids` 删除替换规则。
- `bookshelf_replace_rule_set_enabled`：按 `ids` 启用或禁用替换规则。
- `bookshelf_replace_rule_draft_upsert`：写入草稿分组替换规则；这会落库，不是聊天预览缓存；传 `work_key`、`name + author` 或 `book_url` 且未传 `scope` 时默认作用于书名。
- `bookshelf_replace_rule_draft_apply`：按 `ids` 启用或禁用草稿分组替换规则。
- `bookshelf_replace_rule_rollback`：删除指定草稿分组替换规则。

注意：角色卡和替换规则的标准 CRUD 与草稿接口都是写操作；当前 P0 不做 token 和权限分级，外部调试时应只在受信任局域网环境开启 MCP 服务。

### 设置模块工具

设置模块先覆盖全局规则类配置：TXT 目录规则、全局替换净化规则、字典查询规则。列表接口统一支持 `keyword`、`enabled`、`offset`、`limit`，默认返回精简字段；需要完整规则内容时传 `include_detail=true`。写接口直接落现有数据库表，不做隐藏迁移或兜底修复。

#### 规则统计

- `settings_rule_stats_get`：获取目录规则、净化规则、字典规则的轻量统计。回答“当前有多少目录规则/净化规则/字典规则、哪些分组规则最多”时优先使用。

示例：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":49,"method":"tools/call","params":{"name":"settings_rule_stats_get","arguments":{}}}'
```

返回字段：

- `txt_toc_rules`：总数、启用/停用、默认/自定义、带替换结果的目录规则数量。
- `replace_rules`：总数、启用/停用、正则/纯文本、有效/无效、标题/正文作用域、分组统计。
- `dict_rules`：总数、启用/停用、带展示规则的字典规则数量。

#### 目录规则

- `settings_txt_toc_rule_list`：分页列出 TXT 本地书目录识别规则，默认返回精简字段。
- `settings_txt_toc_rule_get`：按 `id` 获取目录规则。
- `settings_txt_toc_rule_upsert`：新增或更新目录规则，字段包含 `name`、`rule`、`replacement`、`example`、`serial_number`、`enabled`。
- `settings_txt_toc_rule_delete`：按 `ids` 删除目录规则。
- `settings_txt_toc_rule_set_enabled`：按 `ids` 启用或停用目录规则。

示例：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":50,"method":"tools/call","params":{"name":"settings_txt_toc_rule_list","arguments":{"limit":10}}}'
```

#### 净化规则

- `settings_replace_rule_list`：分页列出全局替换净化规则，默认返回精简字段，额外支持 `group`、`scope` 过滤。
- `settings_replace_rule_get`：按 `id` 获取净化规则。
- `settings_replace_rule_upsert`：新增或更新净化规则，字段包含 `name`、`group`、`pattern`、`replacement`、`scope`、`scope_title`、`scope_content`、`exclude_scope`、`enabled`、`is_regex`、`timeout_millisecond`、`order`。
- `settings_replace_rule_delete`：按 `ids` 删除净化规则。
- `settings_replace_rule_set_enabled`：按 `ids` 启用或停用净化规则。

说明：`bookshelf_replace_rule_*` 面向书架书籍作用域和 AI 草稿回滚；`settings_replace_rule_*` 面向全局净化规则管理。

#### 字典规则

- `settings_dict_rule_list`：分页列出字典查询规则，默认返回精简字段。
- `settings_dict_rule_get`：按 `name` 获取字典规则。
- `settings_dict_rule_upsert`：新增或更新字典规则，字段包含 `name`、`url_rule`、`show_rule`、`enabled`、`sort_number`。
- `settings_dict_rule_delete`：按 `names` 删除字典规则。
- `settings_dict_rule_set_enabled`：按 `names` 启用或停用字典规则。

注意：设置模块写接口同样是写操作；当前 P0 不做 token 和权限分级，外部调试时应只在受信任局域网环境开启 MCP 服务。

### ai_chat_conversation_list

分页获取 AI 助手聊天会话摘要。这个接口读取 Room 中持久化的聊天记录，不修改会话。

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":50,"method":"tools/call","params":{"name":"ai_chat_conversation_list","arguments":{"limit":20}}}'
```

支持参数：

- `offset`：分页偏移，默认 `0`。
- `limit`：返回条数，默认 `20`，最大 `50`。
- `keyword`：可选，匹配标题、Skill id、消息正文、思考内容或工具轨迹。
- `include_empty`：可选，默认 `false`，是否返回没有消息的空会话。

`normalized_data.conversations` 每条只包含摘要，例如：

```json
{
  "id": "1781840000000",
  "title": "给当前书生成角色卡",
  "assistant_id": "default",
  "update_time_text": "2026-07-01 09:30:00.000",
  "loaded_skill_ids": ["character_card_generate"],
  "message_count": 4,
  "last_message_role": "assistant",
  "last_message_preview": "以上就是基于采样得到的四个核心角色。",
  "has_reasoning": true,
  "tool_trace_count": 6
}
```

### ai_chat_conversation_get

按 `ai_chat_conversation_list` 返回的 `id` 获取单个会话的消息历史。默认返回可见消息、reasoning、工具调用轨迹和 meta；长文本会按 `text_char_limit` 截断。

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":51,"method":"tools/call","params":{"name":"ai_chat_conversation_get","arguments":{"id":"1781840000000","message_limit":50,"text_char_limit":12000}}}'
```

支持参数：

- `id`：必填，来自 `ai_chat_conversation_list`。
- `message_offset`：消息分页偏移，默认 `0`。
- `message_limit`：返回消息数，默认 `100`，最大 `300`。
- `text_char_limit`：每段正文、思考或工具轨迹的最大字符数，默认 `8192`，最大 `65536`。
- `include_upload_messages`：可选，默认 `false`，是否额外返回模型请求上下文快照 JSON。
- `upload_char_limit`：`upload_messages_json` 最大字符数，默认 `16384`，最大 `131072`。

注意：

- 这个接口是只读诊断接口，不会重新发起模型请求，也不会调用任何写库工具。
- `messages[].content` 是聊天界面保存的可见正文；`messages[].reasoning` 是保存下来的思考内容；`messages[].tool_trace` 是本地记录的工具调用轨迹。
- `include_upload_messages=true` 可能返回较大的模型上下文快照，只有分析“模型到底看到了什么”时再打开。

### agent_memory_status_get / agent_memory_search

AI 助手记忆是 Agent 聊天功能，不参与段落净化、章节净化等 App 内部结构化调用。

调用记忆工具前，应先通过 `agent_memory_status_get` 检查 `AI 设置 -> AI助理 -> 记忆系统` 是否开启。关闭时不要继续读写记忆。

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":52,"method":"tools/call","params":{"name":"agent_memory_search","arguments":{"scope_type":"book","scope_key":"开局十二岁，被校花姐姐们宠坏了|作者名","domain":"character_card","limit":10}}}'
```

常用参数：

- `scope_type`：对象类型，例如 `book`、`book_source`、`conversation`、`global`。
- `scope_key`：稳定对象键。书籍建议优先使用 `书名|作者`，不要只依赖可能随换源变化的 `bookUrl`。
- `subject`：可读对象名称，可选模糊过滤。
- `domain`：业务域，例如 `character_card`。
- `memory_type`：记忆类型，例如 `checkpoint`、`fact`、`preference`、`decision`。
- `keyword`：关键词，匹配标题、内容、对象名和标签。

`agent_memory_upsert` 是写接口，只应在最终用户确认的应用操作成功后调用；不要在普通分析、预览或失败操作后保存记忆。

### debug_log_list

分页获取 App 内存调试日志摘要。这个接口读取的是 `AppLog.logs` 当前内存窗口，不读取外部缓存目录里的日志文件。

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":60,"method":"tools/call","params":{"name":"debug_log_list","arguments":{"limit":20}}}'
```

支持参数：

- `offset`：分页偏移，默认 `0`。
- `limit`：返回条数，默认 `20`，最大 `100`。
- `keyword`：可选，匹配 message 或异常堆栈。
- `only_errors`：可选，只返回带 Throwable 的日志。

### debug_log_get

按 `debug_log_list` 返回的 `id` 获取单条调试日志详情。`id` 使用日志时间戳，内存日志窗口清空或被新日志挤出后会失效。

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":61,"method":"tools/call","params":{"name":"debug_log_get","arguments":{"id":1781840000000,"include_stack":false}}}'
```

### debug_log_clear

清空当前 App 内存调试日志窗口。这个接口不会删除文件日志。

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":62,"method":"tools/call","params":{"name":"debug_log_clear","arguments":{}}}'
```

### network_log_list

分页获取运行时网络请求日志摘要。这个接口不会返回 headers/body，只返回足够定位单条请求的摘要字段，避免一次把网络日志撑爆 MCP 上下文。

建议调试流程：

1. 需要干净现场时先调用 `network_log_clear`。
2. 在 App 内复现问题，或通过 MCP 调用 `book_source_debug` / `book_search` 触发请求。
3. 调用 `network_log_list` 按摘要找目标请求。
4. 调用 `network_log_get` 按 `id` 获取单条详情。

```bash
curl -s http://192.0.2.10:1124/mcp \
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
curl -s http://192.0.2.10:1124/mcp \
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
curl -s http://192.0.2.10:1124/mcp \
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
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":26,"method":"tools/call","params":{"name":"network_log_clear","arguments":{}}}'
```

返回的 `normalized_data.cleared` 是被清掉的日志条数。

### read_aloud_storyboard_debug_get

读取当前章节的 AI 听书分镜调试快照。这个接口只读取 App 已保存的运行时数据，不会重新调用模型。

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":27,"method":"tools/call","params":{"name":"read_aloud_storyboard_debug_get","arguments":{"include_storyboard":true,"include_payload":false}}}'
```

支持参数：

- `include_storyboard`：默认 `true`，返回分镜结果。
- `include_payload`：默认 `false`，是否返回 App 当时发送给模型的请求体。
- `paragraph_limit`：限制返回的段落数量。
- `unit_limit`：限制返回的归因单元数量。
- `segment_limit`：限制返回的分镜片段数量。
- `text_char_limit`：限制长文本字段的字符数。

## Resources

### resources/list

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":30,"method":"resources/list","params":{}}'
```

当前资源：

- `legado://api/mcp`
- `legado://schema/book-source`
- `legado://schema/bookshelf`
- `legado://schema/settings`

### resources/read

读取 MCP 概要：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":31,"method":"resources/read","params":{"uri":"legado://api/mcp"}}'
```

读取书源 schema：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":32,"method":"resources/read","params":{"uri":"legado://schema/book-source"}}'
```

读取书架模块接口概要：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":33,"method":"resources/read","params":{"uri":"legado://schema/bookshelf"}}'
```

读取设置模块接口概要：

```bash
curl -s http://192.0.2.10:1124/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":34,"method":"resources/read","params":{"uri":"legado://schema/settings"}}'
```

## 批量请求

支持 JSON-RPC batch：

```bash
curl -s http://192.0.2.10:1124/mcp \
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
