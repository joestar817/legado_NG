# AI Provider 接入需求与实施计划

## 背景

阅读NG 后续 AI 功能的第一类落点是阅读场景内的轻量调用，例如：

- 章节净化规则候选生成：`system prompt + 当前章节/段落 -> pattern/isRegex 候选`
- 书源调试辅助：根据网络日志、书源规则、调试日志给出定位建议
- 后续更复杂能力：可再接 MCP、skill、脚本验证或多轮工具调用

本阶段目标不是先做具体 AI 功能，而是把 App 内 AI Provider 基础打好。当前临时实现是单 provider 的 Preference 配置页，已经暴露出两个问题：

- 产品形态不对：不能像参考截图那样管理提供商、填写 Base URL/API Key、拉取模型列表并选择模型。
- 协议兼容不够：OpenAI 兼容响应只按最窄字段解析，空响应诊断也不够可定位。

因此后续不应继续在当前 Preference 页上做零散补丁，而应按 cc-switch 的 provider 配置、模型获取和接口探测策略，结合 RikkaHub 的 provider 分层思路，重做一版适合 Legado 原生 View 体系的实现。

## 参考来源

主参考项目：`D:\code\cc-switch-main`

cc-switch 是本轮实现的优先依据，尤其用于：

- 内置 provider 范围
- Base URL / models URL
- 模型列表获取候选逻辑
- 接口探测和错误摘要策略
- DeepSeek / Xiaomi MiMo 等用户主要测试 provider 的默认模型和 reasoning 参数

关键参考文件：

- `src/config/codexProviderPresets.ts`
  - DeepSeek OpenAI Chat 兼容配置：
    - `base_url = https://api.deepseek.com`
    - 默认模型 `deepseek-v4-flash`
    - 模型目录 `deepseek-v4-flash`、`deepseek-v4-pro`
    - reasoning 输出字段为 `reasoning_content`
  - Xiaomi MiMo OpenAI Chat 兼容配置：
    - `base_url = https://api.xiaomimimo.com/v1`
    - 默认模型 `mimo-v2.5-pro`
    - reasoning 输出字段为 `reasoning_content`
- `src/config/claudeProviderPresets.ts`
  - DeepSeek Anthropic 兼容层：
    - `ANTHROPIC_BASE_URL = https://api.deepseek.com/anthropic`
    - `modelsUrl = https://api.deepseek.com/models`
  - Xiaomi MiMo Anthropic 兼容层：
    - `ANTHROPIC_BASE_URL = https://api.xiaomimimo.com/anthropic`
  - Gemini Native:
    - `ANTHROPIC_BASE_URL = https://generativelanguage.googleapis.com`
- `src-tauri/src/services/model_fetch.rs`
  - `models_url` 覆写优先。
  - 否则根据 baseURL 构造候选模型端点。
  - 支持剥离 `/anthropic`、`/api/anthropic`、`/apps/anthropic`、`/api/coding` 等兼容子路径后再探测模型端点。
  - 404/405 时继续尝试下一个候选；其它错误直接返回；错误体截断到 512 字符。
- `src/components/providers/forms/shared/ModelInputWithFetch.tsx`
  - 模型输入框旁提供获取按钮。
  - 获取后按 `ownedBy` 分组展示模型下拉。
- `src/components/providers/forms/shared/EndpointField.tsx`
  - Endpoint 支持完整 URL 开关。
  - 支持“管理和测速”入口。

辅助参考项目：`D:\code\rikkahub-master`

RikkaHub 只作为 provider 抽象和配置 UI 信息架构参考，不作为本轮默认 provider 数量和 DeepSeek/MiMo URL 的权威来源。

关键参考文件：

- `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`
  - Provider 分为 OpenAI、Google、Claude。
  - Provider 自带 `enabled/name/models/apiKey/baseUrl`。
  - OpenAI 额外有 `chatCompletionsPath/useResponseApi/includeHistoryReasoning`。
  - Google 默认 `https://generativelanguage.googleapis.com/v1beta`。
  - Claude 默认 `https://api.anthropic.com/v1`。
- `app/src/main/java/me/rerere/rikkahub/data/datastore/DefaultProviders.kt`
  - 提供 provider 列表设计参考。
  - 不作为本轮内置 provider 数量和 DeepSeek/MiMo 默认 URL 的优先依据。
- `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigure.kt`
  - Provider 详情表单包含名称、API Key、Base URL、OpenAI API path、启用开关等。
  - API Key 有显示/隐藏。
  - Base URL 做合法性校验。
- `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt`
  - provider 详情页调用 `listModels(providerSetting)` 获取模型候选。
  - 模型列表与 provider 绑定，不是全局手填一个模型名。
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/ChatCompletionsAPI.kt`
  - OpenAI 兼容响应解析要考虑 `content`、`reasoning_content`、`reasoning`、`tool_calls` 等字段。

## 总目标

实现一个可长期扩展的 App 内 AI Provider 管理基础：

1. 用户可以像参考截图那样看到提供商列表。
2. 用户可以选择内置 provider 或新增自定义 provider。
3. 用户可以在详情页填写 `Base URL`、`API Key`、provider 名称和协议专属配置。
4. 用户可以从当前 provider 拉取模型列表，并选择默认模型。
5. AI 调用层不依赖 UI，后续净化规则、书源调试、MCP 工具调用都复用同一入口。
6. 错误反馈必须能定位是配置错误、模型不兼容、接口字段解析失败，还是 provider 返回空内容。

## 非目标

- 本阶段不做完整聊天界面。
- 本阶段不做 AI 自动改写章节正文。
- 本阶段不做 MCP 工具调用集成。
- 本阶段不引入 RikkaHub 的完整 Compose UI、会话树、tool calling、图片生成、语音能力。
- 本阶段不直接搬入 RikkaHub 模块依赖；Legado 当前仍以原生 View/XML 为主。
- 本阶段不把 API Key 上传、同步或暴露给 Web/MCP 服务。

## 核心决策

### D1. 当前单 provider Preference 页废弃

当前 `AI 设置` 的单页 Preference 形态只能作为验证代码，不作为最终产品形态继续堆功能。

后续实现应替换为：

- `AI 设置`：Provider 列表页
- `Provider 详情`：配置表单 + 模型管理
- `模型选择`：从 provider 详情页拉取并选择，不再让用户先盲填模型名

### D2. 数据模型改为 provider 列表

应从单组偏好字段升级为列表结构：

- `aiProvidersJson`：保存 provider 列表
- `aiActiveProviderId`：当前默认 provider
- `aiActiveModelId` 或 provider 内默认模型：当前默认模型

Provider 基础字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 本地唯一 id，内置 provider 使用稳定 id |
| `type` | `openai` / `google` / `claude` |
| `enabled` | 是否启用 |
| `builtIn` | 是否内置 provider |
| `name` | 显示名称 |
| `apiKey` | 用户填写的 Key |
| `baseUrl` | API Base URL |
| `models` | 当前 provider 已拉取/选择的模型列表 |

OpenAI 兼容 provider 额外字段：

| 字段 | 说明 |
| --- | --- |
| `chatCompletionsPath` | 默认 `/chat/completions` |
| `useResponseApi` | 预留，默认 false |
| `includeHistoryReasoning` | 预留，默认 true |

### D3. P0 内置 provider 收缩到可测试范围

第一版内置 provider 不追求“大而全”。为了减少测试矩阵和维护成本，只内置用户可重点验证的 provider：

| 名称 | 类型 | Base URL | 默认模型 | models URL / 获取策略 |
| --- | --- | --- | --- | --- |
| OpenAI | OpenAI-compatible | `https://api.openai.com/v1` | 空，用户拉取后选择 | `{base}/models` |
| Claude | Claude-native | `https://api.anthropic.com/v1` | 空，用户拉取后选择 | `{base}/models` |
| Gemini | Google-native | `https://generativelanguage.googleapis.com/v1beta` | 空，用户拉取后选择 | `{base}/models?pageSize=100` |
| DeepSeek | OpenAI-compatible | `https://api.deepseek.com` | `deepseek-v4-flash` | 优先 `https://api.deepseek.com/models`，否则走 cc-switch 候选逻辑 |
| Xiaomi MiMo | OpenAI-compatible | `https://api.xiaomimimo.com/v1` | `mimo-v2.5-pro` | 走 cc-switch 候选逻辑，默认可先使用内置模型目录 |

说明：

- “御三家”指 OpenAI、Claude、Gemini，按各自官方 API 形态实现。
- DeepSeek 是主要测试 provider，Base URL、默认模型、模型列表和 reasoning 字段必须优先对齐 cc-switch。
- Xiaomi MiMo 作为 P0 国内 provider 补充，默认按 cc-switch OpenAI Chat 兼容配置。
- AiHubMix、硅基流动、OpenRouter 等暂不内置；后续通过“自定义 OpenAI 兼容 provider”覆盖。

后续可选内置 provider 必须先有测试 API Key 或用户明确要求，否则不进入默认列表。

### D4. UI 用 Legado 原生 View 实现，不引入 Compose

RikkaHub 是 Compose UI，Legado 当前设置和主界面主要是 XML/View/Preference。

实施时参考 RikkaHub 的信息架构和交互，不直接引入 Compose：

- Provider 列表：RecyclerView + provider item card/row
- Provider 详情：XML 表单 + EditText/Switch/Button/RecyclerView
- API Key：支持显示/隐藏
- 模型管理：按钮触发拉取，列表展示模型，点击设为默认

### D5. 协议层要可测试

Provider 调用层必须拆出可单测的解析函数：

- OpenAI `listModels` 响应解析
- OpenAI chat completion 响应解析
- Google models / generateContent 响应解析
- Claude models / messages 响应解析
- provider 错误体提取

测试连接不能只看 HTTP 200，必须判断是否能拿到可用于业务的文本结果。

### D6. 模型列表获取以 cc-switch 候选逻辑为准

模型列表不能固定拼接单一路径。P0 应实现类似 cc-switch `model_fetch.rs` 的候选策略：

1. 如果 provider 配置了 `modelsUrl`，只请求该 URL。
2. 否则如果 baseURL 以 `/v{N}` 版本段结尾，优先请求 `{base}/models`。
3. 如果 baseURL 不带版本段，优先请求 `{base}/v1/models`。
4. 如果 baseURL 命中已知兼容子路径，例如 `/anthropic`、`/api/anthropic`、`/apps/anthropic`、`/api/coding`、`/claude`，剥离该后缀后追加 `{root}/v1/models` 和 `{root}/models` 候选。
5. 404/405 继续尝试下一个候选；其它 HTTP 错误直接返回可读错误摘要。
6. 错误体截断，避免 HTML 错误页撑爆 UI。

### D7. 接口探测不等于模型列表获取

模型列表只证明鉴权和模型端点可用；接口探测还需要用当前模型发一次真实生成请求。

P0 的“接口探测/测试连接”应显示：

- 实际请求的 provider 名称、Base URL、模型
- HTTP 状态或网络错误
- 响应是否包含正文 content
- 响应是否只有 reasoning，没有普通 content
- 错误体摘要
- 耗时

## 需求矩阵

| ID | 优先级 | 需求 | 验收标准 | 参考/备注 |
| --- | --- | --- | --- | --- |
| AI-P0-01 | P0 | `AI 设置` 展示 provider 列表 | 进入 AI 设置后只看到 OpenAI、Claude、Gemini、DeepSeek、Xiaomi MiMo 这 5 个内置 provider；每项显示名称、类型、启用状态、模型数量 | 内置范围以用户可测试为准，不做大而全 |
| AI-P0-02 | P0 | Provider 详情页可编辑名称、Base URL、API Key | 用户可修改并保存；返回列表后状态保持 | RikkaHub `ProviderConfigure` |
| AI-P0-03 | P0 | API Key 支持隐藏/显示 | 默认隐藏，点击图标切换明文/密文 | 避免明文常驻屏幕 |
| AI-P0-04 | P0 | OpenAI 兼容 provider 支持 Chat Completions Path | DeepSeek 和 MiMo 使用 Chat Completions；默认 path 按 baseURL 形态正确拼接，不重复 `/v1` | 参考 cc-switch OpenAI Chat 兼容配置 |
| AI-P0-05 | P0 | Base URL 合法性校验 | 非 URL 或空路径异常时详情页提示，不允许静默保存为不可用配置 | 可先保存但测试/拉模型必须明确报错 |
| AI-P0-06 | P0 | 拉取模型列表 | 点击“获取模型列表”后按 `modelsUrl` 覆写或 cc-switch 候选 URL 逐个尝试；成功后展示模型列表 | 参考 `model_fetch.rs` |
| AI-P0-07 | P0 | 选择默认模型 | 模型列表中点击/勾选模型后，provider 默认模型生效；列表页显示模型数量/默认模型 | 供后续净化功能调用 |
| AI-P0-08 | P0 | 接口探测/测试连接 | 使用当前 provider + 当前模型发送小请求；成功必须有可用文本或明确显示 reasoning-only；失败显示 HTTP code、错误体摘要、实际 URL 和耗时 | 当前“empty content”要给出更具体诊断 |
| AI-P0-09 | P0 | OpenAI 兼容响应解析增强 | 支持 `message.content`、`reasoning_content`、`reasoning`；如果正文为空但有 reasoning，要显示“仅返回 reasoning，无正文” | 参考 RikkaHub `parseMessage` |
| AI-P0-10 | P0 | 默认 provider URL 准确 | DeepSeek 使用 `https://api.deepseek.com`，MiMo 使用 `https://api.xiaomimimo.com/v1`；DeepSeek Anthropic 兼容层只作为参考，不用于 P0 默认 Chat provider | 以 cc-switch 为准 |
| AI-P0-11 | P0 | 当前单 provider 偏好迁移 | 如果用户已填写当前临时字段，首次进入新版时迁移为一个自定义 provider 或覆盖对应内置 provider | 避免用户重新填 Key |
| AI-P0-12 | P0 | 不影响 MCP | AI provider 是 App 内部调用基础；MCP 服务开关、端口和工具不受影响 | 维持提交边界 |
| AI-P0-13 | P0 | P0 内置 provider 数量受控 | 默认只内置 OpenAI、Claude、Gemini、DeepSeek、Xiaomi MiMo | 没有 API Key 可测的 provider 不进入默认列表 |
| AI-P0-14 | P0 | DeepSeek 优先验收 | DeepSeek 能完成填 Key、拉模型或使用内置模型、选择 `deepseek-v4-flash`、测试连接 | 用户主要测试 provider |
| AI-P0-15 | P0 | MiMo 基础可配置 | MiMo 预置 `mimo-v2.5-pro` 和 `https://api.xiaomimimo.com/v1`，支持测试连接 | 参考 cc-switch |
| AI-P1-01 | P1 | 新增自定义 provider | 用户可从 OpenAI/Google/Claude 类型创建自定义 provider | 用于 AiHubMix、硅基流动、OpenRouter 等非 P0 内置 provider |
| AI-P1-02 | P1 | 删除/禁用 provider | 内置 provider 可禁用；自定义 provider 可删除 | 类似 RikkaHub 管理能力 |
| AI-P1-03 | P1 | 模型别名/显示名 | 模型可显示 `displayName`，必要时允许用户改显示名 | 后续体验优化 |
| AI-P1-04 | P1 | 多模型用途配置 | 区分默认文本模型、净化模型、调试模型 | 一键净化可用低温模型 |
| AI-P1-05 | P1 | 错误日志接入 App 日志 | AI 请求失败时写入可选调试日志，不泄露 API Key | 方便用户截图定位 |
| AI-P2-01 | P2 | 余额查询 | 部分 provider 支持余额 API | 参考 RikkaHub `BalanceOption` |
| AI-P2-02 | P2 | Response API | OpenAI 官方可选 `/responses` | 当前不阻塞净化功能 |
| AI-P2-03 | P2 | Claude prompt cache | Claude 专属增强 | 当前不阻塞 |
| AI-P2-04 | P2 | Google Vertex AI | Google 专属增强 | 当前不阻塞 |

## 页面结构草案

### AI 设置页

第一屏是 provider 列表，不是表单。

每个 provider item 显示：

- 图标占位或类型标识
- provider 名称
- 类型：OpenAI / Gemini / Claude
- 启用状态
- 模型数量
- 当前默认模型
- 右侧菜单：编辑、启用/禁用、删除自定义 provider

顶部操作：

- 返回
- 新增 provider，P0 可暂不开放
- 可选：导入/导出 provider 配置

### Provider 详情页

区域顺序：

1. 基础信息
   - Provider 类型
   - 启用开关
   - Provider 名称
2. 凭据和地址
   - API Key，带显示/隐藏
   - Base URL，带合法性提示
   - OpenAI Chat Completions Path
   - models URL 覆写，高级项，默认隐藏
3. 模型
   - 获取模型列表
   - 当前默认模型
   - 模型列表
4. 验证
   - 接口探测/测试连接
   - 最近一次错误摘要
   - 最近一次请求耗时

## Provider 调用层设计

### 包结构建议

```text
io.legado.app.help.ai
├── AiManager.kt
├── AiProvider.kt
├── AiProviderSetting.kt
├── AiProviderStore.kt
├── AiDefaultProviders.kt
├── AiModelEndpointResolver.kt
├── AiModel.kt
├── AiMessage.kt
├── AiTextResult.kt
├── OpenAiCompatibleProvider.kt
├── GoogleAiProvider.kt
├── ClaudeAiProvider.kt
└── parser/
    ├── OpenAiResponseParser.kt
    ├── GoogleResponseParser.kt
    └── ClaudeResponseParser.kt
```

### 调用入口

后续业务层只调用：

```kotlin
AiManager.generateText(
    providerId = activeProviderId,
    modelId = activeModelId,
    messages = messages,
    params = AiTextParams(...)
)
```

业务层不直接拼 URL、不直接读 API Key、不关心 provider 类型。

## 测试计划

### 单元测试

必须补：

- OpenAI 模型列表解析：
  - 标准 `{ "data": [{ "id": "..." }] }`
  - 空 data
  - 错误体
- 模型列表 URL 候选构造：
  - `https://api.deepseek.com` -> `https://api.deepseek.com/v1/models`
  - `modelsUrl=https://api.deepseek.com/models` 时只使用覆写 URL
  - `https://api.deepseek.com/anthropic` -> 包含剥离 `/anthropic` 后的候选
  - `https://api.xiaomimimo.com/v1` -> `https://api.xiaomimimo.com/v1/models`
- OpenAI 生成响应解析：
  - `choices[0].message.content`
  - `choices[0].message.reasoning_content`
  - content 为空但 reasoning 非空
  - choices 为空
  - finish_reason 异常
- Google 模型列表解析：
  - 只保留支持 `generateContent` 的模型
- Claude 模型列表解析：
  - `data[].id/display_name`

### 本地假服务测试

建议增加一个脚本或单测 mock server：

- 模拟 `/v1/models`
- 模拟 `/v1/chat/completions`
- 模拟返回空 content、reasoning only、错误体

这样不会依赖用户真实 API Key，也不会把 token 浪费在真实请求上。

### 手动验收

最小手动流程：

1. 打开 `我的 -> AI 设置`。
2. 看到 provider 列表。
3. 进入 DeepSeek 详情。
4. 填写 API Key。
5. 点击获取模型列表；如果 DeepSeek 模型端点不可用，仍可使用内置 `deepseek-v4-flash/deepseek-v4-pro` 模型目录继续测试。
6. 选择 `deepseek-v4-flash`。
7. 点击接口探测/测试连接。
8. 成功时显示非空响应；失败时显示具体失败阶段和错误摘要。

## 实施计划

### 阶段 0：冻结并清理当前临时实现

目标：停止在当前 Preference 页上继续堆功能。

任务：

- 标记当前单 provider 偏好字段为迁移来源。
- 保留 provider 网络调用中有价值的部分。
- 废弃当前 `pref_config_ai.xml` 的最终 UI 方案。

验收：

- 文档确认后再进入代码重构。

### 阶段 1：Provider 数据模型和存储

目标：从单 provider 偏好升级为 provider 列表。

任务：

- 新增 `AiProviderStore`。
- 新增 `AiDefaultProviders`，P0 只内置 OpenAI、Claude、Gemini、DeepSeek、Xiaomi MiMo。
- 新增模型端点解析逻辑，参考 cc-switch `model_fetch.rs`。
- 支持旧单 provider 偏好迁移。
- 支持 active provider/model。

验收：

- 无 UI 也能通过测试读取 5 个默认 provider。
- DeepSeek/MiMo 默认 baseURL 和模型符合 cc-switch。
- 旧 Key/Base URL 可以迁移。

### 阶段 2：Provider 列表页

目标：实现接近截图的信息架构。

任务：

- 将 `AI 设置` 改为 provider 列表 Fragment。
- 增加 provider item 布局。
- 支持启用/禁用、进入详情。
- 暂不追求完整图标资产，先用类型 tag 或简单图标占位。

验收：

- 第一屏不是字段表单，而是 provider 列表。

### 阶段 3：Provider 详情页

目标：实现可用配置表单。

任务：

- 名称、API Key、Base URL、OpenAI path、启用开关。
- API Key 显示/隐藏。
- Base URL 校验。
- 保存后返回列表更新。

验收：

- 能完成 Base URL/API Key 填写，不需要盲填模型。

### 阶段 4：模型列表和测试连接

目标：完成 provider 到模型的闭环。

任务：

- `listModels(provider)` 按 cc-switch 候选逻辑拉取模型。
- 模型列表绑定 provider 存储。
- 选择默认模型。
- 接口探测/测试连接使用默认模型。
- 错误显示改为可定位摘要。

验收：

- 用户能用 DeepSeek 完成“填 Key -> 拉模型或使用内置模型 -> 选模型 -> 接口探测”。

### 阶段 5：净化规则候选功能接入

目标：AI provider 基座可被业务使用。

任务：

- 阅读页弹出菜单加入“一键净化”入口。
- 当前章节/选中段落传给 AI。
- AI 只输出规则候选：`pattern/isRegex`。
- 用户确认后生成 Legado `replace_rules` 字段。

验收：

- 不直接改写正文。
- 用户确认前不落库。

## 风险和控制

| 风险 | 控制 |
| --- | --- |
| 参考源冲突 | base URL、模型列表、接口探测以 cc-switch 为准；RikkaHub 只参考 provider 抽象和 UI 层级 |
| 内置 provider 过多无法测试 | P0 只内置 OpenAI、Claude、Gemini、DeepSeek、Xiaomi MiMo |
| 直接照搬 RikkaHub 依赖过重 | 只参考 provider 数据结构和交互，不引入完整 Compose/聊天模块 |
| OpenAI 兼容服务响应差异大 | 解析器单测覆盖 content/reasoning/error/empty |
| API Key 泄露 | UI 默认隐藏；日志不得打印 Authorization/API Key |
| 模型列表过大 | 存储只保留 `id/displayName/type/ability` 必要字段，UI 分页或搜索后续再做 |
| 当前临时配置丢失 | 做一次迁移，把旧字段合并进 provider 列表 |
| 净化功能被误理解为 AI 改文 | 明确只生成替换规则候选，用户确认后才写入规则 |

## 下一步确认点

实施前需要确认：

1. 第一版是否必须支持多个 provider 同时保存。
   - 建议：必须支持。否则无法达到参考截图的 provider 列表体验。
2. 是否需要在 P0 支持新增自定义 provider。
   - 建议：P0 可以先不做完整新增，只保留 5 个内置 provider 和编辑配置；自定义放 P1。
3. 是否允许新增一套 XML/View 页面替代 `PreferenceFragment`。
   - 建议：允许。继续用 Preference 很难做出参考图的体验。
4. 是否把当前临时 AI 改动先整体撤回后重做。
   - 建议：保留 provider 网络层中可用代码，UI 和偏好存储重做。
5. P0 MiMo 是否只保留 `https://api.xiaomimimo.com/v1`，不内置 Token Plan。
   - 建议：只保留普通 MiMo；Token Plan 需要单独 Key/套餐，放 P1 或自定义 provider。
