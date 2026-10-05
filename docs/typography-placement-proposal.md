# Typography（Language fonts）归属设计

> 状态：**已决策并实施（Phase 1–4），Phase 3 已硬化（2026-10-05）**。  
> 决策名：**Language-aware preset + sparse per-script inheritance**。  
> 配套实现：presets 内的 `scriptFonts`、全局/本书 override、TXT/EPUB 渲染接线、字体缓存 mtime、调试插桩清理、EPUB 已知限制文档化（`docs/epub-typography-limitations.md`）。  
> 剩余 backlog：可选三套命名预设模板（与映射到已有预设不是同一件事）。新书预设 + 默认 Tab 见 `docs/reading-settings-global-tab-plan.md`。

## 实施状态

| 阶段 | 状态 | 提交 |
|---|---|---|
| Phase 1 数据模型 + 解析器 | [x] | `37deb1872` + `fc0e2bca3` |
| Phase 2 预设编辑器 UI | [x] | `778184c84` |
| Phase 3 全局兜底页语义 | [x] | `778184c84`（副标题） |
| Phase 4 本书级 UI | [x] | `6d0f15fa8`（本书/预设/全局/原书来源） |
| Phase 5 主脚本检测 + 默认预设 | [x] 新书预设（Cjk/Latin/Other，无 MIXED） | `docs/reading-settings-global-tab-plan.md` |

## 1. 问题与现状

当前实现（Phase 3 已硬化）：

- 三行脚本字体写在 **全局** pref `readScriptTypography`（`global.typography.scripts.*`），作为兜底；
- 预设已可独立配置 `Latin / CJK / Other` 脚本字体（`ReadBookConfig.Config.scriptFonts`），未设置时继承全局；
- 开了「自定义本书设置」的书可写 **本书级** override（`BookReadStyleOverrides.font`）；
- 渲染层：TXT 走 `ScriptFontStyleResolver` 叠加 `ReadCharStyle`；EPUB 走 `NGScriptFont` + unicode-range。

已解决的问题：预设现在能承载完整排版风格，切换预设会同时切换基准字体与脚本字体。

## 2. 最终决策

**脚本字体归属于「预设」，全局脚本字体保留为「预设未设置时的兜底」，本书 override 保留为最高优先级。**

核心原则：

1. **预设是用户的主要排版/风格选择。**
2. **全局排版是默认基础 / 兜底。**
3. **本书级定制是最终覆盖。**
4. **书籍主脚本检测只负责推荐预设，不直接决定字体。**

## 3. 四层概念模型

用户的阅读设置由四个独立概念组成，不要混淆：

```text
Book language/script detection
          ↓
Recommended / default preset
          ↓
Preset typography
          ↓
This-book customization
```

### 3.1 书籍主脚本检测（BookPrimaryScript）

 responsibility：**建议用哪个预设起步。**

输出（`pr/reading-settings` 的 `BookLanguageDetector` / `BookScriptClass`）：

```text
Cjk / Latin / Other   或   null（未知，样本不足）
```

**没有 MIXED。** 混排按阈值多半归 Cjk，否则 Other。

来源优先级：

```text
EPUB <dc:language>
    ↓
内容/脚本分布分析（字符采样）
    ↓
书源 metadata（book.kind / language tag）
    ↓
用户手动选择
```

重要：**检测只用于推荐预设，不直接选择字体。** 字符级字体选择仍由现有的 `ReadScriptClassifierContract` 负责。

推荐系统与解析系统**正交**：

```text
BookPrimaryScript
        │
        ▼
Recommended preset
        │
        ▼
User selects/keeps preset
        │
        ▼
EffectiveReadValueResolver
        │
        ▼
   Effective font
```

用户完全可以把一本 CJK 书切换到 `Latin Reading` 预设，系统不会认为这是错误；只是默认推荐不同。

启发式阈值（如 70%）是**实现细节**，不作为对外契约冻结。因为：

- 书目、附录、注释可能拉高拉丁字符比例；
- 日文同时包含汉字、假名、拉丁，不能简单按「CJK 百分比」等同语言。

### 3.2 推荐 / 默认预设

系统可内置三个**普通预设**（非特殊模式）：

- `Latin Reading`
- `CJK Reading`
- `Other Reading`

它们就是普通预设，用户可以：修改、复制、重命名、删除、忽略。

当检测到书籍主脚本为 CJK 时，系统**推荐** `CJK Reading`；用户确认或另选预设后，才进入排版解析。

### 3.3 预设排版（Preset typography）

每个预设可独立配置 `Latin / CJK / Other` 三个**脚本桶（script buckets）**，不是语言：

- `Latin`：Latin 系字符；
- `CJK`：Han / Hiragana / Katakana / Hangul + 全角形式；
- `Other`：Greek、Arabic、Cyrillic、Thai 等其余脚本。

分类由 `ReadScriptClassifierContract` 在字符级决定；`BookPrimaryScript` 的结果**不反向改变**字符级桶。

```text
Latin = "Noto Serif"      ← 显式值
CJK   = "Source Han Serif" ← 显式值
Other = null               ← 继续解析下一层
```

语义：

| 预设字段值 | 含义 |
|---|---|
| `null` / absent | **不截断解析链**，继续到下一层（global → textFont → platform） |
| 字体值 | 该维度由本预设覆盖 |

**不引入显式 `followGlobal` / `followPreset` 开关。** `null` 即继承，值即覆盖。

> 关键不变量：`null` 不代表「使用某个默认值」「重置到平台」或「禁用」，它只是「本层无意见，继续往下走」。

### 3.4 本书定制（This-book customization）

在「自定义本书设置」模式下，用户可对三行脚本字体做本书级 override：

```text
Latin = null          ← 跟随预设
CJK   = "LXGW WenKai" ← 本书覆盖
Other = null          ← 跟随预设
```

## 4. 解析优先级链（每脚本独立）

```text
THIS BOOK override
    ↓
SELECTED PRESET override
    ↓
GLOBAL script font
    ↓
GLOBAL default font (textFont)
    ↓
PLATFORM fallback
```

用 `Latin / CJK / Other` 分别走同一条链。`EffectiveReadValueResolverContract` 已完整实现预设层（`ReadPresetSnapshot.scriptFonts` / `ReadValueContext.presetScriptFont`）。

> 重要不变量：本书的 **default** 字体覆盖（`bookDefaultFont` / `SparseFontOverrides.default`）**只作用于 DEFAULT 维度**，不拦截 LATIN/CJK/OTHER 脚本桶。脚本维度未被覆盖时继续向下找预设/全局脚本字体，而不是被本书默认字体挡住。

### 4.1 示例

**Global**

```text
Latin = Noto Sans
CJK   = Noto Sans CJK
Other = DejaVu Sans
```

**CJK preset**

```text
Latin = Noto Serif
CJK   = Source Han Serif
Other = null
```

**Book A**

```text
Latin = null
CJK   = LXGW WenKai
Other = null
```

**Effective for Book A**

```text
Latin → Noto Serif      (preset)
CJK   → LXGW WenKai     (this book)
Other → DejaVu Sans     (global)
```

## 5. 与现有架构的对应

| 概念 | 现有存储 / 组件 | 说明 |
|---|---|---|
| 全局脚本字体 | `ReadScriptTypographyStore` (`readScriptTypography` pref) | 全局兜底 |
| 预设脚本字体 | `ReadBookConfig.Config.scriptFonts`（新增） | **preset-owned sparse overrides**；`ReadBookConfig.Config` 在此处代表持久化的预设配置 |
| 预设快照 | `ReadPresetSnapshot.scriptFonts`（已有字段，目前 emptyMap） | materialize 时从 `Config.scriptFonts` 构造 |
| 本书覆盖 | `BookReadStyleOverrides.font`（SparseFontOverrides） | book-owned sparse overrides |
| 字符级脚本分类 | `ReadScriptClassifierContract` | 决定字符进入 Latin/CJK/Other 哪个桶 |
| 有效值解析 | `EffectiveReadValueResolverContract` | 按优先级链解析 effective value + source |

## 6. 实现阶段

### Phase 1 — 数据模型 + 解析器（先做这层，后做 UI）

1. `ReadBookConfig.Config` 增加 **preset-owned** 字段：
   ```kotlin
   @SerializedName("scriptFonts")
   val scriptFonts: SparseFontOverrides? = null
   ```
   明确：`ReadBookConfig.Config.scriptFonts` 是**预设配置**，不是本书 override；本书 override 继续走 `BookReadStyleOverrides.font`。
2. `BookReadStyleOverridesStore.materializePinnedBaseIfNeeded` 把当前预设的 `Config.scriptFonts` 写入 `ReadPresetSnapshot.scriptFonts`；legacy 迁移时同样保留。
3. `hasScriptTypography()` 计入 basePreset 快照的 `scriptFonts`（消除现有 TODO）。
4. 契约测试（用 fixture 验证）：
   - 预设脚本字体 > 全局脚本字体；
   - 本书 override > 预设脚本字体；
   - 缺省字段回落全局；
   - 同一本书内三 scope 可来自不同层（如 Book CJK + Preset Latin + Global Other）；
   - JSON round-trip / legacy migration。

### Phase 2 — 预设编辑器 UI

1. `EditorPage` 增加 **Language fonts** 区块（三行：Latin / CJK / Other）。
2. 复用 `FontSelectDialog`：预设编辑器用 `pendingEditorScriptFontScope`，预设 Tab 语言字体页用 `pendingScriptFontScope`。二者必须互清，避免写错层。
3. 每个维度显示来源：
   - 值 + 「跟随全局」
   - 值 + 「本预设」
4. 保存时写 `durConfig.scriptFonts`。

### Phase 3 — 全局兜底页语义

1. 现有的全局 Language fonts 页保留，但语义改为「全局兜底字体」。
2. 加副标题说明：「新预设或未自定义的预设会继承这些字体」。
3. 继续写 `ReadScriptTypographyStore`。

### Phase 4 — 本书级 UI 一致性

1. 在「自定义本书设置」模式下，Language fonts 三行编辑本书 override。
2. 来源显示支持：本书 / 预设 / 全局。

### Phase 5 — 新书预设（已在默认 Tab 落地）

整书语言只用来 **选已有预设**，不直接选字体。UI 与空状态以 `docs/reading-settings-global-tab-plan.md` 为准。

不要再规划「内置 Latin Reading / CJK Reading / Other Reading 三套预设」作为默认方案——映射目标是用户已有的预设名。

## 7. 迁移策略

- **全局脚本字体数据**：保持不变，作为兜底。
- **现有预设**：`scriptFonts = null`，全部继承全局。
- **本书 override**：保持不变。
- **不复制全局值进每个预设**，避免丢失继承语义。

关键不变量：修改全局 Latin 字体后，所有未显式设置 Latin 的预设自动生效。

## 8. UI 位置

| 入口 | 责任 |
|---|---|
| **全局 → Language fonts** | 配置全局兜底字体 |
| **预设编辑器 → Language fonts** | 配置当前预设的脚本字体覆盖 |
| **本书样式 → Language fonts** | 配置本书级脚本字体覆盖 |

普通用户主要路径：**书籍 → 选择/推荐预设 → 预设决定排版 → 必要时本书微调**。

## 9. 与 EPUB 出版方字体的交互

EPUB 出版方字体与 App 脚本字体是**两条独立规则**，不要混进同一条 inheritance chain。

边界如下：

```text
Publisher CSS font
        │
        ▼
if EpubLayoutProfile.RESPECT AND element has declared font
        │
        ▼
   → PUBLISHER

else
        │
        ▼
   book.scriptFonts[scope]
        ↓
   preset.scriptFonts[scope]
        ↓
   global.scriptFonts[scope]
        ↓
   textFont
        ↓
   platform
```

- `EpubLayoutProfile.RESPECT` + 元素声明了字体 → 出版方字体胜出（source = PUBLISHER）。
- 未声明或 `OVERRIDE` → 走 App 解析链。

因此预设脚本字体用于：

- TXT 渲染；
- EPUB 未声明字体的元素；
- EPUB `OVERRIDE` 模式。

## 10. 验收不变量

- 未设置的脚本维度必须回落到同一继承链，不出现假开关。
- 设置一个脚本维度只影响该脚本字符，不改变 DEFAULT / 其他脚本桶。
- 切换预设时，若新预设有显式脚本字体，则生效；否则继承全局。
- 本书 override 优先级高于预设，预设高于全局。
- 契约测试覆盖：本书 > 预设脚本字体 > 全局脚本字体 > 全局默认 > 平台。
