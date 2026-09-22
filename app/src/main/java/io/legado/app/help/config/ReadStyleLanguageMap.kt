package io.legado.app.help.config

import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.help.book.BookScriptClass
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

data class LanguageStyleBindings(
    @SerializedName("cjk") val cjk: String? = null,
    @SerializedName("latin") val latin: String? = null,
    @SerializedName("other") val other: String? = null,
) {
    fun nameFor(script: BookScriptClass): String? = when (script) {
        BookScriptClass.Cjk -> cjk
        BookScriptClass.Latin -> latin
        BookScriptClass.Other -> other
    }

    fun with(script: BookScriptClass, name: String?): LanguageStyleBindings {
        val stored = name?.trim()?.takeIf { it.isNotEmpty() }
        return when (script) {
            BookScriptClass.Cjk -> copy(cjk = stored)
            BookScriptClass.Latin -> copy(latin = stored)
            BookScriptClass.Other -> copy(other = stored)
        }
    }

    fun assignedName(script: BookScriptClass, existingNames: Collection<String>): String? {
        val raw = nameFor(script)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return raw.takeIf { it in existingNames }
    }
}

object ReadStyleLanguageMap {
    fun current(): LanguageStyleBindings = fromJson(appCtx.getPrefString(PreferKey.readStyleLanguageMap))

    fun update(bindings: LanguageStyleBindings) {
        appCtx.putPrefString(PreferKey.readStyleLanguageMap, toJson(bindings))
    }

    fun set(script: BookScriptClass, name: String?) {
        update(current().with(script, name))
    }

    fun fromJson(json: String?): LanguageStyleBindings {
        if (json.isNullOrBlank()) return LanguageStyleBindings()
        return GSON.fromJsonObject<LanguageStyleBindings>(json).getOrNull() ?: LanguageStyleBindings()
    }

    fun toJson(bindings: LanguageStyleBindings): String = GSON.toJson(bindings)
}
