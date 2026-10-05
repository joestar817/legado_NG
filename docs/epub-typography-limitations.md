# EPUB 排版字体已知限制

> 状态：文档化（2026-10-05）。本文只记录现状边界，不改实现。
> 关联：`docs/typography-placement-proposal.md`（决策）、`docs/reading-poc-3a-epub-script-fonts.md`（3A 验收）。

## 1. CSS unicode-range 匹配：码点级、上下文无关

- `FontFace.unicodeRange` 是**逐码点**匹配，浏览器不感知“这个半角标点在 CJK 上下文中”。
- 因此 CJK 内部的中性/半角标点（`,`、`.`、`U+2013–U+2029` 等）会落到 **Latin face**，不会因为前后是 CJK 而自动跟随 CJK。
- 这是 CSS 字体匹配的固有语义；TXT 渲染器里的「中性字符继承前后文」策略（`ReadScriptClassifierContract`）在 EPUB/WebView 层做不到。

## 2. `document.fonts.check()` 不是 cmap 覆盖证据

- `document.fonts.check()` 只判断 face 是否已加载且 unicode-range 覆盖该码点，**不看字体文件内部 cmap 是否真的有该字形**。
- 判别“字库是否真的能画某字符”，必须用光栅签名（canvas `getImageData` 非空像素计数）或 `measureText` 与参考字体对比，不能只靠 `check()`。

## 3. Publisher 字体胜出的前提

出版方字体（`EpubLayoutProfile`）只有在以下条件同时成立时才压过 App 链：

```text
RESPECT 模式
  + 元素显式声明了 font-family
```

- `OVERRIDE` 模式：走 App 解析链（本书 → 预设 → 全局 → 平台）。
- `RESPECT` 但元素未声明字体：该元素仍走 App 链。

## 4. 系统字体（sans/serif/mono）在 EPUB 侧的注入限制

- EPUB/WebView 无法直接“引用系统字体文件”注入 FontFace（系统字体不保证有稳定可读的文件路径）。
- 当前实现：脚本字体选 `system:0/1/2` 时，Kotlin 侧映射到常见系统字体文件（`/system/fonts/Roboto-Regular.ttf` 等）尝试读取；文件不存在时该 scope 回落预设字体。
- 若设备文件路径不同，正确的替代方案是 CSS `font-family` 系统族回退（`sans-serif` / `serif` / `monospace`），而非注入文件——这是未来如需完善系统字体脚本支持时的方向。

## 5. 补充：个别字体文件被 WebView OTS 拒绝（已观察）

- 真机观察：`方正北魏楷书_GBK.ttf`（6.3MB）在 gateway 正确供流、`FontFace.load()` 仍报网络错误（WebView OTS 拒绝该文件），而 `方正楷体超大字符集.ttf`（18.9MB）可正常加载。
- 行为兜底：Language fonts 页对加载失败的 scope 显示删除线；保存/完成时该 scope 恢复「跟随预设」。
- 原因属字体文件与 WebView 消毒器兼容性；TXT 原生渲染（Android Typeface）通常能加载同一文件。运行时子集化/消毒是未来可选方案，当前不实现。
