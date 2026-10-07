# NG Design System 架构

本文档定义 Reading NG 主题与组件体系的运行时边界。视觉规范继续以
`READING_NG_UI_STYLE.md` 为准；本文只说明主题数据怎样进入 View 与 Compose 组件。

## 目标

建立唯一的数据流：

```text
ThemeConfig / ThemeStore / 未来 ThemeSpec
                    ↓
             NgThemeResolver
                    ↓
             NgThemeSnapshot
            ↙               ↘
     View/XML 组件         NgAppTheme
                              ↓
                       Compose Ng* 组件
```

业务页面不应自行从基础颜色推导卡片、描边、选中态、文字色或系统栏样式。

## 当前兼容层

第一阶段保留现有 `ThemeConfig`、`ThemeStore`、SharedPreferences 和 Activity recreate：

- `NgThemeResolver.resolve(context)` 只读取当前运行时主题并生成快照。
- Resolver 不写偏好、不修复旧状态、不决定主题选择，也不执行迁移。
- 当前 `accentColor` 映射为 NG 主操作色 `primary`。
- 当前 `primaryColor` 映射为 `topBarContainer`，不再与主操作色混用。
- `backgroundColor` 映射为页面背景，`bottomBackground` 暂时作为基础 surface。
- 其余容器、文字、描边和状态颜色由 Resolver 统一补齐。

未来引入 `ThemeSpec`、种子色算法或主题包时，只替换 Resolver 的输入，不改变组件 API。
这一步必须排在组件和页面迁移稳定之后；当前阶段不得改写主题选择、主题 JSON 或背景加载链路。

## 运行时快照

`NgThemeSnapshot` 包含：

- `NgColorScheme`：Material 通用语义和 Reading NG 业务语义。
- `NgShapeTokens`：有限的圆角等级。
- `NgSpacingTokens`：页面、卡片和组件间距。
- `NgTypographyTokens`：稳定的文字层级。
- `NgEffectTokens`：透明度、模糊和动效能力。
- `NgSystemBarTokens`：状态栏、导航栏图标明暗。

墨水屏快照必须关闭模糊和动效，并移除容器透明度。

## Compose 边界

所有 Compose 页面使用 `NgAppTheme` 作为根主题：

- `NgAppTheme` 将同一快照映射为 Material 3 `ColorScheme` 和 `Shapes`。
- NG 扩展语义通过 `NgTheme` 的 CompositionLocal 读取。
- 系统栏图标由快照统一决定，页面不再固定指定明暗。
- Feature 内不得再创建私有的 `lightColorScheme`／`darkColorScheme`。

AI 聊天是首个接入页面；其原私有 `RikkaChatTheme` 已移除。

## View/XML 边界

旧页面可以继续使用现有 ThemeStore。新建或迁移的 NG View 组件应在组件内部通过
`NgThemeResolver.resolve(context)` 读取快照；Feature 页面只选择具名 Variant，不自行计算颜色。

在完整组件 API 出现前，不对旧页面做全仓库机械替换。

## 组件演进顺序

1. 以现有 `ng_ui_styles.xml`、`ng_ui_tokens.xml`、动态强调色 View 和真实生产页面作为视觉基准，不从 Material 默认组件重新设计。
2. 先选择 AI 设置／Provider 这类边界清楚的真实页面，在现有 View 业务中提取该页实际需要的 `Ng*` 原子组件；组件视觉通过后再决定整页 Compose 迁移，不提前重写页面。
3. 以完整页面为单位迁移和人工验收；不得把 ComposeView 塞进旧 RecyclerView item。
4. 页面验收同时冻结字号、图标尺寸、间距、圆角、描边、透明度、禁用／选中／错误状态，后续页面只能复用。
5. 按 `NG_COMPONENT_ACCEPTANCE_CHECKLIST.md` 的真实页面顺序逐步补齐卡片、输入、弹窗、图片和媒体控件。
6. 多个真实页面稳定后再固化 Settings、Management、Editor、Detail 等页面 Pattern。
7. 最后才改造 ThemeSelection、ThemeSpec、种子色、主题包和独立背景层；主题系统只能给已稳定组件提供 Token。

当前 Debug Catalog 仅保留为未发布实验，不再作为视觉或交互验收门禁，也不继续扩展。
组件验收矩阵和代表页面见 `NG_COMPONENT_ACCEPTANCE_CHECKLIST.md`。

截至 2026-07-30，第一批已通过的真实页面组件为 `NgSearchBar`、`NgSecondaryButtonView`、
`NgFloatingTabBar`、`NgMenuPopup/NgActionPopup` 和 `NgLongListBottomSheet` 外壳；准确视觉规则见
`READING_NG_UI_STYLE.md`。其余组件不得仅因已有类名或 Compose 示例就标记为稳定。

## 暂不实施

- 不全量迁移 Compose。
- 不引入 Miuix 作为主组件体系。
- 不在第一阶段引入 MaterialKolor 或主题包 V2。
- 不在组件验收前修改“我的”、主题列表、背景 Host 或现有主题选择。
- 不新增旧主题、旧偏好或内部开发状态的隐式迁移。
- 不把每个视觉问题继续扩展为新的布尔属性。

## 第一阶段验收

- View 与 Compose 可以从同一个 `NgThemeSnapshot` 取得语义。
- AI 聊天不再持有私有 ColorScheme，并能跟随亮暗主题和系统栏图标语义。
- 亮色、暗色和墨水屏解析器拥有 JVM 回归测试。
- 调整 Resolver 不需要修改业务页面。
- 迁移页面必须与当前稳定 NG 页面和主题背景保持一致；不得通过修改主题系统掩盖组件偏差。
