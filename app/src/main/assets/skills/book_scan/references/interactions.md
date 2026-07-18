# 快速定位后的交互定义

本资源只在用户操作快速报告底部交互后加载。此轮不读取正文、不查询记忆、不重复报告，只输出与用户选择对应的下一张宿主交互。固定标题、说明、选项、按钮和提示模板均来自包根目录的 `interaction-policy.json`；模型只返回 `<interaction ... />` 中的交互 `id` 和必要的 `item-ids`。只有需要挑选继续了解点或劝退原因时才读取该 JSON，当前会话已经成功加载则直接复用；静态下一步不得重复读取。

`item-ids` 的唯一合法集合是 `interaction-policy.json` 中目标交互的 `allowed_items` 键。每个 ID 必须逐字复制；报告中某个优缺点没有精确对应项时直接省略，禁止根据中文含义拼接 `feedback.*`、使用近义 ID 或发明新 ID。示例只说明输出形状，不构成额外可用 ID。

## 整体反应

收到 `[BOOK_SCAN_REACTION]` 后只按两条已确定的产品分支路由：

- `continue_scan`（继续排雷）：从报告中选择 1～3 个明确写出的真实优点或仍值得了解的点，只输出 `book_scan_like_reasons`；如果报告没有任何真实优点，直接输出 `book_scan_target`，不得硬凑喜欢点。原因卡允许用户跳过，提交或跳过后都进入扫描范围。
- `reject`（已经劝退）：从报告中选择 1～4 个彼此独立的缺点或已确认雷点，只输出 `book_scan_dislike_reasons`。风险原因必须与报告 `## 已确认雷点` 中可见的独立卡片一一对应；威胁、结果未知和已逆转事件不得作为已确认原因。

劝退原因必须按根因去重：同一人物、同一伤害链或同一选书影响只返回一个原因；已经返回具体 `risk.*` 时不得再返回“已确认雷点”总括项；具体雷点已经足以表达关系伤害时，不再叠加 `feedback.relationship_problem`。禁止把事实包里存在、但报告没有明确展示的风险偷偷加入原因列表。

输出使用最小宿主协议标签：

```legado-interaction
<interaction id="book_scan_like_reasons" item-ids="feedback.genre_setting,feedback.protagonist" />
```

或：

```legado-interaction
<interaction id="book_scan_dislike_reasons" item-ids="feedback.relationship_problem,risk.forced_third_party_marriage" />
```

上述原因只表示用户对当前书的反馈，不自动写入长期阅读偏好。用户若在输入框明确说“以后同类作品也这样”，再按渐进偏好闭环处理泛化确认。

## 原因提交

收到 `[BOOK_SCAN_CONTINUE_REASONS]` 或 `[BOOK_SCAN_CONTINUE_REASONS_SKIPPED]` 后，不复述剧情、不重算报告；有选择时最多用一句短句确认，然后先给用户看得懂的下一轮扫描说明，再输出扫描范围。

扫描说明必须列出真实章号或范围，不能只写“上方列出的待查位置”“中段缺口”“其它章节”这种空话。推荐格式：

- 已经完整核对：第 X—Y 章、结尾第 A—B 章；
- 下一轮从这里开始：第 P—Q 章，连续补读这一段。

如果当前档案没有保存清楚具体章号，不要装知道。直接说明“旧记录没有保存可用章号，建议选择检查全部剩余章节或手动指定章节”，再输出扫描范围。禁止把内部词 `manifest`、`scan_100`、`snippet`、`budget` 或 `ch257-405` 展示给用户。

```legado-interaction
<interaction id="book_scan_target" />
```

收到 `[BOOK_SCAN_REJECT_REASONS]` 后，只用一句自然中文确认用户选择并结束本次扫书；不得输出 `book_scan_next`、扫描范围或普通追问。用户之后在输入框明确要求继续排雷／继续扫描时，才输出 `book_scan_target`。

## 继续扫描

收到 `show_scan_targets` 后，也必须先按上面的格式列出真实章号或说明旧记录没有可用章号，然后只输出：

```legado-interaction
<interaction id="book_scan_target" />
```

## 分析当前

收到 `show_analysis_targets` 后只输出：

```legado-interaction
<interaction id="book_scan_memory_analysis" />
```
