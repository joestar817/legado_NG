# Legado feature modules

迁移依据：`docs/LEGADO_FEATURE_MODULE_MIGRATION_PLAN.md`。所有新增业务实现归属本目录；模块不得依赖 app，NG 实体映射和平台执行归属 `app/integration`。

| 模块 | 实现 |
| --- | --- |
| feature-api | 源身份、导入结果、兼容策略、执行/配置/日志/资源租约端口 |
| feature-backup | 版本化配置归档、预校验、未知模块条目保留 |
| source-yiciyuan | 异次元导入、规则转换、结果归一化、请求/解密兼容 |
| tts-multitts-compat | 安全备份解析、稳定音色/风格、三类 SCRIPT 模板与受限 JSON 响应校验 |
| extension-compat | 保留插件包名/API 的公共 Android ABI 与平台桥 |
| source-runtime | 仓库、索引、信任、安装、加载与生命周期 |
| source-mihon | 漫画扩展、独立筛选会话、更新锁与图片流 |
| source-aniyomi | 视频扩展、剧集、hoster 与播放资源映射 |
| source-tvbox | 命名空间配置、CMS/JAR/JS、WebView 嗅探和会话代理 |
| manga-reader-support | 章节与原图窗口、24 张预算、双页、锚点与跨章状态机 |
| manga-translation | OCR、公共翻译/宿主 AI、擦字回填、队列和文件租约 |
| manga-pdf-support | 分档预览/高清、原生渲染、窗口、缓存和释放 |
| video-support | 线路/清晰度/字幕/音轨选择、过期检查与会话版本 |
| video-preload | 播放页下一集预解析/备用播放器所有权、失效/退避、缓冲预算与加载提示防闪烁 |
| video-player-media3 | 复用现有 ExoPlayer、协议依赖、字幕/音轨合并和六档画面效果 |
| legado-ng-adapter | 公共功能入口、Android 装配、共享图片订阅与资源租约 |

feature-api、feature-backup、source-yiciyuan、tts-multitts-compat、manga-reader-support、video-support 和 video-preload 为 Kotlin/JVM；其余为 Android Library。应用最低 API 23，以满足 Media3 1.11.1。QuickJS Spider 在 Android 23 及以上按需运行。模块不引用 NG 实体或全局应用状态。依赖跟随根版本目录，Java 17。

```powershell
.\gradlew.bat :lib:video-preload:test :lib:video-support:test verifyFeatureBoundaries
.\gradlew.bat :lib:feature-api:test :lib:source-yiciyuan:test :lib:tts-multitts-compat:test :lib:extension-compat:testDebugUnitTest :lib:source-runtime:testDebugUnitTest :lib:source-mihon:testDebugUnitTest :lib:source-aniyomi:testDebugUnitTest :lib:legado-ng-adapter:testDebugUnitTest verifyFeatureBoundaries
```

已接入点见 `UPSTREAM_HOOKS.md`，实际进度与证据见 `docs/FEATURE_MIGRATION_STATUS.md`。本地测试不能替代原生 JS、动态 APK/JAR、OCR 或视频的设备验证。

默认装配全部 16 个模块。`-PlegadoPdf=false -PlegadoVideoEffects=false` 从 Gradle 项目与 adapter 编译依赖中排除 PDF 优化和播放器增强，使用缺省 façade；NG 原有 PDF 与普通播放器入口仍可编译。其余模块暂为固定装配，运行时停用源不会移除其依赖。

`verifyFeatureBoundaries` 检查模块依赖、宿主引用和 hook 登记；`verifyFeatureVisuals` 覆盖 integration 和 Library UI；`verifyFeatureArtifacts` 检查中间 JAR 的插件 ABI 唯一所有者、merged Manifest 与可选模块依赖。后者不执行 APK/DEX/R8，也不证明设备加载兼容。
