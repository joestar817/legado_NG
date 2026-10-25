package io.legado.app.help.bgm.qq

import io.legado.app.help.bgm.MusicCredentialStore
import io.legado.app.help.bgm.MusicAccountProfile
import io.legado.app.help.bgm.MusicAccountExtras
import io.legado.app.help.bgm.MusicHttpClient
import io.legado.app.help.bgm.MusicLoginChallenge
import io.legado.app.help.bgm.MusicLoginResult
import io.legado.app.help.bgm.MusicLoginState
import io.legado.app.help.bgm.MusicPage
import io.legado.app.help.bgm.MusicPlatform
import io.legado.app.help.bgm.MusicPlaybackSource
import io.legado.app.help.bgm.MusicPlaylist
import io.legado.app.help.bgm.MusicPlaylistKind
import io.legado.app.help.bgm.MusicPlaylistProtocol
import io.legado.app.help.bgm.MusicProvider
import io.legado.app.help.bgm.MusicProviderException
import io.legado.app.help.bgm.MusicTrack
import io.legado.app.help.bgm.MusicTrackVipParser
import io.legado.app.help.bgm.MusicPhoneLoginSession
import io.legado.app.help.bgm.MusicPhoneCodeResult
import io.legado.app.help.bgm.MusicPhoneCodeState
import io.legado.app.help.bgm.MusicPhoneProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Native QQ provider. Protocol subset: QQMusicApi 0.8.2, GPL-3.0.
 * Normal playback requires a full song. Temporary listening can use a declared RS02 preview.
 */
internal class QqMusicProvider : MusicProvider {
    override val platform = MusicPlatform.QQ
    override val supportsAccountProfile = true
    private val http = MusicHttpClient(platform, setOf("qq.com", "tencentmusic.com"))
    private val mutex = Mutex()
    private var saved: JSONObject? = null
    private var deviceSession: QqDeviceSession? = null
    private var created: List<MusicPlaylist>? = null
    private val loginLock = Any()
    @Volatile private var activeLoginId: String? = null
    @Volatile private var activeQrSig = ""
    private class PhoneContext(val session: MusicPhoneLoginSession, val http: MusicHttpClient, val saved: JSONObject) {
        val device = QqDeviceSession(http, saved) {}
    }
    @Volatile private var phoneContext: PhoneContext? = null
    private class WechatContext(val id: String) {
        val http = MusicHttpClient(MusicPlatform.QQ, setOf("open.weixin.qq.com", "lp.open.weixin.qq.com"))
        var uuid = ""
    }
    @Volatile private var wechatContext: WechatContext? = null

    override suspend fun isLoggedIn(): Boolean = operation {
        if (!hasCredential()) return@operation false
        try {
            cgi("music.UserInfo.userInfoServer", "GetLoginUserInfo")
            true
        } catch (error: MusicProviderException) {
            if (error.platformCode !in AUTH_ERRORS) throw error
            try {
                refreshCredential()
                true
            } catch (refresh: MusicProviderException) {
                if (refresh.platformCode !in AUTH_ERRORS) throw refresh
                root().remove("credential")
                http.clearCookies()
                save()
                false
            }
        }
    }

    override suspend fun accountProfile(): MusicAccountProfile = operation {
        requireLogin()
        val userId = credential().optString("musicid")
        val euin = encryptedUin()
        val common = session().comm(credential()).put("tmeLoginType", MusicPhoneProtocol.qqLoginType(credential()).toString())
        fun request(module: String, method: String, param: JSONObject) =
            qJson("module" to module, "method" to method, "param" to param)
        // One bounded request; optional blocks can fail without erasing a valid avatar/name.
        val payload = qJson("comm" to common,
            "account" to request("music.UserInfo.userInfoServer", "GetLoginUserInfo", JSONObject()),
            "home" to request("music.UnifiedHomepage.UnifiedHomepageSrv", "GetHomepageHeader",
                qJson("uin" to euin, "IsQueryTabDetail" to 1)),
            "gene" to request("music.recommend.UserProfileSettingSvr", "GetProfileReport",
                qJson("VisitAccount" to euin)),
            "following" to request("music.concern.RelationList", "GetFollowUserList",
                qJson("HostUin" to euin, "From" to 0, "Size" to 1)),
            "fans" to request("music.concern.RelationList", "GetFansList",
                qJson("HostUin" to euin, "From" to 0, "Size" to 1)))
        val response = http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", payload,
            mapOf("User-Agent" to session().userAgent)).json()
        save()
        QqAccountProfileParser.profile(userId, euin, response)
    }

    override suspend fun accountExtras(userId: String): MusicAccountExtras = operation {
        requireLogin()
        if (credential().optString("musicid") != userId) throw MusicProviderException("账号已切换，请重试")
        val common = session().comm(credential())
            .put("tmeLoginType", MusicPhoneProtocol.qqLoginType(credential()).toString())
        fun request(module: String, method: String) =
            qJson("module" to module, "method" to method, "param" to JSONObject())
        // The S-level belongs to SoundPower, not the legacy music_level in the VIP response.
        val payload = qJson("comm" to common,
            "membership" to request("VipLogin.VipLoginInter", "vip_login_base"),
            "soundPower" to request("music.soundPower.SoundPowerSvr", "QueryLevelDetailPage"))
        val response = http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", payload,
            mapOf("User-Agent" to session().userAgent)).json()
        save()
        QqAccountProfileParser.extras(response)
    }

    override suspend fun beginLogin(): MusicLoginChallenge = operation {
        phoneContext?.session?.let(::cancelPhoneLogin)
        clearQrLogin()
        if (!hasCredential()) http.clearCookies()
        val response = http.get(url("https://ssl.ptlogin2.qq.com/ptqrshow", mapOf(
            "appid" to "716027609", "e" to "2", "l" to "M", "s" to "3", "d" to "72", "v" to "4",
            "t" to Math.random().toString(), "daid" to "383", "pt_3rd_aid" to "100497308")),
            mapOf("Referer" to "https://xui.ptlogin2.qq.com/", "User-Agent" to LOGIN_UA))
        val sig = http.cookie("qrsig", "ssl.ptlogin2.qq.com")
        currentCoroutineContext().ensureActive()
        if (sig.isEmpty() || response.body.isEmpty()) throw MusicProviderException("获取 QQ 登录二维码失败")
        val id = UUID.randomUUID().toString()
        activeQrSig = sig
        activeLoginId = id
        MusicLoginChallenge(id = id, image = response.body, expiresAt = System.currentTimeMillis() + 180_000)
    }

    override suspend fun beginWechatLogin(): MusicLoginChallenge = operation {
        phoneContext?.session?.let(::cancelPhoneLogin)
        clearQrLogin()
        if (!hasCredential()) http.clearCookies()
        val context = WechatContext(UUID.randomUUID().toString())
        synchronized(loginLock) {
            wechatContext = context
            activeLoginId = context.id
        }
        try {
            val page = context.http.get(QqWechatLoginProtocol.authorizationUrl(context.id)).text()
            context.uuid = QqWechatLoginProtocol.identifier(page)
            val image = context.http.get("https://open.weixin.qq.com/connect/qrcode/${context.uuid}",
                mapOf("Referer" to "https://open.weixin.qq.com/connect/qrconnect")).body
            currentCoroutineContext().ensureActive()
            if (image.isEmpty() || activeLoginId != context.id) throw MusicProviderException("微信登录已取消")
            MusicLoginChallenge(context.id, image = image, expiresAt = System.currentTimeMillis() + 180_000)
        } catch (error: Exception) {
            cancelLogin(MusicLoginChallenge(context.id))
            throw error
        }
    }

    override suspend fun pollLogin(challenge: MusicLoginChallenge): MusicLoginResult = operation {
        if (challenge.id != activeLoginId || System.currentTimeMillis() >= challenge.expiresAt) {
            cancelLogin(challenge)
            return@operation MusicLoginResult(MusicLoginState.EXPIRED, "二维码已过期")
        }
        wechatContext?.takeIf { it.id == challenge.id }?.let {
            return@operation pollWechatLogin(challenge, it)
        }
        val response = http.get(url("https://ssl.ptlogin2.qq.com/ptqrlogin", mapOf(
            "u1" to "https://graph.qq.com/oauth2.0/login_jump", "ptqrtoken" to QqRequestCodec.hash33(activeQrSig).toString(),
            "ptredirect" to "0", "h" to "1", "t" to "1", "g" to "1", "from_ui" to "1", "ptlang" to "2052",
            "action" to "0-0-${System.currentTimeMillis()}", "js_ver" to "20102616", "js_type" to "1",
            "pt_uistyle" to "40", "aid" to "716027609", "daid" to "383", "pt_3rd_aid" to "100497308", "has_onekey" to "1")),
            mapOf("Referer" to "https://xui.ptlogin2.qq.com/", "User-Agent" to LOGIN_UA,
                "Cookie" to "qrsig=$activeQrSig"))
        val args = parseQrCallback(response.text())
        when (args.firstOrNull()?.toIntOrNull()) {
            66 -> MusicLoginResult(MusicLoginState.WAITING_SCAN)
            67 -> MusicLoginResult(MusicLoginState.WAITING_CONFIRMATION)
            65 -> {
                cancelLogin(challenge)
                MusicLoginResult(MusicLoginState.EXPIRED, "二维码已过期")
            }
            68 -> {
                cancelLogin(challenge)
                MusicLoginResult(MusicLoginState.FAILED, "已取消 QQ 登录")
            }
            0 -> {
                val redirect = args.getOrNull(2)?.toHttpUrlOrNull()
                    ?: throw MusicProviderException("QQ 登录响应异常")
                val uin = redirect.queryParameter("uin").orEmpty()
                val sigx = redirect.queryParameter("ptsigx").orEmpty()
                if (uin.isEmpty() || sigx.isEmpty()) throw MusicProviderException("QQ 登录响应异常")
                authorize(uin, sigx, challenge.id)
                cancelLogin(challenge)
                MusicLoginResult(MusicLoginState.AUTHENTICATED)
            }
            else -> MusicLoginResult(MusicLoginState.FAILED, "QQ 登录状态异常，请刷新二维码")
        }
    }

    override fun cancelLogin(challenge: MusicLoginChallenge) {
        synchronized(loginLock) {
            if (activeLoginId == challenge.id) {
                clearQrLogin()
            }
        }
    }

    private fun clearQrLogin() = synchronized(loginLock) {
        activeLoginId = null
        activeQrSig = ""
        wechatContext?.http?.close()
        wechatContext = null
    }

    private suspend fun pollWechatLogin(challenge: MusicLoginChallenge, context: WechatContext): MusicLoginResult {
        // WeChat uses long polling. A quiet response window is still "waiting", not a failed login.
        val response = withTimeoutOrNull(20_000) {
            context.http.get(url("https://lp.open.weixin.qq.com/connect/l/qrconnect", mapOf(
                "uuid" to context.uuid, "_" to System.currentTimeMillis().toString())),
                mapOf("Referer" to "https://open.weixin.qq.com/"))
        } ?: return MusicLoginResult(MusicLoginState.WAITING_SCAN)
        val (state, code) = QqWechatLoginProtocol.result(response.text())
        if (state == MusicLoginState.AUTHENTICATED) {
            val login = cgi("music.login.LoginServer", "Login",
                qJson("code" to code, "strAppid" to QqWechatLoginProtocol.APP_ID),
                qJson("tmeLoginType" to "1"), useCredential = false)
            currentCoroutineContext().ensureActive()
            synchronized(loginLock) {
                if (activeLoginId != challenge.id || wechatContext !== context) {
                    throw CancellationException("QQ Music login session changed")
                }
                if (!login.has("loginType")) login.put("loginType", 1)
                publishCredential(login)
                created = null
                save()
            }
        }
        if (state in setOf(MusicLoginState.AUTHENTICATED, MusicLoginState.EXPIRED, MusicLoginState.FAILED)) {
            cancelLogin(challenge)
        }
        return MusicLoginResult(state, if (state == MusicLoginState.FAILED) "已取消微信登录" else "")
    }

    override suspend fun sendPhoneCode(session: MusicPhoneLoginSession): MusicPhoneCodeResult = operation {
        session.bind(platform)
        val context = synchronized(loginLock) {
            clearQrLogin()
            phoneContext?.takeIf { it.session === session } ?: run {
                phoneContext?.let { it.session.cancel(); it.http.close() }
                PhoneContext(session, MusicHttpClient(platform, setOf("qq.com", "tencentmusic.com")),
                    JSONObject(root().toString()).apply { remove("credential"); remove("cookies") })
                    .also { phoneContext = it }
            }
        }
        session.markCodeSent(false)
        val response = phoneCgi(context, "SendPhoneAuthCode", MusicPhoneProtocol.qqSend(session),
            qJson("tmeLoginMethod" to 3))
        session.requireActive()
        val result = MusicPhoneProtocol.qqSendResult(response)
        session.markCodeSent(result.state == MusicPhoneCodeState.SENT)
        result
    }

    override suspend fun loginPhoneCode(session: MusicPhoneLoginSession, code: String): MusicLoginResult = operation {
        session.bind(platform)
        session.requireActive(requireCode = true)
        val context = phoneContext?.takeIf { it.session === session }
            ?: throw MusicProviderException("登录已失效，请重新获取验证码")
        val response = phoneCgi(context, "Login", MusicPhoneProtocol.qqLogin(session, code),
            qJson("tmeLoginMethod" to 3, "tmeLoginType" to 0))
        session.requireActive()
        val loginCode = response.optInt("code", -1)
        if (loginCode != 0) throw MusicPhoneProtocol.qqLoginError(loginCode)
        val login = response.optJSONObject("data") ?: throw MusicProviderException("QQ 音乐登录响应异常")
        val nestedCode = login.optInt("code")
        if (nestedCode != 0) throw MusicPhoneProtocol.qqLoginError(nestedCode)
        validateCredential(login)
        val check = phoneCgi(context, "GetLoginUserInfo", JSONObject(), JSONObject(),
            module = "music.UserInfo.userInfoServer", credential = login)
        session.requireActive()
        if (check.optInt("code", -1) != 0) throw MusicPhoneProtocol.qqLoginError(check.optInt("code", -1))
        val candidate = JSONObject(context.saved.toString()).put("credential", login)
            .put("cookies", context.http.snapshotCookies())
        currentCoroutineContext().ensureActive()
        val published = synchronized(loginLock) {
            phoneContext === context && session.commitIfActive {
                MusicCredentialStore.write(platform, candidate)
                saved = candidate
                deviceSession = null
                http.restoreCookies(candidate.optJSONArray("cookies"))
                created = null
            }
        }
        if (!published) throw CancellationException("QQ 音乐登录已取消")
        cancelPhoneLogin(session)
        MusicLoginResult(MusicLoginState.AUTHENTICATED, "登录成功")
    }

    override fun cancelPhoneLogin(session: MusicPhoneLoginSession) {
        session.cancel()
        synchronized(loginLock) {
            phoneContext?.takeIf { it.session === session }?.let {
                phoneContext = null
                it.http.close()
            }
        }
    }

    /** Preserve the response code/data pair so security challenges never look like success. */
    private suspend fun phoneCgi(
        context: PhoneContext, method: String, parameters: JSONObject, overrides: JSONObject,
        module: String = "music.login.LoginServer", credential: JSONObject = JSONObject()
    ): JSONObject {
        context.session.requireActive()
        val common = context.device.comm(credential)
        if (credential.has("loginType")) common.put("tmeLoginType", credential.optInt("loginType").toString())
        overrides.keys().forEach { common.put(it, overrides.get(it).toString()) }
        val payload = qJson("comm" to common,
            "req_0" to qJson("module" to module, "method" to method, "param" to parameters))
        context.session.requireActive()
        val response = context.http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", payload,
            mapOf("User-Agent" to context.device.userAgent)).json()
        context.session.requireActive()
        val outerCode = response.optInt("code", -1)
        if (outerCode != 0) throw MusicPhoneProtocol.qqLoginError(outerCode)
        return response.optJSONObject("req_0") ?: throw MusicProviderException("QQ 音乐登录响应异常")
    }

    override suspend fun logout() = operation {
        phoneContext?.session?.let(::cancelPhoneLogin)
        clearQrLogin()
        created = null
        http.clearCookies()
        saved = JSONObject()
        deviceSession = null
        MusicCredentialStore.clear(platform)
    }

    override suspend fun search(query: String, offset: Int): MusicPage<MusicTrack> = operation {
        require(offset >= 0)
        if (query.isBlank()) return@operation MusicPage(emptyList())
        val response = cgi("music.search.SearchCgiService", "DoSearchForQQMusicMobile", qJson(
            "searchid" to searchId(), "query" to query, "search_type" to 0, "num_per_page" to PAGE_SIZE,
            "page_num" to (offset / PAGE_SIZE + 1), "highlight" to 0, "grp" to 1,
            "selectors" to JSONObject(), "vec_selectors" to JSONArray()))
        val items = tracks(response.optJSONObject("body")?.optJSONArray("item_song"))
        val meta = response.optJSONObject("meta")
        val total = meta?.optInt("sum")
        val next = if (items.isNotEmpty() && meta?.optInt("nextpage", -1) != -1) offset + PAGE_SIZE else null
        MusicPage(items, next, total)
    }

    override suspend fun playlists(offset: Int): MusicPage<MusicPlaylist> = operation {
        require(offset >= 0)
        requireLogin()
        if (offset == 0 || created == null) {
            val data = cgi("music.musicasset.PlaylistBaseRead", "GetPlaylistByUin",
                qJson("uin" to credential().optString("musicid")))
            val entries = data.optJSONArray("v_playlist") ?: JSONArray()
            // This upstream endpoint takes only uin and returns the created list as a snapshot.
            // Do not silently present an unfinished snapshot as the user's whole library.
            if (!data.optBoolean("bFinish", true) && data.optInt("total") > entries.length()) {
                throw MusicProviderException("QQ 音乐自建歌单尚未返回完整，请稍后重试")
            }
            created = (0 until entries.length()).mapNotNull { entries.optJSONObject(it)?.let { entry -> playlist(entry, false) } }
        }
        val own = created.orEmpty()
        val items = own.drop(offset).take(PAGE_SIZE).toMutableList()
        if (items.size == PAGE_SIZE) return@operation MusicPage(items, offset + items.size)
        val favOffset = (offset - own.size).coerceAtLeast(0)
        val count = PAGE_SIZE - items.size
        val data = cgi("music.musicasset.PlaylistFavRead", "CgiGetPlaylistFavInfo",
            qJson("uin" to encryptedUin(), "offset" to favOffset, "size" to count))
        if (data.optBoolean("hide")) throw MusicProviderException("QQ 音乐收藏歌单暂不可读取")
        val entries = data.optJSONArray("v_list") ?: JSONArray()
        (0 until entries.length()).forEach { index ->
            entries.optJSONObject(index)?.let { playlist(it, true) }?.let(items::add)
        }
        val next = if (entries.length() > 0 && data.optInt("hasmore") != 0) own.size + favOffset + entries.length() else null
        MusicPage(items, next, own.size + data.optInt("total"))
    }

    override suspend fun playlistTracks(playlist: MusicPlaylist, offset: Int): MusicPage<MusicTrack> = operation {
        require(playlist.platform == platform && offset >= 0)
        val metadata = playlist.extra.takeIf(String::isNotEmpty)?.let(::JSONObject) ?: JSONObject()
        val dirid = metadata.optInt("dirid")
        val parameters = qJson("disstid" to (metadata.optString("tid").toLongOrNull() ?: playlist.id.toLongOrNull() ?: 0L),
            "dirid" to dirid, "tag" to 1, "song_begin" to offset, "song_num" to PAGE_SIZE,
            "userinfo" to 1, "orderlist" to 1, "onlysonglist" to 0)
        if (!playlist.collected) parameters.put("enc_host_uin", encryptedUin())
        val response = cgi("music.srfDissInfo.DissInfo", "CgiGetDiss", parameters)
        val detailCode = response.optInt("code")
        if (detailCode != 0) throw MusicProviderException("QQ 音乐歌单读取失败（$detailCode）", detailCode)
        val songs = response.optJSONArray("songlist")
        val rawCount = songs?.length() ?: 0
        val items = tracks(songs)
        val total = response.optInt("total_song_num")
        val next = if (rawCount > 0 && (response.optInt("hasmore") != 0 || offset + rawCount < total))
            offset + rawCount else null
        MusicPage(items, next, total)
    }

    override suspend fun createPlaylist(title: String): MusicPlaylist = operation {
        requireLogin()
        val name = MusicPlaylistProtocol.title(title)
        val response = cgi("music.musicasset.PlaylistBaseWrite", "AddPlaylist", qJson("dirName" to name))
        MusicPlaylistProtocol.requireQqWrite(response)
        val result = response.optJSONObject("result")
            ?: throw MusicProviderException("歌单可能已创建，请刷新歌单确认")
        val dirId = result.optInt("dirId")
        val tid = result.optString("tid")
        if (dirId <= 0 || dirId == 201) throw MusicProviderException("歌单可能已创建，请刷新歌单确认")
        created = null
        MusicPlaylist(id = tid.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true } ?: "dir:$dirId",
            platform = platform, title = result.optString("dirName").ifBlank { name },
            ownerId = credential().optString("musicid"), kind = MusicPlaylistKind.OWNED,
            extra = qJson("tid" to tid, "dirid" to dirId).toString())
    }

    override suspend fun deletePlaylist(playlist: MusicPlaylist): Unit = operation {
        requireLogin()
        MusicPlaylistProtocol.requireOwned(playlist, platform, credential().optString("musicid"))
        val dirId = JSONObject(playlist.extra).optInt("dirid")
        if (dirId <= 0 || dirId == 201) throw MusicProviderException("这个歌单不能删除")
        val response = cgi("music.musicasset.PlaylistBaseWrite", "DelPlaylist", qJson("dirId" to dirId))
        MusicPlaylistProtocol.requireQqWrite(response)
        created = null
    }

    override suspend fun addTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>) =
        changeTracks(playlist, tracks, true)

    override suspend fun removeTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>) =
        changeTracks(playlist, tracks, false)

    private suspend fun changeTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>, add: Boolean): Unit = operation {
        requireLogin()
        MusicPlaylistProtocol.requireOwned(playlist, platform, credential().optString("musicid"))
        val response = cgi("music.musicasset.PlaylistDetailWrite",
            if (add) "AddSonglist" else "DelSonglist", MusicPlaylistProtocol.qqTracks(playlist, tracks))
        MusicPlaylistProtocol.requireQqWrite(response)
        created = null
    }

    override suspend fun resolve(track: MusicTrack): MusicPlaybackSource = operation {
        require(track.platform == platform)
        val file = requestFile(track, fileName(track.id, track.mediaId))
            ?: throw MusicProviderException("QQ 音乐暂不可播放")
        playbackSource(track, file)
    }

    override suspend fun resolvePreview(track: MusicTrack): MusicPlaybackSource = operation {
        require(track.platform == platform)
        val info = try {
            QqPlaybackProtocol.trackInfo(cgi("music.trackInfo.UniformRuleCtrl", "CgiGetTrackInfo", qJson(
                "ctx" to 0, "client" to 1, "types" to JSONArray().put(track.songType),
                "modify_stamp" to JSONArray().put(0), "mids" to JSONArray().put(track.id))), track.id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Missing metadata may still allow the original full song, but never declares a preview.
            null
        }
        val detailedTrack = QqPlaybackProtocol.playbackTrack(track, info)
        var fullSongError: MusicProviderException? = null
        val fullSongFile = try {
            requestFile(detailedTrack, fileName(detailedTrack.id, detailedTrack.mediaId))
        } catch (error: MusicProviderException) {
            if (error.platformCode != 104003) throw error
            fullSongError = error
            null
        }
        if (fullSongFile != null) {
            return@operation playbackSource(detailedTrack, fullSongFile, allowDispatch = true)
        }
        val previewFilename = QqPlaybackProtocol.previewFileName(info)
            ?: throw (fullSongError ?: MusicProviderException("QQ 音乐暂不可播放"))
        val previewFile = requestFile(detailedTrack, previewFilename)
            ?: throw MusicProviderException("QQ 音乐暂未提供可用试听")
        playbackSource(detailedTrack, previewFile, preview = true, allowDispatch = true)
    }

    /** Called within operation's account mutex; must not re-enter another public resolver. */
    private suspend fun requestFile(
        track: MusicTrack,
        filename: String,
    ): Pair<JSONObject, String>? {
        val result = cgi("music.vkey.GetVkey", "UrlGetVkey", qJson(
            "uin" to credential().optString("str_musicid").ifEmpty { credential().optString("musicid", "0") },
            "filename" to JSONArray().put(filename), "guid" to UUID.randomUUID().toString().replace("-", ""),
            "songmid" to JSONArray().put(track.id), "songtype" to JSONArray().put(track.songType), "ctx" to 0))
        val path = QqPlaybackProtocol.mediaPath(result, track.id, filename) ?: return null
        return result to path
    }

    private suspend fun playbackSource(
        track: MusicTrack,
        file: Pair<JSONObject, String>,
        preview: Boolean = false,
        allowDispatch: Boolean = false,
    ): MusicPlaybackSource {
        val (result, path) = file
        var address = QqPlaybackProtocol.mediaAddress(path, result.optJSONArray("sip"))
        if (address == null && allowDispatch) {
            val dispatch = cgi("music.audioCdnDispatch.cdnDispatch", "GetCdnDispatch", qJson(
                "guid" to UUID.randomUUID().toString().replace("-", ""), "uid" to "0",
                "use_new_domain" to 1, "use_ipv6" to 1))
            val code = dispatch.optInt("retcode", -1)
            if (code != 0) throw MusicProviderException("QQ 音乐音频地址异常（$code）", code)
            address = QqPlaybackProtocol.mediaAddress(path, dispatch.optJSONArray("sip"))
        }
        if (address == null) throw MusicProviderException("QQ 音乐音频地址异常")
        return MusicPlaybackSource(address, mapOf("Referer" to "https://y.qq.com/"),
            expiresAt = System.currentTimeMillis() + 15 * 60_000, preview = preview,
            fullDurationMs = track.durationMs, standalonePreview = preview)
    }

    private suspend fun authorize(uin: String, sigx: String, loginId: String) {
        http.get(url("https://ssl.ptlogin2.graph.qq.com/check_sig", mapOf(
            "uin" to uin, "pttype" to "1", "service" to "ptqrlogin", "nodirect" to "0", "ptsigx" to sigx,
            "s_url" to "https://graph.qq.com/oauth2.0/login_jump", "ptlang" to "2052", "ptredirect" to "100",
            "aid" to "716027609", "daid" to "383", "j_later" to "0", "low_login_hour" to "0", "regmaster" to "0",
            "pt_login_type" to "3", "pt_aid" to "0", "pt_aaid" to "16", "pt_light" to "0", "pt_3rd_aid" to "100497308")),
            mapOf("Referer" to "https://xui.ptlogin2.qq.com/", "User-Agent" to LOGIN_UA), false)
        // Read the accumulated jar, not just the last response. Empty cookie snapshots must not erase check_sig cookies.
        val skey = http.cookie("p_skey", "graph.qq.com")
        if (skey.isEmpty()) throw MusicProviderException("QQ 登录授权未完成，请刷新二维码")
        val response = http.postForm("https://graph.qq.com/oauth2.0/authorize", mapOf(
            "response_type" to "code", "client_id" to "100497308",
            "redirect_uri" to "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/",
            "scope" to "get_user_info,get_app_friends", "state" to "state", "switch" to "", "from_ptlogin" to "1",
            "src" to "1", "update_auth" to "1", "openapi" to "1010_1030",
            "g_tk" to QqRequestCodec.hash33(skey, 5381).toString(), "auth_time" to System.currentTimeMillis().toString(),
            "ui" to UUID.randomUUID().toString()), mapOf("User-Agent" to LOGIN_UA), false)
        val location = response.headers["Location"]?.toHttpUrlOrNull()
        val code = location?.queryParameter("code").orEmpty()
        if (code.isEmpty()) throw MusicProviderException("QQ 登录授权未完成，请刷新二维码")
        val login = cgi("QQConnectLogin.LoginServer", "QQLogin", qJson("code" to code),
            qJson("tmeLoginType" to "2"), useCredential = false)
        currentCoroutineContext().ensureActive()
        synchronized(loginLock) {
            if (activeLoginId != loginId) throw MusicProviderException("QQ 登录已取消")
            publishCredential(login)
            created = null
            save()
        }
    }

    private suspend fun refreshCredential() {
        val old = credential()
        val parameters = MusicPhoneProtocol.qqRefreshParameters(old)
        publishCredential(cgi("music.login.LoginServer", "Login", parameters,
            qJson("tmeLoginType" to MusicPhoneProtocol.qqLoginType(old).toString())))
        save()
    }

    private fun publishCredential(value: JSONObject) {
        validateCredential(value)
        root().put("credential", value)
    }

    private fun validateCredential(value: JSONObject) {
        val key = value.opt("musickey") as? String
        if (value.optLong("musicid") <= 0 || key.isNullOrBlank() || key == "null") {
            throw MusicProviderException("QQ 登录凭据异常，请重新登录")
        }
        if (!value.has("loginType")) value.put("loginType", MusicPhoneProtocol.qqLoginType(value))
    }

    private suspend fun cgi(module: String, method: String, parameters: JSONObject = JSONObject(),
        overrideComm: JSONObject = JSONObject(), useCredential: Boolean = true): JSONObject {
        val common = session().comm(if (useCredential) credential() else JSONObject())
        if (useCredential && credential().has("loginType")) {
            common.put("tmeLoginType", credential().optInt("loginType").toString())
        }
        overrideComm.keys().forEach { common.put(it, overrideComm.get(it).toString()) }
        val payload = qJson("comm" to common, "req_0" to qJson("module" to module, "method" to method, "param" to parameters))
        val response = http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", payload,
            mapOf("User-Agent" to session().userAgent))
        save()
        return qqCgiData(response.json())
    }

    private fun root(): JSONObject = saved ?: (MusicCredentialStore.read(platform) ?: JSONObject()).also {
        saved = it
        http.restoreCookies(it.optJSONArray("cookies"))
    }

    private fun session(): QqDeviceSession = deviceSession ?: QqDeviceSession(http, root(), ::save).also { deviceSession = it }
    private fun credential(): JSONObject = root().optJSONObject("credential") ?: JSONObject()
    private fun hasCredential(): Boolean = credential().optLong("musicid") > 0 && credential().optString("musickey").isNotEmpty()
    private fun requireLogin() { if (!hasCredential()) throw MusicProviderException("请先登录 QQ 音乐") }
    private fun encryptedUin(): String = credential().optString("encryptUin").ifEmpty {
        throw MusicProviderException("QQ 登录信息不完整，请重新登录")
    }

    private fun save() {
        val value = saved ?: return
        val cookies = if (hasCredential()) http.snapshotCookies() else JSONArray()
        // A QR token is only useful in its visible login flow and is never persisted.
        val persistent = JSONArray()
        for (index in 0 until cookies.length()) {
            val cookie = cookies.optJSONObject(index) ?: continue
            if (!cookie.optString("cookie").startsWith("qrsig=")) persistent.put(cookie)
        }
        value.put("cookies", persistent)
        MusicCredentialStore.write(platform, value)
    }

    private suspend fun <T> operation(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                root()
                block()
            } catch (error: CancellationException) { throw error
            } catch (error: MusicProviderException) { throw error
            } catch (_: Exception) { throw MusicProviderException("QQ 音乐处理失败，请稍后重试") }
        }
    }

    private fun playlist(value: JSONObject, collected: Boolean): MusicPlaylist? {
        val tid = value.firstString("id", "tid", "dissid")
        val dirid = value.firstString("dirid", "dirId").toIntOrNull() ?: 0
        if (tid.isEmpty() && dirid == 0) return null
        return MusicPlaylist(id = tid.ifEmpty { "dir:$dirid" }, platform = platform,
            title = value.firstString("title", "dissname", "name", "dirName").ifEmpty { if (dirid == 201) "我喜欢的音乐" else "未命名歌单" },
            coverUrl = value.firstString("picurl", "cover", "logo", "picUrl"),
            trackCount = value.firstString("songnum", "songNum", "song_cnt").toIntOrNull() ?: 0,
            collected = collected, extra = qJson("tid" to tid, "dirid" to dirid).toString(),
            ownerId = if (collected) "" else credential().optString("musicid"),
            kind = when {
                collected -> MusicPlaylistKind.COLLECTED
                dirid == 201 -> MusicPlaylistKind.SYSTEM
                dirid > 0 -> MusicPlaylistKind.OWNED
                else -> MusicPlaylistKind.UNKNOWN
            })
    }

    private fun tracks(values: JSONArray?): List<MusicTrack> = if (values == null) emptyList() else
        (0 until values.length()).mapNotNull { index -> values.optJSONObject(index)?.let { track ->
            val mid = track.optString("mid")
            if (mid.isEmpty()) return@let null
            val singers = track.optJSONArray("singer") ?: JSONArray()
            val album = track.optJSONObject("album")
            val albumMid = album?.optString("mid").orEmpty()
            MusicTrack(mid, platform, track.optString("title").ifEmpty { track.optString("name") },
                artist = (0 until singers.length()).mapNotNull { singers.optJSONObject(it)?.optString("name") }.joinToString(" / "),
                album = album?.optString("name").orEmpty(),
                coverUrl = if (albumMid.isEmpty()) "" else "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg",
                durationMs = track.optLong("interval") * 1000,
                mediaId = track.optJSONObject("file")?.optString("media_mid").orEmpty(),
                songType = track.optInt("type"), numericId = track.optLong("id"),
                isVip = MusicTrackVipParser.qq(track))
        } }

    private fun JSONObject.firstString(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        optString(key).takeIf { it.isNotEmpty() && it != "null" }
    }.orEmpty()

    private fun searchId(): String = ((1 + QqRequestCodec.randomInt(20)) * 18014398509481984L +
        QqRequestCodec.randomInt(4194305) * 4294967296L + System.currentTimeMillis() % 86_400_000).toString()

    private fun url(base: String, query: Map<String, String>): String = base.toHttpUrl().newBuilder().apply {
        query.forEach { (key, value) -> addQueryParameter(key, value) }
    }.build().toString()

    internal companion object {
        private const val PAGE_SIZE = 30
        private val AUTH_ERRORS = setOf(1000, 104400, 104401)
        private const val LOGIN_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun parseQrCallback(text: String): List<String> {
            val content = Regex("ptuiCB\\((.*?)\\)").find(text)?.groupValues?.get(1)
                ?: throw MusicProviderException("QQ 登录响应异常")
            return Regex("'((?:\\\\.|[^'])*)'").findAll(content).map { it.groupValues[1] }.toList()
        }

        fun fileName(mid: String, mediaMid: String): String = "M500${mediaMid.ifEmpty { mid + mid }}.mp3"

        fun validMediaUrl(value: String): Boolean {
            val url = value.toHttpUrlOrNull() ?: return false
            return ((url.isHttps && url.port == 443) || (url.scheme == "http" && url.port == 80)) &&
                url.username.isEmpty() && url.password.isEmpty() &&
                (url.host == "qq.com" || url.host.endsWith(".qq.com"))
        }
    }
}
