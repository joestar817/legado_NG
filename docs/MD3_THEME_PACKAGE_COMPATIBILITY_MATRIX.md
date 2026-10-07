# MD3 主题包与 Reading NG 兼容矩阵

> 核对基线：2026-08-01，当前 Reading NG 合并提交。本文以实际解析器、规范化层、`NgManagedTheme` 与运行时消费者为准，不把“字段能读取”写成“运行时已适配”。本阶段已收口，剩余映射暂缓，不继续改动书架、书籍详情和封面渲染结构。

## 1. 状态定义

| 标记 | 含义 |
| --- | --- |
| ✅ 已生效 | 已有 NG 运行时消费者，导入并应用后会改变界面。 |
| 🟡 部分适配 | 已转换并参与运行时，但存在来源语义降级、条件限制或回退。 |
| 📦 仅保留 | 字段或资源会校验、解压并写入 `ng-md3-profile.json`，但没有 NG 运行时消费者。 |
| 🔒 用户设置 | 字段会读取和保留，但主题包无权覆盖设备或用户的独立设置。 |
| 🛡️ 有意隔离 | 字段会保留，但为避免破坏 NG 的 Renderer/Component/Variant 体系，明确禁止直接控制 NG 组件外观。 |
| ➖ 运输字段 | 只用于资源寻址、归档和兼容报告，本身不是运行时状态。 |

“保留”目前具体指：原 ZIP 完整事务式解压、原始清单保存到 `rawManifestJson`、已登记字段以 JSON 字面量保存到 `normalizedFields`、未知 portable 字段保存到 `unknownFields`。当前没有实现或验证“把已安装 NG 主题重新导出成 MD3 包”，因此不能把保留能力等同于已完成双向回导。

## 2. 当前总体结论

| 范围 | 当前状态 | 说明 |
| --- | --- | --- |
| portable V1 字段登记 | 105/105 | 105 个已知字段均有机器可读登记并可无损进入 Profile。 |
| portable V1 标准资源槽位 | 12/12 | 均验证引用路径、存在性和 ZIP 安全性，并随包安装。 |
| 日夜配色 | 🟡 | 20 个颜色字段参与 NG 配色物化；`customMode` 仅保留。动态色、内置主题码缺少种子时不能精确复现。 |
| 日夜全局背景 | ✅ | 背景图片与 0～25 虚化值已进入 `NgManagedTheme` 并运行时生效。这里是 App 全局背景，不是阅读正文背景。 |
| 主界面导航图标 | 🟡 | portable 的书架/发现/RSS/我的四个资源槽位可运行时生效；必须四项完整可解码。Home 没有 NG 对应入口。 |
| 字体 | ✅ | `font.app`（兼容 `appFontPath` 包内路径）进入 View 与 Compose 应用字体；显式专用字体和阅读正文的独立字体不覆盖。 |
| 封面图集与封面规则 | 🟡 | 图集会复制为独立 NG 封面方案。“仅导入”不改变当前选择；“导入并应用”或以后应用该主题时，包内存在 `coverSelection` 才同步选择对应图集，并应用明确声明的 Wi-Fi、强制默认封面及日夜书名／作者策略。其余封面高级样式字段仍只保留。 |
| 容器图片、透明度、圆角、边框、分隔线、模糊 | 🛡️ | 全部保留，不直接映射到 NG 卡片/抽屉/Dock。只有 NG 先定义公开语义 Token 后才允许逐项接入。 |
| 主题模式、Dock、界面栏透明、导航顺序等 | 🔒 | 主题包不能覆盖。跟随/日间/夜间/墨水、悬浮 Dock 和界面栏透明仍由用户独立设置。 |
| Miuix | 📦/转换标记 | 只记录来源并转为 NG 语义，不引入或启用 Miuix 引擎。 |
| 未知字段/资源 | 📦 | portable 会逐项保留并报告；legacy 依靠原始 JSON 和完整 ZIP 保留，未建立未知字段的逐项索引。 |

## 3. portable V1 顶层结构

| 项 | 类型/默认 | 当前适配 | 说明 |
| --- | --- | --- | --- |
| `formatVersion` | `Int = 1` | ✅ 校验 | 仅接受版本 1。 |
| `name` | `String?` | ✅ | 主题名称；空值回退为“MD3 主题”。 |
| `config` | Object | ✅ | 解析下列 105 个字段；清单中必须是对象。 |
| `assets` | `Map<String,String>` | ✅/➖ | 绑定标准或未知资源槽位到 ZIP 相对路径。 |
| `coverAlbums` | Array | ✅ | 校验 ref、日夜图片路径和重复项，安装进独立封面仓库。 |
| `coverSelection` | Object | ✅ | 校验选择的图集是否存在，并在应用主题时选择对应本机图集。 |

## 4. 105 个 `config` 字段逐项清单

### 4.1 外观与设备偏好（4 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `themeMode` | `String = "0"` | 🔒 | 仅作为 `themeModeHint` 和兼容报告证据；不会切换跟随、日间、夜间或墨水。 |
| `launcherIcon` | `String = "ic_launcher"` | 🔒 | 不切换桌面图标。 |
| `isPredictiveBackEnabled` | `Boolean = true` | 🔒 | 不覆盖用户的预测性返回设置。 |
| `fontScale` | `Int = 10` | 🔒 | 不覆盖 NG 的字体缩放倍数；NG 仍使用独立 0.8～1.6/跟随系统设置。 |

### 4.2 配色生成与规格（8 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `appTheme` | `String = "0"` | 🟡 | `0` 识别为动态色、`12` 识别为自定义，其余识别为内置主题码；最终统一物化成 NG 手动日夜六色。内置主题码本身不会调用 MD3 内置主题实现。 |
| `paletteStyle` | `String = "tonalSpot"` | ✅ | 映射 neutral/vibrant/expressive/rainbow/fruitSalad/monochrome/fidelity/content；未知值回退 tonalSpot。 |
| `materialVersion` | `String = "material3"` | 🟡 | expressive 映射为 NG `Material 3 Expressive (2025)`，其余映射 `Material 3 (2021)`；不是引入 MD3 Renderer。 |
| `customMode` | `String? = "tonalSpot"` | 📦 | 保存原字面量，当前颜色规范化不独立消费该字段。 |
| `customContrast` | `String = "Default"` | ✅ | 映射默认/中/高对比度。 |
| `enableDeepPersonalization` | `Boolean = false` | 🟡 | 仅在 `appTheme == "12"` 且至少一个手动颜色非 0 时选择手动六色；不启用 MD3 的其它深度个性化逻辑。 |
| `cPrimary` | `Int = 0` | 🟡 | 日间种子色；0 代表未携带。缺失时回退手动主色，再回退 NG 默认种子。 |
| `cNPrimary` | `Int = 0` | 🟡 | 夜间种子色；规则同上。 |

### 4.3 日夜六色与纯黑（13 项）

这些字段只有在 `appTheme == "12"`、开启深度个性化且至少一个手动颜色非 0 时作为显式手动色使用；值为 0 表示该槽位由 NG 调色板补齐，不表示透明黑。非手动来源仍保留字段，但运行时以种子生成结果为准。

| 字段 | 类型/默认 | 状态 | NG 槽位/行为 |
| --- | --- | --- | --- |
| `isPureBlack` | `Boolean = false` | ✅ | 夜间手动背景缺失时强制纯黑背景。 |
| `themeColor` | `Int = 0` | ✅（条件） | 日间主色。 |
| `secondaryThemeColor` | `Int = 0` | ✅（条件） | 日间次要色/顶栏容器基色。 |
| `primaryTextColor` | `Int = 0` | ✅（条件） | 日间主要文字色。 |
| `secondaryTextColor` | `Int = 0` | ✅（条件） | 日间次要文字色。 |
| `themeBackgroundColor` | `Int = 0` | ✅（条件） | 日间背景色。 |
| `labelContainerColor` | `Int = 0` | ✅（条件） | 日间标签/容器色。 |
| `themeColorNight` | `Int = 0` | ✅（条件） | 夜间主色。 |
| `secondaryThemeColorNight` | `Int = 0` | ✅（条件） | 夜间次要色/顶栏容器基色。 |
| `primaryTextColorNight` | `Int = 0` | ✅（条件） | 夜间主要文字色。 |
| `secondaryTextColorNight` | `Int = 0` | ✅（条件） | 夜间次要文字色。 |
| `themeBackgroundColorNight` | `Int = 0` | ✅（条件） | 夜间背景色；缺失且 `isPureBlack=true` 时为纯黑。 |
| `labelContainerColorNight` | `Int = 0` | ✅（条件） | 夜间标签/容器色。 |

导入后顶栏文字模式固定为 NG 的“自动”，由 NG 对实际背景计算浅/深色；主题包不额外提供第七个顶栏文字颜色槽位。

### 4.4 来源 Renderer（2 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `composeEngine` | `String = "material"` | 📦 | 记录 MATERIAL/MIUIX/UNKNOWN 来源；目标始终为 READING_NG。 |
| `useMiuixMonet` | `Boolean = false` | 📦 | 只参与来源判断并给出转换警告；不会启用 Miuix Monet 或依赖。 |

### 4.5 书籍详情（5 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `bookInfoInputColor` | `Int = 0` | 📦 | 尚无 BookInfo 组件消费者。 |
| `bookInfoFollowCoverColor` | `Boolean = true` | 📦 | 不改变 NG 书籍详情取色。 |
| `bookInfoBackgroundBlur` | `String = "on"` | 📦 | 不改变 NG 详情页背景虚化。 |
| `bookInfoNetworkCoverBackground` | `String? = null` | 📦 | 路径字面量保留，未绑定运行时资源。 |
| `bookInfoDefaultCoverBackground` | `String? = null` | 📦 | 路径字面量保留，未绑定运行时资源。 |

### 4.6 表面、卡片、分隔线与模糊（23 项）

以下字段全部是 **🛡️ 有意隔离**：可解析、保留和报告，但不允许直接覆盖 NG 的卡片透明度、圆角、边框、分组拼接、Dock 或模糊实现。原因是这些属性属于 `Renderer + Component + Variant/State`，不是可无条件跨 Renderer 复制的主题颜色。将来只有 NG 先定义稳定语义 Token，才会逐项开放受控映射。

| 字段 | 类型/默认 | MD3 含义 | NG 当前状态 |
| --- | --- | --- | --- |
| `containerOpacity` | `Int = 100` | 通用容器不透明度 | 🛡️ 仅保留 |
| `overrideBaseCardCornerRadius` | `Boolean = false` | 是否覆盖卡片圆角 | 🛡️ 仅保留 |
| `baseCardCornerRadius` | `Float = 16` | 卡片圆角数值 | 🛡️ 仅保留 |
| `overrideBaseCardBorder` | `Boolean = false` | 是否覆盖卡片边框 | 🛡️ 仅保留 |
| `baseCardBorderWidth` | `Float = 1` | 边框宽度 | 🛡️ 仅保留 |
| `baseCardBorderColor` | `Int = 0` | 日间边框色 | 🛡️ 仅保留 |
| `baseCardBorderColorNight` | `Int = 0` | 夜间边框色 | 🛡️ 仅保留 |
| `disableSplicedColumnGroupCornerRadius` | `Boolean = false` | 禁用拼接分组圆角 | 🛡️ 仅保留 |
| `enableItemDivider` | `Boolean = false` | 启用条目分隔线 | 🛡️ 仅保留 |
| `itemDividerWidth` | `Float = 1` | 分隔线宽度 | 🛡️ 仅保留 |
| `itemDividerLength` | `Float = 80` | 分隔线长度 | 🛡️ 仅保留 |
| `itemDividerColor` | `Int = 0` | 分隔线颜色 | 🛡️ 仅保留 |
| `enableBlur` | `Boolean = false` | 启用表面模糊 | 🛡️ 仅保留 |
| `enableProgressiveBlur` | `Boolean = false` | 启用渐进模糊 | 🛡️ 仅保留 |
| `topBarBlurRadius` | `Int = 24` | 顶栏模糊半径 | 🛡️ 仅保留 |
| `bottomBarBlurRadius` | `Int = 8` | 底栏模糊半径 | 🛡️ 仅保留 |
| `topBarBlurAlpha` | `Int = 73` | 顶栏模糊叠加透明度 | 🛡️ 仅保留 |
| `bottomBarBlurAlpha` | `Int = 40` | 底栏模糊叠加透明度 | 🛡️ 仅保留 |
| `bottomBarLensRadius` | `Float = 24` | 底栏液态玻璃镜片半径 | 🛡️ 仅保留 |
| `topBarOpacity` | `Int = 100` | 顶栏不透明度 | 🛡️ 仅保留 |
| `bottomBarOpacity` | `Int = 100` | 底栏不透明度 | 🛡️ 仅保留 |
| `appColumnBackgroundOpacity` | `Int = 100` | 页面列背景不透明度 | 🛡️ 仅保留 |
| `glassCardBackgroundOpacity` | `Int = 100` | 玻璃卡片不透明度 | 🛡️ 仅保留 |

### 4.7 书架标签与卡片（4 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `enableCustomTagColors` | `Boolean = false` | 📦 | 未写入书架标签主题。 |
| `customTagColorsJson` | `String? = null` | 📦 | JSON 字面量保留，未解析成 NG Token。 |
| `bookshelfCardColor` | `Int = 0` | 📦 | 不覆盖 NG 日间书架卡片。 |
| `bookshelfCardColorDark` | `Int = 0` | 📦 | 不覆盖 NG 夜间书架卡片。 |

### 4.8 App Shell 用户布局（8 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `showHome` | `Boolean = true` | 🔒 | 不新增或隐藏 NG 导航入口。 |
| `showDiscovery` | `Boolean = true` | 🔒 | 不改变“发现”入口可见性。 |
| `showRss` | `Boolean = true` | 🔒 | 不改变订阅/RSS 入口可见性。 |
| `showStatusBar` | `Boolean = true` | 🔒 | 不改变系统状态栏设置。 |
| `swipeAnimation` | `Boolean = true` | 🔒 | 不覆盖页面滑动动画偏好。 |
| `tabletInterface` | `String = "auto"` | 🔒 | 不覆盖平板布局选择。 |
| `defaultHomePage` | `String = "bookshelf"` | 🔒 | 不覆盖默认主页。 |
| `mainNavigationOrder` | `String = "home,bookshelf,explore,rss,my"` | 🔒 | 不重排 NG 底部导航。 |

### 4.9 App Shell 主题外观（10 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `showBottomView` | `Boolean = true` | 🔒 | 不隐藏用户当前底栏。 |
| `useFloatingBottomBar` | `Boolean = false` | 🔒 | 不切换 NG 悬浮 Dock；仍由独立开关控制。 |
| `useFloatingBottomBarLiquidGlass` | `Boolean = false` | 🛡️ | 不启用 MD3 液态玻璃实现。 |
| `labelVisibilityMode` | `String = "auto"` | 🔒 | 不覆盖 NG 底栏标签策略。 |
| `navIconHome` | `String = ""` | 📦 | 字段字面量保留；当前运行时只认 `assets["navigation.home"]`，且 NG 没有 Home 槽位消费者。 |
| `navIconBookshelf` | `String = ""` | 📦 | 字段字面量不直接消费；portable 资源槽位 `navigation.bookshelf` 可运行时生效。 |
| `navIconExplore` | `String = ""` | 📦 | 字段字面量不直接消费；portable 资源槽位 `navigation.explore` 可运行时生效。 |
| `navIconRss` | `String = ""` | 📦 | 字段字面量不直接消费；portable 资源槽位 `navigation.rss` 可运行时生效。 |
| `navIconMy` | `String = ""` | 📦 | 字段字面量不直接消费；portable 资源槽位 `navigation.my` 可运行时生效。 |
| `useFlexibleTopAppBar` | `Boolean = true` | 🛡️ | 不改变 NG 顶栏组件变体。 |

### 4.10 App 全局背景与容器图片（9 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `bgImageLight` | `String? = null` | ✅ | portable 未声明 `background.light` 槽位时作为日间全局背景相对路径回退；安装后必须能解析为包内文件。 |
| `bgImageDark` | `String? = null` | ✅ | 夜间全局背景路径；规则同上。 |
| `bgImageBlurring` | `Int = 0` | ✅ | 日间背景虚化，限制为 0～25。 |
| `bgImageNBlurring` | `Int = 0` | ✅ | 夜间背景虚化，限制为 0～25。 |
| `largeContainerBackgroundImageLight` | `String? = null` | 🛡️ | 保存到 Background Profile，未进入 `NgManagedTheme`，不改变 NG 大容器。 |
| `largeContainerBackgroundImageDark` | `String? = null` | 🛡️ | 同上，夜间。 |
| `itemBackgroundImageLight` | `String? = null` | 🛡️ | 保存到 Background Profile，未改变 NG 条目卡片。 |
| `itemBackgroundImageDark` | `String? = null` | 🛡️ | 同上，夜间。 |
| `enableContainerBackgroundImage` | `Boolean = false` | 🛡️ | 保存开关，不启用 MD3 容器图片 Renderer。 |

这里的背景是 App 页面背景。阅读正文的背景/纸张纹理属于阅读界面的独立配置，当前 MD3 主题包适配不会覆盖它。

### 4.11 App 字体（1 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `appFontPath` | `String? = null` | ✅ | 优先使用 `font.app` 资源槽位；缺少该槽位时兼容安全的包内相对路径。字体进入 NG Compose Typography 与 View 文本创建入口，不覆盖显式专用字体，也不进入阅读正文的独立字体配置。 |

### 4.12 封面规则与默认封面（17 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `selectedCoverAlbumId` | `String? = null` | 🔒 | 配置字面量保留；portable 正式选择关系以 `coverSelection.albumRef` 为准，不使用来源设备的本机 ID。 |
| `coverLoadOnlyWifi` | `Boolean = false` | ✅ | 字段存在时进入主题封面 Profile，并在应用主题时同步到 NG 封面网络策略。 |
| `coverUseDefault` | `Boolean = false` | ✅ | 字段存在时进入主题封面 Profile，并在应用主题时启用或停用强制默认封面。 |
| `coverShowShadow` | `Boolean = false` | 📦 | 不改变封面阴影。 |
| `coverShowStroke` | `Boolean = true` | 📦 | 不改变封面描边。 |
| `coverDefaultColor` | `Boolean = true` | 📦 | 不改变默认封面颜色策略。 |
| `coverDefaultImage` | `String = ""` | 📦 | 日间默认封面路径字面量保留，未绑定运行时。 |
| `coverTextColor` | `Int = -16777216` | 📦 | 日间封面文字色未应用。 |
| `coverShadowColor` | `Int = -16777216` | 📦 | 日间封面阴影色未应用。 |
| `coverShowName` | `Boolean = true` | ✅ | 字段存在时随主题应用日间封面书名显示策略。 |
| `coverShowAuthor` | `Boolean = true` | ✅ | 字段存在时随主题应用日间封面作者显示策略。 |
| `coverDefaultImageDark` | `String = ""` | 📦 | 夜间默认封面路径字面量保留，未绑定运行时。 |
| `coverTextColorN` | `Int = -1` | 📦 | 夜间封面文字色未应用。 |
| `coverShadowColorN` | `Int = -1` | 📦 | 夜间封面阴影色未应用。 |
| `coverShowNameN` | `Boolean = true` | ✅ | 字段存在时随主题应用夜间封面书名显示策略。 |
| `coverShowAuthorN` | `Boolean = true` | ✅ | 字段存在时随主题应用夜间封面作者显示策略。 |
| `coverInfoOrientation` | `String = "0"` | 📦 | 不改变书籍详情封面方向/布局。 |

封面图集并非网格布局专属。当前把它作为独立“封面方案”安装，供统一的默认/错误封面 Resolver 使用；书架网格、书架列表、书籍详情等既有消费者会共同获得同一按书稳定选择的日夜封面。“导入并应用”或以后应用该主题时，会同步应用 `coverSelection` 以及包内明确声明的 Wi-Fi、强制默认封面和日夜书名／作者策略；“仅导入”不会立即改动当前封面。

### 4.13 运输字段（1 项）

| 字段 | 类型/默认 | 状态 | 当前行为与边界 |
| --- | --- | --- | --- |
| `assets` | `Map<String,String>? = null` | ➖ | DTO 内兼容字段；正式 portable 顶层也有 `assets`。资源绑定会做安全路径和存在性校验，未知槽位原样保留。 |

## 5. 12 个标准资源槽位逐项清单

| 资源槽位 | 状态 | 当前运行时行为 |
| --- | --- | --- |
| `background.light` | ✅ | 优先于 `bgImageLight`，作为日间 App 全局背景。 |
| `background.dark` | ✅ | 优先于 `bgImageDark`，作为夜间 App 全局背景。 |
| `container.large.light` | 🛡️ | 已校验和保留，未接管 NG 大容器。 |
| `container.large.dark` | 🛡️ | 已校验和保留，未接管 NG 夜间大容器。 |
| `container.item.light` | 🛡️ | 已校验和保留，未接管 NG 条目卡片。 |
| `container.item.dark` | 🛡️ | 已校验和保留，未接管 NG 夜间条目卡片。 |
| `navigation.home` | 📦 | 写入资源 Profile，但 NG 主界面没有 Home Tab 消费器。 |
| `navigation.bookshelf` | 🟡 | 与 explore/rss/my 四项齐全且都能解码时，在标准与悬浮底栏以 40dp、原色显示。 |
| `navigation.explore` | 🟡 | 同上。 |
| `navigation.rss` | 🟡 | 同上。 |
| `navigation.my` | 🟡 | 同上。 |
| `font.app` | ✅ | 写入 `resourceProfile.appFont`，由 Compose Typography 与 View 文本创建入口共同消费。 |

四个底栏图标采用全有或全无策略：任何一个缺失或无法解码，整组回退为 NG 24dp 默认图标和主题色染色，避免社区彩色图标与 NG 默认图标混搭。

## 6. 封面图集对象逐项清单

| 路径 | 类型/默认 | 状态 | 校验/行为 |
| --- | --- | --- | --- |
| `coverAlbums[].ref` | `String = ""` | 🟡 | 必填、非空、包内唯一；用于安装批次内关联，不直接成为本机方案 ID。 |
| `coverAlbums[].name` | `String = ""` | 🟡 | 作为独立 NG 封面方案显示名；重名时增加序号。 |
| `coverAlbums[].lightImages` | Array | 🟡 | 日间图片列表；校验后复制到独立封面仓库。 |
| `coverAlbums[].lightImages[].path` | `String = ""` | 🟡 | 安全 ZIP 相对路径；运行时不再依赖主题包目录。 |
| `coverAlbums[].darkImages` | Array | 🟡 | 夜间图片列表；校验后复制到独立封面仓库。 |
| `coverAlbums[].darkImages[].path` | `String = ""` | 🟡 | 安全 ZIP 相对路径；运行时不再依赖主题包目录。 |
| `coverSelection.albumRef` | `String? = null` | ✅ | 非空时必须指向已声明图集；安装时转换为本机图集 ID，并随主题 Profile 应用。 |

## 7. legacy `application_theme.json` V1 适配清单

legacy 不是 105 字段 DTO，而是按已知对象分支读取。原始 JSON 和 ZIP 会保留，但只有下列字段已建立明确转换：

| legacy 路径 | 状态 | NG 转换/边界 |
| --- | --- | --- |
| `version` | ✅ 校验 | 仅接受 1。 |
| `config.name` | ✅ | 主题名；缺失时回退日间 `themeName`。 |
| `config.dayTheme.themeName` | ✅ | 名称回退。 |
| `config.dayTheme.accentColor` | ✅ | 日间主色，必填。 |
| `config.dayTheme.primaryColor` | ✅ | 日间次要色，必填。 |
| `config.dayTheme.backgroundColor` | ✅ | 日间背景色，必填；主要文字色由 NG 对背景计算。 |
| `config.dayTheme.bottomBackground` | ✅ | 日间标签容器色，必填；次要文字色由 NG 对该背景计算。 |
| `config.dayTheme.backgroundImgPath` | ✅ | 日间 App 全局背景。 |
| `config.dayTheme.backgroundImgBlur` | ✅ | 日间虚化，限制 0～25。 |
| `config.dayTheme.transparentNavBar` | 🔒 | 只保留兼容证据，不覆盖 NG“界面栏透明”。 |
| `config.nightTheme.*` | 🟡 | 同日间字段；整个对象可缺失，缺失时颜色预览回退日间，背景记为缺失。 |
| `dayBottomBar.layoutMode` / `nightBottomBar.layoutMode` | 📦 | `floating` 转成兼容 Profile 提示，但不切换 NG Dock。 |
| `dayBottomBar.effectMode` / `nightBottomBar.effectMode` | 📦 | `glass` 转成兼容 Profile 提示，但不启用液态玻璃。 |
| `dayBottomBar.icons.*` / `nightBottomBar.icons.*` | 📦 | 保存为 `legacy.navigation.{day/night}.{name}` 未知资源槽位，尚未映射到 portable 四图标消费者。 |
| `dayCover.name` | 🟡 | 作为合并后的 `legacy.default` 独立 NG 封面方案名称。 |
| `dayCover.images[]` | 🟡 | 合并为 `legacy.default` 日间图片，验证并复制到独立封面仓库。 |
| `nightCover.images[]` | 🟡 | 合并为 `legacy.default` 夜间图片，验证并复制到独立封面仓库。 |

legacy 只要携带有效的日间或夜间封面图片，adapter 就会生成指向 `legacy.default` 的 `coverSelection`。“仅导入”只安装图集；“导入并应用”或以后应用该主题时会选择该图集。

legacy 中未列出的字段没有逐字段登记：它们仍存在于 `rawManifestJson` 和完整安装包中，但不会进入 NG 运行时，也不会出现在 portable 的 `unknownFields` 索引里。

## 8. 未知字段、未知资源与安全边界

- portable `config` 未登记字段：逐项写入 `unknownFields`，导入预览显示数量警告。
- portable 未知 `assets` 槽位：验证引用文件后写入 `resources`，不运行。
- 所有资源引用必须是 `/` 分隔的包内相对路径；拒绝绝对路径、盘符、空段、`.`、`..`、NUL、大小写重名和不存在文件。
- 限制：最多 4096 个 ZIP 条目，清单最多 4 MiB，单文件最多 64 MiB，解压总量最多 512 MiB。
- 安装使用 staging 目录和原子移入；导入预览不写偏好、不解压、不重建 Activity。
- “仅导入”只加入主题库；“导入并应用”才应用生成后的 NG 主题。

## 9. 当前收口边界与暂缓项

1. **本阶段已经完成**：portable/legacy 安全导入、日夜配色与全局背景、`font.app` 应用字体、portable 四枚主导航图标、独立封面图集安装、`coverSelection` 和六项封面功能规则。
2. **涉及书架功能，当前暂缓**：书架标签颜色、书架卡片日夜颜色、封面阴影／描边／默认色与文字色、日夜单张默认封面、书籍详情取色／背景／布局等。后续应按书架、书籍详情和封面组件分别设计，不在主题兼容层先写全局覆盖逻辑。
3. **涉及 NG 组件结构，继续隔离**：容器图片、透明度、圆角、边框、分隔线、表面模糊、液态玻璃。只有先定义对应 `Renderer + Component + Variant/State` Token 才能重新评估，当前不列入开发计划。
4. **继续保持用户所有权**：主题模式、字体缩放、预测性返回、启动图标、主页／导航顺序、Dock 模式、界面栏透明、系统栏和平板布局。
5. **其它兼容缺口一并暂缓**：portable `navIcon*` 字段、legacy 日夜／选中态导航图标、Home 槽位以及 NG→MD3 完整回导。本阶段不继续实现，也不把“原字段已保留”标记成“完全兼容”。
