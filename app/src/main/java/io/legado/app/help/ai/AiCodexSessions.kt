package io.legado.app.help.ai

import com.google.gson.JsonObject
import io.legado.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import java.net.BindException
import java.util.UUID

internal const val CODEX_API = "https://chatgpt.com/backend-api/codex"
// Public OAuth client and loopback ports used by the Codex native login protocol.
private const val CODEX_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
private const val CODEX_AUTH = "https://api.openai.com/auth"

internal object AiCodexSessions : AiOpenAiAccountSessions {
    private val store = AiOpenAiAccountStore("legado.ai.codex.accounts.v1", "codex-accounts")
    private val oauthClient by lazy { chatGptHttpClient(25).newBuilder().retryOnConnectionFailure(false).build() }
    private val loginLock = Mutex()
    private val sessionLock = Mutex()

    override fun accounts() = store.accounts()

    override fun allowsPlan(account: AiChatGptAccount?) =
        account?.tokens != null && !account.codexWorkspaceId.isNullOrBlank()

    override suspend fun signIn(clientId: String?, openBrowser: (String) -> Unit): AiChatGptAccount =
        withContext(Dispatchers.IO) {
            loginLock.withLock {
                val previous = account(clientId)
                val attempt = AiChatGptAuthorization.create()
                val response = openLoopback().use { listener ->
                    val url = "$CHATGPT_ISSUER/oauth/authorize".toHttpUrl().newBuilder()
                        .addQueryParameter("response_type", "code")
                        .addQueryParameter("client_id", CODEX_CLIENT_ID)
                        .addQueryParameter("redirect_uri", listener.redirectUri)
                        .addQueryParameter("scope", "openid profile email offline_access")
                        .addQueryParameter("code_challenge", attempt.challenge())
                        .addQueryParameter("code_challenge_method", "S256")
                        .addQueryParameter("id_token_add_organizations", "true")
                        .addQueryParameter("codex_cli_simplified_flow", "true")
                        .addQueryParameter("state", attempt.state)
                        .addQueryParameter("originator", "reading_ng")
                        .apply { previous?.email?.let { addQueryParameter("login_hint", it) } }
                        .build()
                    withContext(Dispatchers.Main) { openBrowser(url.toString()) }
                    val callback = listener.await(attempt.state)
                    if (callback is AiChatGptCallback.Rejected) {
                        throw AiChatGptException(if (callback.denied) R.string.ai_chatgpt_consent_denied
                            else R.string.ai_chatgpt_sign_in_failed)
                    }
                    val code = callback as AiChatGptCallback.Code
                    if (code.clientId != null && code.clientId != CODEX_CLIENT_ID) invalidIdentity()
                    requestTokens(FormBody.Builder()
                        .add("grant_type", "authorization_code")
                        .add("client_id", CODEX_CLIENT_ID)
                        .add("redirect_uri", listener.redirectUri)
                        .add("code_verifier", attempt.verifier)
                        .add("code", code.value)
                        .build())
                }
                val signedIdentity = response.stringOrNull("id_token") ?: invalidIdentity()
                val keysRequest = Request.Builder().url("$CHATGPT_ISSUER/.well-known/jwks.json").build()
                val keys = oauthClient.newCall(keysRequest).readCancellableStream { it.use { response ->
                    if (!response.isSuccessful) invalidIdentity()
                    response.chatGptJson() ?: invalidIdentity()
                } }
                val identity = verifyChatGptIdentity(signedIdentity, keys, CODEX_CLIENT_ID, nonce = null)
                val tokens = readCodexTokens(response)
                val workspace = codexWorkspace(tokens.accessToken)
                if (previous != null &&
                    (previous.subject != identity.subject || previous.codexWorkspaceId != workspace)) {
                    throw AiChatGptException(R.string.ai_chatgpt_account_changed)
                }
                sessionLock.withLock {
                    val existing = accounts().firstOrNull {
                        it.subject == identity.subject && it.codexWorkspaceId == workspace
                    }
                    AiChatGptAccount(
                        clientId = existing?.clientId ?: UUID.randomUUID().toString(),
                        subject = identity.subject,
                        email = identity.email,
                        tokens = tokens,
                        codexWorkspaceId = workspace,
                    ).also(store::save)
                }
            }
        }

    override suspend fun accessToken(setting: AiProviderSetting, forInference: Boolean): String =
        withContext(Dispatchers.IO) {
            sessionLock.withLock {
                requireSelection(setting, forInference)
                val account = account(setting.chatGptAccountId)
                    ?: throw AiChatGptException(R.string.ai_chatgpt_sign_in_required)
                if (account.needsReauthorization()) reauthorize()
                val tokens = account.tokens ?: throw AiChatGptException(R.string.ai_chatgpt_sign_in_required)
                val now = System.currentTimeMillis() / 1000
                val refreshBuffer = if (tokens.refreshToken == null) 0 else 30
                if (tokens.expiresAt > now + refreshBuffer) return@withLock tokens.accessToken
                val refreshToken = tokens.refreshToken ?: reauthorize()
                currentCoroutineContext().ensureActive()
                val refreshed = withContext(NonCancellable) {
                    val response = try {
                        requestTokens(jsonBody(JsonObject().apply {
                            addProperty("client_id", CODEX_CLIENT_ID)
                            addProperty("grant_type", "refresh_token")
                            addProperty("refresh_token", refreshToken)
                        }))
                    } catch (error: AiChatGptException) {
                        if (error.messageRes == R.string.ai_chatgpt_reauthorize_required) {
                            store.save(account.copy(reauthorizationRequired = true))
                        }
                        throw error
                    }
                    val renewed = readCodexTokens(response, refreshToken)
                    if (codexWorkspace(renewed.accessToken) != account.codexWorkspaceId ||
                        codexAccessClaims(renewed.accessToken)?.stringOrNull("sub") !=
                        codexAccessClaims(tokens.accessToken)?.stringOrNull("sub")
                    ) {
                        store.save(account.copy(reauthorizationRequired = true))
                        reauthorize()
                    }
                    store.save(account.copy(tokens = renewed))
                    renewed
                }
                currentCoroutineContext().ensureActive()
                requireSelection(setting, forInference)
                refreshed.accessToken
            }
        }

    override suspend fun resolveRequestError(
        clientId: String?,
        accessToken: String,
        error: AiChatGptException,
    ): AiChatGptException {
        if (error.messageRes == R.string.ai_chatgpt_reauthorize_required) {
            withContext(Dispatchers.IO) {
                sessionLock.withLock { account(clientId)?.afterAccessTokenRejected(accessToken)?.let(store::save) }
            }
        }
        return error
    }

    override suspend fun signOut(clientId: String): Boolean = withContext(Dispatchers.IO) {
        val tokens = sessionLock.withLock {
            val account = account(clientId) ?: return@withLock null
            store.save(account.copy(tokens = null, reauthorizationRequired = false))
            AiProviderStore.selectChatGptAccount(
                clientId, refresh = true, onlyIfSelected = true, providerId = AiDefaultProviders.CODEX_ID,
            )
            account.tokens
        } ?: return@withContext true
        val request = Request.Builder().url("$CHATGPT_ISSUER/oauth/revoke").post(jsonBody(JsonObject().apply {
            addProperty("token", tokens.refreshToken ?: tokens.accessToken)
            addProperty("token_type_hint", if (tokens.refreshToken == null) "access_token" else "refresh_token")
            if (tokens.refreshToken != null) addProperty("client_id", CODEX_CLIENT_ID)
        })).build()
        try {
            oauthClient.newCall(request).readCancellableStream { it.use { response -> response.isSuccessful } }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private fun requireSelection(setting: AiProviderSetting, forInference: Boolean) {
        val current = AiProviderStore.provider(AiDefaultProviders.CODEX_ID)
        if (!setting.isCodexSubscription() || current?.chatGptAccountId != setting.chatGptAccountId ||
            (forInference && current?.enabled != true)
        ) throw AiChatGptException(R.string.ai_chatgpt_account_changed)
    }

    private fun openLoopback() = try {
        AiChatGptLoopback(port = 1455, redirectHost = "localhost")
    } catch (_: BindException) {
        AiChatGptLoopback(port = 1457, redirectHost = "localhost")
    }

    private suspend fun requestTokens(body: RequestBody): JsonObject {
        val request = Request.Builder().url("$CHATGPT_ISSUER/oauth/token").post(body).build()
        return oauthClient.newCall(request).readCancellableStream { response ->
            response.use {
                val json = it.chatGptJson()
                if (!it.isSuccessful) {
                    if (json?.stringOrNull("error") == "invalid_grant") reauthorize()
                    throw chatGptRequestError(it.code, json)
                }
                json ?: invalidIdentity()
            }
        }
    }
}

private fun reauthorize(): Nothing = throw AiChatGptException(R.string.ai_chatgpt_reauthorize_required)

private fun invalidIdentity(): Nothing = throw AiChatGptException(R.string.ai_chatgpt_invalid_identity)

private fun codexAccessClaims(token: String): JsonObject? = runCatching {
    val parts = token.split('.', limit = 4)
    require(parts.size == 3 && token.length <= 32768)
    decodeJwtObject(parts[1])
}.getOrNull()

private fun codexWorkspace(token: String): String = codexAccessClaims(token)?.objectOrNull(CODEX_AUTH)
    ?.stringOrNull("chatgpt_account_id")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }
    ?: invalidIdentity()

private fun readCodexTokens(data: JsonObject, previousRefresh: String? = null): AiChatGptTokens {
    val token = data.stringOrNull("access_token")?.takeIf(String::isNotBlank) ?: invalidIdentity()
    val now = System.currentTimeMillis() / 1000
    val expiresAt = runCatching { codexAccessClaims(token)?.get("exp")?.asLong }.getOrNull()
        ?: data.positiveInt("expires_in")?.let { now + it }
        ?: invalidIdentity()
    if (expiresAt <= now) invalidIdentity()
    return AiChatGptTokens(
        accessToken = token,
        refreshToken = data.stringOrNull("refresh_token")?.takeIf(String::isNotBlank) ?: previousRefresh,
        expiresAt = expiresAt,
        scopes = emptySet(),
    )
}

internal fun Request.Builder.addCodexWorkspace(accessToken: String) =
    header("ChatGPT-Account-Id", codexWorkspace(accessToken))

internal suspend fun AiProviderSetting.getCodexAccountJson(url: String): JsonObject {
    val endpoint = url.toHttpUrl()
    require(endpoint.isHttps && endpoint.host == "chatgpt.com" && endpoint.port == 443 &&
        endpoint.encodedPath in listOf("/backend-api/codex/models", "/backend-api/wham/usage"))
    val token = AiCodexSessions.accessToken(this)
    val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $token")
        .addCodexWorkspace(token).build()
    return chatGptHttpClient(timeoutSeconds).newCall(request).readCancellableStream { response ->
        response.use {
            val json = it.chatGptJson()
            if (!it.isSuccessful) {
                throw AiCodexSessions.resolveRequestError(chatGptAccountId, token, chatGptRequestError(it.code, json))
            }
            json ?: throw AiChatGptException(R.string.ai_chatgpt_temporarily_unavailable)
        }
    }
}
