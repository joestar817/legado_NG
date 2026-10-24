package io.legado.app.help.bgm

import com.google.gson.annotations.SerializedName

enum class MusicPlatform(val label: String) {
    @SerializedName("netease") NETEASE("网易云"),
    @SerializedName("qq") QQ("QQ 音乐"),
    @SerializedName("local") LOCAL("本地")
}

/** Read-only account presentation data. Never persisted with credentials or playback settings. */
data class MusicAccountProfile(
    @SerializedName("userId") val userId: String,
    @SerializedName("nickname") val nickname: String = "",
    @SerializedName("avatarUrl") val avatarUrl: String = "",
    @SerializedName("signature") val signature: String = "",
    @SerializedName("level") val level: Int? = null,
    @SerializedName("followingCount") val followingCount: Long? = null,
    @SerializedName("followerCount") val followerCount: Long? = null,
    @SerializedName("playlistCount") val playlistCount: Long? = null,
    @SerializedName("listenSongs") val listenSongs: Long? = null,
    @SerializedName("detailsLoaded") val detailsLoaded: Boolean = false,
)

/** VIP level and badge can remain after expiry; null activity means the platform did not report it. */
data class MusicMembershipInfo(
    @SerializedName("level") val level: Int,
    @SerializedName("iconUrl") val iconUrl: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("isActive") val isActive: Boolean? = null,
)

/** Optional, in-memory profile details. Music level is independent of an active subscription. */
data class MusicAccountExtras(
    @SerializedName("membership") val membership: MusicMembershipInfo? = null,
    @SerializedName("musicLevel") val musicLevel: Int? = null,
    @SerializedName("musicLevelIconUrl") val musicLevelIconUrl: String = "",
    @SerializedName("membershipLoaded") val membershipLoaded: Boolean = true,
    @SerializedName("musicLevelLoaded") val musicLevelLoaded: Boolean = true,
)

data class MusicTrack(
    @SerializedName("id") val id: String,
    @SerializedName("platform") val platform: MusicPlatform,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String = "",
    @SerializedName("album") val album: String = "",
    @SerializedName("coverUrl") val coverUrl: String = "",
    @SerializedName("durationMs") val durationMs: Long = 0,
    @SerializedName("mediaId") val mediaId: String = "",
    @SerializedName("songType") val songType: Int = 0,
    @SerializedName("localPath") val localPath: String = "",
    @SerializedName("numericId") val numericId: Long = 0,
    @SerializedName("localReference") val localReference: LocalMusicReference? = null,
    /** Catalogue badge only; null means unknown and never determines playback permission. */
    @SerializedName("isVip") val isVip: Boolean? = null,
) {
    val key: String get() = "${platform.name}:$id"
}

/** An explicitly selected external file. localPath continues to denote an app-owned copy only. */
data class LocalMusicReference(
    @SerializedName("uri") val uri: String,
    @SerializedName("rootUri") val rootUri: String,
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("modified") val modified: Long,
    @SerializedName("digest") val digest: String = "",
)

enum class MusicPlaylistKind {
    @SerializedName("unknown") UNKNOWN,
    @SerializedName("system") SYSTEM,
    @SerializedName("owned") OWNED,
    @SerializedName("collected") COLLECTED
}

data class MusicPlaylist(
    @SerializedName("id") val id: String,
    @SerializedName("platform") val platform: MusicPlatform,
    @SerializedName("title") val title: String,
    @SerializedName("coverUrl") val coverUrl: String = "",
    @SerializedName("trackCount") val trackCount: Int = 0,
    @SerializedName("collected") val collected: Boolean = false,
    @SerializedName("extra") val extra: String = "",
    @SerializedName("ownerId") val ownerId: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("kind") val kind: MusicPlaylistKind = MusicPlaylistKind.UNKNOWN
) {
    val key: String get() = "${platform.name}:$id"
    val editable: Boolean get() = kind == MusicPlaylistKind.OWNED && !collected && ownerId.isNotBlank()
}

data class MusicPage<T>(
    @SerializedName("items") val items: List<T>,
    @SerializedName("nextOffset") val nextOffset: Int? = null,
    @SerializedName("total") val total: Int? = null
)

/** A temporary media address. Never persisted with a queue or included in logs. */
data class MusicPlaybackSource(
    @SerializedName("url") val url: String,
    @SerializedName("headers") val headers: Map<String, String> = emptyMap(),
    @SerializedName("expiresAt") val expiresAt: Long = 0,
    @SerializedName("preview") val preview: Boolean? = null,
    @SerializedName("fullDurationMs") val fullDurationMs: Long = 0,
    /** A separately authorized file; its media timeline starts at zero, without extra clipping. */
    @SerializedName("standalonePreview") val standalonePreview: Boolean = false
)

/** Login material belongs to the visible login flow and is not part of saved settings. */
data class MusicLoginChallenge(
    @SerializedName("id") val id: String,
    @SerializedName("qrContent") val qrContent: String = "",
    @SerializedName("image") val image: ByteArray? = null,
    @SerializedName("expiresAt") val expiresAt: Long = System.currentTimeMillis() + 120_000
)

enum class MusicLoginState {
    @SerializedName("waiting_scan") WAITING_SCAN,
    @SerializedName("waiting_confirmation") WAITING_CONFIRMATION,
    @SerializedName("authenticated") AUTHENTICATED,
    @SerializedName("expired") EXPIRED,
    @SerializedName("failed") FAILED
}

data class MusicLoginResult(
    @SerializedName("state") val state: MusicLoginState,
    @SerializedName("message") val message: String = ""
)

enum class BgmRepeatMode(val label: String) {
    @SerializedName("list") LIST("列表循环"),
    @SerializedName("one") ONE("单曲循环"),
    @SerializedName("shuffle") SHUFFLE("随机播放")
}

data class BgmSettings(
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("volume") val volume: Float = 0.15f,
    @SerializedName("repeatMode") val repeatMode: BgmRepeatMode = BgmRepeatMode.LIST,
    @SerializedName("queue") val queue: List<MusicTrack> = emptyList(),
    @SerializedName("index") val index: Int = 0,
    @SerializedName("localTracks") val localTracks: List<MusicTrack> = emptyList(),
    @SerializedName("selectionRevision") val selectionRevision: Long = 0
) {
    val currentTrack: MusicTrack? get() = queue.getOrNull(index)
}

enum class BgmPlaybackStatus { IDLE, WAITING, LOADING, PLAYING, PAUSED, ERROR }

data class BgmPlaybackState(
    val status: BgmPlaybackStatus = BgmPlaybackStatus.IDLE,
    val trackKey: String? = null,
    val message: String = ""
)

class MusicProviderException(message: String, val platformCode: Int? = null) : Exception(message)
