package io.legado.app.help.ai

import androidx.annotation.StringRes
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import splitties.init.appCtx
import java.io.IOException

internal class AiChatGptException(
    @StringRes val messageRes: Int,
    val providerCode: String? = null,
) : IllegalStateException() {
    override val message: String get() = appCtx.getString(messageRes)
}

/** Registration, renewal and revocation for the official Sign in with ChatGPT flow. */
internal object AiChatGptSessions : AiOpenAiAccountSessions {
    private const val TOKEN_ENDPOINT = "$CHATGPT_ISSUER/api/accounts/oauth/token"
    private val oauthClient by lazy { chatGptHttpClient(20) }
    private val loginLock = Mutex()
    private val sessionLock = Mutex()
    private var retryClientId: String? = null

    override fun accounts(): List<AiChatGptAccount> = AiChatGptAccountStore.accounts()

    override suspend fun signIn(clientId: String?, openBrowser: (String) -> Unit): AiChatGptAccount =
        withContext(Dispatchers.IO) {
            loginLock.withLock {
                val previous = account(clientId)
                val requestedId = previous?.clientId ?: retryClientId
                val hostId = AiChatGptAccountStore.hostId()
                val attempt = AiChatGptAuthorization.create()
                val redirect: String
                val callback = AiChatGptLoopback().use { listener ->
                    redirect = listener.redirectUri
                    val url = attempt.url(
                        hostId, redirect, requestedId, previous?.email,
                        requestPlanConsent = previous?.tokens?.allowsPlan == false,
                    )
                    withContext(Dispatchers.Main) { openBrowser(url.toString()) }
                    listener.await(attempt.state)
                }
                retryClientId = null
                if (callback is AiChatGptCallback.Rejected) {
                    throw AiChatGptException(if (callback.denied) R.string.ai_chatgpt_consent_denied
                        else R.string.ai_chatgpt_sign_in_failed)
                }
                callback as AiChatGptCallback.Code
                val issued = callback.clientId ?: requestedId
                if (issued.isNullOrBlank() || issued == CHATGPT_DYNAMIC_CLIENT ||
                    (requestedId != null && issued != requestedId)) {
                    throw AiChatGptException(R.string.ai_chatgpt_invalid_identity)
                }
                sessionLock.withLock {
                    val tokenResponse = requestTokens(issued, "authorization_code") {
                        add("code", callback.value)
                        add("code_verifier", attempt.verifier)
                        add("redirect_uri", redirect)
                    }
                    if (tokenResponse.error == "invalid_grant" && previous == null) retryClientId = issued
                    val data = tokenResponse.body
                        ?: throw AiChatGptException(R.string.ai_chatgpt_sign_in_failed)
                    val idToken = data.stringOrNull("id_token")
                        ?: throw AiChatGptException(R.string.ai_chatgpt_invalid_identity)
                    val keys = getAuthJson("$CHATGPT_ISSUER/.well-known/jwks.json")
                    val identity = verifyChatGptIdentity(idToken, keys, issued, attempt.nonce)
                    val known = account(issued)
                    if (known != null && known.subject != identity.subject) {
                        throw AiChatGptException(R.string.ai_chatgpt_invalid_identity)
                    }
                    val tokens = readTokens(data)
                    AiChatGptAccount(issued, identity.subject, identity.email ?: known?.email, tokens)
                        .also(AiChatGptAccountStore::save)
                }
            }
        }

    override suspend fun accessToken(setting: AiProviderSetting, forInference: Boolean): String = withContext(Dispatchers.IO) {
        sessionLock.withLock {
            requireCurrent(setting, forInference)
            val account = account(setting.chatGptAccountId)
                ?: throw AiChatGptException(R.string.ai_chatgpt_sign_in_required)
            if (account.reauthorizationRequired) throw AiChatGptException(R.string.ai_chatgpt_reauthorize_required)
            val tokens = account.tokens ?: throw AiChatGptException(R.string.ai_chatgpt_sign_in_required)
            val time = now()
            if (account.needsReauthorization(time)) failExpiredSession(account)
            if (!tokens.allowsPlan) throw AiChatGptException(R.string.ai_chatgpt_plan_permission_required)

            val refresh = tokens.refreshToken
            if (refresh == null || tokens.expiresAt > time + 60L) return@withLock tokens.accessToken
            // Once renewal starts, finish saving the rotated token even if the chat is cancelled.
            val updated = withContext(NonCancellable) {
                val response = requestTokens(account.clientId, "refresh_token") { add("refresh_token", refresh) }
                if (response.error in unusableRefreshErrors) failExpiredSession(account)
                val data = response.body ?: throw AiChatGptException(R.string.ai_chatgpt_temporarily_unavailable)
                val renewed = readTokens(data, tokens.scopes)
                if (renewed.refreshToken == null) throw AiChatGptException(R.string.ai_chatgpt_temporarily_unavailable)
                renewed.also { AiChatGptAccountStore.save(account.copy(tokens = it)) }
            }
            requireCurrent(setting, forInference)
            if (!updated.allowsPlan) throw AiChatGptException(R.string.ai_chatgpt_plan_permission_required)
            updated.accessToken
        }
    }

    private fun failExpiredSession(account: AiChatGptAccount): Nothing {
        AiChatGptAccountStore.save(account.copy(tokens = null, reauthorizationRequired = true))
        throw AiChatGptException(R.string.ai_chatgpt_reauthorize_required)
    }

    override suspend fun resolveRequestError(clientId: String?, accessToken: String, error: AiChatGptException): AiChatGptException {
        if (error.messageRes != R.string.ai_chatgpt_reauthorize_required) return error
        return withContext(Dispatchers.IO) {
            sessionLock.withLock {
                val account = account(clientId)
                val updated = account?.afterAccessTokenRejected(accessToken)
                    ?: return@withLock AiChatGptException(R.string.ai_chatgpt_account_changed)
                if (updated !== account) AiChatGptAccountStore.save(updated)
                if (updated.reauthorizationRequired) {
                    error
                } else {
                    AiChatGptException(R.string.ai_chatgpt_retry_session, error.providerCode)
                }
            }
        }
    }

    override suspend fun signOut(clientId: String): Boolean = withContext(Dispatchers.IO) {
        sessionLock.withLock {
            val account = account(clientId) ?: return@withLock true
            val tokens = account.tokens
            if (tokens == null && !account.reauthorizationRequired) return@withLock true
            withContext(NonCancellable) {
                val confirmed = try {
                    tokens == null ||
                        revoke(clientId, tokens.refreshToken ?: tokens.accessToken, tokens.refreshToken != null)
                } finally {
                    AiChatGptAccountStore.save(account.copy(tokens = null, reauthorizationRequired = false))
                    AiProviderStore.selectChatGptAccount(clientId, refresh = true, onlyIfSelected = true)
                }
                confirmed
            }
        }
    }

    private fun requireCurrent(setting: AiProviderSetting, forInference: Boolean) {
        val current = AiProviderStore.provider(AiDefaultProviders.CHATGPT_ID)
        if (current == null || (forInference && !current.enabled) || !current.matchesCatalogRequest(setting)) {
            throw AiChatGptException(R.string.ai_chatgpt_account_changed)
        }
    }

    private suspend fun revoke(clientId: String, token: String, refresh: Boolean): Boolean {
        return try {
            val discovery = getAuthJson("$CHATGPT_ISSUER/.well-known/openid-configuration")
            val endpoint = discovery.stringOrNull("revocation_endpoint")?.toHttpUrlOrNull()
            if (discovery.stringOrNull("issuer") != CHATGPT_ISSUER || endpoint == null ||
                !endpoint.isHttps || endpoint.host != "auth.openai.com" || endpoint.port != 443 ||
                endpoint.hasUserInfo() || endpoint.query != null || endpoint.fragment != null) return false
            val request = Request.Builder().url(endpoint).post(FormBody.Builder()
                .add("client_id", clientId)
                .add("token", token)
                .add("token_type_hint", if (refresh) "refresh_token" else "access_token")
                .build()).build()
            for (attempt in 0..1) {
                val status = try {
                    oauthClient.newCall(request).readCancellableStream { response -> response.use { it.code } }
                } catch (_: IOException) {
                    503
                }
                if (status == 200) return true
                if (status < 500 || attempt == 1) break
                delay(400)
            }
            false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun getAuthJson(url: String): JsonObject {
        val request = Request.Builder().url(url).get().build()
        return oauthClient.newCall(request).readCancellableStream { response ->
            response.use {
                if (!it.isSuccessful) throw AiChatGptException(R.string.ai_chatgpt_temporarily_unavailable)
                it.chatGptJson() ?: throw AiChatGptException(R.string.ai_chatgpt_invalid_identity)
            }
        }
    }

    private suspend fun requestTokens(
        clientId: String,
        grant: String,
        fields: FormBody.Builder.() -> Unit,
    ): TokenResponse {
        val body = FormBody.Builder().add("grant_type", grant).add("client_id", clientId)
            .add("resource", CHATGPT_API).apply(fields).build()
        val request = Request.Builder().url(TOKEN_ENDPOINT).post(body).build()
        return oauthClient.newCall(request).readCancellableStream { response ->
            response.use {
                val data = it.chatGptJson()
                if (it.isSuccessful) TokenResponse(data)
                else TokenResponse(null, data?.chatGptErrorCode())
            }
        }
    }

    private fun readTokens(data: JsonObject, previousScopes: Set<String> = emptySet()): AiChatGptTokens {
        val access = data.stringOrNull("access_token")?.takeIf(String::isNotBlank)
        val lifetime = runCatching { data.get("expires_in")?.asLong }.getOrNull()
        if (access == null || lifetime == null || lifetime !in 1..86400 ||
            !data.stringOrNull("token_type").equals("Bearer", ignoreCase = true)) {
            throw AiChatGptException(R.string.ai_chatgpt_invalid_identity)
        }
        val scopes = data.stringOrNull("scope")?.split(' ')?.filter { it.isNotBlank() }?.toSet() ?: previousScopes
        val refresh = data.stringOrNull("refresh_token")?.takeIf(String::isNotBlank)
        return AiChatGptTokens(access, refresh, now() + lifetime, scopes)
    }

    private fun now() = System.currentTimeMillis() / 1000

    private data class TokenResponse(val body: JsonObject?, val error: String? = null)

    private val unusableRefreshErrors = setOf("invalid_grant", "invalid_refresh_token", "token_expired",
        "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused")
}

internal fun chatGptHttpClient(timeoutSeconds: Int) = aiHttpClient(timeoutSeconds).newBuilder()
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

internal fun Response.chatGptJson(): JsonObject? = runCatching {
    JsonParser.parseString(peekBody(4 * 1024 * 1024L).string()).asJsonObject
}.getOrNull()

private fun JsonObject.chatGptErrorCode(): String? = (objectOrNull("error") ?: this).let { error ->
    error.stringOrNull("code") ?: error.get("error")?.takeIf { it.isJsonPrimitive }?.asString
}

internal fun chatGptRequestError(status: Int?, payload: JsonObject?): AiChatGptException {
    val error = payload?.objectOrNull("error") ?: payload
    val code = payload?.chatGptErrorCode()
    val resource = when (code) {
        "subscription_sharing_usage_limit_exceeded", "subscription_sharing_workplace_individual_usage_limit_exceeded" ->
            R.string.ai_chatgpt_usage_limit
        "subscription_sharing_usage_unavailable", "subscription_sharing_user_unavailable" ->
            R.string.ai_chatgpt_temporarily_unavailable
        "subscription_sharing_user_not_eligible", "chatpass_v2_scope_not_authorized",
        "chatpass_v2_invalid_authorization_context" -> R.string.ai_chatgpt_plan_unavailable
        "subscription_sharing_unsupported_capability" ->
            if (error?.stringOrNull("param")?.startsWith("tools") == true) R.string.ai_chatgpt_tools_unavailable
            else R.string.ai_chatgpt_capability_unavailable
        "subscription_sharing_route_not_supported" -> R.string.ai_chatgpt_capability_unavailable
        "model_not_found", "model_not_supported" -> R.string.ai_chatgpt_capability_unavailable
        "subscription_sharing_invalid_user" -> R.string.ai_chatgpt_reauthorize_required
        "context_length_exceeded" -> R.string.ai_chatgpt_context_limit
        else -> when {
            status == 401 -> R.string.ai_chatgpt_reauthorize_required
            status == 403 -> R.string.ai_chatgpt_plan_unavailable
            status == 429 -> R.string.ai_chatgpt_rate_limit
            status != null && status >= 500 -> R.string.ai_chatgpt_temporarily_unavailable
            else -> R.string.ai_chatgpt_request_failed
        }
    }
    return AiChatGptException(resource, code)
}
