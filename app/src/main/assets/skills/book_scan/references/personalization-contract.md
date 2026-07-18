# 个性化推书契约 v2

本文件冻结稀疏读者偏好、作品标签命中、基础风险与快速定位适配结论。它属于 BookScan System Workflow；App、Kotlin、Room、Agent Kernel 和通用 MCP 不得复制题材、标签、风险或枚举语义。

作品成分、可学习偏好、严重风险和深度分析信号统一使用[读者标签库](reader-tags/index.md)中的稳定 `tag_id`。作品标签是事实，用户态度是偏好，两者不得写在同一字段。

## AgentMemory 范围

- 通用偏好：`scope_type=global`、`scope_key=default`、`domain=reader_preference`、`memory_type=global_profile`
- 分类偏好：`scope_type=global`、`scope_key=default`、`domain=reader_preference`、`memory_type=category_profile`
- 单书事实：`scope_type=book`、`scope_key=入口固定的原样 work_key`、`domain=book_scan`

同一范围只保留一条 `global_profile`。更新前精确查询并复用已有记忆 `id`。不得从阅读时长、书架、弃书、跳过问题或模型猜测静默学习。

## 稀疏通用偏好 schema

```json
{
  "schema_version": 2,
  "profile_id": "default",
  "dimension_answers": {
    "relationship_routes": {
      "answer_type": "multi|unrestricted",
      "values": ["route.no_romance|route.single_partner|route.multi_partner"],
      "source_kind": "ui_choice|explicit_text",
      "source_text": "用户明确表达的原话",
      "confirmed_by_user": true
    },
    "narrative_centers": {
      "answer_type": "multi|unrestricted",
      "values": ["center.single_male|center.multi_male_ensemble"],
      "source_kind": "ui_choice|explicit_text",
      "source_text": "用户明确表达的原话",
      "confirmed_by_user": true
    },
    "defense_level": {
      "answer_type": "single",
      "values": ["defense.god|defense.heavy|defense.cloth|defense.low|defense.negative"],
      "source_kind": "ui_choice|explicit_text",
      "source_text": "用户明确表达的原话",
      "confirmed_by_user": true
    }
  },
  "tag_stances": {
    "标签库稳定 tag_id": {
      "stance": "like|neutral|avoid",
      "source_kind": "ui_choice|explicit_text",
      "source_text": "用户明确表达的原话",
      "confirmed_by_user": true
    }
  },
  "custom_rules": []
}
```

- `dimension_answers`、`tag_stances` 和 `custom_rules` 可以为空；没有字段或没有键就是 `unknown`，不是喜欢或默认接受。
- `neutral` 是用户明确的“无所谓”，属于已知态度。
- “都可以”使用 `answer_type=unrestricted, values=[]`，不伪造一个作品标签。
- 每个答案自己保存来源和确认状态。更新只合并本次明确项，禁止丢失旧项。
- `schema_version=1` 不静默迁移，不得用其阻塞快速定位。

## 分类自然语言规则

```json
{
  "rule_id": "custom:稳定 id",
  "scope": "global|category:<category_id>",
  "stance": "prefer|avoid|monitor",
  "text": "经用户明确确认的规则",
  "source_text": "用户原话",
  "confirmed_by_user": true
}
```

单书抱怨默认不泛化。只有用户明确说明对以后同类作品也适用，才能写入全局或分类规则。

## 单书标签命中 schema

`tag_hits` 属于 `window_bundle` 事实，同一本书面对不同读者必须一致：

```json
{
  "tag_id": "标签库稳定 id",
  "detection_status": "confirmed|suspected",
  "intensity": "low|medium|high",
  "evidence_refs": []
}
```

- 单一关键词不足以确认标签；必须符合标签库 `definition`，并排除 `exclude`。
- 快扫不得将 `detectability=low` 或 `analysis_only` 写成 `confirmed`。
- `tag_hits` 不得保存 `stance`、适配结论或偏好推断。

## 基础风险 schema

```json
{
  "schema_version": 1,
  "risk_kind": "risk-tags.md 中的稳定 tag_id",
  "base_level": "god_risk|risk|frustrating",
  "evidence_status": "confirmed|ongoing|threatened|outcome_unknown|reversed",
  "relationship_context": {
    "before": "事件前关系",
    "commitments": [],
    "consent_context": "正文明确的意愿；无证据为 unknown"
  },
  "reader_consequence": {
    "affected": [],
    "irreversible_outcome": "",
    "relationship_impact": ""
  },
  "evidence_refs": []
}
```

- 只有 `confirmed|ongoing` 进入已确认避坑警告。
- `threatened|outcome_unknown` 只能作为低剧透未知，不得写成已执行结果。
- `reversed` 保留原事件与反转证据，不删除历史事实。
- NTR、送女、恶性强迫婚配等 `always_warn` 风险只预警，不询问用户是否喜欢。
- 暴力、酷刑、受辱、亲人死亡、永久伤残和长期压抑先写 `content_intensity`，不自动升级为关系神雷。

## 个性化适配

报告阶段在当前上下文内形成以下内部结构，不写 assessment 记忆，不输出 JSON 或审计字段：

```json
{
  "fit_level": "high_match|match|tradeoff|mismatch|avoid|insufficient",
  "match_evidence": [],
  "conflict_evidence": [],
  "decisive_unknowns": [],
  "confidence": "high|medium|low",
  "coverage_boundary": "实际首尾覆盖"
}
```

- 已知 `like` 与命中标签构成匹配，`avoid` 构成冲突，`neutral` 不升降级，未知不得代替用户表态。
- 档案为空时仍根据作品成分、作品受众和已确认严重风险给出当前建议；不得伪装成高精度个性化结论。
- 防御等级只改变内容强度和一般风险对推荐的影响，不隐藏事实。防御未知时只客观说明强度。
- 作品类型与当前口味冲突只表示不适合当前读者，不自动变成作品缺陷。

## 快扫未知边界

1. 开头只支持题材、开篇钩子、主角初始路线、早期关系、开篇节奏和已发生风险。
2. 结尾只支持结局状态、关系归宿、不可逆结果和仍在持续的风险。末尾番外是结局证据，不是“提前剧透”。
3. 首尾不是连续剧情。未读中段的人物成长、关系过程、伏笔、节奏、剧情完成度、作者意图和隐藏风险一律保持未知。
4. 未知只在会改变推荐时简短呈现；不得为了填满固定栏目编造问题。
