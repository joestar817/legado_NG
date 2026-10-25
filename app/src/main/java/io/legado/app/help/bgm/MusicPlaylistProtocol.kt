package io.legado.app.help.bgm

import org.json.JSONArray
import org.json.JSONObject

/** Shared parsing and write guards; missing ownership never grants permission. */
internal object MusicPlaylistProtocol {
    fun netease(row: JSONObject, accountId: String): MusicPlaylist? {
        val id = row.optString("id").takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true } ?: return null
        val owner = row.optJSONObject("creator")?.optString("userId").orEmpty()
        val own = accountId.isNotBlank() && owner == accountId
        val collected = owner.isNotBlank() && !own
        return MusicPlaylist(
            id = id, platform = MusicPlatform.NETEASE, title = row.optString("name"),
            coverUrl = row.optString("coverImgUrl").takeUnless { it == "null" }.orEmpty(),
            trackCount = row.optInt("trackCount"), collected = collected, ownerId = owner,
            description = row.optString("description").takeUnless { it == "null" }.orEmpty(),
            kind = when {
                !own -> if (collected) MusicPlaylistKind.COLLECTED else MusicPlaylistKind.UNKNOWN
                row.optInt("specialType", -1) == 5 -> MusicPlaylistKind.SYSTEM
                row.optInt("specialType", -1) == 0 -> MusicPlaylistKind.OWNED
                else -> MusicPlaylistKind.UNKNOWN
            },
        )
    }

    fun requireOwned(playlist: MusicPlaylist, platform: MusicPlatform, accountId: String) {
        if (playlist.platform != platform || !playlist.editable || accountId.isBlank() ||
            playlist.ownerId != accountId) throw MusicProviderException("这个歌单无法编辑，请刷新后重试")
    }

    fun neteasePlaylistTrackIds(row: JSONObject): Set<String> {
        val entries = row.optJSONArray("trackIds")
            ?: throw MusicProviderException("歌单歌曲列表不可用")
        val ids = buildSet {
            for (index in 0 until entries.length()) {
                val id = entries.optJSONObject(index)?.optString("id")
                    ?.toLongOrNull()?.takeIf { it > 0 }
                    ?: throw MusicProviderException("歌单歌曲信息不完整")
                add(id.toString())
            }
        }
        if (row.optInt("trackCount", entries.length()) > entries.length()) {
            throw MusicProviderException("歌单歌曲未完整返回")
        }
        return ids
    }

    fun title(value: String): String = value.trim().also {
        if (it.isBlank() || it.length > 40) throw MusicProviderException("歌单名称请填写 1～40 个字")
    }

    fun neteaseTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>, add: Boolean): JSONObject {
        val ids = checkedTracks(playlist, tracks).map {
            it.id.toLongOrNull()?.takeIf { id -> id > 0 }
                ?: throw MusicProviderException("歌曲信息不完整，请重新搜索")
        }
        return JSONObject().put("op", if (add) "add" else "del").put("pid", playlist.id)
            .put("trackIds", JSONArray(ids).toString()).put("imme", "true")
    }

    fun qqTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>): JSONObject {
        val metadata = JSONObject(playlist.extra)
        val dirId = metadata.optInt("dirid")
        if (dirId <= 0 || dirId == 201) throw MusicProviderException("这个歌单无法编辑")
        val songs = JSONArray()
        checkedTracks(playlist, tracks).forEach {
            if (it.numericId <= 0) throw MusicProviderException("歌曲信息不完整，请重新搜索")
            songs.put(JSONObject().put("songId", it.numericId).put("songType", it.songType))
        }
        return JSONObject().put("dirId", dirId).put("tid", metadata.optLong("tid"))
            .put("bFmtUtf8", true).put("v_songInfo", songs)
    }

    private fun checkedTracks(playlist: MusicPlaylist, tracks: List<MusicTrack>): List<MusicTrack> {
        if (tracks.isEmpty() || tracks.size > 100 || tracks.any { it.platform != playlist.platform }) {
            throw MusicProviderException("请从当前平台选择 1～100 首歌曲")
        }
        return tracks.distinctBy(MusicTrack::key)
    }

    fun requireQqWrite(data: JSONObject) {
        val code = data.optInt("retCode", -1)
        if (code != 0) throw MusicProviderException("QQ 音乐未完成修改（$code）", code)
    }

    fun requireNeteaseWrite(data: JSONObject) {
        val code = data.optInt("code", -1)
        if (code == 502) throw MusicProviderException("歌曲已在歌单中", code)
        if (code != 200) throw MusicProviderException("网易云未完成修改（$code）", code)
    }
}
