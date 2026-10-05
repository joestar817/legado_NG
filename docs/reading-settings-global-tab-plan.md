# 阅读样式抽屉：默认 Tab + 新书预设设计计划

> 状态：用词与信息架构已锁定（2026-10-05 UX 评审），**已在 `favorite` 实现**（数据层 + 默认 Tab；未做 Commit 4 字体入口合并）。  
> 基准：`favorite` 上的代码行为见 §0.1；语言策略以 `pr/reading-settings` 的 Policy/Detector 为准。  
> 目标：抽出应用范围的策略到独立 **默认** Tab；把「脚本字体」（怎么画字）和「新书预设」（开书时选哪套预设）分开。

## 0. 已锁定用词

代码层可继续用 `GLOBAL` / `ReadStyleLanguageMap` / `scriptFonts`。用户可见文案必须用下表。

| 用户可见（中 / EN） | 不要用 | 含义 |
|---|---|---|
| Tab **默认** / **Defaults** | 全局 / Global | 应用范围的策略与兜底。`ReadValueSource.GLOBAL` 来源小字仍译「全局」 |
| **脚本字体** / **Script fonts** | 语言字体（可逐步替换） | 这一页里 Latin/CJK/Other **字符**用什么字体 |
| **新书预设** / **New-book preset** | 语言预设 | 检测到**整书语言**后，建议选用哪一个**阅读预设**（整套样式，不是某一款字体） |
| **中文书 / 西文书 / 其他语言** | 映射行上的裸 CJK / Latin / Other | 整书语言类；与脚本字体的 CJK/Latin/Other **不是同一轴** |
| **正文** / **Body** | 在合并字体页上继续叫「本书字体」 | DEFAULT 桶 |
| **浮动窗跟随 App** / **Floating color follows app** | 外观 → 跟随应用颜色 | 只影响浮动窗，不改正文配色 |
| **跟随预设** / **Follow preset** | — | 解析层：这一行没有自己的覆盖 |
| **跟随全局**（编辑预设空脚本行，现网文案） | 与「恢复跟随当前预设」混用 | 继承 **全局脚本 pref**（`ReadScriptTypographyStore`），不是本书 `follow_global` |
| **恢复跟随当前预设** / **Follow current preset** | 恢复跟随全局 | 本书基准 `follow_global` |
| **清空并恢复预设** | — | 留在 **预设** Tab 管理区，不进默认 Tab |

### 「新书预设」选的是 Preset，不是 Script

两套东西名字都带「语言」，但对象不同：

```text
脚本字体
  问题：这一页的汉字 / 拉丁字母 / 其他文字分别用哪款字体？
  favorite 现状：两套入口（见 §0.1）——预设 Tab「语言字体」写全局兜底；编辑预设写该预设 `scriptFonts`
  目标归属：当前选中预设的排版；本书开启自定义时可再覆盖

新书预设（默认 Tab 的策略）
  问题：打开一本中文书时，先套哪一套阅读预设？
  答案：一个预设名，例如「宋体阅读」「Serif」
  归属：默认 Tab
```

「宋体阅读」「Serif」在新书预设里是 **预设卡片的名字**，不是字体文件。若现有预设名看起来像字体，UI 右侧应显示预设预览/类型，避免被读成脚本字体行。

```text
默认 → 新书预设
  中文书     →  宋体阅读     ← 整套预设（颜色+字体+间距…）
  西文书     →  Serif        ← 预设名，不是字体文件
  其他语言   →  用当前预设   ← 未映射：保持当时选中的预设并记住
```

`pr/reading-settings` 的检测器 **没有 MIXED**（只有 Cjk / Latin / Other，未知返回 `null`）。未知：保持当前预设且 **不写入** remembered。已检出 Other 但该行未映射：保持当前预设并 **提交** remembered（见 §0.1）。

## 0.1 对照代码（favorite 2026-10-05；语言策略来自 `pr/reading-settings`）

「目标 UX」若与下表冲突，**以代码为准**，改计划而不是假装已经这样实现。

| 说法 | 实际 |
|---|---|
| 四个根 Tab | `ReadStylePage` 根页只有 `PRESET / ADJUST / HIGHLIGHT`。没有默认/全局 Tab。 |
| 语言预设已在 favorite | **没有** `ReadStyleLanguageMap` / Binder / Policy。`Book.ReadConfig` 也没有 `languageHint` / `scriptClass` / `readStyleName`（旧 JSON 里这两键会被 Gson 丢掉，见 `BookReadStyleSessionTest`）。 |
| 预设页「语言字体」= 当前预设的三脚本 | **否。** `writeScriptFont`：`onlyThisBook` → 本书 `writeScope`；否则 → `ReadScriptTypographyStore`（全局 pref）。预设自己的三脚本只在 **编辑预设** `setEditorScriptFont` → `durConfig.scriptFonts`。 |
| 语言字体副标题「新预设会继承」 | 画在 `LANGUAGE_FONTS` 页上，开了自定义本书时仍显示，但写入已是本书覆盖——文案与写入不一致。 |
| 跟随应用颜色在本书模式置灰 | **隐藏。** `floatingColorManagedGlobally = !onlyThisBook && readFloatingFollowAppGlobally`。开关即写 pref，不进 `ReadStyleSnapshot` / Discard。 |
| 清空并恢复预设 | 已在预设页，本书模式同样隐藏。 |
| 编辑预设空脚本行「跟随全局」 | 字符串 `read_style_follow_global`：指继承 **全局脚本 pref**，不是本书 `follow_global` 基准。 |
| 新书映射空 = 其他语言 | 未映射 = 保持 **当时选中的预设** 并记住。未知语言 = 保持且 **不** 记住。 |
| Discard 回滚全局开关 | 不会。快照含 configList / 本书 / 全局脚本 JSON / 本书 overrides，不含浮动窗跟随 App。移植后语言映射也应即改即存。 |

## 0.2 落地后（相对 §0.1）

- 根 Tab 已是 `PRESET / ADJUST / HIGHLIGHT / APP_DEFAULTS`；默认页只有浮动窗 + 新书预设。「清空并恢复预设」仍在预设 Tab。
- `ReadStyleLanguageMap` / Binder / Policy 已移植；`Book.ReadConfig` 有 `languageHint` / `scriptClass` / `readStyleName`。
- 「浮动窗跟随 App」在 `onlyThisBook` 时**置灰**并说明原因，不再隐藏。
- Binder 在 `ReadBookConfig.onlyThisBook` 时跳过自动套用映射，避免把新书预设写进本书自定义样式（计划未写死；按「本书覆盖优先」保留）。
- Commit 4 字体入口合并未做：预设 Tab「语言字体」仍写全局兜底。

## 1. 目标信息架构（已落地，Commit 4 除外）

```text
阅读设置
├── 预设
│   ├── 当前预设选择
│   ├── 预设管理（新建/编辑/导入/导出/删除 + 清空并恢复预设）
│   ├── 字体                 ← 目标：当前预设正文 + 三脚本（今日预设 Tab「语言字体」仍写全局兜底）
│   └── 本书覆盖
│       ├── 自定义本书设置
│       ├── 字体             ← 本书覆盖；标题带「· 本书」
│       └── EPUB 排版
├── 调整
├── 高亮
└── 默认
    ├── 浮动窗
    │   └── 浮动窗跟随 App
    └── 新书
        └── 新书预设 >
              中文书 / 西文书 / 其他语言 → 预设名
```

规则：

> **Preset 定义配置**（含该预设的脚本字体）。  
> **Book 对单本书覆盖配置。**  
> **Defaults 定义配置如何被选择/应用**（新书选哪套预设、浮动窗是否跟 App）。

**目标归属（本计划）**：三个脚本选择器是 **当前预设的排版**（编辑预设页 `durConfig.scriptFonts`），不是默认 Tab。

**favorite 现状并非如此**——预设 Tab 上的「语言字体」在未开「自定义本书」时写入的是 **全局兜底** `ReadScriptTypographyStore`，不是选中预设。编辑预设页才写 `scriptFonts`。实现时不要把这两套入口合成一套却仍走 `writeScriptFont`。

## 2. 正文 vs Latin（英文书）

对**纯英文**书，两行常常看起来一样，但不是同一层。渲染层还做了短路：

`ReadBookConfig.scriptFontPath(scope)` 仅在该脚本的有效字体 **与 DEFAULT 有效字体不是同一文件** 时才返回路径。`ScriptFontStyleResolver` 对 `DEFAULT` 分类直接 `null`（不换字）。因此 Latin 若解析到与正文相同的文件，英文页面 **完全不换 typeface**，和只设正文没有视觉差别。

| 行 | 代码 | 纯英文书 |
|---|---|---|
| **正文** | `textFont` / DEFAULT 桶（预设页「本书字体」仅 `onlyThisBook` 时出现） | 整页英文的基准 |
| **Latin** | `scriptFont(LATIN)` → `scriptFontPath` | 与正文同文件则路径为 null，看起来没区别 |
| **CJK / Other** | 同上 | 出现汉字/希腊字母且路径不同于正文时才换字 |

差别只在：中英混排且 Latin ≠ 正文；或用户给 Latin 另选文件。合并字体页仍应保留两行。

## 3. UI 细化

### 3.1 默认 Tab

```text
默认

浮动窗
  浮动窗跟随 App                [ON/OFF]
  仅浮动窗颜色，不改正文

新书
  新书预设                     >
```

- 即改即存，**不进** Done/Discard 会话（与现 §8.8 全局开关相同）。
- 开启「自定义本书设置」时 Tab 仍在：「浮动窗跟随 App」**置灰**并显示原因（本书正用本书/预设的浮动窗颜色）；新书预设保持可点。
- **现状（favorite）是隐藏不是置灰**：`PresetPage` 用 `if (!state.onlyThisBook)` 整行不画跟随应用颜色和恢复全部。本计划改成置灰，属于行为变更，需验收。
- 不要隐藏行，避免列表跳动。

### 3.2 新书预设页

```text
新书预设
只在这本书还没有记过你选的预设时生效

中文书          宋体阅读        >
西文书          Serif           >
其他语言        用当前预设      >
```

空状态必须与 `ReadStyleLanguagePolicy`（`pr/reading-settings`）一致，不要发明第四种：

| 情况 | 代码 | 用户应看到 |
|---|---|---|
| 该语言行未填映射 | `assignedName` → null → `mapped ?: currentStyleName`，且 `commitRememberedStyle = true`（Other 未映射测试已钉死） | **用当前预设**，并记住，下次不再改 |
| 语言未知（样本太短 / 无 hint） | `script == null`，**不** commit | 保持当前预设；等正文采样后再决定 |
| 已记住 `readStyleName` | remembered 优先，即使映射指向别的预设 | 用记住的那一套 |

没有 MIXED。混排书按检测器阈值多半归 **中文书**，否则 **其他语言**。

优先级（与 `decide()` 一致）：

```text
本书已记住的预设名  >  新书预设映射（存在且预设还在）  >  普通当前预设
```

### 3.3 预设页字体（目标合并 vs 今日路由）

今日 **不要** 把三行都当成「当前预设」：

| 入口 | 函数 | 写入 |
|---|---|---|
| 预设 Tab → 语言字体（`onlyThisBook=false`） | `writeScriptFont` | `ReadScriptTypographyStore` 全局 pref |
| 预设 Tab → 语言字体（`onlyThisBook=true`） | `writeScriptFont` | 本书 `writeScope` |
| 编辑预设 → 语言字体（`onlyThisBook=false`） | `setEditorScriptFont` | `durConfig.scriptFonts` |
| 编辑预设 → 语言字体（`onlyThisBook=true`） | `setEditorScriptFont` | 本书 `writeScope`（与上一行相同 store） |
| 预设 Tab → 本书字体（仅 `onlyThisBook`） | `textFont` setter | 本书 DEFAULT `writeDefaultFont` |
| 正文字体对话框（无 pending scope） | `textFont` setter | 本书 DEFAULT 或当前预设 `textFont` |

合并入口后按 **行 + 模式** 路由，禁止编辑器三脚本继续走 `writeScriptFont`（那会写到全局 pref）。

```text
自定义本书 OFF →  字体 · 当前预设     正文→textFont；三脚本→setEditorScriptFont
自定义本书 ON  →  字体 · 本书         正文→writeDefaultFont；三脚本→writeScope
（全局兜底三脚本若仍保留单独入口：writeScriptFont → ReadScriptTypographyStore）
```

**清空并恢复预设** 留在预设管理区（与新建/导入/删除一起）。今日本书模式会把该行隐藏；本计划不把它搬到默认 Tab。

## 4. 数据层来源

从 `pr/reading-settings` 只移植语言→预设策略（不拿旧 UI 重排）：

| 文件 | 职责 |
|---|---|
| `BookLanguageDetector.kt` | 整书语言检测 |
| `ReadStyleLanguageMap.kt` | 中文/西文/其他 → `presetName` |
| `ReadStyleLanguagePolicy.kt` | 选择策略（remembered 优先） |
| `ReadStyleLanguageBinder.kt` | 在 `ReadBook` 中应用 |
| `Book.kt` ReadConfig | `languageHint` / `scriptClass` / `readStyleName` |
| `PreferKey.kt` | `readStyleLanguageMap` |
| `ReadBook.kt` | `upReadBookConfig` 与正文采样处调用 binder |
| 测试 | `BookLanguageDetectorTest`、`ReadStyleLanguageMapTest`、`ReadStyleLanguagePolicyTest` |

JSON 列字段，**不需要 Room 迁移**。

## 5. 实现阶段

### Commit 1：移植新书预设数据层

提取上述文件到从 `favorite` 拉出的分支（例如 `defaults-tab`），跑三组语言测试。

### Commit 2：新增默认 Tab 与 UI 迁移

1. `ReadStylePage` 增加 `DEFAULTS`（代码枚举避免与 `ReadValueScope.DEFAULT` 混淆，可用 `APP_DEFAULTS` / `GLOBAL_PAGE`）。
2. 根 Tab：预设 / 调整 / 高亮 / **默认**。
3. `DefaultsPage`：浮动窗 + 新书两组。
4. 从 `PresetPage` 移出「跟随应用颜色」→ 改文案为「浮动窗跟随 App」。
5. **不要**把「清空并恢复预设」移走。
6. 字符串：`read_style_tab_defaults` = 默认 / Defaults；`read_style_new_book_preset` = 新书预设 / New-book preset；映射行用中文书/西文书/其他语言。

### Commit 3：状态 wiring

`ReadStyleUiState` 增加三份映射 + 检测到的整书语言类；`onNewBookPresetChanged` 写 `ReadStyleLanguageMap` 后 `refreshUi()`。即改即存。

### Commit 4（可选）：合并字体入口

正文 + 三脚本同一页；按 `onlyThisBook` 改标题。路由见 §3.3：预设三脚本必须 `setEditorScriptFont`，全局兜底才 `writeScriptFont`。改动面过大则先改文案：弱化「本书字体」与「语言字体」并列，并修正本书模式下仍显示「新预设会继承」的副标题。

## 6. 验证清单

- [ ] `:app:compileAppDebugKotlin` 与现有契约测试 + 三组语言测试。
- [ ] 四 Tab：预设 / 调整 / 高亮 / 默认。
- [ ] 默认 Tab 只有浮动窗 + 新书；恢复全预设仍在预设 Tab。
- [ ] 自定义本书时默认 Tab 仍在，浮动窗开关置灰有原因文案。
- [ ] 中文书映射到某预设后首次打开会选中它；手动换预设后再打开不被强制切回。
- [ ] 未知语言保持当前预设且不写入 remembered；Other 未映射保持当前并记住。
- [ ] Discard 不回滚新书预设映射、不回滚浮动窗跟随 App。

## 7. 风险

1. Cherry-pick `pr/reading-settings` 时只用文件 diff，不要整提交（该分支曾混有 myreader）。
2. 字体入口合并时按 §3.3 路由，勿把正文与 Latin 写成同一个 store，也勿把预设脚本写进 `ReadScriptTypographyStore`。
3. Binder 不得覆盖 `rememberedStyleName`。
4. 默认 Tab 一旦存在，新的应用范围开关都放这里，不要再堆回预设页。

## 8. 与之前尝试的区别

`pr/reading-settings` 曾在预设页内做「预设 / 本书 / 全局」分组（`427c8fa11`），后 revert（`e109084bb`）。本方案用独立 **默认** Tab，不拆预设页结构。
