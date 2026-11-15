# NG 接入点

核对基线：NG `7b6087410f287511080f2a93b82795231f4b72fd`，2026-11-09。

以下路径仅承载委托、输入/返回值映射或平台调用，业务实现归属 lib。

| 宿主文件 | 职责与默认行为 | 回归 |
| --- | --- | --- |
| `settings.gradle` | 引入 lib/settings.gradle；集中模块清单 | Gradle 配置 |
| `build.gradle` | JVM 插件与边界检查入口 | verifyFeatureBoundaries |
| `app/build.gradle` | 只依赖 feature-api 和 adapter | app Kotlin 编译 |
| `app/proguard-rules.pro` | 保留脚本响应对象的反射成员 | 仅规则登记，未做 release 打包 |
| `app/src/main/assets/web/help/md/jsHelp.md` | 新增异次元 JS 兼容 API 帮助 | 与实际签名核对 |
| `app/src/main/java/io/legado/app/ui/association/BookSourceImportReader.kt` | 文件/URL/文本逐条转换；普通 JSON 保留原导入与预览/保存流程 | 导入回归 |
| `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditViewModel.kt` | 编辑导入经同一转换入口 | 普通 JSON / 异次元 |
| `app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt` | 标记源的内容/规则/结果兼容；默认策略原值；getStringSafe 保留取消 | 规则与取消 |
| `app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt` | 标记源显式 webJs 时交给已有 WebView；视频解析读取请求头/脚本/延迟，重定向保留规则上下文，兼容百分号编码 URL 选项 | 编码选项/源签名上下文 |
| `app/src/main/java/io/legado/app/help/http/BackstageWebView.kt` | 按来源实现增加资源请求拦截及大小写无关匹配，原规则脚本/延迟/HTML 回退继续复用；单次完成与取消清理 | 视频嗅探/正常规则网页 |
| `app/src/main/java/io/legado/app/help/JsExtensions.kt` | fetch/aJax/match/get 委托，网络仍走 NG 源作用域；视频解析期间捕获 startBrowser/Await、openUrl 和嵌套播放器请求 | HTTP 状态与取消/视频跳转拦截 |
| `app/src/main/java/io/legado/app/help/source/SourceVerificationHelp.kt` | 视频规则解析期间将浏览器请求交给当前解析会话，避免显示网页或等待前台验证页 | 规则跳转/正常验证隔离 |
| `app/src/main/java/io/legado/app/ui/login/SourceLoginJsExtensions.kt` | 视频规则内 showBrowser 记录播放网页，其他调用沿用原对话框 | 视频规则/正常网页隔离 |
| `app/src/main/java/io/legado/app/help/JsEncodeUtils.kt` | ES/AES 解密委托 | 字节/流/Base64/hex |
| `app/src/main/java/io/legado/app/model/webBook/BookList.kt` | 空列表、书籍地址修复委托 | 空响应/非法 URL |
| `app/src/main/java/io/legado/app/model/webBook/BookChapterList.kt` | 目录 URL 规则/结果委托 | 目录规则 |
| `app/src/main/java/io/legado/app/model/webBook/BookContent.kt` | 标记的图片源正文转 img | 普通文本/音频/视频默认不变 |
| `app/src/main/java/io/legado/app/utils/ImageUtils.kt` | 标记源脚本清洗/执行和返回值解包；普通源原路径 | 原图/封面 |
| `modules/rhino/src/main/java/com/script/rhino/RhinoClassShutter.kt` | 新迁移包沿用 App 类直接导入默认拒绝；宿主返回对象沿用现有包装通道 | 直接导入与注入响应回归 |
| `app/src/main/java/io/legado/app/data/entities/BaseSource.kt` | 显式带 Job 的 evalJS 重载，旧签名仍传 null | 原书源类策略/取消 |
| `app/src/main/java/io/legado/app/help/tts/TtsScriptEngineClient.kt` | 准备/签名/音频传同一取消上下文；可选固定路径 JSON 业务校验委托，缺省保留旧行为 | 旧脚本/取消/火山响应 |
| `app/src/main/java/io/legado/app/ui/config/TtsEngineConfigFragment.kt` | 本地 ZIP 文件读取/预览委托，同一 JSON 导入及冲突流程 | 原 JSON 文件/ZIP 取消预览 |

后续接入条目随代码同时登记。该检查不代表 APK 中 ABI、混淆和 merged Manifest 已验证。

| 配置备份宿主文件 | 职责与默认行为 | 回归 |
| --- | --- | --- |
| `app/src/main/java/io/legado/app/help/storage/Backup.kt` | 其它设置分类增加版本化功能配置加密条目；加密沿用 NG 备份密码 | 格式/模块缺失 |
| `app/src/main/java/io/legado/app/help/storage/Restore.kt` | 写数据前校验条目，最终恢复配置并重建源运行时；旧备份缺失条目时原行为 | 旧备份/未来条目 |

| 视频宿主文件 | 职责与默认行为 | 回归 |
| --- | --- | --- |
| `app/src/main/java/io/legado/app/model/VideoPlay.kt` | 每次解析捕获会话版本；单链接、订阅、选集在独立规则拦截上下文中解析；选集解析委托共用入口，优先接管预开的下一集；新源清理预加载 | 迟到回调/清晰度位置恢复/规则禁止网页跳转/预加载接管 |
| `app/src/main/java/io/legado/app/integration/video/NgVideoEpisodeResolver.kt` | 共用剧集解析入口传播扩展错误，避免对内部章节地址继续通用嗅探；普通规则源保留章节地址回退 | 扩展原始错误/应用 Kotlin 编译 |
| `app/src/main/java/io/legado/app/integration/video/NgVideoLoadControl.kt` | 显式转发 Media3 LoadControl 生命周期、回退缓冲与起播方法，仅加载决策叠加备用预算 | 真实 ExoPlayer 初始化/生命周期/备用预算与接管 |
| `app/src/main/java/io/legado/app/help/video/Media3VideoEngine.kt` | 平台播放器、显式媒体请求、字幕/音轨/画面效果；按 lib/video-preload 预算暂停准备备用实例，接管后扩展缓冲；备用实例不绑定字幕/当前 Surface | data HLS/请求头/密钥/原画/备用实例回调隔离 |
| `app/src/main/java/io/legado/app/help/video/VideoPlaybackBridge.kt` | 委托 video-support 监测停滞/恢复预算，video-preload 管理下一集和加载提示防闪烁；接管播放器沿用输出 Surface，暂停/退出清理备用实例 | 起播/断流/连续播放/恢复额度/播放器接管 |
| `app/src/main/java/io/legado/app/ui/video/VideoSettings.kt` | NG 设置入口委托清晰度、效果和预开下一集开关 | NG 主题/设置 |
| `app/src/main/java/io/legado/app/ui/video/VideoPlayerActivity.kt` | 全屏使用黑色窗口底色、透明系统栏与浅色图标，退出恢复主题背景、系统栏对比度和分隔线配置 | 全屏/横竖屏/返回窗口 |
| `app/src/main/java/io/legado/app/ui/video/VideoPlayerScreen.kt` | 当前播放实例字幕输出表面；背景和控制条渐变延伸到窗口边缘，控件、字幕和手势单独避让安全区；加载提示使用防闪烁状态 | 字幕/全屏/安全区/缓冲提示 |
| `app/src/main/java/io/legado/app/ui/video/VideoFloatingContent.kt` | 小窗加载提示使用同一防闪烁状态；字幕/恢复保留原始缓冲状态 | 小窗/后台移交 |

| 扩展接入宿主文件 | 职责与默认行为 | 回归 |
| --- | --- | --- |
| `app/src/main/java/io/legado/app/App.kt` | 主进程初始化本地扩展运行时；沙盒进程跳过；启动不刷新仓库 | 应用 Kotlin 编译 |
| `app/src/main/java/io/legado/app/ui/browser/WebViewModel.kt` | 扩展验证会话直接使用挑战 URL/请求头，普通 NG 网页沿用原规则解析 | 验证会话/应用 Kotlin 编译 |
| `app/src/main/java/io/legado/app/ui/browser/WebViewActivity.kt` | 委托 integration 复用可见验证页、Cookie 与结束通知；普通书源验证沿用原流程 | 取消/超时/新 clearance/应用 Kotlin 编译 |
| `app/src/main/java/io/legado/app/lib/cronet/CronetHelper.kt` | 发出每个带扩展 Cookie 上下文的请求前，委托模块按当前 URL 附加 Cookie；普通请求原样返回 | 短路传输/域与路径隔离 |
| `app/src/main/java/io/legado/app/lib/cronet/AbsCallBack.kt` | 扩展请求的重定向和最终响应 Cookie 委托回写共享 Jar；普通 NG 请求沿用原 Cookie 流程 | 响应 Cookie/后续请求复用 |
| `app/src/main/java/io/legado/app/model/webBook/WebBook.kt` | 仅扩展协议源委托搜索/发现/详情/目录/正文 | 普通源原分支、来源类型隔离 |
| `app/src/main/java/io/legado/app/help/glide/LegadoGlideModule.kt` | 注册扩展图片租约加载器 | 普通 URL 仍走原加载器 |
| `app/src/main/java/io/legado/app/help/glide/ImageLoader.kt` | 扩展图片协议交给已注册的加载器 | 普通文件、HTTP、content URI 原分支 |
| `app/src/main/java/io/legado/app/help/book/BookHelp.kt` | 扩展图片下载委托并复用 NG 严格缓存校验与发布 | 普通图片解密路径、取消 |
| `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceManageScreen.kt` | 现有书源菜单增加扩展管理动作 | NG 主题/菜单 |
| `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt` | 管理动作打开 integration 的扩展管理 Activity；仓库、TVBox、来源设置为独立页面 | 应用 Kotlin 编译 |

| 漫画资源宿主文件 | 职责与默认行为 | 回归 |
| --- | --- | --- |
| `app/src/main/java/io/legado/app/model/BookCover.kt` | 捕获书籍/源/资源会话，显示 HIGH、预取 LOW；本地漫画保持原加载 | 原图共享/预加载取消 |
| `app/src/main/java/io/legado/app/model/ImageProvider.kt` | 普通正文原图经同一 resolver；文件尺寸/位图读取持有租约；本地书原有提取路径 | 普通正文缓存与清理 |
| `app/src/main/java/io/legado/app/model/localBook/PdfFile.kt` | 本地图片流委托独立 PDF 模块，关闭输入流释放文件租约 | 文档版本/原图流 |
| `app/src/main/java/io/legado/app/help/glide/OkHttpModelLoader.kt` | 有书籍请求快照时使用共享原图 fetcher；普通封面仍用原 fetcher | 缓存/配置版本 |
| `app/src/main/java/io/legado/app/model/ReadManga.kt` | 重置/退出使资源会话失效，目录重排等待原图任务退出 | 迟到发布/重排屏障 |
| `app/src/main/java/io/legado/app/ui/book/manga/MangaImages.kt` | 视口候选委托模块，24 张预算；尺寸只读规范缓存并持有租约 | 零网络预取/反向取消 |
| `app/src/main/java/io/legado/app/ui/book/manga/MangaFlipController.kt` | 连续阅读翻章状态调用模块跨章策略，原始页映射和手势门控由宿主管理 | 跨章等待/手势门控 |
| `app/src/main/java/io/legado/app/ui/book/manga/MangaPagedImages.kt` | 模块双页布局映射、原始跳页到显示槽、双图独立加载/翻译 | 原始页码/RTL/翻页版本 |
| `app/src/main/java/io/legado/app/ui/book/manga/MangaReaderState.kt` | 独立漫画章节/原图设置映射 | 一次迁移、设置隔离 |
| `app/src/main/java/io/legado/app/ui/book/manga/ReadMangaActivity.kt` | 设置动作写入模块配置并更新预取窗口 | 设置映射 |
| `app/src/main/java/io/legado/app/ui/book/manga/MangaReaderScreen.kt` | NG 数值组件显示独立预取设置 | NG 主题/输入 |
