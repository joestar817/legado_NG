# Video preload

独立 Kotlin/JVM 模块，不依赖 Android、NG 实体或具体播放器。宿主提供下一集身份、解析函数、备用播放器工厂和释放函数，所有入口在同一播放器线程调用。

当前集播放稳定且前方已有 3 秒缓冲后，延迟 600 ms 解析相邻下一集。只保留一个暂停的备用播放器；资源最多保留 2 分钟，并提前 5 秒避开已知凭证有效期。解析失败退避 30 秒，跳集、源/线路/质量/音轨变化及退出清理旧任务和播放器。`take` 转交已有播放器，即使播放器仍在准备，也可以继续该次准备而不重复解析；宿主按真实 READY 状态显示等待。

`VideoPreloadPolicy` 负责准入和预算，`NextVideoPreloader` 负责取消、过期、迟到结果及资源所有权，`PlaybackLoadingGate` 仅延迟加载提示，不改变真实缓冲和错误恢复状态。

Android 接入在 `app/integration/video/NgVideoPreload.kt`。解析沿用同一正文规则、扩展资源和后台嗅探；备用 Media3 不抢音频焦点，不绑定当前画面/字幕，接管时使用原 Surface。默认保留一个备用播放器，电视/低内存设备默认关闭，可在视频设置切换。当前实现不预读详情页，不为直播预开下一集。

验证：`:lib:video-preload:test`、`:app:compileAppDebugKotlin`、`verifyFeatureBoundaries`。本地测试不证明真实书源、双解码器或设备首帧耗时。
