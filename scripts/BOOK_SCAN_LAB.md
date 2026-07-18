# AI 扫书无界面实验室

`book_scan_lab.py` 用来在不点击 Android UI 的情况下，快速跑完整扫书流程。它使用当前工作区里的真实 Skill、真实 `interaction-policy.json` 和真实模型；书架 MCP、AgentMemory 与宿主交互由本地适配器模拟。

它适合发现这些问题：

- 模型有没有按需加载 Skill 和参考文件；
- 读取了哪些目录、章节片段或完整正文；
- 章节片段是否被错误算成完整正文覆盖；
- 报告和交互协议是否有效，是否返回未知 ID；
- 快速定位、继续排雷、选择范围和二次报告能否连起来；
- 同一本书重新进入时，本地档案是否可被宿主发现；
- 每轮耗时、工具次数和累计 token 是否失控；
- 报告是否暴露 `manifest`、`scan_100`、`snippet覆盖` 等内部协议词。

它不能替代 Android 验收：Compose 版式、Markdown/结构化报告真实渲染、数据库 Hook、会话生命周期和输入区状态仍要在 App 中验证。

## 运行

仓库根目录 `.env` 需要包含：

```dotenv
DEEPSEEK_API_KEY=...
```

PowerShell：

```powershell
python scripts\book_scan_lab.py `
  --scenario scripts\fixtures\book_scan_lab\white_snake_full_flow.json `
  --book "C:\Users\joest\Documents\leidian14\Pictures\txt\白蛇 作者：lingyungzs.txt" `
  --reset
```

切换 Pro 模型并开启与 App 相同的深度思考参数：

```powershell
python scripts\book_scan_lab.py `
  --scenario scripts\fixtures\book_scan_lab\white_snake_full_flow.json `
  --book "C:\Users\joest\Documents\leidian14\Pictures\txt\白蛇 作者：lingyungzs.txt" `
  --model deepseek-v4-pro `
  --thinking --reasoning-effort high `
  --name white-snake-pro-thinking `
  --reset
```

只校验场景、书籍解析、Skill 和策略文件，不调用模型：

```powershell
python scripts\book_scan_lab.py `
  --scenario scripts\fixtures\book_scan_lab\example_harem.json `
  --dry-run --reset
```

## 场景

场景的 `flow` 描述用户实际点击路径。固定交互文案和 prompt 不复制到场景中，而是运行时从 App 的 `interaction-policy.json` 加载：

```json
{
  "flow": [
    {"interaction": "book_scan_reaction", "select": "continue_scan"},
    {"interaction": "book_scan_like_reasons", "action": "skip"},
    {"interaction": "book_scan_target", "select": "scan_100"}
  ]
}
```

`recover_protocol_errors=true` 只用于探索：模型返回未知 item ID 时，实验室会记录真实 App 应当拒绝的协议错误，再按场景中期望的交互继续跑后续流程。它不会把错误结果标成通过。

## 产物

默认写入 `build/book_scan_lab/<name>/run_001/`：

- `report.html`：适合快速浏览的总览；
- `summary.json`：结论、token、覆盖和失败项；
- `transcript.md`：每轮最终可见输出；
- `run.json`：完整消息、工具轨迹和检查结果；
- `raw/`：每一步模型请求与原始响应；
- `state/`：模拟 MCP 工具结果、回执和 AgentMemory。

`complete_read_indexes` 只统计未截断的完整正文；`navigation_indexes` 只统计章节首尾片段。两者不会互相冒充。

## 两阶段架构实验

`book_scan_two_stage_lab.py` 不运行当前完整工作流 Skill，而是用于验证更轻的职责边界：宿主先提供全书目录和首尾片段，模型只返回疑点章号；宿主补读疑点完整正文后，第二次模型调用生成报告。书名、作者、状态、篇幅、真实覆盖和固定交互由宿主写入。

真实模型调用默认使用流式响应，避免兼容网关在长时间无响应体时主动断开连接。仅在排查供应商兼容问题时使用 `--no-stream`；正常质量测试和完整流程测试不要关闭流式。

```powershell
python scripts\book_scan_two_stage_lab.py `
  --book "C:\Users\joest\Documents\leidian14\Pictures\txt\白蛇 作者：lingyungzs.txt" `
  --model deepseek-v4-pro --thinking --reasoning-effort high `
  --name white-snake-pro-two-stage `
  --reset
```

长篇书可运行完整流程实验。它会先快速定位，再模拟用户选择“继续排雷 / 扫描剩余章节”，按批浏览全书章节首尾，跨批追踪人物和关系线，汇总后补读可疑章节全文，生成二次报告并把状态保存到 `state.json`。再次运行同名任务会复用已完成阶段，网络中断或单批结构化输出截断时不会从头重扫。

App 的“自动”档不会发送 `thinking` 或 `reasoning_effort`。对应命令为：

```powershell
python scripts\book_scan_two_stage_lab.py `
  --book "C:\Users\joest\Documents\leidian14\Pictures\txt\天之下.txt" `
  --model deepseek-v4-pro --thinking `
  --reasoning-effort auto --planning-reasoning-effort auto `
  --full-flow --batch-size 60 `
  --name tianzhixia-pro-full-flow-auto `
  --reset
```

完整流程产物位于 `build/book_scan_two_stage_lab/<name>/`：

- `quick_output.md` / `final_output.md`：快速定位和继续扫描后的报告；
- `batch_*_plan.json`：每批疑点、追踪人物和未闭合因果；
- `entity_occurrences.json` / `deep_consolidation_plan.json`：跨批人物定位和正文补读计划；
- `state.json`：覆盖、交互、报告和书籍内容指纹，用于验证重进恢复；
- `attempts.jsonl`：实际模型尝试和 token，包含失败重试，不只统计最终成功响应；
- `summary.json` / `report.html`：流程结论和可视化结果。

该脚本是架构对照实验，不代表已经修改正式 App 或 Skill。

### 并行批次实验

`--parallel-workers` 可以把继续排雷阶段的导航批次并行执行，模拟“子 Agent / 子任务”模式。默认值是 `1`，也就是旧的串行行为；建议先用 `2`，最多小范围尝试 `3`，避免供应商限流或本机同时写太多请求结果。

默认 `--parallel-mode navigation` 只是旧对照模式，并行发生在“章节首尾片段导航”阶段：

- 每个批次拿自己的章节片段和快扫阶段留下的追踪对象，独立找疑点；
- 子任务只返回疑点章号、追踪人物和未闭合因果，不写最终报告；
- 宿主按批次编号合并结果，再统一做跨批人物定位、疑点正文补读和最终报告；
- 如果某个批次失败，产物目录里已有成功批次会被保留，重跑同名任务可从缓存继续。

用户当前讨论的产品方向不是这个旧模式，而是 `--parallel-mode raw-chunks`：

- 快速定位先生成一版作品画像；
- 继续排雷时不再跑章节首尾导航，而是把原始章节正文按范围切给多个 worker；
- 每个 worker 直接阅读自己负责区间的完整章节正文，只产出事实包、风险发现、追踪人物和未闭合问题；
- 主控合并 worker 事实包，再抽少量关键章补读/复核并生成统一报告；
- 产物里会单独记录 `raw_chunk_chapter_count` 和 `raw_chunk_ranges`，不把它伪装成 `navigation_count`。

示例：

```powershell
python scripts\book_scan_two_stage_lab.py `
  --book "C:\Users\joest\Documents\leidian14\Pictures\txt\元始法则.txt" `
  --model grok-4.5 `
  --api-url https://pipio.io/v1/chat/completions `
  --api-key-env PIPIO_API_KEY `
  --reasoning-effort auto --planning-reasoning-effort auto `
  --full-flow --batch-size 60 --parallel-workers 2 --parallel-mode raw-chunks `
  --report-style bold `
  --name yuanshi-grok45-parallel2 `
  --reset
```

Pipio Grok 示例（从项目 `.env` 读取 `PIPIO_API_KEY`）：

```powershell
python scripts\book_scan_two_stage_lab.py `
  --book "C:\Users\joest\Documents\leidian14\Pictures\txt\白蛇 作者：lingyungzs.txt" `
  --model grok-4.5 `
  --api-url https://pipio.io/v1/chat/completions `
  --api-key-env PIPIO_API_KEY `
  --reasoning-effort auto --planning-reasoning-effort auto `
  --full-flow --batch-size 60 `
  --name white-snake-grok45-full-flow-auto `
  --reset
```
