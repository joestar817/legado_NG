# 渐进阅读偏好闭环 v2

本资源只处理 `global/default/reader_preference/global_profile`。字段契约见[个性化推书契约](references/personalization-contract.md)，标签定义见[读者标签库](references/reader-tags/index.md)。

## 扫描优先

1. 没有偏好档案、档案稀疏或当前书命中未知标签都不得阻断快速定位。
2. 先读取当前书首尾样本、保存中性事实并输出当前报告；不先展开问卷。
3. 报告末尾只让用户在 `继续排雷 / 已经劝退` 两条分支中选择，不直接展示抽象标签偏好表。继续排雷时可选记录当前书真实兴趣点；已经劝退时记录当前书问题和已确认风险；这些原因不自动泛化。
4. 已有事实完整时，任何偏好新增或修改都只重算报告，禁止重读正文。
5. 偏好档案不存在时保持不存在。模型不得创建空 `global_profile`，不得在用户选择前写入任何默认项；只有 App 在用户提交声明式 `memory_patch` 后执行写入。
6. 只有用户明确表示“以后同类作品也按这个口味判断”或主动要求保存长期偏好时，才从当前书已确认的中性标签中生成 `multi_tag_stance`；单次“这本书喜欢／不喜欢”只留在当前会话。

## 候选门禁

每个候选必须同时满足：

- 存在于当前 `window_bundle.tag_hits` 且 `detection_status=confirmed`，或能由 `relationship_profile.structure` 直接确认对应的中性感情路线；
- 标签库 `layer=adaptive`，或相关分类已命中时的 `layer=conditional`；
- 标签显式声明 `question_policy=selection_impact`；没有声明时默认 `never_ask`；
- 标签检测门槛与当前覆盖相符，快扫不得询问 `detectability=low`；
- `global_profile.tag_stances` 中没有该 `tag_id`；
- 用户回答会实质改变当前推荐或展示强度。

按决策价值排序后默认取 3 项；只有 1～2 项时不凑数，确有必要时最多 5 项。跨类别去重。`always_warn`、`analysis_only`、`never_ask`、负面人物／质量评价、`suspected`、未命中标签和已知态度不得进入问题卡。绿帽、性侵、严重虐待、强制婚配、强行复合、不可逆死亡等确定风险只警告，绝不转换成“是否喜欢”问题。

## 通用交互

用户明确要求把当前判断泛化为长期偏好，并且存在合法候选时，才输出一个动态 `legado-interaction`：

```legado-interaction
{
  "version": 2,
  "id": "reader_preference_adaptive_tags",
  "type": "multi_tag_stance",
  "title": "这些作品特征会怎样影响你的选择？",
  "description": "每项单选；只保存你明确选择的项目，未选择和跳过仍保持未知。",
  "options": [
    {"label": "更偏好这类作品", "value": "like"},
    {"label": "可以接受", "value": "neutral"},
    {"label": "会因此劝退", "value": "avoid"}
  ],
  "items": [
    {
      "id": "当前书已确认命中的稳定 tag_id",
      "label": "标签库显示名",
      "description": "标签库定义原文"
    }
  ],
  "memory_patch": {
    "operation": "json_map_merge",
    "scope_type": "global",
    "scope_key": "default",
    "domain": "reader_preference",
    "memory_type": "global_profile",
    "title": "默认阅读偏好",
    "content": "用户在扫书报告中明确选择的标签态度。",
    "map_field": "tag_stances",
    "value_field": "stance",
    "identity_fields": ["schema_version", "profile_id"],
    "base_data": {
      "schema_version": 2,
      "profile_id": "default",
      "dimension_answers": {},
      "tag_stances": {},
      "custom_rules": []
    },
    "on_base_mismatch": "replace"
  },
  "submit": {"label": "保存并更新推荐"},
  "skip": {"label": "暂时跳过"}
}
```

这是结构示意，实际输出优先使用最小宿主协议，只返回交互 ID 与 `item_ids`；标题、说明、选项、按钮、标签文案和 `memory_patch` 全部由 `interaction-policy.json` 注入。`items` 必须为 1～5 项并使用稳定 `tag_id`。只有用户显式保存时，App 才用 `base_data` 替换不匹配的遗留根对象并合并本次选择；每个被选标签写成 `{stance,source_kind=ui_choice,source_text="标签名：选项名",confirmed_by_user=true}`，旧未选项原样保留。这不是迁移旧字段。Skill 不直接调用记忆工具，App 不理解具体标签业务。没有合法候选时不输出空卡，继续保留单书反馈，不创建长期偏好。

## 明确反馈与写入

- 只有用户在卡片中选择 `like|neutral|avoid` 或明确将自然语言泛化到以后同类作品，才能写入长期偏好。
- `neutral` 是已确认的“可以接受”，以后不再重复询问。没有键才是 `unknown`。
- 未选择、跳过、关闭、停止扫描、弃书、删书、阅读时长和书架状态均不能产生偏好。
- “这本书的系统写得烦”、选择继续排雷／已经劝退以及原因多选默认都只是单书反馈；要写入全局档案，必须再确认是否适用于以后同类作品。
- 保存由交互块的声明式 `memory_patch` 执行，只合并本次有选择的 `tag_stances`，保留其它 `dimension_answers`、`tag_stances` 和 `custom_rules`，并复用现有记忆 `id`；基础档案不存在时使用 `base_data` 新建稀疏 v2。
- 用户点击保存已构成明确授权；写入失败必须保留本次选择并显示错误，不得假装已学习。
- 写入成功后，原报告卡保留在会话中并展示每个项目的确切已选状态；后续模型只输出“推荐已更新”的差量结论和一个结构化下一步交互，不用缩短版报告覆盖原内容。

## 旧档案

`schema_version=1` 的档案不得静默迁移或补默认值。内部开发期将它视为无有效偏好；扫描仍正常继续，用户下次明确反馈时按 v2 重建。
