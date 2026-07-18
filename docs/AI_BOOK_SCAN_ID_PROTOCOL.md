# AI 扫书 ID 协议边界

## 目标

凡是产品需要稳定复用、匹配、筛选、聚合、渲染或写入的内容，都由只读 System Workflow 协议声明；模型只返回稳定 ID 和证据。模型不得创建近义标签、改写 UI 模板或决定持久化目标。

自然语言报告仍由模型生成。宿主不使用关键词或正则审查报告文风，只校验工具参数、枚举、目录成员和写入权限。

## 字段所有权

| 区域 | 模型返回 | 协议与宿主负责 | 允许自由文本 |
| --- | --- | --- | --- |
| 交互 | `interaction_id`、动态 `item_ids` | 类型、版本、标题、说明、选项、按钮、prompt、写入模板 | 无 |
| 用户选择 | `item_id + option_id` | 选项显示名、保存回显、偏好写入 | 用户主动输入的原话 |
| 作品标签 | `tag_id`、命中状态、强度、证据 | 标签名称、定义、排除边界、提问策略 | 证据摘要 |
| 风险 | `risk_kind`、状态、等级、证据 | 风险名称及固定分类 | 事件经过、影响和结果说明 |
| 感情结构 | structure/tone/outcome/commitment ID | 中文显示名及到作品标签的投影 | 关系事实说明 |
| 内容强度 | `content_type_id`、强度、状态、证据 | “酷刑、暴力、亲人死亡”等标准名称 | 具体发生了什么 |
| 作品类型 | `genre_ids/component_ids/driver_ids` | 标准类型名称和目录关系 | 速览中的自然语言解释 |
| 事件与人物 | 可枚举状态使用 ID | 固定状态显示名 | 人名、行为、心理原文和事件事实 |
| Memory 外壳 | 业务数据 ID 与证据 | scope/domain/type/schema/title/source/status | 导航摘要 |
| 报告 | 引用已确认事实 ID | 向模型提供 ID 对应定义 | 推荐判断和报告正文 |

凡是进入偏好连接、候选筛选、风险合并或统计的字段，必须使用 ID；自由文本只能解释，不能参与机器匹配。

## 交互协议

静态交互只需要：

```json
{"id":"book_scan_next"}
```

动态偏好交互只需要：

```json
{
  "id": "reader_preference_adaptive_tags",
  "item_ids": ["route.single_partner", "tone.dark_cruel"]
}
```

宿主根据 `interaction-policy.json` 生成完整 `AiChatInteraction`。模型即使同时输出标题、标签文案、按钮或写入字段，宿主也忽略这些副本，以协议值为准。

拒绝条件只包括：

- 未声明的 interaction ID；
- 未进入允许目录的 item ID；
- 重复、空或超出数量上限的 item ID；
- 当前 Agent Mode 无权执行的写入。

不能因为标题、按钮或说明没有逐字一致而拒绝。

## 事实协议

当前 `window_bundle v8` 已经要求 `tag_hits[].tag_id` 和 `risks[].risk_kind`，但仍有三类缺口：

1. 标签目录是 Markdown，交互策略和离线评测各自复制了一份映射；
2. 宿主写入前只校验证据章节范围，没有校验 catalog ID 和枚举；
3. `work_components`、`content_intensity.type`、`commitment_state` 等仍可能产生近义自由文本。

收口顺序：

1. 将现有目录无语义变更地转成机器可读 catalog，保留 `reader_tag_catalog_version=2`，单独增加格式版本；
2. `interaction-policy`、事实协议和离线评测共同引用该 catalog，不再复制标签名称；
3. 在 v8 中新增可选 `genre_ids/component_ids/driver_ids/content_type_ids/commitment_ids`，报告匹配只读取 ID，自由文本只展示；
4. 宿主根据只读 `fact-protocol.json` 校验 JSON 类型、枚举和 catalog 成员后再落盘；
5. 稳定后才升级严格事实 schema。协议格式变化不得让现有《白蛇》档案自动失效或再次读取正文。

模型无法在目录中找到精确 ID 时，只能省略、保留 unknown，或在允许的状态字段中标为 suspected；不得创建相近 ID，也不得由宿主做模糊映射。

## 兼容边界

- 当前开发态不为旧发布数据添加隐式迁移。
- 本轮交互归一化必须兼容已经保存的完整交互块，使现有报告无需重新调用模型即可恢复 UI。
- 事实目录机器化不得改变现有稳定 ID 的语义，也不得因为文件格式改变触发重扫。
- 将来严格升级事实 schema 时，只允许用户明确触发“基于现有档案整理”；无法精确映射的自由文本保持未知，禁止重读正文或猜测映射。
