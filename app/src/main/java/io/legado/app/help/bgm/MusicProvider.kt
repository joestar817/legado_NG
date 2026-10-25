package io.legado.app.help.bgm

/** Account catalogue access. Writes only run after an explicit library management action. */
interface MusicProvider {
    val platform: MusicPlatform
    val supportsPlaylistEditing: Boolean get() = false
    val supportsPlaylistUnsubscribe: Boolean get() = false
    val supportsAccountProfile: Boolean get() = false
    suspend fun isLoggedIn(): Boolean
    suspend fun accountProfile(): MusicAccountProfile {
        throw MusicProviderException("当前平台暂不支持读取账号资料")
    }
    /** Optional account decoration, loaded after the basic profile is already visible. */
    suspend fun accountMembership(userId: String): MusicMembershipInfo? = null
    suspend fun accountExtras(userId: String): MusicAccountExtras =
        MusicAccountExtras(membership = accountMembership(userId))
    suspend fun beginLogin(): MusicLoginChallenge
    suspend fun beginWechatLogin(): MusicLoginChallenge {
        throw MusicProviderException("当前平台不支持微信扫码")
    }
    suspend fun pollLogin(challenge: MusicLoginChallenge): MusicLoginResult
    fun cancelLogin(challenge: MusicLoginChallenge) = Unit
    suspend fun sendPhoneCode(session: MusicPhoneLoginSession): MusicPhoneCodeResult
    suspend fun loginPhoneCode(session: MusicPhoneLoginSession, code: String): MusicLoginResult
    fun cancelPhoneLogin(session: MusicPhoneLoginSession) { session.cancel() }
    suspend fun logout()
    suspend fun search(query: String, offset: Int = 0): MusicPage<MusicTrack>
    suspend fun playlists(offset: Int = 0): MusicPage<MusicPlaylist>
    suspend fun playlistTracks(playlist: MusicPlaylist, offset: Int = 0): MusicPage<MusicTrack>
    suspend fun playlistTrackIds(playlist: MusicPlaylist): Set<String> =
        collectPlaylistTrackIds { offset -> playlistTracks(playlist, offset) }
    suspend fun createPlaylist(title: String): MusicPlaylist
    suspend fun editPlaylist(playlist: MusicPlaylist, title: String, description: String): MusicPlaylist {
        throw MusicProviderException("当前平台暂不支持编辑歌单")
    }
    suspend fun deletePlaylist(playlist: MusicPlaylist)
    suspend fun unsubscribePlaylist(playlist: MusicPlaylist) {
        throw MusicProviderException("当前平台暂不支持取消收藏")
    }
    suspend fun addTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>)
    suspend fun removeTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>)
    suspend fun resolve(track: MusicTrack): MusicPlaybackSource
    /** Temporary listening may use an explicitly authorized preview instead of the full song. */
    suspend fun resolvePreview(track: MusicTrack): MusicPlaybackSource = resolve(track)
}
