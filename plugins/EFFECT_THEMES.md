# 主题与听书特效作者 SDK 1.1.0

主题与听书特效分别分发为独立 ZIP，安装、更新、参数、选择、另存、导出和卸载互不影响。作者可共用源码和原始素材，宿主不会把一个安装项同时用在界面和播放器，也不依赖具体内容 ID。

## 独立构建

维护者导出 `:modules:effect-theme-api:exportEffectThemeSdk`，解压 `build/outputs/effect-theme-sdk/effect-theme-sdk-1.1.0.zip`。作者只需要此 SDK、JDK 17 与 Android SDK 36，不需要 App 源码。Windows PowerShell：

```powershell
.\gradlew.bat packageEffectTheme --no-parallel '-PthemeProject=D:\themes\my-theme'
```

作者项目包含 `theme.json`、`src/main/java`、`src/main/assets`。`distributions` 可声明 `["theme"]`、`["listening_effect"]` 或两者；它只用于作者构建，不进入发布清单。一次编译后分别产生：

- `build/outputs/theme/<id>-<versionCode>-theme.zip`
- `build/outputs/listening-effect/<id>-<versionCode>-listening_effect.zip`

两个 ZIP 都完整包含代码与素材，不引用对方，删除任意一个不会影响另一个。只在确实支持对应用途时声明输出；需要不同参数或素材时使用独立作者配置。官方猫咪、樱花各输出两包，雨夜、火焰、流体只输出听书特效包。

本地联调时，将 `*-listening_effect.zip` 放到手机。在朗读或有声书的播放器打开「播放动效 → 动效包」，点「已安装」右侧的导入图标。确认导入后点卡片启用。修改后重新打包即可重复导入，同 ID、同版本且内容有变化时会提示替换。

## 清单与用途

完整发布清单使用 `version=2` 与 `theme` 配置体。主题为 `format=reading-ng-theme`，`theme.effect.targets` 必须恰好为 `["app"]`；听书特效为 `format=reading-ng-listening-effect`，必须恰好为 `["player"]`。普通无代码主题继续使用原主题格式。导入入口会拒绝错误用途、旧双用途包和格式/用途不一致的包，不自动拆分或迁移旧记录；需重新导入独立新版 ZIP。

内部共享配置体不代表共享安装。听书包不携带 App 顶栏、抽屉、封面库等界面设置，只保留渲染、参数、播放器配色与备用背景需要的数据。两种包可使用相同作者 ID，但版本、目录、安装记录、运行错误和重载状态分别归各自库。

`theme.effect` 声明 API 1 入口、设备要求、静态预览、参数表及播放器取色策略；`publication.versionCode` 是递增整数，`versionName`、作者与名称独立填写。发布包不写设备路径、安装摘要或安装来源。作者可附 `build.dependencies` 固定版本 Maven 坐标，该字段不发布。SDK API 必须 `compileOnly`，不得打包 API 类、`io.legado.app` 实现或 native 库。

资源中心使用独立类型 `theme` / `listening_effect`，后者位于听书分类，能力声明为 `listening.effect.1`；特效主题使用 `theme.effect.1`。本地 ZIP 不要求在线目录，导入不会自动启用。听书特效沿用现有听书数据的备份策略，不混入“外观与布局”整包备份；跨设备请导出独立 ZIP，导入后再手动选择。

## 素材和预览

ZIP 只包含 `manifest.json`、`classes*.dex` 和 `assets/**`。每个包自带静态备用背景、配色和预览。`lightBackgroundAsset` / `darkBackgroundAsset` 指向包内文件；播放器可按 `playerPalette` 使用包的配色或书籍封面色。

`effect.preview` 是 PNG/JPEG/WebP 静帧，最大 2 MiB，宽高各 1～2048。预览、渲染纹理与备用背景分别引用；原始渲染素材直接复制，仅 ZIP 无损压缩，不进行 Q95 或其它图像重编码。最终检查覆盖格式/用途、参数、预览、DEX 接口、构造器、路径和大小。

## API 1 合同

- `EffectThemePlugin.createSession(Context, Assets, Callbacks)` 在 UI 线程创建独立 session；`getView()` 返回同一未挂载 View。
- `update(Map<String,String>, animationAllowed, timelineOriginNanos)` 接收完整参数快照；null 时间原点代表本地时钟。参数支持 range / choice / color / boolean，颜色为 #RRGGBB 或 #AARRGGBB。
- 宿主只读 `host.coverColor` / `host.primaryColor` 使用 #AARRGGBB，作者不能将其声明为可写参数。
- `assets.open(path)` 读取当前安装版本的资产相对路径，流必须关闭，不直接访问 App AssetManager。
- 首帧可显示后调用 `onReady()`，失败调用 `onError(message)`；宿主核对 session 身份后处理异步回调。
- `release(Runnable)` 可重复调用，每次 completion 仅执行一次，GL/解码线程停止后才能完成；完成后不得继续读取素材。
- 复制、改名、另存后的完整包必须独立运行，不得把作者 ID 当成宿主固定业务身份。

公共 API 1 签名保持不变。新增用途不等于修改渲染接口；修改签名必须升级合同并检查 `modules/effect-theme-api/api-v1.txt`。应验证空库、离线导入/取消、错类型拒绝、同 ID 两库共存、更新/降级、另存/导出、卸载隔离与后台释放。构建成功不代表这些设备流程已经验收。
