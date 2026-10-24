package io.legado.app.help.bgm.qq

import io.legado.app.help.bgm.MusicProviderException
import io.legado.app.help.bgm.MusicTrack
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** QQMusicApi 0.8.2 song metadata and ordinary/standalone-preview resource checks. */
internal object QqPlaybackProtocol {
    fun trackInfo(response: JSONObject, mid: String): JSONObject? {
        val tracks = response.optJSONArray("tracks") ?: return null
        return (0 until tracks.length()).firstNotNullOfOrNull { index ->
            tracks.optJSONObject(index)?.takeIf { it.text("mid") == mid }
        }
    }

    fun playbackTrack(original: MusicTrack, info: JSONObject?): MusicTrack {
        if (info == null) return original
        val seconds = info.optLong("interval", 0)
        return original.copy(
            mediaId = info.optJSONObject("file")?.text("media_mid")
                ?.takeIf(::validMediaId) ?: original.mediaId,
            songType = info.optInt("type", original.songType),
            durationMs = if (seconds in 1..Long.MAX_VALUE / 1000) seconds * 1000 else original.durationMs,
        )
    }

    fun previewFileName(info: JSONObject?): String? {
        val file = info?.optJSONObject("file") ?: return null
        if (file.optLong("size_try", 0) <= 0) return null
        val mid = (info.optJSONArray("vs")?.opt(0) as? String)?.takeIf(::validMediaId) ?: return null
        // try_begin/try_end do not describe the separately encoded RS02 file's timeline.
        return "RS02$mid.mp3"
    }

    /** Null means the successful reply contains no usable URL, not a guessed resource. */
    fun mediaPath(response: JSONObject, mid: String, filename: String): String? {
        val info = response.optJSONArray("midurlinfo")?.optJSONObject(0) ?: return null
        val code = info.optInt("result", -1)
        if (code != 0) throw MusicProviderException("QQ 音乐暂不可播放（$code）", code)
        if ((info.has("songmid") && info.text("songmid") != mid) ||
            (info.has("filename") && info.text("filename") != filename)) {
            throw MusicProviderException("QQ 音乐音频资源不匹配")
        }
        val path = info.text("purl").takeIf(String::isNotBlank) ?: return null
        if (path.substringBefore('?').substringBefore('#').substringAfterLast('/') != filename) {
            throw MusicProviderException("QQ 音乐音频资源不匹配")
        }
        return path
    }

    fun mediaAddress(path: String, servers: JSONArray?): String? {
        if (servers != null) {
            for (index in 0 until servers.length()) {
                val base = servers.optString(index).toHttpUrlOrNull() ?: continue
                if (!QqMusicProvider.validMediaUrl(base.toString())) continue
                val resolved = base.resolve(path) ?: continue
                if (QqMusicProvider.validMediaUrl(resolved.toString())) return resolved.toString()
            }
        }
        return path.takeIf { QqMusicProvider.validMediaUrl(it) }
    }

    private fun validMediaId(value: String): Boolean = value.isNotEmpty() && value.length <= 128 &&
        value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }

    private fun JSONObject.text(key: String): String = (opt(key) as? String).orEmpty()
}
