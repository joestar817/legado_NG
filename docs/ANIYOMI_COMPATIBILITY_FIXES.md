# Aniyomi 目录与视频资源兼容修复

日期：2026-11-15。依据用户提供的 23:03–23:05 设备日志，以及从当前调试应用只读提取的 AniHQ 16.5、Anikoto 16.13 扩展。检查副本与 DEX 输出位于忽略目录 `build/aniyomi-diagnostics/`，没有修改设备上的扩展或应用。

## 目录

日志中的 `anihq.ccnaruto-shippuuden` 来自 `AniyomiProvider.chapters` 无条件查询季度，再由默认 `seasonListRequest` 直接连接 baseUrl 与作品 slug。实际 AniHQ 扩展的 `seasonListParse` 直接抛出 UnsupportedOperationException，但错误请求在进入 parser 前已失败。

- 提供方按 `SAnime.fetch_type` 选择季度或剧集 API。
- CatalogItem.metadata 使用带格式标记的封装保存 fetch_type、season_number 和原始 memo；兼容此前直接保存的 memo。历史条目没有 fetch_type 时沿用 ABI 的 Episodes 默认值；季度来源可通过刷新详情取得新的字段。
- AnimeHttpSource 的默认详情、目录、季度、线路和视频请求用 HttpUrl.resolve 处理 slug、根路径、完整 URL 与协议相对 URL。扩展自己覆写的请求方法不受此默认实现控制。

## 播放

实际 Anikoto 的提取代码给成功得到的视频附加 `demuxer-lavf-o=force_mpegts=1` 和 `force_mpegts=1`；原提供方将所有非空 MPV/FFmpeg 参数一律拒绝。

- 接受上述两个精确的 demuxer 提示，交给 Media3 原生媒体处理。其他 MPV/FFmpeg 参数和 localhost:1 代理仍明确失败；不宣称完成了本地代理适配。
- 补齐旧 Video 构造器的 getVideoUrl 和未初始化视频的 resolveVideo 调用，再转换为中立资源，保留请求头、字幕、音轨、preferred 与线路身份。
- 单条线路或清晰度失败不丢弃其他可用资源；所有候选失败时保留首个实际错误，协程取消始终传播。遵循扩展线路/视频排序。
- 宿主解析入口对扩展正文错误直接传播，避免继续嗅探内部章节地址而以通用错误覆盖原始原因。

`No extractor for VidPlay` 是该 Anikoto 版本在对应地址上直接返回空列表时发出的日志。本次没有增加 VidPlay 提取器，不能据此宣称该线路可播；修复让其他已成功提取的资源不会被参数检查或其他线路错误连带丢弃。

## 验证

- `build/aniyomi-log-fix-check.log`：extension-compat 与 source-aniyomi 共 40 项 JVM 测试通过，其中新增 URL 2 项、提供方 10 项；16 模块边界检查通过。
- 应用初次增量编译没有识别源码中存在的 GSON/getDanmaku 顶层声明；关闭 Kotlin 增量缓存后，`:app:compileAppDebugKotlin` 与 16 模块边界检查通过，见 `build/aniyomi-app-recheck.log`。未为该缓存异常修改函数或导入。
- 保留工作区原有视频预加载实现。本次只在其 NgVideoEpisodeResolver 中增加扩展异常传播的一行 hook。
- 未构建/覆盖安装 APK，未验证真实站点播放、Media3 解复用效果或设备首帧。编译检查继续排除已有的 `:app:verifyNgDesignSystem` 失败项。
