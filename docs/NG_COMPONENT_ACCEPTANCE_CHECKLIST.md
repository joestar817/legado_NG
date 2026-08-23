# Reading NG 真实页面组件验收清单

本文档以当前已经投入使用的 Reading NG UI 为唯一视觉基准。组件不在独立 Catalog 中重新设计，
而是从真实页面提取、复刻并在原业务场景中验收。

## 基准来源

- 样式：`app/src/main/res/values/ng_ui_styles.xml`
- Token：`app/src/main/res/values/ng_ui_tokens.xml`
- 动态强调色组件：`AccentBgTextView`、`AccentStrokeTextView`、`ThemeSwitch`
- 透明分组：`NgGlassLayout`
- 页面背景：`BaseActivity` 与当前 `ThemeConfig` 背景图片链路
- 图标、卡片和专用状态：现有 `ng_bg_*` drawable 与真实页面绑定代码

Compose 组件必须匹配这些已经调好的颜色、透明度、尺寸、字号、圆角、图标和交互状态。
Material 3 只作为 Compose 实现底层，不能把 Material 默认外观当作新的设计稿。

## 当前已通过的真实页面切片

2026-07-30，AI 设置／Provider 试点完成第一轮真机验收。以下结果可以作为后续页面的复用起点：

| 状态 | 组件／规则 | 已验收内容 | 下一次复验场景 |
| --- | --- | --- | --- |
| 已通过；TTS 详情待复验 | `NgSearchBar` | Provider、模型和模型选择抽屉统一为 44dp 搜索框，搜索／清除可交互；TTS 引擎详情发音人页已接入同一组件 | TTS 详情的名称搜索、过滤联动和空／加载状态 |
| 已通过 | `NgSecondaryButtonView` | 白色高不透明背景、主题强调色文字和 1dp 描边 | 朗读设置次操作、通用表单操作区 |
| 已通过 | `NgFloatingTabBar` | 48dp 等宽悬浮栏；配置／模型使用独立 24dp 图标并真正居中 | 角色池或另一详情页的同级分区 |
| 已通过 | `NgMenuPopup`／`NgActionPopup` | Provider 新增类型使用 18dp 圆角、44dp 行高菜单 | 其它标题栏新增／更多菜单 |
| 已通过（选择抽屉） | `NgLongListBottomSheet` | AI 模型、发音人和 TTS 引擎共用透明承载层与无描边白色搜索／过滤卡片；语言／性别未选中项使用白色承载面，选中项保留语义色 | 下一类长列表抽屉；内容卡片仍按业务另行验收 |
| 已通过 | 强调色规则 | 小节标题、按钮、选中 Tab 使用当前主题原始强调色 | 暖色、竹影、雾霭逐页复验 |
| 已通过 | 图标策略 | 不限定单一图标库；Provider Logo 保留，通用图标统一尺寸／视口／语义 | 持续筛选重复和不协调图标 |
| 已通过 | 文案规则 | 删除用户无需了解的内部调用说明；常规功能不添加大段解释 | 后续 AI、MCP 和调试设置页 |
| 已通过 | `TitleBar` 标题间距 | 返回触控区域保持不变，公共 Toolbar 的导航内容 inset 与额外标题前距统一为 0dp | AI 设置、AI 助理、AI 净化、AI 听书 |
| 已通过 | 思考深度选择弹窗 | 删除入口用途和互不影响说明，只保留标题、图标、当前值与档位 | 后续新增档位入口继续保持同样的信息密度 |
| 已通过（AI 设置） | `NgSettingsItemView` | 六项真实设置卡片的 64dp 高度、36dp 图标、16/13sp 文案、动态摘要、Chevron／Switch、整行点击和导航行为 | 朗读设置菜单复验同一组件；`Value`／`Custom` 另行验收 |
| 已通过（朗读设置） | `NgSettingsItemView` | 三项 Chevron 导航、两个静态摘要、一个异步动态引擎摘要及入口行为 | 校准 Compose 实现；`Value`／`Custom` 另行验收 |
| 已通过（AI Compose） | Compose `NgSettingsItem`／`NgSettingsGroup`／`NgSettingsIcon` | 一个页面级 ComposeView、六项 View 基线复刻、动态摘要、导航、SwitchCompat 内部桥接 | 朗读设置复验同一 Settings Pattern |
| 待真机复验（朗读 Compose） | Compose Settings Pattern | 单个透明 ComposeView、三个 Chevron、两个静态摘要、一个异步引擎摘要和三个入口 | 通过后选择下一个同结构页面 |
| 待真机复验（封面设置 Compose） | Compose Settings Pattern | 单个透明 ComposeView、通用／日间／夜间三组设置、Chevron、Switch、禁用依赖和图片操作回调 | 日夜封面选择／替换／删除、作者禁用态和背景连续性 |

“已通过”表示该组件在当前真实页面可继续复用，不表示相关组件族已经全 App 迁移完成。尤其是通用 SettingsItem、卡片、Tag、Dialog、图片、完整 Compose 页面和主题系统仍处于待验收状态。

## 组件清单

| 优先级 | 组件族 | 必须覆盖的变体 | 当前真实参考页面 | 主要基准资源 | 验收重点 |
| --- | --- | --- | --- | --- | --- |
| P0 | 页面骨架 `NgPageScaffold` | TitleBar、透明根布局、滚动内容、系统栏 | “我的”、AI 设置、朗读设置、书籍详情 | `BaseActivity`、`TitleBar`、`Ng.Page`、`Ng.PageContent` | 当前主题背景图片连续透出；导航内容 inset 与额外标题前距统一 0dp 且返回触控区不缩小；状态栏和返回行为不变；页面不得增加实色底 |
| P0 | 设置分组 `NgSettingsSection/Group` | 分组标题、单组、多组、组内间隔 | AI 设置菜单、朗读设置菜单、AI 配置详情 | `Ng.SettingsSectionLabel`、`Ng.SettingsGroup`、`NgGlassLayout` | 保留透明玻璃分组、24dp 圆角和当前模糊；滚动时不得出现背景断层 |
| P0 | 设置项 `NgSettingsItem` | Chevron、Switch、Value、Custom、启用、禁用、长摘要 | AI 设置菜单、朗读设置菜单、“我的”、朗读更多设置 | `NgSettingsItemView`、`Ng.SettingsItem`、`Ng.SettingsTitle`、`Ng.SettingsSummary` | AI 设置与朗读设置均已通过 View 组件验收，已覆盖 Chevron、Switch、静态／动态摘要、整行点击和导航；下一步校准 Compose，`Value`／`Custom` 仍待真实页面验收 |
| P0 | 设置图标 `NgSettingsIcon` | 图片图标、文字图标、无图标 | AI 设置菜单、朗读设置菜单、“我的” | `Ng.SettingsIconImage`、`Ng.SettingsIcon`、`ng_bg_settings_icon` | 36dp 图标容器、7dp 内边距、同一图标家族和动态强调色；不允许大小跳变 |
| P0 | 按钮 `NgButton` | Primary、Secondary、Dialog Primary、Dialog Secondary、Disabled、Danger | Provider 详情、Skill、AI 净化确认、角色编辑 | `NgSecondaryButtonView`、`BookInfoActionButton`、Compose `NgActionBarButton`、`Ng.SmallButton.*`、`Ng.DialogButton.*` | 已通过 View Secondary；Compose 图文操作栏按钮已在主题背景抽屉进入 Trial，复刻 42dp／图标／描边基线，其余 Variant 继续逐页验收 |
| P0 | 图标按钮 `NgIconButton` | 普通、强调、危险、禁用、图片上操作 | TitleBar、听书播放器、书籍详情、角色页 | 现有 Toolbar menu、Compose 播放器图标按钮 | 触控区域不小于 40/48dp；图标视口统一；不能用字符代替正式图标 |
| P0 | 内容卡片 `NgCard/ListCard` | Entry、List、Provider、Prompt、Selected、Disabled | AI Provider、AI Prompt、模型列表、TTS 引擎与发音人、书源与替换规则 | `Ng.Card`、`Ng.ProviderCard`、`Ng.PromptCard`、现有 `ng_bg_*card` | 复用当前 14dp 内边距、18dp 圆角和透明白承载面；标题、摘要、状态与尾部操作对齐 |
| P0 | 搜索 `NgSearchBar` | 页面、BottomSheet、清除、IME Search | Provider、模型列表、模型选择抽屉、TTS 引擎详情 | `NgSearchBar`、`Ng.SearchPill` | 已通过 44dp 高度、15sp、统一图标／清除／焦点；TTS 详情只按显示名称搜索，待真机复验过滤联动和状态 |
| P0 | 标题栏菜单 `NgActionPopup` | 新增类型、更多、选中、分组 | Provider 新增菜单 | `NgMenuPopup`、`NgActionPopup` | 已通过 18dp 圆角、44dp 行高、自适应宽度；不回退系统方形 PopupMenu |
| P0 | 底部切换 `NgFloatingTabBar` | 两项纯图标、可选图文、选中、无障碍 | Provider 配置／模型 | `NgFloatingTabBar`、`ng_bg_character_tabs` | 已通过 48dp 等宽栏、独立 24dp 图标物理居中和主题强调色选中态 |
| P1 | 输入 `NgTextField` | Outlined、Compact、密码、错误、只读 | TTS 引擎配置、AI 模型编辑、角色编辑 | `Ng.OutlinedField*` | 56/34dp 与当前一致；焦点、键盘、错误和长文本不改变布局 |
| P1 | 选择控件 `NgSelectionControl` | Switch、Spinner、Segmented、Slider、Checkbox、Radio、FilterChip、ChoiceCard | AI 配置、TTS 参数、角色编辑、朗读模式、长列表筛选、导出格式 | `ThemeSwitch`、`Ng.Spinner*`、`Ng.Segmented*`、Compose `NgSlider`、`NgFilterChipGroup`、`NgChoiceCard` | `NgSlider` 已在主题背景虚化与字体缩放进入 Trial；`NgFilterChipGroup` 已在批量换源抽屉进入 Trial，覆盖 WRAP 与固定 70dp 双排横向轨道、空选即全部、多选和长名称省略；`NgChoiceCard` 已在书架管理导出格式进入 Trial，仍需真机验收横向滑动、互斥反馈与日夜主题 |
| P1 | 标签与状态 `NgTag/Badge` | Info、Success、Warning、Error、Neutral、性别、语言 | AI Provider/Prompt、TTS 引擎与发音人、角色与发音人 | `Ng.Tag.*`、TTS 专用 `ng_bg_tts_*tag` | 24dp 高度、12sp 文案、颜色只表达状态；长标签不能挤压主标题 |
| P1 | 弹窗 `NgDialog` | Standard、Confirmation、Editor、LongContent、List | AI 净化确认、自定义范围、角色编辑、导入冲突、代码/日志查看 | `Ng.DialogRoot/Header/Body/Section/ActionBar` | 当前圆角、内边距和按钮尺寸；返回、取消、键盘、长内容滚动和横竖屏恢复 |
| P1 | BottomSheet `NgBottomSheet` | 设置列表、单选、滑杆、长列表 | 发音人、AI 模型选择、TTS 引擎、朗读模式、章节目录 | `NgLongListBottomSheet`、`layout_ng_model_filters`、`layout_tts_voice_filters` | 选择型长列表的背景、28dp 顶部圆角、compact 标题和透明筛选层已通过；设置／滑杆类抽屉及内容卡片继续按业务另验收 |
| P1 | 图片 `NgImage` | 封面、头像、图标、占位、失败、加载、图片上操作 | 书籍详情、搜索结果、书架、角色页、听书播放器 | 现有封面 ImageView、`ng_bg_read_aloud_player_cover` | 固定比例、裁切、圆角、占位与失败态统一；图片加载不得造成列表跳动 |
| P2 | 页面状态 `NgContentState` | Loading、Empty、Error、Retry、Progress | 书源调试、网络日志、AI 模型加载、TTS 发音人加载 | `StepLoadingView`、现有空态与错误提示 | 状态转换可见但不闪烁；重试真实可用；错误文案短且不暴露内部协议 |
| P2 | 高密度工具卡 `NgDiagnosticCard` | 时间线、阶段、日志、代码、网络状态 | 书源调试页、网络日志、应用日志、代码查看 | 调试页阶段卡片、`NgDialog` 日志/代码外壳 | 高信息密度下仍能快速定位；状态色、等宽内容、复制和整卡点击保持 |
| P2 | 媒体控制 `NgMediaControl` | 主播放、上一段/下一段、进度、倍率、引擎/发音人、Quick Action | 听书播放器、全局 Mini Player | `activity_read_aloud_player.xml` 与 `ng_bg_read_aloud_*` | 不重新设计播放器；保持当前按钮尺寸、层级、背景、状态反馈和播放性能 |
| P2 | 页面 Pattern | Settings、Management、Editor、Detail、SelectionMode | AI/TTS 设置、书源管理、角色编辑、书籍详情、批量选择 | 上述稳定组件组合 | 组件稳定后再抽 Pattern；Pattern 只统一布局，不改变业务数据流和导航 |

### `NgSlider` Trial 约束

- `CONTINUOUS` 使用听书进度条同款 6dp 低矮胶囊轨道、强调色已选区与外圈圆形滑块，不显示刻度。
- `DISCRETE` 使用 10dp 胶囊轨道并保留同款圆形滑块，增加圆点刻度和档位吸附；禁止复刻 MD3 的块状轨道、竖直分隔滑块或其它独立外观。
- 背景虚化 0～25 使用离散外观，展示全部整数刻度并按整数吸附；刻度仍沿用 NG 圆点，不改成 MD3 块状分段。
- 字体缩放直接展示 0.8～1.6 倍的九个用户档位，不得把内部持久化的 8～16 十分位整数暴露给用户；拖动只更新抽屉草稿，保存后才重建界面。
- 组件只负责数值、触控、禁用态与无障碍语义；业务是否即时保存、延迟预览或提交后生效由页面状态决定。

## 真实页面验收顺序

### 1. AI 设置菜单

文件：`fragment_ai_config_menu.xml`、`AiConfigMenuFragment.kt`。

首批收口：页面骨架、分组标题、玻璃分组、设置项、图片图标、Chevron、Switch。
这是已有 NG grouped-list 的最小完整页面，适合作为第一张 Compose 页面，不需要重新决定视觉。

六个条目的 View 组件和单个页面级 `ComposeView` 整页迁移均已通过真机验收；Fragment 继续负责摘要、配置和导航，Compose 负责渲染与事件回传。

### 2. 朗读设置菜单

文件：`fragment_read_aloud_config.xml`、`ReadAloudConfigFragment.kt`。

三个现有入口的 View 组件版本已经通过；当前进一步完成单个页面级 `ComposeView` 迁移，仍是两个静态摘要、一个异步动态引擎摘要和三个 Chevron，等待真机复验。

### 2.1 封面设置菜单

文件：`fragment_cover_config.xml`、`CoverConfigFragment.kt`、`CoverConfigScreen.kt`。

整页已使用单个透明 `ComposeView` 复用主题设置同款分组标题、半透明设置卡、Chevron 与 Switch；原 Preference XML 已删除。日夜默认封面的文件选择／替换／删除、作者依赖书名和封面缓存刷新保持原业务边界，等待真机复验后再把该 Pattern 扩到下一张设置页。

### 3. AI Provider / Prompt / 模型列表

文件：`fragment_ai_config.xml`、`item_ai_provider.xml`、`item_ai_prompt.xml`、`item_ai_model.xml`。

第一轮已收口搜索、Provider 新增菜单、详情页底部配置／模型切换、次按钮和模型选择 BottomSheet 外壳；这些结论已写入 `READING_NG_UI_STYLE.md`。

卡片、状态标签、列表选择态和图标细节仍继续按真实页面验收。列表整体迁移时再使用 Compose，不在 RecyclerView item 内逐个嵌入 `ComposeView`。

### 4. TTS 引擎配置

文件：`fragment_tts_engine_config.xml`、`item_tts_config_field.xml`、`item_tts_engine.xml`、`item_tts_voice.xml`。

收口输入框、紧凑输入、Spinner、Switch、滑杆、按钮、筛选、发音人标签和试听状态。

### 5. AI 净化与通用弹窗

文件：`dialog_ai_purify_confirm.xml`、`dialog_ai_purify_custom_range.xml`、
`dialog_tts_engine_import_conflict.xml`、日志/代码类 `NgDialog`。

收口弹窗外壳、内容区、状态块、主次按钮、危险操作、长内容、复制与键盘行为。

### 6. 角色与书籍详情

文件：`activity_book_character_tts.xml`、`activity_book_character_edit.xml`、`activity_book_info.xml`。

收口 Tabs、角色卡、封面/头像、标签、编辑表单和详情卡片。

### 7. 听书播放器

文件：`activity_read_aloud_player.xml`、`dialog_read_aloud_*_sheet.xml`。

播放器属于组合组件和性能敏感页面，等基础按钮、图片、Sheet、Slider 和设置项稳定后再迁移，
不得把它作为第一批基础组件试验场。

### 8. 调试、日志与高密度页面

文件：书源调试、网络日志、应用日志和代码查看相关 Activity/Dialog/item。

最后验证高密度信息、Loading、错误、复制、长文本和列表性能，避免基础视觉未稳定时重复返工。

## 每个页面的执行方法

1. 先以当前 View 页面和当前主题背景录制基准截图与行为清单。
2. 只提取该页面实际使用的 `Ng*` 组件，不提前制造未使用的抽象组件。
3. Compose 实现读取同一运行时主题，但尺寸、颜色、Drawable 语义和状态以现有 NG 为准。
4. 以完整页面为单位替换，不在旧列表项中零散嵌入 `ComposeView`。
5. 人工验收当前主题背景、点击、开关、输入、返回、旋转、大字号和长文案。
6. 页面通过后才复用其组件迁移下一页；失败则回退该页面，不修改主题系统掩盖问题。

## 当前明确暂停

- Debug Catalog 不再作为视觉或交互验收门禁，也不继续扩展。
- 不从 Material 默认组件重新设计颜色、字号、按钮高度、圆角或背景。
- 不在组件和真实页面稳定前改 Theme V2、背景 Host 或主题包。
- 不因为 Compose 迁移改变现有导航、偏好、数据加载和业务行为。
