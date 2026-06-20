# Reading NG UI Style Guide

本文档定义阅读 NG 后续新增 UI 的统一设计方向。目标不是全量切换到 Material Design 3，也不是继续每个功能单独手搓界面，而是建立一套适合阅读 NG 的稳定设计语言。

## 核心原则

阅读 NG 的 UI 采用：

> 结构参考 Material Design 3，配色和氛围保持 Reading NG 自有风格。

具体含义：

- 借用 MD3 的页面层级、组件语义、间距节奏和表单形态。
- 不直接照搬 MD3 / Material You 的动态色、默认紫色、灰紫背景和完整组件外观。
- 保留当前阅读 NG 已形成的暖色背景、白色承载面、红色强调、清爽调试卡片和阅读类 App 的柔和感。
- 设置类页面应允许主题背景图片透出：页面根布局默认透明，一级设置菜单使用 MD3 grouped list，二级内容列表再使用半透明圆角卡片。
- 新增 AI、调试、设置类 UI 时，优先复用本文档约定，不再按单个功能临时设计。

## 参考对象

可以参考：

- RikkaHub 的信息架构：设置首页、二级列表、详情页、底部 tab。
- RikkaHub 的组件语义：卡片、圆形图标、tag、Outlined 输入框、Switch、Segmented 控件。
- MD3 的结构层级：TopAppBar、List、Detail、Dialog、Card、Chip、OutlinedTextField。

不要照搬：

- RikkaHub 的紫色主色。
- Material You 动态取色。
- MD3 默认 `surfaceContainer` 灰紫色系。
- Compose 专属动效和组件实现。
- 与阅读 NG 首页、调试页冲突的高饱和或冷色主题。

## 视觉方向

阅读 NG 的整体视觉应保持：

- 温和：适合长时间阅读和设置。
- 清爽：避免厚重阴影、复杂边框和过多装饰。
- 工具化：调试、AI、规则编辑等页面应信息清楚、操作明确。
- 一致：新增功能必须优先匹配首页、搜索页、调试页已有风格。

当前视觉基准：

- 书架首页：暖色背景、白色圆角搜索框、白色内容承载区、红色强调。
- 书源调试页：顶部搜索条、时间轴、阶段卡片、状态 tag、轻量分隔。
- 网络日志弹窗：清爽列表、紧凑信息、状态颜色明确。

## Design Tokens

这些 token 是方向约束，实际实现可映射到项目现有主题色、drawable 或资源。

### Color

| Token | 用途 | 建议 |
| --- | --- | --- |
| `ng_background` | 页面背景 | 当前主题背景或暖色渐变背景 |
| `ng_surface` | 实色承载面 | 白色或接近白色 |
| `ng_surface_card` | 设置卡片承载面 | 半透明白或半透明暗色，用于透出背景图 |
| `ng_surface_panel` | 强承载面 | 比卡片更不透明，用于输入区、底部操作区和重要面板 |
| `ng_surface_soft` | 次级承载面 | 暖白、半透明白、轻微灰白 |
| `ng_primary` | 主强调 | 阅读 NG 红色系 |
| `ng_on_surface` | 主文本 | 接近黑色，不使用纯黑过重 |
| `ng_on_surface_variant` | 次级文本 | 中性灰 |
| `ng_outline` | 细边框 | 暖灰、低对比 |
| `ng_success` | 成功状态 | 绿色 |
| `ng_warning` | 警告状态 | 橙色 |
| `ng_error` | 错误状态 | 红色 |
| `ng_info` | 信息状态 | 蓝色或灰蓝 |

约束：

- AI、调试、设置页的强调色优先使用 `ng_primary`，不要默认变成 MD3 紫色。
- 状态色只表达状态，不参与大面积装饰。
- 背景允许跟随主题和渐变，但承载面必须保证可读性。
- 透明度统一通过 `ng_surface_card`、`ng_surface_panel`、`ng_icon_container` 等 token 调整，不在单个页面里散写 `#AAFFFFFF` 之类的临时颜色。

### Shape

| 组件 | 建议圆角 |
| --- | --- |
| 页面承载面 | 24dp 或按现有首页面板 |
| 搜索框 | 胶囊形或大圆角 |
| 列表卡片 | 14dp-18dp |
| 输入框 | 12dp-16dp |
| tag / chip | 胶囊形 |
| 小按钮 | 10dp-14dp |
| 弹窗内容块 | 12dp-16dp |

约束：

- 不要混用太多圆角等级。
- 功能按钮不应比内容卡片更抢眼，除非是主操作。

### Spacing

优先使用这些间距：

- 页面水平边距：16dp 或 18dp。
- 卡片内边距：14dp-18dp。
- 列表项垂直间距：8dp-14dp。
- 标题和摘要间距：4dp-6dp。
- 表单字段间距：12dp-18dp。
- 底部操作区高度：64dp-82dp。

约束：

- 设置页可以比阅读页密一些，但不能挤。
- 调试页允许更高信息密度，但必须有清晰分组。

### Typography

建议层级：

| 层级 | 用途 |
| --- | --- |
| 大标题 | 二级页面标题、详情页标题 |
| title | 卡片标题、阶段标题、功能入口 |
| body | 表单内容、正文说明 |
| label | tag、状态、辅助说明 |
| mono | URL、日志、代码、JSON |

约束：

- 中文界面优先使用系统无衬线，不强行引入复杂字体。
- 日志、URL、代码内容可使用等宽字体。
- 不要在紧凑卡片里使用过大的标题字号。

## 页面模式

### 一级设置页

用于 `AI 设置` 这类入口页。

结构：

- 外层使用现有 `TitleBar`。
- 页面根布局不使用实色背景，让主题背景图片或渐变可以透出。
- 内容区使用 MD3 grouped list 风格：一个圆角分组承载多个行式设置项。
- 普通设置入口不使用独立卡片；卡片只用于 provider、prompt、skill、模型等二级列表内容。
- 可直接操作的开关使用右侧 `Switch`。
- 进入二级页面的入口使用右侧箭头。

适用：

- 提供商
- 提示词
- 技能
- 默认模型
- 自动应用开关

### 二级列表页

用于 provider、prompt、skill、模型等列表。

结构：

- 顶部可选搜索框。
- 列表项使用统一 `ListCard`。
- 左侧图标，中间标题和摘要，右侧状态或操作。
- tag 放在摘要下或右侧，避免挤压标题。

适用：

- 提供商列表
- 提示词列表
- AI 技能列表
- 规则候选列表

### 详情页

用于配置单个 provider、prompt、skill。

结构：

- 顶部标题区：图标 + 名称 + 主操作。
- 内容区按分组展示。
- 表单使用 Outlined 风格输入框。
- 多分区详情可以用底部 tab 或 segmented 控件。
- 保存、恢复默认、测试连接等操作位置固定，不要每页变化。

适用：

- Provider 配置
- Prompt 编辑
- Skill 配置
- 模型参数配置

### 确认弹窗

用于 AI 净化候选、规则候选、批量操作确认。

结构：

- 标题 + 摘要。
- 列表内容清晰分块。
- 底部固定操作按钮。
- 不显示用户看不懂的隐藏风险逻辑。
- 如果有风险或校验失败，必须用明确、可理解的文本解释。

适用：

- 段落净化确认
- 章节净化候选确认
- 整段删除确认
- 标题优化确认

## 通用组件

第一版 XML 资源已经落盘，后续新增或改造页面优先使用这些 `ng_*` 资源，不再新增功能私有的 `bg_ai_*`、`bg_xxx_card` 等重复资源，除非确有业务差异。

资源位置：

- Token：`app/src/main/res/values/ng_ui_tokens.xml`
- 夜间 Token：`app/src/main/res/values-night/ng_ui_tokens.xml`
- Style：`app/src/main/res/values/ng_ui_styles.xml`
- Drawable：`app/src/main/res/drawable/ng_bg_*.xml`

### SettingsGroup / SettingsItem

用于一级设置菜单。

结构：

- 分组标题。
- 一个圆角分组容器。
- 每行左侧图标，中间标题 + 摘要，右侧箭头、Switch 或当前值。
- 行间使用轻分割线，避免每个设置项都变成独立卡片。

推荐资源：

- 分组标题：`@style/Ng.SettingsSectionLabel`
- 分组容器：`@style/Ng.SettingsGroup`
- 设置项：`@style/Ng.SettingsItem`
- 图标：`@style/Ng.SettingsIcon`
- 标题：`@style/Ng.SettingsTitle`
- 摘要：`@style/Ng.Summary`

约束：

- 一级设置菜单不要使用 `EntryCard` 堆叠。
- 设置项高度应稳定，右侧控件对齐，避免像内容卡片一样漂浮。
- 背景图透出由分组容器透明度控制，不能让每行单独写透明色。

### GlassPanel

用于带主题背景图的设置页和工具页承载块。

推荐资源：

- View：`io.legado.app.ui.widget.NgGlassLayout`
- 样式：`@style/Ng.SettingsGroup`

视觉：

- 背景从页面根背景采样，降采样后模糊，形成毛玻璃底。
- 上层叠加半透明 tint，保证文字可读。
- 边缘使用半透明描边，不按具体背景图写死暖色、绿色或其它单一配色。

约束：

- 优先用于一级设置页 grouped list。
- 后续设置页迁移时复用 `NgGlassLayout`，不要再为每个页面单独写固定色卡片。
- 当前组件适合背景图或纯色背景；如果背后是复杂动态列表，需要先评估缓存刷新策略。

### EntryCard

用于少量强调入口或二级列表候选，不再作为一级设置菜单默认形态。

结构：

- 左侧圆形图标。
- 中间标题 + 一行摘要。
- 右侧箭头或 Switch。

推荐资源：

- 卡片：`@style/Ng.EntryCard`
- 圆形图标：`@style/Ng.IconCircle`
- 标题：`@style/Ng.CardTitle`
- 摘要：`@style/Ng.Summary`

约束：

- 标题不超过一行。
- 摘要用于状态，不放长说明。

### ListCard

用于二级列表项。

结构：

- 左侧图标。
- 中间标题、摘要、可选 tag 行。
- 右侧更多、拖拽、状态或箭头。

推荐资源：

- 卡片：`@style/Ng.ListCard`
- 标题：`@style/Ng.CardTitle`
- 摘要：`@style/Ng.Summary`
- 状态：`@style/Ng.Tag.*`

约束：

- 禁用项可以弱化，但不要使用大面积红色背景，除非是错误状态。
- 当前使用项可用红色或 info tag 标识。

### OutlinedField

用于配置输入。

结构：

- 标签。
- 圆角边框输入框。
- 可选辅助说明或错误说明。

推荐资源：

- 标签：`@style/Ng.Label`
- 输入框：`@style/Ng.OutlinedField`

约束：

- 边框使用低对比暖灰。
- 聚焦色使用 `ng_primary`。
- API Key 等敏感字段必须有显示/隐藏能力或保持密码输入。

### Tag

用于状态。

类型：

- 成功：启用、成功。
- 警告：禁用、待确认。
- 信息：模型数量、当前使用、耗时。
- 错误：失败、不可用。

推荐资源：

- `@style/Ng.Tag.Info`
- `@style/Ng.Tag.Success`
- `@style/Ng.Tag.Warning`
- `@style/Ng.Tag.Error`
- `@style/Ng.Tag.Neutral`

约束：

- tag 是辅助信息，不应成为主视觉。
- 同一卡片内 tag 不宜超过 3 个。

### ActionBar

用于详情页底部或弹窗底部。

结构：

- 次要操作在左或靠前。
- 主操作在右。
- 危险操作要明确文案。

推荐资源：

- 操作区：`@style/Ng.ActionBar`
- 文本按钮：`@style/Ng.ActionText`
- 小按钮：`@style/Ng.SmallButton.Primary` / `@style/Ng.SmallButton.Secondary`

约束：

- 不要把保存、恢复默认、测试连接随意分散。
- 同一类页面操作位置保持一致。

### SearchPill

用于页面顶部搜索和过滤入口。

推荐资源：

- 容器：`@style/Ng.SearchPill`

约束：

- 只承载搜索图标、输入文本和必要清除按钮。
- 不要在搜索框内放复杂操作。

### Segmented

用于详情页内切换 `配置 / 模型`、`正文 / 预览` 等少量互斥视图。

推荐资源：

- 容器：`@style/Ng.SegmentedContainer`
- 普通项：`@style/Ng.SegmentedItem`
- 选中项：`@style/Ng.SegmentedItem.Selected`

约束：

- 只用于 2 到 4 个互斥项。
- 不替代页面级导航。

## AI 页面约定

AI 相关页面必须遵守：

- 一级菜单是 `AI 设置`。
- 二级能力包括 `提供商`、`提示词`，后续可扩展 `技能`、`默认模型`。
- Provider、Prompt、Skill 都使用二级列表 + 详情页模式。
- AI 功能 UI 参考 MD3 结构，但颜色使用 Reading NG token。
- 不要让单个功能把 AI 设置页变成孤立表单。

后续新增 AI 功能时，应先判断它属于：

- Provider：模型接入和连接能力。
- Prompt：任务说明和模型行为偏好。
- Skill：端到端功能编排。
- Runtime：阅读页中的即时操作入口。

## 调试页面约定

调试类页面应保持：

- 搜索条或过滤条在顶部。
- 主信息使用行式列表或阶段卡片。
- 状态信息使用 tag。
- 详细日志、响应体、JSON、HTML 使用 CodeView 或等宽展示。
- 大响应内容必须截断或提供导出，不直接撑爆弹窗。

## 不推荐做法

避免：

- 每个功能单独定义一套卡片、颜色、按钮。
- 大面积使用 MD3 紫色或动态色覆盖阅读 NG 主题。
- 在同一页混用 PreferenceScreen 风格、RikkaHub 风格和自绘风格。
- 为了“现代感”增加不必要阴影、渐变、装饰图形。
- 卡片内塞过多解释文字。
- 弹窗使用宽松大字号导致正文候选错位或遮挡按钮。

## 验收标准

新增 UI 功能至少检查：

- 是否使用本文档已有页面模式。
- 是否复用现有 Reading NG 色彩和承载面风格。
- 是否与书架首页、搜索页、调试页冲突。
- 手机竖屏下文字是否溢出或挤压。
- 弹窗底部按钮是否遮挡内容。
- 状态信息是否一眼可懂。
- 如果是 AI 功能，是否区分 Provider、Prompt、Skill、Runtime。

## 后续建议

短期：

- 先将 AI 设置首页改为 grouped list，将 provider/prompt 二级列表和详情页按本文档统一一轮。
- 将 AI 章节净化确认弹窗收敛到统一确认弹窗模式。

中期：

- 抽出 View/XML 版通用组件：`GlassPanel`、`SettingsGroup`、`SettingsItem`、`EntryCard`、`ListCard`、`OutlinedField`、`Tag`、`ActionBar`。
- 把 AI、MCP、网络日志、书源调试等开发功能统一到同一工具型视觉语言。

长期：

- 如果未来要迁移 Compose，可以继续使用同一套 token 和页面模式，不把视觉方向绑定到当前 XML 实现。
