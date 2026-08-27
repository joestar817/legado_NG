# NG 视觉体系

## 定位

NG 设计系统负责组件语义、主题色、排版、间距与行为；视觉体系只解释这些组件应使用什么材质和反馈。切换视觉体系不得改变页面结构、信息层级、控件数量、回调或业务时序。

当前体系：

- `透明玻璃`：现有 NG 半透明容器、主题染色、边缘高光与静态降级。
- `液态玻璃`：在透明玻璃语义上增加实时背景捕获、模糊和边缘折射。

用户选择与日间／夜间主题、颜色主题包和背景图相互独立。E-Ink 模式临时接管实际渲染，退出后恢复用户原先选择的视觉体系。

## 材质角色

业务组件只声明角色，不直接选择 Shader 参数：

- `NAVIGATION`：播放器控制 Dock；使用高透近中性表面、窄边缘折射和克制 vibrancy。
- `TOP_NAVIGATION`：主界面顶部 Dock；窄边缘、低位移折射，以稳定背景和柔和高光为主。
- `BOTTOM_NAVIGATION`：主界面底部 Dock；使用更强模糊与中性表面，避免正文可读地透出。透明与液态模式都不叠加选中磨砂遮罩，只用图标资源、主题色和前景明暗表达状态；液态模式额外提供轻微尺度强调。
- `OVERLAY`：抽屉、菜单、对话框外壳。
- `INTERACTIVE`：播放器小型按钮、音色／来源胶囊和滑块 Thumb；以透明透镜、方向高光和按压形变为主。
- `ICON_ACTION`：固定顶栏等小型图标操作；沿用顶部 Dock 的磨砂、窄边缘折射，并提高小面积承载面强度。
- `ACTION`：带文字的中型操作按钮；沿用顶部 Dock 的磨砂参数，优先保证文案稳定。
- `CONTENT`：书籍详情等信息卡；以书架顶部 Dock 为材质基线，使用同档模糊、窄边缘折射和低色散。
- `SETTINGS`：设置导航／Preference 卡；与书架顶部 Dock 使用同一磨砂参数和方向高光。
- `SOFT_SURFACE`：少量焦点内容面。

正文、消息气泡和密集列表项不属于液态玻璃承载角色。

## 渲染分级

- Android 13+／API 33+：模糊与 AGSL 圆角边缘折射。
- Android 12／API 31～32：只启用模糊，保留 NG 材质层。
- API 21～30：回退到透明玻璃。
- E-Ink：回退到高可读实色材质。

完整液态后端按角色组合饱和度增强、模糊、depth lens、可选色散和方向高光；不得用降低透明度并增加完整描边代替真实折射。

## 接入边界

业务组件不得逐个判断当前是透明玻璃还是液态玻璃。统一调用链为：

```text
既有 NgGlassSurface／领域公共材质组件
    → NgVisualSurface
        → 当前视觉体系后端
```

组件只声明 `NAVIGATION／TOP_NAVIGATION／BOTTOM_NAVIGATION／OVERLAY／INTERACTIVE／ICON_ACTION／ACTION／CONTENT／SETTINGS／SOFT_SURFACE` 语义。一个 Compose 页面只需在外壳提供一次背景采样源，同一绘制树中的公共材质组件即可自动切换；以后新增视觉体系时也只扩展后端和角色参数，不改业务实例。

同一 Compose 绘制树优先使用 GraphicsLayer backdrop。View 列表与独立 `ComposeView` Dock 不能共享 Compose 捕获图层时，使用公共 View-backed `RenderNode` 后端，从明确位于承载面后方的内容 View 与窗口背景实时重绘；不得通过静态窗口截图伪造 backdrop，也不得为了材质接入擅自迁移页面结构。手工绘制且未使用任何公共材质组件的旧控件，需要先完成一次组件规范化，但不为每套视觉体系重复适配。

全屏 Compose Activity 若直接透出 decor background，使用 `NgWindowLiquidGlassBackdropHost` 建立一个位于内容后的透明 View source，并通过页面级 CompositionLocal 让公共组件复用 View-backed `RenderNode` 后端；它不是 PixelCopy 或位图截图。页面中的 `NgGlassSurface`、`NgVisualIconButton` 和 `NgActionBarButton` 继续只声明材质语义，由公共后端选择透明或液态实现。

View／Fragment 混合的设置页在实际内容后提供一次 `ng_liquid_glass_backdrop_source`。`NgVisualSurface` 会自动发现该页面 source，Compose 的 `NgSettingsItem／NgSettingsSliderItem／NgExpandableSettingsItem` 统一经 `SETTINGS` 角色渲染；Preference 页面统一使用 `NgSettingsItemGlassLayout`。不得在各设置 Fragment 中重复传 source 或判断视觉体系。

View `NgFloatingTabBar` 的详情 Dock 在页面存在约定 source 时自动接入 View-backed 后端，材质完全复用书架悬浮底栏的 `BOTTOM_NAVIGATION`、表面色、圆角和透明度配置。不能把该规则外推到所有底部元素：实色内容栏、列表附属栏、表单分段控件和抽屉内操作栏保持各自原材质。

整体偏暗的播放器不复用书架偏亮磨砂。听书与音频播放页只让 `NAVIGATION／INTERACTIVE` 控制层使用高透近中性液态玻璃；章节／字幕、文本场景、音频信息／歌词卡等 `SOFT_SURFACE` 内容层保持稳定暗色透明材质。两页各自只提供一次真实背景录制，控制面只吸收极少当前主题色，通过窄边缘折射、弱方向高光和按压形变表达液态，不以大面积染色或高遮蔽制造实体层。

当前第一阶段只在 Debug 的“设置 → 外观设置 → 视觉体系”中开放。主界面书架顶部 Compose Dock、底部 View `NgFloatingTabBar`、书籍详情页、“我的”一级菜单、配置页／关于页的同类设置卡，以及 AI Provider／TTS 引擎详情页底部切换 Dock 已接入真实背景采样；其它页面在接入宿主和完成性能验收前会由统一入口可靠回退透明玻璃。

## 上游来源

背景图层与圆角折射实现参考并修改自 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)，上游采用 Apache License 2.0。Legado 没有直接引入其 Maven 依赖，以避免把当前 Kotlin／Compose 基础栈整体升级。完整许可证随 APK 源码资产保留在 `app/src/main/assets/licenses/AndroidLiquidGlass-LICENSE-2.0.txt`。
