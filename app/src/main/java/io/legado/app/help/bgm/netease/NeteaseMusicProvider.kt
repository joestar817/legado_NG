package io.legado.app.help.bgm.netease

import io.legado.app.help.bgm.MusicCredentialStore
import io.legado.app.help.bgm.MusicAccountProfile
import io.legado.app.help.bgm.MusicMembershipInfo
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

internal class NeteaseMusicProvider : MusicProvider {
    override val supportsPlaylistEditing = true
    override val supportsPlaylistUnsubscribe = true
    override val supportsAccountProfile = true
    override val platform = MusicPlatform.NETEASE
    private val http = MusicHttpClient(platform, setOf("163.com", "126.net", "127.net", "163yun.com"))
    private val mutex = Mutex()
    private var loaded = false
    private var state = JSONObject()
    @Volatile private var activeChallenge = ""
    private var lastPollAt = 0L
    private var lastPoll = MusicLoginResult(MusicLoginState.WAITING_SCAN)
    private val details = linkedMapOf<String, PlaylistDetail>()
    private val loginLock = Any()
    private class PhoneContext(val session: MusicPhoneLoginSession, val http: MusicHttpClient, val state: JSONObject)
    @Volatile private var phoneContext: PhoneContext? = null
    private class QrContext(val key: String, val http: MusicHttpClient, val state: JSONObject)
    @Volatile private var qrContext: QrContext? = null

    override suspend fun isLoggedIn(): Boolean = locked {
        if (http.cookie("MUSIC_U").isEmpty()) return@locked false
        accountId(refresh = true).isNotEmpty()
    }

    override suspend fun accountProfile(): MusicAccountProfile = locked {
        if (http.cookie("MUSIC_U").isEmpty()) {
            throw MusicProviderException("网易云登录已失效，请重新登录")
        }
        val account = NeteaseAccountProfileParser.account(
            api("/api/w/nuser/account/get", JSONObject(), weapi = true))
        try {
            NeteaseAccountProfileParser.detail(
                api("/api/v1/user/detail/${account.userId}", JSONObject(), weapi = true), account)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Optional statistics failing must not discard the verified name/avatar or log the user out.
            account
        }
    }

    override suspend fun accountMembership(userId: String): MusicMembershipInfo? = locked {
        if (http.cookie("MUSIC_U").isEmpty() || userId.isBlank() || accountId() != userId) {
            throw MusicProviderException("账号已变化，请重新打开账号资料")
        }
        NeteaseAccountProfileParser.membership(
            api("/api/music-vip-membership/front/vip/info", JSONObject().put("userId", userId), weapi = true),
            userId,
        )
    }

    override suspend fun beginLogin(): MusicLoginChallenge = locked {
        phoneContext?.session?.let(::cancelPhoneLogin)
        synchronized(loginLock) {
            activeChallenge = ""
            qrContext?.http?.close()
            qrContext = null
        }
        val authHttp = MusicHttpClient(platform, setOf("163.com", "126.net", "127.net", "163yun.com"))
        val candidate = JSONObject(state.toString()).apply { remove("uid"); remove("cookies") }
        try {
            val response = api("/api/login/qrcode/unikey", JSONObject().put("type", 3),
                authHttp = authHttp, requestState = candidate, persistResponse = false)
            val key = response.optString("unikey").ifEmpty {
                response.optJSONObject("data")?.optString("unikey").orEmpty()
            }
            if (key.isEmpty()) throw MusicProviderException("网易云未返回登录二维码")
            currentCoroutineContext().ensureActive()
            synchronized(loginLock) {
                activeChallenge = key
                qrContext = QrContext(key, authHttp, candidate)
            }
            lastPollAt = 0
            lastPoll = MusicLoginResult(MusicLoginState.WAITING_SCAN)
            MusicLoginChallenge(key,
                qrContent = "https://music.163.com/login?codekey=${URLEncoder.encode(key, "UTF-8")}",
                expiresAt = System.currentTimeMillis() + 180_000)
        } catch (error: Exception) {
            authHttp.close()
            throw error
        }
    }

    override suspend fun pollLogin(challenge: MusicLoginChallenge): MusicLoginResult = locked {
        if (challenge.id != activeChallenge || challenge.expiresAt <= System.currentTimeMillis()) {
            return@locked MusicLoginResult(MusicLoginState.EXPIRED, "二维码已过期")
        }
        val context = qrContext?.takeIf { it.key == challenge.id }
            ?: return@locked MusicLoginResult(MusicLoginState.EXPIRED, "二维码已过期")
        if (System.currentTimeMillis() - lastPollAt < 2500) return@locked lastPoll
        lastPollAt = System.currentTimeMillis()
        val result = api("/api/login/qrcode/client/login", JSONObject().put("key", challenge.id).put("type", 3),
            allowedCodes = setOf(800, 801, 802, 803), authHttp = context.http,
            requestState = context.state, persistResponse = false)
        // A cancelled/changed dialog must never publish a late login response.
        if (activeChallenge != challenge.id) return@locked MusicLoginResult(MusicLoginState.EXPIRED)
        lastPoll = when (val code = result.optInt("code")) {
            800 -> MusicLoginResult(MusicLoginState.EXPIRED, "二维码已过期")
            801 -> MusicLoginResult(MusicLoginState.WAITING_SCAN)
            802 -> MusicLoginResult(MusicLoginState.WAITING_CONFIRMATION, "请在网易云音乐确认")
            803 -> {
                val account = api("/api/w/nuser/account/get", JSONObject(), weapi = true,
                    authHttp = context.http, requestState = context.state, persistResponse = false)
                val uid = account.optJSONObject("account")?.optString("id")
                    ?: account.optJSONObject("profile")?.optString("userId").orEmpty()
                if (uid.isEmpty() || uid == "0" || context.http.cookie("MUSIC_U").isEmpty()) {
                    throw MusicProviderException("网易云登录状态校验失败")
                }
                currentCoroutineContext().ensureActive()
                synchronized(loginLock) {
                    if (activeChallenge != challenge.id || qrContext !== context) {
                        throw CancellationException("网易云登录已取消")
                    }
                    val candidate = JSONObject(context.state.toString()).put("uid", uid)
                        .put("cookies", context.http.snapshotCookies())
                    MusicCredentialStore.write(platform, candidate)
                    state = candidate
                    http.restoreCookies(candidate.optJSONArray("cookies"))
                    details.clear()
                    activeChallenge = ""
                    qrContext = null
                    context.http.close()
                }
                MusicLoginResult(MusicLoginState.AUTHENTICATED, "登录成功")
            }
            else -> MusicLoginResult(MusicLoginState.FAILED, "网易云登录失败（$code）")
        }
        lastPoll
    }

    override fun cancelLogin(challenge: MusicLoginChallenge) {
        synchronized(loginLock) {
            if (activeChallenge == challenge.id) {
                activeChallenge = ""
                qrContext?.http?.close()
                qrContext = null
            }
        }
    }

    override suspend fun sendPhoneCode(session: MusicPhoneLoginSession): MusicPhoneCodeResult = locked {
        session.bind(platform)
        val context = synchronized(loginLock) {
            activeChallenge = ""
            qrContext?.http?.close()
            qrContext = null
            phoneContext?.takeIf { it.session === session } ?: run {
                phoneContext?.let { it.session.cancel(); it.http.close() }
                PhoneContext(session, MusicHttpClient(platform, setOf("163.com", "126.net", "127.net", "163yun.com")),
                    JSONObject(state.toString()).apply { remove("uid"); remove("cookies") })
                    .also { phoneContext = it }
            }
        }
        session.markCodeSent(false)
        val response = api("/api/sms/captcha/sent", MusicPhoneProtocol.neteaseSend(session), weapi = true,
            authHttp = context.http, requestState = context.state, validateCode = false, persistResponse = false)
        session.requireActive()
        val result = MusicPhoneProtocol.neteaseSendResult(response)
        session.markCodeSent(result.state == MusicPhoneCodeState.SENT)
        result
    }

    override suspend fun loginPhoneCode(session: MusicPhoneLoginSession, code: String): MusicLoginResult = locked {
        session.bind(platform)
        session.requireActive(requireCode = true)
        val context = phoneContext?.takeIf { it.session === session }
            ?: throw MusicProviderException("登录已失效，请重新获取验证码")
        val response = api("/api/w/login/cellphone", MusicPhoneProtocol.neteaseLogin(session, code), weapi = true,
            authHttp = context.http, requestState = context.state, validateCode = false, persistResponse = false)
        session.requireActive()
        if (response.optInt("code", -1) != 200) throw MusicPhoneProtocol.neteaseLoginError(response)
        val account = api("/api/w/nuser/account/get", JSONObject(), weapi = true,
            authHttp = context.http, requestState = context.state, persistResponse = false)
        session.requireActive()
        val uid = account.optJSONObject("account")?.optString("id")
            ?: account.optJSONObject("profile")?.optString("userId").orEmpty()
        if (uid.isEmpty() || uid == "0" || context.http.cookie("MUSIC_U").isEmpty()) {
            throw MusicProviderException("账号尚未完成登录，请在网易云音乐完成注册、绑定或安全验证")
        }
        val candidate = JSONObject(context.state.toString()).put("uid", uid)
            .put("cookies", context.http.snapshotCookies())
        currentCoroutineContext().ensureActive()
        val published = synchronized(loginLock) {
            phoneContext === context && session.commitIfActive {
                MusicCredentialStore.write(platform, candidate)
                state = candidate
                http.restoreCookies(candidate.optJSONArray("cookies"))
                details.clear()
            }
        }
        if (!published) throw CancellationException("网易云登录已取消")
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

    override suspend fun logout() = locked {
        phoneContext?.session?.let(::cancelPhoneLogin)
        synchronized(loginLock) {
            activeChallenge = ""
            qrContext?.http?.close()
            qrContext = null
        }
        http.clearCookies()
        details.clear()
        state = JSONObject().put("deviceId", state.optString("deviceId"))
            .put("nuid", randomHex(32)).put("nmtid", "00O${randomHex(19)}")
        MusicCredentialStore.clear(platform)
    }

    override suspend fun search(query: String, offset: Int): MusicPage<MusicTrack> = locked {
        if (query.isBlank()) return@locked MusicPage(emptyList())
        val start = offset.coerceAtLeast(0)
        val result = api("/api/cloudsearch/pc", JSONObject().put("s", query.trim()).put("type", 1)
            .put("limit", PAGE_SIZE).put("offset", start).put("total", true))
            .optJSONObject("result") ?: JSONObject()
        val songs = result.optJSONArray("songs") ?: JSONArray()
        val total = result.optInt("songCount", songs.length())
        MusicPage(parseTracks(songs, result.optJSONArray("privileges")), next(start, songs.length(), total), total)
    }

    override suspend fun playlists(offset: Int): MusicPage<MusicPlaylist> = locked {
        val uid = accountId()
        if (uid.isEmpty()) throw MusicProviderException("请先登录网易云")
        val start = offset.coerceAtLeast(0)
        val result = api("/api/user/playlist", JSONObject().put("uid", uid).put("limit", PAGE_SIZE)
            .put("offset", start).put("includeVideo", true), weapi = true)
        val rows = result.optJSONArray("playlist") ?: JSONArray()
        val items = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                MusicPlaylistProtocol.netease(row, uid)?.let(::add)
            }
        }
        MusicPage(items, if (result.optBoolean("more") && rows.length() > 0) start + rows.length() else null)
    }

    override suspend fun playlistTracks(playlist: MusicPlaylist, offset: Int): MusicPage<MusicTrack> = locked {
        require(playlist.platform == platform)
        val start = offset.coerceAtLeast(0)
        val cacheKey = "${state.optString("uid")}:${playlist.id}"
        val cached = details[cacheKey]?.takeIf { start > 0 && System.currentTimeMillis() - it.loadedAt < 120_000 }
        val detail = cached ?: run {
            val row = api("/api/v6/playlist/detail", JSONObject().put("id", playlist.id)
                .put("n", 100000).put("s", 0)).optJSONObject("playlist")
                ?: throw MusicProviderException("网易云歌单详情不可用")
            val ids = row.optJSONArray("trackIds")
            val tracks = row.optJSONArray("tracks") ?: JSONArray()
            PlaylistDetail(buildList {
                if (ids != null) {
                    for (index in 0 until ids.length()) ids.optJSONObject(index)?.optString("id")
                        ?.takeIf(String::isNotEmpty)?.let(::add)
                } else {
                    for (index in 0 until tracks.length()) tracks.optJSONObject(index)?.optString("id")
                        ?.takeIf(String::isNotEmpty)?.let(::add)
                }
            }, System.currentTimeMillis()).also {
                details[cacheKey] = it
                if (details.size > 4) details.remove(details.keys.first())
            }
        }
        val ids = detail.ids.drop(start).take(PAGE_SIZE)
        if (ids.isEmpty()) return@locked MusicPage(emptyList(), total = detail.ids.size)
        val requests = JSONArray().apply { ids.forEach { put(JSONObject().put("id", it.toLongOrNull() ?: it)) } }
        val result = api("/api/v3/song/detail", JSONObject().put("c", requests.toString()), weapi = true)
        val byId = parseTracks(result.optJSONArray("songs") ?: JSONArray(), result.optJSONArray("privileges"))
            .associateBy(MusicTrack::id)
        MusicPage(ids.mapNotNull(byId::get), next(start, ids.size, detail.ids.size), detail.ids.size)
    }

    override suspend fun playlistTrackIds(playlist: MusicPlaylist): Set<String> = locked {
        require(playlist.platform == platform)
        val row = api("/api/v6/playlist/detail", JSONObject().put("id", playlist.id)
            .put("n", 100000).put("s", 0)).optJSONObject("playlist")
            ?: throw MusicProviderException("网易云歌单详情不可用")
        if (row.optString("id") != playlist.id) throw MusicProviderException("歌单信息不匹配，请重试")
        MusicPlaylistProtocol.neteasePlaylistTrackIds(row)
    }

    override suspend fun createPlaylist(title: String): MusicPlaylist = locked {
        val uid = accountId()
        if (uid.isBlank()) throw MusicProviderException("请先登录网易云")
        val name = MusicPlaylistProtocol.title(title)
        val result = api("/api/playlist/create", JSONObject().put("name", name)
            .put("privacy", "0").put("type", "NORMAL"), weapi = true)
        MusicPlaylistProtocol.requireNeteaseWrite(result)
        val row = result.optJSONObject("playlist")
            ?: throw MusicProviderException("歌单可能已创建，请刷新歌单确认")
        val id = row.optString("id").takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }
            ?: throw MusicProviderException("歌单可能已创建，请刷新歌单确认")
        MusicPlaylist(id, platform, row.optString("name").ifBlank { name },
            row.optString("coverImgUrl").takeUnless { it == "null" }.orEmpty(),
            ownerId = uid, kind = MusicPlaylistKind.OWNED)
    }

    override suspend fun editPlaylist(playlist: MusicPlaylist, title: String, description: String): MusicPlaylist = locked {
        MusicPlaylistProtocol.requireOwned(playlist, platform, accountId())
        val name = MusicPlaylistProtocol.title(title)
        if (description.length > 1000) throw MusicProviderException("简介最多 1000 个字")
        val namePath = "/api/playlist/update/name"
        val descPath = "/api/playlist/desc/update"
        val result = api("/api/batch", JSONObject()
            .put(namePath, JSONObject().put("id", playlist.id).put("name", name).toString())
            .put(descPath, JSONObject().put("id", playlist.id).put("desc", description).toString()))
        if (listOf(namePath, descPath).any { result.optJSONObject(it)?.optInt("code", -1) != 200 }) {
            throw MusicProviderException("部分信息可能已更新，请刷新歌单核对")
        }
        playlist.copy(title = name, description = description)
    }

    override suspend fun deletePlaylist(playlist: MusicPlaylist): Unit = locked {
        MusicPlaylistProtocol.requireOwned(playlist, platform, accountId())
        MusicPlaylistProtocol.requireNeteaseWrite(api("/api/playlist/remove",
            JSONObject().put("ids", JSONArray().put(playlist.id).toString()), weapi = true))
        details.clear()
    }

    override suspend fun unsubscribePlaylist(playlist: MusicPlaylist): Unit = locked {
        if (accountId().isBlank()) throw MusicProviderException("请先登录网易云")
        if (playlist.platform != platform || !playlist.collected ||
            playlist.kind != MusicPlaylistKind.COLLECTED) throw MusicProviderException("这个歌单无法取消收藏")
        MusicPlaylistProtocol.requireNeteaseWrite(api("/api/playlist/unsubscribe",
            JSONObject().put("id", playlist.id)))
        details.clear()
    }

    override suspend fun addTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>) =
        changeTracks(playlist, tracks, true)

    override suspend fun removeTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>) =
        changeTracks(playlist, tracks, false)

    private suspend fun changeTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>, add: Boolean): Unit = locked {
        MusicPlaylistProtocol.requireOwned(playlist, platform, accountId())
        val result = api("/api/playlist/manipulate/tracks",
            MusicPlaylistProtocol.neteaseTracks(playlist, tracks, add), allowedCodes = setOf(502))
        MusicPlaylistProtocol.requireNeteaseWrite(result)
        details.clear()
    }

    override suspend fun resolve(track: MusicTrack): MusicPlaybackSource = locked {
        require(track.platform == platform)
        // This is the normal eapi endpoint exercised by the account probe (song_url, 128 kbps).
        val result = api("/api/song/enhance/player/url", JSONObject()
            .put("ids", JSONArray().put(track.id).toString()).put("br", 128000))
        val row = result.optJSONArray("data")?.let { values ->
            (0 until values.length()).mapNotNull(values::optJSONObject).firstOrNull { it.optString("id") == track.id }
        } ?: throw MusicProviderException("网易云未返回播放地址")
        val raw = row.optString("url").takeUnless { it == "null" }.orEmpty()
        if (raw.isEmpty()) {
            val code = row.optInt("code", result.optInt("code"))
            throw MusicProviderException("网易云暂不可播放（$code）", code)
        }
        if (!isMediaUrl(raw)) throw MusicProviderException("网易云播放地址无效")
        MusicPlaybackSource(raw, mapOf("Referer" to "https://music.163.com/", "User-Agent" to WEB_UA),
            System.currentTimeMillis() + row.optLong("expi", 1200).coerceAtLeast(0) * 1000,
            preview = row.opt("freeTrialInfo")?.let { it != JSONObject.NULL } ?: false)
    }

    private suspend fun accountId(refresh: Boolean = false): String {
        if (!refresh && state.optString("uid").isNotEmpty()) return state.getString("uid")
        val result = api("/api/w/nuser/account/get", JSONObject(), weapi = true, allowedCodes = setOf(301))
        val id = result.optJSONObject("account")?.optString("id")
            ?: result.optJSONObject("profile")?.optString("userId").orEmpty()
        if (id.isNotEmpty() && id != "0") {
            state.put("uid", id)
            persist()
            return id
        }
        state.remove("uid")
        return ""
    }

    private suspend fun api(
        path: String, data: JSONObject, weapi: Boolean = false, allowedCodes: Set<Int> = emptySet(),
        authHttp: MusicHttpClient = http, requestState: JSONObject = state,
        validateCode: Boolean = true, persistResponse: Boolean = true,
    ): JSONObject {
        val now = System.currentTimeMillis()
        val deviceId = requestState.getString("deviceId")
        val header = JSONObject().put("osver", "Microsoft-Windows-10-Professional-build-19045-64bit")
            .put("deviceId", deviceId).put("os", "pc").put("appver", "3.1.17.204416")
            .put("versioncode", "140").put("mobilename", "").put("buildver", (now / 1000).toString())
            .put("resolution", "1920x1080").put("__csrf", authHttp.cookie("__csrf"))
            .put("channel", "netease").put("requestId", "${now}_${(0..999).random().toString().padStart(3, '0')}")
        listOf("MUSIC_U", "MUSIC_A", "NMTID").forEach { name ->
            authHttp.cookie(name).takeIf(String::isNotEmpty)?.let { header.put(name, it) }
        }
        data.put("e_r", false)
        val headers = mutableMapOf("Referer" to "https://music.163.com/", "User-Agent" to if (weapi) WEB_UA else API_UA)
        val fields: Map<String, String>
        val url: String
        if (weapi) {
            data.put("csrf_token", authHttp.cookie("__csrf"))
            val cookie = JSONObject(header.toString()).put("__remember_me", "true")
                .put("_ntes_nuid", requestState.getString("nuid")).put("_ntes_nnid", "${requestState.getString("nuid")},$now")
                .put("ntes_kaola_ad", "1").put("WEVNSM", "1.0.0")
            headers["Cookie"] = cookieHeader(cookie)
            fields = NeteaseCrypto.weapi(data)
            url = "https://music.163.com/weapi/${path.removePrefix("/api/")}"
        } else {
            if (!header.has("NMTID")) header.put("NMTID", requestState.getString("nmtid"))
            data.put("header", header)
            headers["Cookie"] = cookieHeader(header)
            fields = NeteaseCrypto.eapi(path, data)
            url = "https://interfacepc.music.163.com/eapi/${path.removePrefix("/api/")}"
        }
        val response = authHttp.postForm(url, fields, headers)
        val body = runCatching { response.json() }.getOrElse { NeteaseCrypto.decodeResponse(response.body) }
        if (persistResponse && state.optString("uid").isNotEmpty()) persist()
        val code = body.optInt("code", 200)
        if (validateCode && code != 200 && code !in allowedCodes) {
            if (code == 301) throw MusicProviderException("网易云登录已失效，请重新登录", code)
            throw MusicProviderException("网易云请求失败（$code）", code)
        }
        return body
    }

    private fun ensureLoaded() {
        if (loaded) return
        state = MusicCredentialStore.read(platform) ?: JSONObject()
        if (state.optString("deviceId").isEmpty()) state.put("deviceId", randomHex(16))
        if (state.optString("nuid").isEmpty()) state.put("nuid", randomHex(32))
        if (state.optString("nmtid").isEmpty()) state.put("nmtid", "00O${randomHex(19)}")
        http.restoreCookies(state.optJSONArray("cookies"))
        loaded = true
    }

    private fun persist() {
        state.put("cookies", http.snapshotCookies())
        MusicCredentialStore.write(platform, state)
    }

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                ensureLoaded()
                block()
            } catch (e: CancellationException) { throw e }
            catch (e: MusicProviderException) { throw e }
            catch (_: Exception) { throw MusicProviderException("网易云数据暂不可用，请稍后重试") }
        }
    }

    private data class PlaylistDetail(val ids: List<String>, val loadedAt: Long)

    internal companion object {
        private const val PAGE_SIZE = 100
        private const val WEB_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val API_UA = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
        private fun randomHex(bytes: Int) = NeteaseCrypto.bytes(bytes).toByteString().hex()
        private fun next(start: Int, count: Int, total: Int) = (start + count).takeIf { count > 0 && it < total }
        private fun cookieHeader(cookie: JSONObject): String = cookie.keys().asSequence().joinToString("; ") { key ->
            "$key=${URLEncoder.encode(cookie.get(key).toString(), "UTF-8").replace("+", "%20")}"
        }
        internal fun isMediaUrl(raw: String): Boolean {
            val url = raw.toHttpUrlOrNull() ?: return false
            return url.username.isEmpty() && url.password.isEmpty() &&
                ((url.scheme == "https" && url.port == 443) || (url.scheme == "http" && url.port == 80)) &&
                setOf("163.com", "126.net", "127.net", "163yun.com").any { url.host == it || url.host.endsWith(".$it") }
        }
        internal fun parseTracks(values: JSONArray, privileges: JSONArray? = null): List<MusicTrack> = buildList {
            val privilegesById = MusicTrackVipParser.neteasePrivileges(privileges)
            for (index in 0 until values.length()) {
                val song = values.optJSONObject(index) ?: continue
                val id = song.optString("id")
                if (id.isEmpty()) continue
                val album = song.optJSONObject("al") ?: song.optJSONObject("album")
                val artists = song.optJSONArray("ar") ?: song.optJSONArray("artists") ?: JSONArray()
                val names = (0 until artists.length()).mapNotNull { artists.optJSONObject(it)?.optString("name") }
                add(MusicTrack(id, MusicPlatform.NETEASE, song.optString("name"), names.joinToString(" / "),
                    album?.optString("name").orEmpty(), album?.optString("picUrl").orEmpty(),
                    song.optLong("dt", song.optLong("duration")),
                    isVip = MusicTrackVipParser.netease(song, privilegesById[id])))
            }
        }
    }
}
