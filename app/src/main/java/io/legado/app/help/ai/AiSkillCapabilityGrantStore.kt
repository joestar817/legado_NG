package io.legado.app.help.ai

import android.content.Context
import com.google.gson.reflect.TypeToken
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.security.MessageDigest

/**
 * 按不可变 Skill runtime revision 保存用户授予的 MCP capability。
 * Skill 内容或版本变化后 token 随之变化，旧授权不会自动继承。
 */
object AiSkillCapabilityGrantStore {

    private const val PREFERENCES_NAME = "ai_skill_capability_grants"
    private val listType = object : TypeToken<List<String>>() {}.type

    fun granted(runtimeRevision: String): Set<String> {
        if (runtimeRevision.isBlank()) return emptySet()
        val raw = preferences().getString(key(runtimeRevision), null) ?: return emptySet()
        return runCatching { GSON.fromJson<List<String>>(raw, listType) }
            .getOrNull()
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
    }

    fun grant(runtimeRevision: String, capabilityIds: Collection<String>) {
        require(runtimeRevision.isNotBlank()) { "Skill runtime revision 不能为空" }
        preferences().edit()
            .putString(key(runtimeRevision), GSON.toJson(capabilityIds.distinct().sorted()))
            .apply()
    }

    private fun preferences() = appCtx.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    private fun key(runtimeRevision: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(runtimeRevision.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "grant.$digest"
    }
}
