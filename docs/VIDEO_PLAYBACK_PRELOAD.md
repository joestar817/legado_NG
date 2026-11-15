# 播放页下一集预解析与预开

2026-11-15。参考 `D:\project\guoapp1` 的提前解析、双播放器交接和加载提示防闪烁思路，适配 NG 的规则解析和 Media3。首次媒体地址仍由当前集规则/嗅探取得，不要求详情页提前得到播放地址。

## 所有权

- `lib/video-preload`：独立 Kotlin/JVM。`NextVideoPreloader` 管理单个下一集、600 ms 防抖、30 秒解析预算、2 分钟有效期、已知凭证提前 5 秒失效、30 秒失败退避、取消/迟到结果和播放器所有权。`StandbyBufferBudget` 控制备用缓冲；`PlaybackLoadingGate` 延迟显示加载提示。
- `app/integration/video/NgVideoEpisodeResolver`：正文规则、扩展资源、请求头/字幕/音轨、DASH 临时文件及 NG 实体映射。当前集与预加载共用该解析入口，仍调用原后台嗅探和源网络实现。
- `app/integration/video/NgVideoPreload`：将当前剧集、源配置、章节、选定线路/清晰度/音轨和播放器配置转换成缓存身份；复制下一集规则数据后在 IO 执行解析，主线程创建备用 Media3。
- `Media3VideoEngine` / `VideoPlaybackBridge`：Android 平台播放器与画面/音频焦点/字幕接管。备用播放器暂停且静音，不取得前台音频焦点或抢占字幕、画面。

## 时序

当前集正常播放、时长已知且前方有 3 秒缓冲后，开始准备相邻下一集。下一集解析完成后，由暂停的 Media3 实例请求播放列表、密钥和媒体数据，流媒体资源继续沿用原有绕过磁盘缓存的策略，通过保留同一播放器复用其内部样本缓冲。

备用实例加载决策在缓冲达到 8 秒或已分配样本内存达到 4 MiB 时停止继续加载。这是加载控制预算，单个正在进行的请求、分片和解码器开销可能超过预算，不是进程内存或流量硬上限。接管后解除备用预算，使用当前缓冲档位继续加载，无须重新创建该播放器或重新解析。

切集命中时转移备用播放器所有权、停止并释放旧播放器、绑定原有 Surface、恢复倍速、接入字幕/效果并按自动播放设置启动。如果备用实例还没 READY，保留该次准备并显示真实等待；没有准备结果则走原前台解析。当前实现不接管尚未完成的后台规则解析，提前切集仍会取消旧任务并由前台解析。

跳到其他集、线路/质量/音轨/源配置变化、重试、暂停、恢复错误和退出会释放旧备用实例。解析失败不打断当前集，后台按退避重试。仅相邻下一集参与预开；本地/单链接/订阅没有相邻剧集时不调度，直播不调度。电视和低内存设备默认关闭，视频设置可覆盖默认值。

窗口和悬浮窗共用 500 ms 加载提示防闪烁状态，错误恢复、字幕和播放控制继续读取真实 preparing/buffering。正常首播优先使用解码器尺寸回调；额外的媒体尺寸探测延迟 1.5 秒，已有尺寸时不再重复请求媒体。

## 验证边界

定向测试覆盖预加载复用、一次性转交、跳集/配置不匹配、取消与迟到返回、凭证/TTL、失败退避、解析超时、创建期间取消、直播与缓冲准入、备用预算和短暂缓冲提示。应用 Kotlin 编译、模块边界和功能 UI 规则检查另行执行。

`build/video-preload-final-check.log`：14 项新预加载测试和 24 项原视频支持测试通过，`:app:compileAppDebugKotlin`、`verifyFeatureBoundaries`（16 模块）与 `verifyFeatureVisuals` 通过。编译验证排除了全局 `verifyNgDesignSystem`；该检查单独记录于 `build/video-preload-design-check.log`。

单独执行的全局设计检查未通过：`BookInfoActivity.kt` 有 1 处颜色值问题，`AccentStrokeImageButton.kt` 有 4 处视觉布尔参数问题。这两个文件本次均未修改，属于既有问题。

真实书源规则副作用、后台 WebView、MediaCodec 双实例兼容、音轨/字幕/增强接管、Surface 首帧和实际切集耗时需要设备验收。READY 表示播放器可继续播放，不等同已将首帧画到当前 Surface；不承诺所有片源无等待。

## 2026-11-15 播放器初始化回归

用户设备出现 `播放器初始化失败: getBackBufferDurationUs not implemented`。对照本机实际使用的 Media3 1.11.1 与应用字节码，原 `object : LoadControl by baseLoadControl` 只生成 allocator 和自定义加载决策，Java 默认方法没有被转发到 DefaultLoadControl；构建播放器时读取 back buffer 会进入接口抛错默认实现。onPrepared、轨道选择、起播、停止和释放也存在相同问题。

平台层改用 `app/integration/video/NgVideoLoadControl` 显式转发当前 Media3 的现代 LoadControl 回调，只有 shouldContinueLoading 叠加原有 StandbyBufferBudget。保留当前缓冲档位，以及备用实例 8 秒/4 MiB 的加载准入和接管后解除预算的行为；不通过吞异常或关闭预加载处理。

`NgVideoLoadControlTest` 使用 Robolectric API 29 和真实 Media3：覆盖前台/备用 ExoPlayer 构建与释放、配置的回退缓冲、准备/轨道选择/起播/停止/再次准备/释放，以及时长/实际 allocator 字节预算和接管。设备解码与播放仍需单独验证。

验证结果：`build/media3-load-control-focused-check.log` 中新增 4 项回归全部通过，预加载模块 14 项测试与 16 模块边界检查通过，应用 Kotlin/Java 编译通过。使用 `-Pkotlin.incremental=false` 排除此前增量缓存异常；仍排除既有 `:app:verifyNgDesignSystem`。常规测试入口会先编译所有宿主测试，被既有 Compose `assertExists/assertDoesNotExist` 导入及 NgInterfaceFontComposeTest 参数错误阻断（`build/media3-load-control-check.log`）；聚焦验证通过忽略目录中的临时 init script 限定测试源码，未修改这些无关测试。尚未打包安装或进行设备播放复测。
