package io.legado.app.help.ai

internal interface AiOpenAiAccountSessions {
    fun accounts(): List<AiChatGptAccount>

    fun account(clientId: String?): AiChatGptAccount? =
        clientId?.let { id -> accounts().firstOrNull { it.clientId == id } }

    fun allowsPlan(account: AiChatGptAccount?): Boolean = account?.tokens?.allowsPlan == true

    suspend fun signIn(clientId: String?, openBrowser: (String) -> Unit): AiChatGptAccount

    suspend fun accessToken(setting: AiProviderSetting, forInference: Boolean = false): String

    suspend fun resolveRequestError(
        clientId: String?,
        accessToken: String,
        error: AiChatGptException,
    ): AiChatGptException

    suspend fun signOut(clientId: String): Boolean
}

internal fun AiProviderSetting.accountSessions(): AiOpenAiAccountSessions? = when {
    isCodexSubscription() -> AiCodexSessions
    isChatGptSubscription() -> AiChatGptSessions
    else -> null
}
