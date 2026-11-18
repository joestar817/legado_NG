package io.legado.app.help.source

import com.google.gson.JsonNull
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON

private val gsonWithNulls by lazy { GSON.newBuilder().serializeNulls().create() }

internal fun BookSource.toSourceJson(): String = buildString { writeSourceJson(this) }

internal fun BookSource.writeSourceJson(out: Appendable) {
    if (enabledCookieJar == null) {
        // Missing CookieJar means enabled in imported JSON. Preserve an explicit null.
        val json = GSON.toJsonTree(this).asJsonObject
        json.add("enabledCookieJar", JsonNull.INSTANCE)
        gsonWithNulls.toJson(json, out)
    } else {
        GSON.toJson(this, out)
    }
}
