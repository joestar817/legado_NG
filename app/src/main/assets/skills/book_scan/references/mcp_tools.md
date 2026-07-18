# 扫书可用 MCP

只用 App 已开放的读书工具，不把整本书直接塞进模型上下文。

## 当前可用

- `bookshelf_book_get`：确认目标书，读取书名、作者、简介、分类、完结状态等基础信息。
- `bookshelf_chapter_list`：读取目录；可用 `include_cache_status=true` 同时看章节是否有本地正文。
- `bookshelf_cache_status_get`：只查一段章节的正文缓存状态，不读取正文。
- `bookshelf_chapter_content_get`：读取一个已缓存或本地章节正文；不会联网抓取；可用 `char_limit` 控制返回长度。
- `bookshelf_text_window_get`：从某章开始读取相邻章节窗口；不会联网抓取；可用 `chapter_count` 和 `char_limit` 控制范围。
- `bookshelf_chapter_snippets_get`：批量读取任意章节的开头和结尾片段；不会联网抓取。v2.5 默认不用它做快速定位或继续排雷，只保留为故障诊断和以后版本备用。
- `agent_memory_search / agent_memory_upsert / agent_memory_batch_upsert`：读取或保存当前书扫书事实、当前书反馈和明确确认的长期偏好。保存正文形成的快扫事实时，必须把本轮正文工具回执放到顶层 `source_receipt_ids`，不要放进单条 item 内，也不要省略。快速定位完成后至少保留一条 `scope_type=book`、`scope_key=入口 work_key`、`domain=book_scan` 的本书记录；推荐用 `memory_type=manifest` 标记覆盖范围、样本来源、当前判断和未知边界，便于下次进入时识别已有档案。

## 当前边界

- `bookshelf_text_window_get` 只适合相邻章节窗口，不适合一次读取任意多个分散章节。
- `bookshelf_chapter_snippets_get` 可以读任意章节首尾片段，但只读已缓存或本地章节，不会主动联网抓正文；当前扫书主流程不要依赖它。
- 能用于确认或排除风险的覆盖只来自完整正文或连续正文窗口。

## 调用顺序

快速定位优先顺序：

1. 用 `bookshelf_book_get` 确认目标书。
2. 用 `bookshelf_chapter_list` 读目录，必要时带 `include_cache_status=true`。
3. 用 `bookshelf_text_window_get` 读取开头第 1—10 章，`chapter_count=10`，`char_limit=0`。
4. 用 `bookshelf_text_window_get` 读取结尾或最新 10 章，`chapter_count=10`，`char_limit=0`；如果和开头范围重叠，合并范围，避免重复读取。
5. 快速定位正文读取上限是 2 次 `bookshelf_text_window_get`。书不足 20 章且头尾范围重叠时可以合并为 1 次；除此之外不得第 3 次调用正文窗口。
6. 两端窗口成功后立即进入记忆保存和报告生成。不要把第 1—10 章拆成第 1—5 章、第 6—10 章重读，也不要为了核对、总结或保存记忆重新读取同一范围。
7. 如果窗口返回截断、失败或缓存缺失，如实说明缺失边界；不要缩小窗口重试，不要改用单章读取或片段伪装完整覆盖。

继续排雷优先顺序：

1. 查询 manifest 和目录。
2. 计算尚未完整读过的连续缺口。
3. 按用户选择从缺口开头连续读取约 100 章、约 300 章、全部剩余或指定范围。
4. 每次用 `bookshelf_text_window_get` 读取 10～20 章完整正文，`char_limit=0`；截断就拆小。
