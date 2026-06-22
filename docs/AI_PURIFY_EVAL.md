# AI 替换净化评测

## 目标

开发阶段不能只靠 App 弹窗人工点确认。评测分两层：

- live eval：使用 App 当前章节净化同款 JSON 输入格式、同款固定协议和默认提示词，调用真实模型，统计准确率。
- replay eval：不调用模型，只回放已观察到的坏响应，防止协议解析和基础校验回归。

## Live Eval

默认数据集：

- `app/src/test/resources/ai-purify-fixtures/shanheji_live_eval.json`
- 数据来源：`data/山河稷 作者：姬叉.txt` 前 50 章导出片段，以及已观察到的问题样例。

## 生成章节数据集

从小说原文生成每章 App 请求 payload 和人工复核用 expected 草稿：

```powershell
python .\scripts\build_ai_purify_dataset.py
```

默认输入：

- `data/山河稷 作者：姬叉.txt`
- 前 50 章
- 章节净化默认分批上限：10000 字

默认输出：

- `data/ai-purify-dataset/shanheji/payloads/*.json`
  - App 请求前的 user payload，格式为 JSON 数组 `[{ "id": 1, "input": "..." }]`。
- `data/ai-purify-dataset/shanheji/expected_draft/*.json`
  - 符合模型返回协议的 expected 草稿，格式为 JSON 数组 `[{ "id": 1, "output": "..." }]`。
- `data/ai-purify-dataset/shanheji/review/*.json`
  - 人工复核辅助文件，包含 `input`、`output`、命中的本地规则和 `status=draft`。
- `data/ai-purify-dataset/shanheji/shanheji_live_eval_draft.json`
  - live eval fixture，引用每章 payload 和 expected 草稿。

复核流程：

1. 先看 `review/*.json`，判断本地草稿是否正确。
2. 需要修改 gold 时，改同编号 `expected_draft/*.json`，只保留确认要模型返回的 `{id, output}`。
3. 正常段落不要写进 `expected_draft`，否则会被视为“应该修改”。
4. 复核后用 `shanheji_live_eval_draft.json` 跑 DeepSeek 对比。

`scripts/ai_purify_live_eval.py` 会优先读取 fixture 中的 `payloadFile` 和 `expectedDraftFile`，所以复核后不需要重新生成总 fixture。

运行方式：

```powershell
python .\scripts\ai_purify_live_eval.py `
  --model deepseek-v4-flash `
  --save-raw .\build\ai-purify-live-eval.json
```

对比复核后的前 50 章数据集：

```powershell
python .\scripts\ai_purify_live_eval.py `
  --fixture data\ai-purify-dataset\shanheji\shanheji_live_eval_draft.json `
  --model deepseek-v4-flash `
  --save-raw .\build\ai-purify-live-eval-shanheji.json
```

其它数据集：

```powershell
python .\scripts\build_ai_purify_dataset.py `
  --book "data\你个妖怪还想去西游？作者：梦雅 1-260 作者：.txt" `
  --out data\ai-purify-dataset\yaoguai_xiyou `
  --max-chapters 50

python .\scripts\build_ai_purify_dataset.py `
  --book "data\糖果魔法师与旅行⊙1-1110 作者：.txt" `
  --out data\ai-purify-dataset\tangguo_mofashi `
  --max-chapters 50
```

`糖果魔法师与旅行` 的源文件标题形如 `第1章(1)`，脚本会按前 50 个章节标题块生成数据集。该书 draft 规则会把 `灵梦`、免费外群/中转群、24 小时删除、支持正版、本群提取 VIP 章节等整段广告生成为 `output=""`。

如需测试思考模式：

```powershell
python .\scripts\ai_purify_live_eval.py `
  --fixture data\ai-purify-dataset\shanheji\shanheji_live_eval_draft.json `
  --model deepseek-v4-flash `
  --thinking enabled `
  --save-raw .\build\ai-purify-live-eval-shanheji-thinking.json
```

`--thinking disabled` 是 App 当前章节净化兼容默认值；`--thinking omit` 表示不发送 thinking 参数。

脚本会自动读取仓库根目录 `.env`，可直接使用其中的 `DEEPSEEK_API_KEY`、`AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL` 等配置；命令行参数优先级更高。

如使用 OpenAI-compatible 代理：

```powershell
$env:AI_API_KEY="你的 key"
$env:AI_BASE_URL="https://your-openai-compatible-host"
$env:AI_MODEL="deepseek-v4-flash"
python .\scripts\ai_purify_live_eval.py --save-raw .\build\ai-purify-live-eval.json
```

脚本会从源码中抽取：

- `AiPurifyHelper.kt` 中章节批量净化固定协议。
- `AiPromptStore.kt` 中 `CHAPTER_OPTIMIZE` 默认提示词。

模型请求的 user 内容与 App 章节批量净化一致，是 JSON 数组：

```json
[
  {"id": 75, "input": "「不可以幺？」"}
]
```

模型应该只返回发生删除或替换的段落：

```json
[
  {"id": 75, "output": "「不可以么？」"}
]
```

## Live 指标

- `accuracy`：整组段落准确率，包含应改段落改对，以及正常段落没有被误改。
- `expected_change_exact`：只看 `expectedChanges` 中应改段落是否精确匹配。
- `unchanged_correct`：不应改的段落保持不变数量。
- `false_negative`：该改但模型没返回，或没有改到期望结果。
- `false_positive`：不该改的正常正文被模型返回为变更。
- `exact_mismatch`：段落 id 对了，但 `output` 与人工期望不一致，例如 `不可以幺？ -> 不可以吗？`。
- `protocol_errors`：协议违规，例如返回旧字段 `cleaned/text/content`，或缺少 `output`。

默认情况下，`false_negative`、`false_positive`、`exact_mismatch` 或 `protocol_errors` 任一存在都会使脚本返回非 0。若只想临时忽略旧字段协议问题，可加 `--ignore-protocol-errors`。

注意：`expected_draft` 是脚本生成草稿，不等于人工 gold。复杂乱码书籍中，draft 可能保留了应删除的污染字符，因此 `exact_mismatch` 需要结合 raw 输出人工判断。

提示词边界：

- 明确污染、乱码、异常字符和明确错字必须处理，不能用“保守”作为漏处理理由。
- 保护原文语义是独立硬约束：不得把原本正确的词语、句式、语气、标点和人物表达改成更通顺或另一种意思。
- “不确定时保留原文”只适用于无法确认是否为污染或错字的内容。

## 当前基准

`deepseek-v4-pro --thinking disabled` 在提示词优化后，对《山河稷》前 50 章 draft fixture 的结果：

- `accuracy=97.7%`
- `expected_change_exact=94.2%`
- `false_negative=12`
- `false_positive=33`
- `exact_mismatch=35`
- `protocol_errors=0`
- App 可见变更 `811` 条，精确 `768` 条，可见精确率约 `94.7%`

raw 文件：

- `build/ai-purify-live-eval-shanheji-pro-disabled-prompt3.json`

`deepseek-v4-pro --thinking disabled` 在《你个妖怪还想去西游？》第一章 targeted 样例中，当前提示词可正确处理复杂污染：

- `这将⑸近十年 -> 这将近十年`
- `一搭没1∽∷...SＯuSＵo：一搭 -> 一搭没一搭`
- `夏暖liu轻抬蛇首顺着六他手指⒊的方向...四头顶上2 -> 夏暖轻抬蛇首顺着他手指的方向...头顶上`

raw 文件：

- `build/ai-purify-live-eval-yaoguai-xiyou-pro-disabled-prompt5-ch1.json`

## Replay Eval

Replay fixture：

- `app/src/test/resources/ai-purify-fixtures/shanheji_protocol_replay.json`

运行方式：

```powershell
.\gradlew.bat --console=plain :app:testAppDebugUnitTest --tests io.legado.app.help.ai.AiPurifyEvalTest
```

当前覆盖：

- 第 75 段结果错挂到第 74 段时，必须因 `source_mismatch` 拒绝。
- `不可以幺？ -> 不可以么？` 这种正确替换应通过。
- `不可以幺？ -> 不可以吗？` 协议可通过，但必须记为 `exactMismatches`。
- 局部污染字符删除时，应保留周围正文。
- 旧协议 `text` 字段响应必须因 `schema_error` 拒绝。

Replay 不启动 App、不点击 UI、不调用真实模型，只用于稳定回归。真实提示词效果以 live eval 为准。
