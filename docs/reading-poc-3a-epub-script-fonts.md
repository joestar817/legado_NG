# Phase 3A PoC：EPUB 脚本字体渲染路径（same-family + unicode-range）

> 状态：**PASS（2026-10-03，Pixel_Tablet 模拟器 Android 35 真机验收通过）**，详见文末「验收记录」。
> 目标严格限定为**证明 EPUB 多字体渲染路径可行**——不建全局模型、不加持久化字段、不做 UI、不改 8-flag 契约。
> 依据：docs/reading-settings-redesign-plan.md §3.7（脚本分类）、Phase 3 挂接点注记。
> 决策门：PASS → 字体缓存 → 脚本数据模型 → UI → TXT PoC；FAIL → 回炉 EPUB 渲染架构，不进 UI/model。

## 1. 现状挂接点（已白盒核实）

字体投递链：`EpubLayoutController` 读 `ReadBookConfig.textFont` → `EpubReaderFont.kt:9-27` 取字节（64MB 上限）→ `EpubFontData.forWebView` 修 vmtx → `EpubResourceGateway.kt:28-33,54-62,167-172` 以 `__ng_reader_<uuid>/font` 同域供流 → `EpubLayoutSurface.kt:310-315` `configure()` 传 `fontUrl` → `reader.js:79-82` 注册 `NGReaderFont`/`NGTitleFont`（`loadReaderFont` :86-104）。

family 单一接缝：`readerFamily(reader)`（`reader.js:83-85`）= `'NGReaderFont,' + (fontFamily || 'sans-serif')`，被三处消费：`applyFeaturePolicy`（:1242，feature=override 时逐元素内联）、`:where(html)` 零优先级默认表（:1351-1356，恒注入）、标题族（:1278，`'NGTitleFont,' + family`）。

字体等待点：`reader.js:1373-1375` 附近——分页/测量在字体就绪后进行，PoC 注册必须并入同一等待点。

## 2. 改动清单（白名单，此外一行不动）

### Kotlin（3 处 + 1 新文件）

**K1. 新增 `app/src/main/java/io/legado/app/ui/book/read/epub/EpubScriptFontPoc.kt`**（PoC 后整文件删除）：

```kotlin
/** Phase 3A PoC 开关与测试字体路径。不接入任何持久化模型/Config/Room/resolver。 */
object EpubScriptFontPoc {
    const val ENABLED = true
    const val LATIN_ASSET = "poc-fonts/latin.ttf"   // 仅 Latin 字形、特征明显的测试字体
    const val CJK_ASSET = "poc-fonts/cjk.ttf"       // CJK 子集化测试字体（见 §4）
}
```

**K2. `EpubResourceGateway`**（:162-172 现有 `__ng_reader_<uuid>/font` 分支旁）：`ENABLED` 时增加 `__ng_reader_<uuid>/poc_latin_font`、`__ng_reader_<uuid>/poc_cjk_font` 两个路径，读 assets 字节并复用 `EpubFontData.forWebView` 修 vmtx（WebView OTS 校验必需）。

**K3. `EpubLayoutSurface.configure()`**（:310-315）：`ENABLED` 时在 options JSON 增加 `pocLatinFontUrl` / `pocCjkFontUrl`（同域 URL）。JS 侧以"字段存在"为开关，无需 JS 常量。

### JS（`app/src/main/assets/epub/reader.js`，2 处 + 1 探针）

**J1. `loadReaderFont` 区域（:86-104 附近）新增 `loadPocScriptFonts(value)`**：

```js
async function loadPocScriptFonts(value) {
    if (!value.pocLatinFontUrl || !value.pocCjkFontUrl) return;
    // 同一 logical family；base 无 unicode-range（基础 face），CJK 带区段。
    var base = new FontFace('NGReaderPoC', 'url("' + value.pocLatinFontUrl + '")');
    var cjk = new FontFace('NGReaderPoC', 'url("' + value.pocCjkFontUrl + '")', {
        unicodeRange: 'U+3000-303F,U+3040-309F,U+30A0-30FF,' +
            'U+3400-4DBF,U+4E00-9FFF,U+F900-FAFF,U+FF00-FFEF'
    });
    await Promise.all([base.load(), cjk.load()]);
    // 重叠区段（base 覆盖全部码点）按 CSS 级联「后注册者赢」：先 base 后 CJK。
    // PoC 验证项 R1：实证本 WebView 的顺序敏感性；若相反则翻转并记录。
    document.fonts.add(base);
    document.fonts.add(cjk);
    window.__ngPocScriptFontsReady = true;
}
```

挂接：与 `loadReaderFont` 同一等待点（:1373 附近），`Promise.all` 并入，保证分页发生在 face 就绪后。

**J2. `readerFamily()`（:83-85）——唯一改法是把 PoC family 前置进现有返回路径，零语言判断**：

```js
function readerFamily(reader) {
    return (window.__ngPocScriptFontsReady ? 'NGReaderPoC,' : '') +
        'NGReaderFont,' + ((reader && reader.fontFamily) || 'sans-serif');
}
```

标题链（:1278 `'NGTitleFont,' + family`）经同一 family 自动覆盖 CJK 标题，无需改动。

**J3. 验收探针（PoC 后删除）`window.__ngPocProbe()`**，返回 JSON：

- `faces`：`document.fonts` 中两个 `NGReaderPoC` face 的 `unicodeRange` 值（验收 B）；
- `check`：`document.fonts.check('16px NGReaderPoC', 'A' / '你' / 'こ')`（加载完整性）；
- `metrics`：offscreen canvas `ctx.font = '32px NGReaderPoC'` 下 `measureText('HelloWorld')`、`measureText('你好世界')` 宽度，与 `'32px serif'` 参考族对比 + `getImageData` 非空像素计数（证明非 tofu、且 CJK 命中的是 CJK face 而非 Latin face 的缺字形）；

### 测试 fixture（2 处）

**F1. `scripts/fixtures/poc-mixed-epub/` + `build_poc_epub.py`**：最小 EPUB（mimetype / container.xml / content.opf / ch1.xhtml），三个段落：

```text
P1: Hello World 你好世界 こんにちは
P2: A 你好 B 世界 C こんにちは D          （单 text node 内切换）
P3: 中文，with ASCII、「引号」与 123 —— dash — test   （中性字符边界）
```

**F2. `app/src/main/assets/poc-fonts/`**：两个视觉特征明显不同的小字体。CJK 必须子集化（`pyftsubset noto-cjk.ttf --unicodes="U+3000-303F,U+3040-30FF,U+4E00-9FFF" --output-file=cjk.ttf`），否则 assets 膨胀数 MB。实施时先查 `app/src/main/assets/` 现有字体可否复用其一；注意许可，PoC 后删除。

## 3. 验收标准

### 通过项（A–H）

| # | 项 | 判定方式 |
|---|----|---------|
| A | Rendering：Latin glyph→latin.ttf，CJK/日文 glyph→cjk.ttf | 探针 metrics JSON 自动断言（宽度差异 + 非 tofu），辅以截图 |
| B | Same family：`document.fonts` 两个 face 同属 `NGReaderPoC`，CJK face `unicodeRange` 正确 | 探针 faces JSON |
| C | Mixed text：P1/P2/P3 无 tofu、无整段被单一字体吃掉；P2 单 text node 内切换成功 | 截图 + 探针 |
| D | Existing path：不新增渲染管线；`ENABLED=false` 时与现状逐像素一致 | 对照截图 |
| E | Neutral 边界：全角标点（，。「」）→ CJK face；半角标点/数字/U+2013–2029（—— —）→ Latin face | P3 截图 + 探针 |
| F | 禁用 CJK face 后：`English→Latin、你好→后续 family（per-glyph fallback）`，**非整段 fallback**——证明匹配发生在字体匹配层而非 JS block 分流 | 临时注释 cjk 注册重跑 P1 |
| G | Gate 语义不变：`features.font=respect` 时未声明样式的混合文本仍按脚本分字体（`:where(html)` 路径）；出版方声明了 font-family 的元素保持原书（既有闸门语义，记录为预期） | fixture 加一段带作者 font-family 的段落对照 |
| R1 | 注册顺序实证：重叠区段"后注册者赢"在本 WebView 成立（否则翻转并记录） | 探针 + 截图 |

### Negative acceptance（E）

- 不新增 DB schema / Room 迁移；
- 不新增持久化字段/pref；
- 不新增 resolver rule；`EffectiveReadValueResolver`/`EpubFormattingProfile` 契约零改动；
- 不改变现有 Typography UI；
- `git diff --stat` 白名单：`reader.js`、`EpubResourceGateway.kt`、`EpubLayoutSurface.kt`、新增 `EpubScriptFontPoc.kt`、`assets/poc-fonts/*`、`scripts/fixtures/poc-mixed-epub/*`。

### 已知边界（记录，不阻塞 PoC）

- CSS per-glyph 匹配是上下文无关的：CJK 之间的 em-dash/半角标点会落 Latin face（§3.7-2 的"中性字符继承前后文"在 CSS 层做不到，Phase 3 模型/Phase 4 TXT 再议）；
- Hangul（U+AC00-D7AF）本 PoC 不测；契约分类含 Hangul，生产实现时补入区段；
- Ext-B+（U+20000+）生僻字不覆盖；
- FontFace/unicode-range 需要现代 Chromium WebView（Chrome ≥ 36 时代特性），若个别老旧 WebView 失败，记录为平台限制而非模型否决。

## 4. 完成判定与后续

```text
3A PASS（A–H 全过 + negative 全满足）
  → 清理：ENABLED=false 或删 EpubScriptFontPoc.kt + poc-fonts + 探针
  → 下一步：字体缓存（§3.7-5）→ global.typography.scripts 模型 → Language fonts UI → TXT PoC
3A FAIL（任一核心项不过）
  → 不进 UI/model；回炉 EPUB 渲染架构评估
```

## 5. 验收记录（2026-10-03，Pixel_Tablet / Android 35，CDP 探针 + 截图）

**结论：PASS（A–H 全过，negative 全满足）。**

| 项 | 结果 | 证据 |
|----|------|------|
| A | ✓ | 探针：Latin 'HelloWorld'@32px = 174.69px（DejaVu，对照系统 156.44px）；Han '你好世界' = 128.0002px（恰好 4em 全宽）且非 tofu（像素 1580）；截图 P1 双字体肉眼可分 |
| B | ✓ | `document.fonts`：2 个 `NGReaderPoC` face 均 loaded，base `U+0-10FFFF`，CJK 带 7 段 range |
| C | ✓ | P1/P2 截图：单 text node 内 Latin（DejaVu）/ Han / 假名逐字形切换 |
| D | ✓ | `ENABLED=false` 重打包：probe `ready=false, faces=[]`，Latin 宽度回落 156.44（系统默认）、Han 回落 1575（系统回退签名） |
| E | ✓ | P3：全角标点（，。「」）→ CJK face；`123`、半角、`——`（U+2014）→ Latin face |
| F | ✓ | `FontFaceSet.delete(cjk)` 后：Han 仍渲染（回退到后续 family，光栅签名 1575≠1580），无 tofu、无整段回退；Latin 保持 DejaVu |
| G | ✓ | P4 作者 `font-family: serif`：Latin 与 CJK 均为 serif 链，PoC faces 不越权（features.font=respect 语义不变） |
| R1 | ✓ | base→CJK 注册顺序工作正常。注：两个 face 字形覆盖天然不相交（Latin 子集无 CJK 字形），重叠区段顺序问题在本形态下不会出现；生产形态（Latin 基础字体 + CJK 区段字体）同样不相交 |
| negative | ✓ | diff 白名单内：reader.js / EpubResourceGateway.kt / EpubLayoutSurface.kt / 新增 EpubScriptFontPoc.kt / assets/poc-fonts/ / scripts/fixtures/poc-mixed-epub/（含 CDP 辅助脚本 cdp_eval.js） |

**实施期修正（评审发现，已并入）**：`loadPocScriptFonts` 的 `await Promise.all` 必须 try/catch——字体 404/OTS 拒绝时未捕获的拒绝会冒泡到 `configureLayout` 外层 catch，把整章打进 error 页；现已静默回落（ready 不置位）。

**探针方法注记**：
- `document.fonts.check()` 按 unicode-range 判定、**不看 cmap 覆盖**——不是判别证据（空 family 也返回 true）；判别用 faces 列表 + 光栅签名。
- 光栅签名法：canvas 绘制同一文本，本 PoC 的 cjk.ttf（Noto 子集）= 1580 px，系统 Noto CJK 回退 = 1575 px；宽度相同（同字库 advance），像素差异证明文件不同——两个独立 WebView 均复现 1580，删除 face 后变 1575。

**遗留状态**：PoC 脚手架已删除（`82b4ae817`，2026-10-04）：`EpubScriptFontPoc.kt`、`poc-fonts/`、`reader.js` 的 PoC 痕迹（`loadPocScriptFonts`/`__ngPocProbe`/`readerFamily()` 前置）、`scripts/fixtures/poc-mixed-epub/` 全部移除。生产接线（`ReadScriptTypographyStore` → reader.js 的 `FontFace` 注入）为 Phase 3 剩余项。
