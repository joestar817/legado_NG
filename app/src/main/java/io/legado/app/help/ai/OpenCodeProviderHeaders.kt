package io.legado.app.help.ai

import io.legado.app.BuildConfig
import okhttp3.Request
import java.security.MessageDigest
import java.util.UUID

/** Apply OpenCode's session routing only to the built-in provider. */
internal fun Request.Builder.addOpenCodeProviderHeaders(
    setting: AiProviderSetting,
    conversationId: String? = null
): Request.Builder {
    if (!setting.builtIn || setting.id != AiDefaultProviders.OPENCODE_ID) return this

    // Standalone requests do not have an existing conversation.
    val sessionId = conversationId ?: UUID.randomUUID().toString()
    require(sessionId.isNotBlank()) { "OpenCode session ID must not be blank" }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(sessionId.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    return header("x-opencode-session", "ses_$digest")
        .header("User-Agent", "Legado-NG/${BuildConfig.VERSION_NAME}")
}
