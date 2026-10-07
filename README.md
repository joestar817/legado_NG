<div align="center">

<img width="128" height="128" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Legado NG" />

# 阅读 NG · Legado NG

### Next Generation Legado

**致力于打造下一代阅读体验**

一款支持自定义书源和本地书籍的 Android 阅读器。<br>
自由调整阅读样式，用 AI 辅助阅读，为不同角色搭配声音，也能用动态主题装点界面。

[![GitHub Stars](https://img.shields.io/github/stars/joestar817/legado_NG?style=flat-square&logo=github)](https://github.com/joestar817/legado_NG/stargazers)
[![GitHub Release](https://img.shields.io/github/v/release/joestar817/legado_NG?include_prereleases&style=flat-square&label=release)](https://github.com/joestar817/legado_NG/releases/latest)
[![GitHub Downloads](https://img.shields.io/github/downloads/joestar817/legado_NG/total?style=flat-square&label=downloads)](https://github.com/joestar817/legado_NG/releases)
[![License](https://img.shields.io/github/license/joestar817/legado_NG?style=flat-square)](LICENSE)

**[下载最新版](https://github.com/joestar817/legado_NG/releases/latest)** ·
**[使用帮助](app/src/main/assets/web/help/md/appHelp.md)** ·
**[交流反馈](https://t.me/+lYttMZGrQ1RkOTE1)** ·
**[English](English.md)**

</div>

## 为什么选择 Legado NG

- **更丰富的界面与主题**：重新设计主要页面，提供透明玻璃、液态玻璃和动态背景等多种效果。
- **AI 辅助阅读**：净化正文、分析书籍，也可以围绕正在读的书与 AI 对话。
- **更自由的听书体验**：管理朗读引擎和发音人，为不同角色搭配音色，支持多人朗读与有声书播放。

## Why fork

我常在中文书和英文书之间换着读。字体按文字自己安顿好，就该是默认，不用每换一本再设一次。

中文和西文排在同一行，字号只该有一个。我要的是西文自己把大小收回去，看起来和汉字相称，行距仍旧跟着中文走。这件事我等了很久。

取色也一样。色块、色轮、连续色，我想慢慢试，抬手才算数。AI 配色只是一时好玩的试验。

EPUB 默认听我的排版。

都是很小的执念。用着它，每天读书都高兴。

> 我不会 Kotlin，只是用 AI 瞎搞。上游大神，非常感谢。

## 核心能力

|  | 功能 | 说明 |
| :---: | --- | --- |
| 📖 | 在线与本地阅读 | 导入自定义书源，搜索多个来源、浏览发现、切换书源；也可扫描或导入本地 TXT、EPUB 文件 |
| 🗂️ | 书架与书籍管理 | 列表或网格展示书籍，支持分组、排序、阅读记录、书签、关联作品和书籍快捷操作 |
| 🎨 | 界面与主题 | 透明玻璃、液态玻璃、柔光和动态背景；可单独设置阅读配色，选择悬浮控件和翻页方式 |
| ✨ | AI 阅读辅助 | 净化段落或章节、生成替换规则、分析书籍、整理角色卡，并结合当前书籍内容对话 |
| 🎧 | 多角色听书 | 使用系统或在线朗读引擎，为角色分配声音；支持跨章播放、提前缓存、进度跳转和有声书离线缓存 |
| 🧩 | 书源与规则 | 导入、编辑、登录、分组和批量管理书源，编写目录与正文规则；为替换规则分组、指定适用范围并查看效果 |
| 🔌 | AI 服务与扩展 | 接入多家 AI 服务，通过技能（Skills）和工具调用扩展 AI 功能；内置 MCP 服务，供外部工具访问书籍、章节等数据 |
| ☁️ | 备份与配置 | 通过 WebDAV 备份和恢复数据，导入书源、订阅源、主题、阅读排版和朗读引擎配置 |
| 🛠️ | 调试与日志 | 逐步调试书源规则，查看高亮代码、调试日志和网络日志；支持隐藏日志中的敏感信息及导出日志 |

## 当前状态

Legado NG 持续开发中，后续会继续修复问题、改善兼容性和运行速度，并完善使用细节。

- 基础阅读、书架、搜索换源、规则管理、设置与备份等主要流程可正常使用。
- AI、在线朗读和 MCP 功能需要自行配置后使用。
- 少量 JavaScript 书源使用的独立脚本运行环境（QuickJS）仍处于实验阶段。
- 欢迎通过 Issues 或交流群反馈使用中遇到的问题。

## 界面展示

<p align="center">
  <a href="docs/images/readme/ng-my.webp"><img src="docs/images/readme/ng-my.webp" width="23%" alt="Legado NG 我的页面" /></a>
  <a href="docs/images/readme/ng-ai-providers.webp"><img src="docs/images/readme/ng-ai-providers.webp" width="23%" alt="Legado NG AI 提供商管理" /></a>
  <a href="docs/images/readme/ng-tts-voices.webp"><img src="docs/images/readme/ng-tts-voices.webp" width="23%" alt="Legado NG 发音人管理" /></a>
  <a href="docs/images/readme/ng-about.webp"><img src="docs/images/readme/ng-about.webp" width="23%" alt="Legado NG 关于页面" /></a>
</p>

## 下载与开始使用

前往 **[GitHub Releases](https://github.com/joestar817/legado_NG/releases/latest)** 下载最新 APK。

首次使用时，可以导入自己的书源或订阅源，也可以直接导入本地 TXT／EPUB 文件。AI 和在线朗读按需配置，不影响基础阅读。

Legado NG 可与阅读原版、阅读 Sigma 同时安装，各自保存应用数据。

| 类型 | 包名 |
| --- | --- |
| 正式版 | `io.legado.app.ng.release` |
| 调试版 | `io.legado.app.ng.debug` |

## 项目关系

Legado NG 从阅读 Sigma 的代码发展而来，沿用 Legado 的书源规则和阅读功能，并在此基础上改进界面，加入 AI 辅助阅读、多角色听书和动态主题。

## 使用须知

Legado NG 只提供阅读器、规则引擎和相关管理工具，不提供任何书籍、书源、订阅源或其他内容服务。

应用中的网页访问、自定义规则、第三方书源、订阅源及其他外部数据均由用户自行配置或导入。项目开发者不制作、不维护、不分发第三方内容源，也无法保证第三方数据的合法性、可用性或安全性。

使用者应遵守所在地法律法规，并自行确认和承担所使用数据来源及内容的责任。

## 交流与帮助

- [下载最新版](https://github.com/joestar817/legado_NG/releases/latest)
- [使用帮助](app/src/main/assets/web/help/md/appHelp.md)
- [Telegram 交流反馈群组](https://t.me/+lYttMZGrQ1RkOTE1)
- [GitHub Issues](https://github.com/joestar817/legado_NG/issues)

反馈问题时，建议同时提供应用版本、Android 版本、复现步骤，以及必要的日志或截图。

## 动态主题与动效素材来源

Legado NG 的“湖畔樱花”“好奇猫咪”动态主题及播放器“雨夜”动效，使用了由 Wallpaper Engine 创意工坊社区作品适配而来的场景素材。本项目免费开源，不单独销售这些素材：

- “湖畔樱花”：取材自 [Workshop 3056182945「樱花」](https://steamcommunity.com/sharedfiles/filedetails/?id=3056182945)
- “好奇猫咪”：取材自 [Workshop 3455074362「4K Curious Cats (PHONE)」](https://steamcommunity.com/sharedfiles/filedetails/?id=3455074362)
- “雨夜”：取材自 [Workshop 3503882817「Convenience Store in the Rain」](https://steamcommunity.com/sharedfiles/filedetails/?id=3503882817)

原作品著作权归各自作者所有。如权利人认为相关使用不当，请通过项目 Issue 或交流渠道联系我们，我们会及时删除或替换相关素材。

## 致谢

感谢以下项目提供的代码、设计与实现参考：

- [gedoor/legado](https://github.com/gedoor/legado) — Legado 原项目，为规则生态和核心阅读能力奠定了基础。
- [Luoyacheng/legado-E](https://github.com/Luoyacheng/legado-E) — 阅读 Sigma，Legado NG 最初的直接代码基础。
- [Rimchars/legado](https://github.com/Rimchars/legado) — 阅读Archive，AI 多角色分镜与角色化朗读的重要灵感来源，也为部分旧设备兼容性问题的定位和修复提供了参考。
- [LegadoTeam/legado](https://github.com/LegadoTeam/legado) — 阅读 Beta，Legado NG 的单文件 JavaScript 书源支持直接参考了其实现。
- [skybbk1001/legadoT](https://github.com/skybbk1001/legadoT) — 阅读 T；Legado NG 所参考的单文件 JavaScript 书源方案最初由该项目作者实现。
- [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3) — 主题体系相关设计参考。
- [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) — AI 服务商管理、模型配置和聊天体验的重要参考。
- 感谢本项目使用的所有开源依赖、素材作者、贡献者和测试者。

## 许可证

项目源代码基于 [GNU General Public License v3.0](LICENSE) 开源。第三方素材的权利归各自作者所有，并按上方来源说明使用和处理。

---

<div align="center">

如果 Legado NG 对你有帮助，欢迎点个 ⭐ 支持项目。

**[Star](https://github.com/joestar817/legado_NG)** ·
**[Releases](https://github.com/joestar817/legado_NG/releases/latest)** ·
**[Community](https://t.me/+lYttMZGrQ1RkOTE1)**

</div>
