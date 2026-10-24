# 听书 BGM 平台协议来源

歌曲VIP目录标识补充（2026-10-23）：搜索和歌单已有响应直接提供字段，不新增逐曲请求。QQ参考固定提交的[Pay定义](https://github.com/L-1124/QQMusicApi/blob/27861e51432ea6b6e88dc35c4e8c8e239c0339e8/qqmusic_api/models/base.py#L131-L149)，仅`pay_month=1`且`pay_play=1`时标记VIP；下载付费`pay_down`不参与，单独购买不冒充会员。网易参考[歌曲详情fee说明](https://raw.githubusercontent.com/NeteaseCloudMusicApiEnhanced/api-enhanced/main/public/docs/home.md)，仅`fee=1`表示VIP；0/4/8分别为免费或无版权、专辑购买、低音质免费而高音质/下载付费，不显示VIP。只有song.fee缺失/null时才读取同ID privilege.fee。未知/畸形字段保留未知，不推免费。两平台字段均仅用于列表展示，不根据104003、试听资源、账号会员状态或音质权益推断，正式取址与权限仍以平台实际响应为准。

样本：QQ“听书BGM”已有授权详情中的三字段为000/111/000/111/111，对应官方第2/4/5首；网易官方匿名`/api/v3/song/detail`与搜索核对2011185017/1397345903/28786838/556673324，fee为0/1/0/8，与截图仅《悬溺》有VIP一致。QQ没有独立的客户端UI isVip协议字段，上述为已验证样本上的保守映射，不宣称覆盖全部特殊权益。

QQ独立试听补充（2026-10-23）：复用固定0.8.2提交的 `modules/song.py` 中 `query_song`、`get_song_urls`、`get_cdn_dispatch` 与 `SpecialSongFileType.TRY`。详情 `music.trackInfo.UniformRuleCtrl.CgiGetTrackInfo` 按同MID匹配；普通 `M500{file.media_mid}.mp3`，试听为 `RS02{vs[0]}.mp3`，songmid仍为原曲MID。试听资源必须同时有非空vs[0]及正数size_try。本地新微信会话验证《无仙》普通MP3返回104003、RS02授权成功且完整解码60秒；try_begin/end差值却为25.759秒，所以不把该窗口当RS02时轴。不采用EVkey或替代源。URL及返回文件身份检查后，仅临时试听允许已确认的独立片段；正式BGM、未确认的其它平台片段仍受原限制。

这些实现只向平台自身请求登录、账号可访问的元数据、正常播放地址及用户主动执行的歌单管理操作，不依赖本机 Node/Python 服务，不包含替代音源。临时媒体地址只在播放前解析，不作为队列数据持久化。

## 网易云

- 参考项目：[NeteaseCloudMusicApiEnhanced/api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced)，npm `@neteasecloudmusicapienhanced/api` **4.41.1**，MIT License。
- 协议参考：`util/crypto.js` 的 weapi/eapi、`util/request.js` 的请求头与 Cookie；`module/login_qr_key.js`、`login_qr_check.js`、`login_status.js`、`user_playlist.js`、`playlist_detail.js`、`song_detail.js`、`cloudsearch.js`、`song_url.js`。
- 手机登录另参考同项目的 [`captcha_sent.js`](https://raw.githubusercontent.com/NeteaseCloudMusicApiEnhanced/api-enhanced/master/module/captcha_sent.js) 和 [`login_cellphone.js`](https://raw.githubusercontent.com/NeteaseCloudMusicApiEnhanced/api-enhanced/master/module/login_cellphone.js)：weapi 发送验证码与提交手机号／验证码，成功后校验账号再保存会话。当前没有实际发送短信或登录验收。
- 本功能使用 `/api/song/enhance/player/url`、eapi、`br=128000`。这是 2026-10-20 独立账号探针实际取得两个正常 MP3 地址并解码短片段的端点。调研中的 `song_url_v1`/xeapi 属于另一路协议，不是该实测的依据，也未为本功能引入 X25519 依赖。
- 只移植必要算法与请求字段，未集成解灰、替代解析、播放上报或云盘上传。
- 原项目声明：Copyright (c) 2013-2022 Binaryify，后续贡献者见原仓库。MIT 许可原文保留于 [licenses/netease-api-enhanced-MIT.txt](licenses/netease-api-enhanced-MIT.txt)。

网易云账号资料补充（2026-10-21）：沿用上述版本的 [login_status.js](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/main/module/login_status.js) 和 [user_detail.js](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/main/module/user_detail.js)。账号身份与基础头像／昵称从现有 `/api/w/nuser/account/get` 获取；只为当前登录 UID 请求 `/api/v1/user/detail/{uid}`（weapi），补充 `level`、`listenSongs` 和 `profile` 中的签名、`follows`、`followeds`、`playlistCount`，并核对返回 UID。数据只保留在界面内存，不存入账号凭据或播放设置。

初次资料接入时按上游文档公开示例 UID 做了一次无登录的官方域名只读字段探测，返回 200，确认上述字段存在；仅记录字段名和存在性，没有读取当前用户凭据，也没有验证当前用户的实际数值。`listenSongs` 是歌曲数量；听歌时长、徽章、乐迷身份和收藏专辑统计仍未接入。不要把这些参考截图内容当作已实现能力。当前用户资料与 Android 呈现仍需用户验收。

VIP 等级补充（2026-10-21）：按同一上游的 [vip_info.js](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/main/module/vip_info.js)，使用 `/api/music-vip-membership/front/vip/info`（weapi），显式传入刚读取的当前账号 `userId`。解析 `data.redVipLevel` 和 `data.redVipLevelIcon`；公开示例已确认正数等级和官方 PNG 地址。若响应携带 `userId`，必须一致；图片只接受平台域名的 HTTP(S) 地址，无凭据和异常端口。正数等级仅表示平台返回的会员等级，不以此推断订阅是否仍有效或到期日期。

会员信息作为可选后续请求，在基础资料发布后读取，独立8秒超时与错误提示，不清除登录态或挡住原资料。缺图使用真实等级文字，零等级不展示徽标，缺失／非法等级作为读取失败处理。动态徽标字段、客户端详细会员信息、成长值及佩戴徽章未纳入本次实现；没有调用签到／领取／续费等写操作。新增6项解析测试源码，本次常规安装不额外运行Gradle单测。

## QQ 音乐

- 参考项目：[L-1124/QQMusicApi](https://github.com/L-1124/QQMusicApi)，**0.8.2**，源码提交 `27861e51432ea6b6e88dc35c4e8c8e239c0339e8`，GPL-3.0。
- 请求协议参考：`modules/login.py`、`user.py`、`song.py`、`songlist.py`、`search.py`，`core/versioning.py`、`core/executor.py`，`utils/qimei.py`、`device.py`、`android_session.py`，`algorithms/sign.py`。
- 使用手机 QQ 二维码/OAuth、Android 20.9.0.8 设备与会话字段；账号检查、刷新、资料、自建/收藏歌单、详情、搜索和普通 MP3 vkey。Cookie 逐次合并，避免独立探针发现的空 Cookie 快照覆盖问题。
- 手机验证码登录参考上述固定版本 `modules/login.py` 的 `SendPhoneAuthCode`、`Login`；发送／提交使用 `tmeLoginMethod=3`，最终凭据类型按响应决定，刷新按实际类型生成参数。安全验证、绑定、限流明确返回，不自动注册或绕过验证。当前未实际发送短信或登录验收；没有实现 QQ／微信原生同机跳转。
- 独立探针已经真实验证登录和读取歌单；普通播放地址样本实返 `104003`，未取得媒体。应用保留平台返回码，不推断全部歌曲失效或自动替换源。Android 运行结果仍需安装后验收。
- 本项目按 GPLv3 发布；QQ 协议适配的派生实现沿用 GPLv3，原项目及作者声明见上述固定源码与项目根目录 `LICENSE`。

微信扫码与资料补充（2026-10-21）：沿用固定版本的 [modules/login.py](https://github.com/L-1124/QQMusicApi/blob/27861e51432ea6b6e88dc35c4e8c8e239c0339e8/qqmusic_api/modules/login.py)、[modules/user.py](https://github.com/L-1124/QQMusicApi/blob/27861e51432ea6b6e88dc35c4e8c8e239c0339e8/qqmusic_api/modules/user.py) 和 [models/user.py](https://github.com/L-1124/QQMusicApi/blob/27861e51432ea6b6e88dc35c4e8c8e239c0339e8/qqmusic_api/models/user.py)。微信扫码使用 QQ 音乐网站授权 `wx48db31d50e334801`，经 `qrconnect` 获取二维码、独立内存客户端轮询，再由 `music.login.LoginServer.Login` 以 `tmeLoginType=1` 换取音乐凭据。UUID／二维码／授权 code 不落盘；切换、关闭、取消和过期释放上下文，提交前复核请求代次，后续刷新保持账号类型。长轮询无事件超时仍视为等待，不当成登录失败。

资料同批检查 `GetLoginUserInfo`，读取 `GetHomepageHeader`、`GetProfileReport`、`GetFollowUserList`、`GetFansList`，只展示身份校验通过的头像、昵称、签名和真实关注／粉丝总数。主页接口失败可使用同账号音乐基因卡片；缺失或隐私隐藏的统计不补零。独立 `VipLogin.VipLoginInter.vip_login_base` 读取当前会员标志、等级及官方图片；历史等级不能单独证明仍是会员。勋章、搭子、访问数、客户端装扮和听歌小时未接入，不使用截图中的数值填充。

QQ 双等级更正（2026-10-22）：官方卡片的S等级由同一固定提交的 [modules/sound_power.py](https://github.com/L-1124/QQMusicApi/blob/27861e51432ea6b6e88dc35c4e8c8e239c0339e8/qqmusic_api/modules/sound_power.py) 中 `music.soundPower.SoundPowerSvr.QueryLevelDetailPage` 提供，空param、当前登录凭据；[models/sound_power.py](https://github.com/L-1124/QQMusicApi/blob/27861e51432ea6b6e88dc35c4e8c8e239c0339e8/qqmusic_api/models/sound_power.py) 明确 `powerInfo.level` 的0对应S0。此前会员块中的 `userinfo.music_level` 不能代替这个账号等级，现停止消费该字段。两接口合并成原一次附加资料HTTP请求，各自校验code／缺失值，认证失败拒绝整体；不添加好友榜、勋章、点赞、隐私、领取等调用。

VIP继续以 `identity.level/icon` 表示历史等级与官方图片；当前svip／star／HugeVip／vip标志只决定有效状态。明确未生效且有正数历史等级时返回isActive=false供UI灰显，状态缺失不猜过期，零等级不制造历史VIP。QQ和网易云均使用官方原图及文字回退；QQ状态未知与非会员区别保留，网易云未提供有效性时不自动灰显。本轮按用户要求仅展示VIP与账号等级，移除有效期行和仅为该行增加的日期解析。当前账号原始响应未通过抓包取得，S等级实际数值与徽标灰显仍待设备验收。

原版徽章接入（2026-10-22）：继续使用上述已请求的音响力响应，将模型明确标记为当前等级图片的 `powerInfo.icon` 传到界面；不再用普通灰色S等级标签代替原图，不读取 `nextLevelIcon`。图片和有效的 `powerInfo.level` 同时绑定，并复用会员图片的腾讯图片域名、协议、端口和userinfo校验；缺图或拒绝地址不抹掉已知等级，等级本身无效则不显示对应图片。不新增网络调用、凭据读取或持久化字段。最新用户截图已能显示S8，但原版图片尺寸、当前账号返回的图片样式仍需本轮安装后的人工验收。

公开只读探测已确认微信授权页与二维码图片返回200；上游公开测试账号的音乐基因卡片返回0，字段及账号身份吻合。匿名主页请求返回10000，不能当成成功资料；探测仅保存状态／字段结构，不保存临时二维码标识或个人资料。新增10项离线测试源码，本次按仓库规则不单独运行Gradle单测。手机短信、两类真实扫码／换票、登录后会员资料仍待用户验收。

同机微信／QQ一键登录仍是阻塞项：项目没有可用于 QQ 音乐换票、绑定本应用包名／签名的移动授权配置。腾讯 [QQ Android 接入说明](https://docs.playernetwork.intlgame.com/docs/zh/Authentication/ThirdParty/QQ/Android/) 要求注册应用包名、签名和 AppID。网站扫码 AppID 不代表本应用已取得原生授权资格；通用 QQ／微信身份也不等于 QQ 音乐会话。没有添加假成功入口或冒用官方 App 的包名／签名，等待授权配置或另行验证可用的官方网页链路。

## QQ 手机验证码安全验证修复（2026-10-21）

用户实测图片验证后进入客户端下载／安装确认。根因是 `BgmActivity` 将 `securityURL` 交给外部浏览器；[QQ 音乐官方验证页](https://y.qq.com/m/client/safety_captcha/index.html) 在验证回调成功后，若没有 `redirectUrl` 就调用 `Music.client.open('ui','setSecureVerificationStatus', {status:0})`，普通浏览器触发客户端打开／下载兜底。官方公开HTML和安全组件已只读核对，未重放用户含手机号的完整URL，未发送短信或代用户完成验证。

现由NG独立验证弹窗载入官方页面，保留平台挑战参数，仅注入一次性成功回跳地址。完成必须来自当前验证页、当前随机标识和主框架导航，重复／取消／旧标识均拒绝；HTTPS `.invalid` 回跳在WebView内截获，不发出网络请求。没有注入JavaScript或安装原生JS接口，也不伪造验证码票据。官方滑块组件先提交 `iSubCmd=71`，仅当平台返回 `iRet=0` 才调用成功回调；失败会刷新图形或显示错误。回跳仅允许重新申请短信；只有发送接口确认成功才进入已发送状态。

校验地址与页面／资源导航分别限界，禁文件和内容URI、明文及混合内容、APK／应用商店／下载页、外部scheme、多窗口和下载处理；SSL错误取消，控制台不转录，销毁时停止加载并销毁专用WebView。不使用通用书源浏览器、不导入导出音乐或书源Cookie。验证后保留原MusicPhoneLoginSession和QQ临时设备／Cookie上下文，避免重新生成身份导致验证失效。网易云及QQ两种扫码不变。

新增7项离线回归测试源码，覆盖参数保留／替换、一次性且限定主框架的回跳、取消／旧挑战、服务端初始跳转、下载／外部唤起、资源域名、无效初始URL；本轮不另跑Gradle单测。真实图片验证→短信→登录仍由用户验收。

## 歌单管理（2026-10-21）

- 网易云参考同一项目的 [playlist_create.js](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/master/module/playlist_create.js)、[playlist_update.js](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/master/module/playlist_update.js)、[playlist_delete.js](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/master/module/playlist_delete.js)、[playlist_tracks.js](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/master/module/playlist_tracks.js) 和 [playlist_subscribe.js](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/master/module/playlist_subscribe.js)。仅编辑名称／简介，保留原标签；批量响应逐项检查。重复添加、验证要求和平台错误直接显示，不进行隐式重试。
- QQ 使用固定提交的 [modules/songlist.py](https://github.com/L-1124/QQMusicApi/blob/27861e51432ea6b6e88dc35c4e8c8e239c0339e8/qqmusic_api/modules/songlist.py)：新建／删除普通歌单，添加／移出歌曲。歌曲写入使用数字 `songId` 和 `songType`，不把播放用的 MID 当作数字 ID；同时检查外层 code、业务块 code 与 retCode。该参考版本没有歌单改名／取消收藏方法，当前 QQ 不显示这两个操作。
- 普通写入同时核对平台、当前账号、所有者和歌单类型；系统歌单与他人收藏歌单不能走普通改名／删除／歌曲移除路径。“我喜欢的音乐”当前保留浏览与试听，不开放普通歌单编辑动作。
- 新建、编辑、选歌目标和删除确认由 ViewModel 持有；提交中禁止重复操作。新建成功而后续添加失败时保留已创建的歌单，重试只添加歌曲，不重复创建。响应不明时提示刷新核对，不自动重发写请求。
- 这些接口已完成代码接入与离线用例编写；本轮未使用真实账号创建、改名、删歌或删除歌单。真实平台响应和界面交互仍需人工验收，代码存在不代表远端写入已验证。

## 离线回归边界

协议测试使用固定、虚构的输入与上游离线生成的加密向量，检查 UTF-8、AES/RSA、元数据解析、媒体域名和 Cookie 快照。测试不保存账号凭据，不请求平台，不代替 Android 上的扫码、权限、后台播放或视觉验收。

手机号登录新增13项离线测试源码，覆盖会话取消／最终提交、请求字段、验证码与响应类型、刷新字段和验证链接域名。本轮不额外运行 Gradle 单测；界面默认手机登录，只有用户点击按钮才发送验证码。输入仅保留在 ViewModel 内存，切换方式／关闭后撤销临时认证会话；验证码冷却按平台、区号、号码保留截止时间。

临时认证客户端关闭时先同步标记失效并清除 Cookie，取消调用、TLS 连接回收和线程池释放统一投递至 `Dispatchers.IO`，不依附已取消的页面协程。关闭幂等，失效后不接受新请求或迟到 Cookie；退出／切换登录时的旧会话撤销与凭据提交门继续由原 Provider 负责。该修正针对 2026-10-20 日志中扫码切换触发的 `NetworkOnMainThreadException`；新增 5 项清理调度回归测试源码，测试尚未单独执行。
