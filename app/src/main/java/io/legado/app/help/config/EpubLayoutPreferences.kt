package io.legado.app.help.config

import android.content.Context
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import org.json.JSONObject
import splitties.init.appCtx
import java.security.MessageDigest

/** Per-book display choices in the existing preference store, with explicit JSON keys. */
internal object EpubLayoutPreferences {
    const val PUBLISHER = "publisher"
    val features = linkedMapOf(
        "font" to "原书字体", "size" to "原书字号", "color" to "原书文字颜色",
        "decoration" to "原书背景与装饰", "paragraph" to "原书段落格式",
        "title" to "标题特殊样式", "initial" to "首字特殊样式",
    )
    fun key(bookUrl: String): String = "epubLayout." + MessageDigest.getInstance("SHA-256")
        .digest(bookUrl.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun decode(value: String?): Map<String, Boolean> {
        val json = value?.let { runCatching { JSONObject(it) }.getOrNull() }
        return (listOf(PUBLISHER) + features.keys).associateWith { json?.optBoolean(it, true) ?: true }
    }

    fun read(bookUrl: String?, context: Context = appCtx): Map<String, Boolean> =
        decode(bookUrl?.let { context.getPrefString(key(it)) })

    fun set(bookUrl: String, feature: String, enabled: Boolean, context: Context = appCtx) {
        require(feature == PUBLISHER || feature in features)
        context.putPrefString(key(bookUrl), JSONObject(read(bookUrl, context) + (feature to enabled)).toString())
    }

    fun reset(bookUrl: String, context: Context = appCtx) = context.removePref(key(bookUrl))
}
