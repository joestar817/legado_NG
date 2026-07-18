# 首次快速定位（实验 B：保持首尾 10 章）

## 目标

只用物理开头 10 章和物理末尾 10 章，帮助未读用户迅速看清作品定位、主要代入角色、核心关系、压抑程度和已经暴露的重大风险。本轮不要读取 `full_scan.md`、`risk_terms.md` 或 `analysis/`。

## 固定流程

1. 调用 `bookshelf_current_book_get`，取得稳定 `work_key` 和总章数。
2. 调用 `agent_memory_status_get`，再用 `scope_type=book`、`scope_key=work_key`、`domain=book_scan`、`memory_type=manifest` 精确查询。已有档案时停止首次快扫并基于记忆回答。
3. 调用 `bookshelf_cache_status_get`；缺少正文时明确停止。
4. 开头分两次完整读取：索引 `0..4`、`5..9`，每次 `chapter_count=5,char_limit=0`。
5. 读完开头后，立即提取人物、关系、不可逆伤害、重大关系变化、压抑来源和风险候选；不得只保存覆盖范围。用一次 `agent_memory_batch_upsert` 保存 opening window、变化项和 manifest。
6. 末尾分两次完整读取：起点为 `max(0,total_count-10)`，同样每次最多 5 章。
7. 把末尾新事实与已有记忆综合，只更新 ending window、变化项和 manifest；已有项目复用查询/写入结果中的 id，不回传整份历史。
8. AgentMemory 写入成功后直接输出一份完整报告和下一步入口。不要输出进度播报或内部 JSON。

每条记忆固定使用 `scope_type="book"`、原样 `work_key`、`domain="book_scan"`；不能把换行变成字面量 `\n`。不得维护第二套扫书档案。

## 每个首尾样本必须先提取的事实

- 主要代入角色及身份，不能把重要配角随意认作主角。
- 核心关系双方：事件前关系、是否互有情愫或已有排他期待、当前状态。
- 永久伤残、死亡、长期囚禁、人格/尊严摧毁等不可逆伤害。
- 被迫婚配、第三方伴侣、背叛、下药、强迫、情感勒索等重大关系变化。
- 角色即时心理、反抗/接受、后续主动行为；证据缺失写 `unknown`，不能因此删除已经存在的高危客观事实。
- 当前段落造成的读者体验：爽、压抑、愤怒、看乐子、悲伤或证据不足。

## 重大风险最低口径

- 核心感情角色与主角已互有情愫或存在明确感情期待，却被迫与第三方成婚：必须建立 `relationship_forced_marriage` 事件，严重度通常为 `critical`；按本项目口径同时标记 `relationship.green_hat` 与 `relationship.sent_love_interest`。被迫、未圆房、第三方失去性能力或未来可能反转，都不能取消已经发生的强制婚配过程。
- 主要代入角色遭受永久肢体伤残：建立 `character_permanent_injury`，严重度至少 `high`。
- 证据足以确认事件、但关系前史不足时，事件仍要进入 AgentMemory 和首屏；关系解释标为“关系语境待补”，不能输出“暂无重大雷点”。
- 黑暗型、操控型主角不是一句“这是主角特性所以不是雷点”就能排除；应作为主角画像和可能劝退特征如实提示。
- 同样的下药或越界事件必须结合双方既有关系、受影响者即时心理和后续主动行为判断读者反馈，不能只凭事件名称机械定性。
- 风险事实必须由本轮实际读取正文直接支持；禁止依赖模型记忆或未读取章节补写番外、结局和具体事件。死亡不能误写成永久伤残；已在主时间线彻底改写且没有当前持续伤害的前世悲剧、普通黑暗背景和中性路线属性，不自动升级成重大雷点。

## 最小 AgentMemory 档案

只保存首次快扫必要的分类型记忆；不要展开决策线程或 14 维完整分析：

1. 唯一 `manifest`：`total_count/fully_read_ranges/missing_ranges/covered_count/event_ids/memory_ids/unresolved`。
2. `window`：opening 与 ending 各一条。
3. `character`：主要代入人物各一条。
4. `relationship`：核心关系各一条。
5. `reader_experience`：压抑来源、缓解与读者影响。
6. `event`：已确认或疑似重大事件各一条。

每个 window 的 `data` 至少包含 `range/characters/relationships/dimension_signals/event_facts/reader_effects`。事件字段使用 `event_key,event_type,term_ids,status,severity,confidence,chapter_indexes,participants,fact,spoiler_safe_summary,attributes`。没有事件时不创建虚假事件记忆；正文已经出现强制婚配或永久伤残却没有对应事件属于失败。

已有记忆必须复用返回的 `id` 更新；每批只写变化项和 manifest，不复制完整历史。结构化字段必须保持 JSON 原生类型，不能压成字符串。

## 报告顺序

1. 基础信息与一句话定位。
2. 作品画像：流派、核心卖点、整体风格、世界观、结构与视角、适合/慎入读者。
3. 主角画像：最多 3 名真正主要代入角色，说明身份、特征、代入体验和可能劝退点。
4. 情感关系：明确谁与谁、此前关系、当前变化和读者影响。
5. 阅读情绪与压抑指数：说明压力来源和缓解节奏。
6. 重大雷点：先于扫描边界；有 `critical/high` 事件时必须逐项醒目展示事实、关系影响和结论边界。
7. 扫描边界：明确只覆盖首尾 10 章。
8. 最后追加 `book_scan_next` interaction，不得替代报告。

报告面向手机：不用表格，不打印记忆 JSON，不堆陌生人名。命中重大风险只是必要条件，作品画像、人物代入、核心关系、阅读情绪和扫描边界也不得省略。
