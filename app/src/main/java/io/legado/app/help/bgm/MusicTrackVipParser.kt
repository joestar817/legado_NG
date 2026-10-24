package io.legado.app.help.bgm

import org.json.JSONArray
import org.json.JSONObject

/** Catalogue presentation only. Download prices, playback failures and trials are not VIP badges. */
internal object MusicTrackVipParser {
    fun qq(song: JSONObject): Boolean? {
        val pay = song.optJSONObject("pay") ?: return null
        val monthly = pay.integer("pay_month")?.takeIf { it == 0 || it == 1 } ?: return null
        val playback = pay.integer("pay_play")?.takeIf { it == 0 || it == 1 } ?: return null
        return monthly == 1 && playback == 1
    }

    fun netease(song: JSONObject, privilege: JSONObject? = null): Boolean? {
        if (!song.isNull("fee")) return neteaseFee(song)
        val id = song.positiveId() ?: return null
        val nested = song.optJSONObject("privilege")?.takeIf { it.positiveId() == id }
        if (nested != null && !nested.isNull("fee")) return neteaseFee(nested)
        return privilege?.takeIf { it.positiveId() == id }?.let(::neteaseFee)
    }

    fun neteasePrivileges(values: JSONArray?): Map<String, JSONObject> = buildMap {
        if (values == null) return@buildMap
        for (index in 0 until values.length()) {
            val privilege = values.optJSONObject(index) ?: continue
            val id = privilege.positiveId() ?: continue
            put(id, privilege)
        }
    }

    private fun neteaseFee(value: JSONObject): Boolean? = when (value.integer("fee")) {
        1 -> true
        0, 4, 8 -> false
        else -> null
    }

    // optInt would silently turn fractional or malformed values into a known catalogue state.
    private fun JSONObject.integer(key: String): Int? = opt(key)?.toString()?.toIntOrNull()

    private fun JSONObject.positiveId(): String? =
        opt("id")?.toString()?.toLongOrNull()?.takeIf { it > 0 }?.toString()
}
