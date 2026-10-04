# TODO

## Parked: 阅读页日/夜不跟随系统（matepad-pro / main）

**现象**：App 主题「常规模式」跟随系统日夜切换正常，但打开书后阅读页始终不随系统切日/夜。

**根因**：
- 阅读页日夜由 `ReadBookConfig.isNightTheme` 驱动（`durConfig.curTextColor()/curBgType()/curBgDrawable()`）。
- `readThemeMode`（阅读侧边栏：跟随系统/日间/夜间）缺省时回落为 `DAY`，不是 `FOLLOW_SYSTEM`。
- `syncFollowSystemTheme()` 仅在 `readThemeMode == FOLLOW_SYSTEM` 时生效。
- 备份/恢复排除了 `themeMode`、`readNightTheme`、`readThemeMode`（`BackupConfig.shouldRestorePreference`），且 `Restore.kt` 只重载 `readNightTheme`（默认 false），从不恢复 `readThemeMode` → 恢复/重装后阅读主题丢失，恒为日间。

**可选修复**：
- A（推荐，最小）：恢复时若 `readThemeMode` 缺失且 App 主题为跟随系统（themeMode="0"），将阅读主题置为 `FOLLOW_SYSTEM`。
- B（完整）：把 `readThemeMode`（可选含 `readNightTheme`）纳入备份恢复，保留侧边栏选择；需同步改 `BackupRestorePolicyTest`。
- C（改默认）：`resolveReadThemeMode` 在 pref 缺失时回落 `FOLLOW_SYSTEM` 而非 `DAY`；影响所有新装/老用户默认值，风险最大。

**决策（2026-10-04）：A — 恢复时缺失的阅读页日/夜模式按 FOLLOW_SYSTEM 处理。**

实现边界（已冻结）：

```text
备份含日/夜模式字段   → 恢复所存值
备份缺失该字段       → FOLLOW_SYSTEM（不是"当前全局默认"）
```

- 不把该字段加入备份负载；不改变全局默认值。
- 语义：缺失 = 非用户 override = 系统跟随；未来全局默认变化不影响旧备份的恢复结果。
- 推论（已知晓并接受）：App 主题被显式设为日/夜、而系统模式相反的用户，恢复后阅读页将跟随系统而非 App 主题——这是"缺失即 FOLLOW_SYSTEM"的直接推论（与原选项 A 的"App 主题为跟随系统才补"条件不同，按无条件版执行）。

实现检查单：
- [x] `Restore.kt` 恢复路径：pref `readThemeMode` 缺失时写入 `FOLLOW_SYSTEM`（存在则不动）。已实现：`resolveRestoredReadThemeMode`（固定回落 FOLLOW_SYSTEM，不随全局默认漂移）。
- [x] `BackupRestorePolicyTest` 增补：缺失→FOLLOW_SYSTEM；存在→保留所存值；备份负载不含该字段（格式不变）。（3 条新增测试）

## Backlog: Phase 3 后续（3A PASS 后，按序执行）

1. [x] **字体缓存**（§3.7-5，已落地 `5940d4fa8`）：`StyledTypefaceCache`（`(path, weight, italic)` 键的 typeface LruCache）接入 `ChapterProvider.resolveStyledTypeface`。
2. [x] **脚本数据模型**（已落地 `ffa538ce9`）：`ReadScriptTypographyStore`（`global.typography.scripts`，形状复用 `SparseFontOverrides`，pref `readScriptTypography`）；book 级 override 同构（Phase 2b）。myreader 映射桥接随语言映射分支合并时实现。
3. [x] **Language fonts UI 已落地**（`6d0f15fa8` + `253495cb8` + `e19e76dfe` + `778184c84`）：全局页=兜底（副标题）；预设编辑器=预设覆盖；本书模式=本书覆盖。三处均复用 `FontSelectDialog`，写后 `UP_CONFIG(1,2,5)` 刷新字体表。
4. [x] **TXT PoC 已落地并真机 PASS**（`5e4a67a6e`，`ENABLED=false` 休眠，2026-10-04 模拟器验收：逐脚本切换/中性继承/段首回落/Other 回落/无 tofu；`poc-mixed.txt` fixture 在 `scripts/fixtures/poc-mixed-epub/`）：`ReadScriptClassifierContract` 生产分类器 + `TxtScriptFontPoc` ReadCharStyle 叠加。已知限制：行高由正文字体决定，显著更高的脚本字体可能裁切。
5. [x] **TXT 生产化**（`87d6d57c4` + `3ea1fcaa4`）：`ReadBookConfig.scriptFont/scriptFontPath/hasScriptTypography` 接 `EffectiveReadValueResolverContract`；`ScriptFontStyleResolver` 生产叠加（fontProvider 注入，11 条契约测试）。
6. [x] **预设级脚本字体**（`37deb1872` + `fc0e2bca3`）：`Config.scriptFonts` + `ReadValueContext.presetScriptFont` 层；契约测试 5 条。
7. [x] **EPUB 生产化**（`cde21bd08`）：`EpubResourceGateway` per-scope 字体 + `reader.js` `NGScriptFont` FontFace（unicode-range），并入字体等待链。已知边界：CSS per-glyph 上下文无关（U+2013–2029 落 Latin face）。
8. [ ] **（可选/可延后）主脚本检测 + 默认预设推荐**：`BookPrimaryScript` + 内置三预设；见 `docs/typography-placement-proposal.md` Phase 5。

**PoC 脚手架清理标记**（已完成 `82b4ae817`，两套脚手架全删）：
- [x] `EpubScriptFontPoc.kt`、`poc-fonts/`、`reader.js` 三处 PoC 痕迹、`scripts/fixtures/poc-mixed-epub/` 全部删除；
- [x] `TxtScriptFontPoc.kt`、`TxtScriptFontPocTest.kt`、`TextChapterLayout` 的 PoC hook 已还原；
- 保留：`ReadScriptClassifierContract`（生产实现）与 `ReadScriptClassifierTest`。

## Phase 1 打磨清单（2026-10-03 已处理）

- [x] 三向确认弹窗 neutral 按钮措辞：「取消 / Cancel」→「继续编辑 / Keep editing」（新增 `R.string.read_style_keep_editing`，`ReadStyleDialog.showUnsavedConfirm` 改用；全局 `R.string.cancel` 不动）。
- [x] Cancel 与 Discard 表现相同的复现路径：两个根因——(1) `NgDismissibleDrawer` 下拉后 `dismissed=true` 未复位（已修 `resetSignal`）；(2) 系统返回键路径 `Dialog.cancel()` 先无条件 `dismiss()`、`onCancel` 后到，确认框弹在已关闭的抽屉上（已修：root 页 `BackHandler` 全拦截 → `requestDismiss()`，不再让 `Dialog.cancel()` 触发；`unsavedConfirmShowing` 防重入）。按钮措辞改「继续编辑 / Keep editing」+ `ReadUnsavedConfirmRouter` + 5 条语义契约。

## Phase 3 should-fix（下轮处理，非阻塞）

- [ ] 字体缓存键并入 `File(path).lastModified()`：同路径字体文件被外部覆盖时，当前键（仅 path）会返回旧字形（Glide mtime 签名同款教训）。content:// 与 assets:// 豁免（assets 不可变、content:// 无法廉价 stat，文档注明）。
