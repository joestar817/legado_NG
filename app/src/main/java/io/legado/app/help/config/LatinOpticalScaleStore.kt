package io.legado.app.help.config

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import org.json.JSONObject
import splitties.init.appCtx

/** 全局西文比例。缺省表示自动。不进预设，不进本书。 */
object LatinOpticalScaleStore {
    const val PREF_KEY = PreferKey.readLatinOpticalScale

    fun manual(): Float? {
        val json = appCtx.getPrefString(PREF_KEY)?.takeIf { it.isNotBlank() } ?: return null
        val value = runCatching { JSONObject(json).optDouble("latinScale", Double.NaN).toFloat() }.getOrNull()
            ?: return null
        if (!value.isFinite()) return null
        return LatinOpticalScale.clamp(value)
    }

    fun save(scale: Float?) {
        if (scale == null || !scale.isFinite()) {
            appCtx.putPrefString(PREF_KEY, null)
            return
        }
        val payload = JSONObject().put("latinScale", LatinOpticalScale.clamp(scale).toDouble())
        appCtx.putPrefString(PREF_KEY, payload.toString())
    }

    fun snapshotJson(): String? = appCtx.getPrefString(PREF_KEY)

    fun restoreSnapshot(json: String?) {
        appCtx.putPrefString(PREF_KEY, json)
    }
}
