package io.legado.app.help.ai

import androidx.annotation.StringRes
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import splitties.init.appCtx
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal const val WORK_BUDDY_API = "https://copilot.tencent.com"
internal const val WORK_BUDDY_CHAT_PATH = "/v2/chat/completions"
internal const val WORK_BUDDY_MODELS_PATH = "/console/enterprises/personal/models"
private const val WORK_BUDDY_AUTH_TIMEOUT_SECONDS = 20
private const val WORK_BUDDY_LOGIN_TIMEOUT_SECONDS = 300L
private const val WORK_BUDDY_LOGIN_PENDING_CODE = 11217
private const val WORK_BUDDY_INVALID_REFRESH_CODE = 12153
private const val MAX_RESPONSE_BYTES = 1024L * 1024

internal class AiWorkBuddyException(
    @StringRes val messageRes: Int,
    val status: Int? = null,
    val providerCode: Int? = null,
) : IOException() {
    override val message: String
        get() {
            val description = appCtx.getString(messageRes)
            val details = listOfNotNull(status?.let { "HTTP $it" }, providerCode?.let { "#$it" })
            return if (details.isEmpty()) description else "$description (${details.joinToString(", ")})"
        }
}

internal data class AiWorkBuddyAccount(
    @SerializedName("id") val id: String,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("uid") val uid: String?,
    @SerializedName("enterprise_id") val enterpriseId: String?,
    @SerializedName("domain") val domain: String?,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_at") val expiresAt: Long?,
    @SerializedName("reauthorization_required") val reauthorizationRequired: Boolean = false,
) {
    override fun toString() = "AiWorkBuddyAccount"
}

internal interface AiWorkBuddyAccountStorage {
    fun read(): AiWorkBuddyAccount?
    fun write(account: AiWorkBuddyAccount?)
}

private object EncryptedWorkBuddyAccountStorage : AiWorkBuddyAccountStorage {
    private val file = AiEncryptedAccountFile("legado.ai.workbuddy.account.v1", "workbuddy-account", "Reading NG WorkBuddy")
    private var loaded = false
    private var cached: AiWorkBuddyAccount? = null

    @Synchronized
    override fun read(): AiWorkBuddyAccount? {
        if (!loaded) {
            cached = runCatching { file.read()?.let { GSON.fromJson(it, AiWorkBuddyAccount::class.java) } }.getOrNull()
            loaded = true
        }
        return cached
    }

    @Synchronized
    override fun write(account: AiWorkBuddyAccount?) {
        try {
            file.write(GSON.toJson(account))
        } catch (_: Exception) {
            throw AiWorkBuddyException(R.string.ai_workbuddy_storage_failed)
        }
        cached = account
        loaded = true
    }
}

internal val workBuddySessions = AiWorkBuddySessions(EncryptedWorkBuddyAccountStorage, ::aiHttpClient)

internal class AiWorkBuddySessions(
    private val storage: AiWorkBuddyAccountStorage,
    private val clientForTimeout: (Int) -> OkHttpClient,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val accountLock = Mutex()
    private val checkInLock = Mutex()
    private val authClient by lazy { workBuddyClient(clientForTimeout(WORK_BUDDY_AUTH_TIMEOUT_SECONDS)) }

    fun account(id: String?): AiWorkBuddyAccount? =
        id?.let { storage.read()?.takeIf { account -> account.id == id } }

    fun hasCredentials(id: String?): Boolean = account(id)?.reauthorizationRequired == false

    suspend fun signIn(openBrowser: (String) -> Unit): AiWorkBuddyAccount {
        val stateReply = authClient.readWorkBuddyJson(
            workBuddyRequest("/v2/plugin/auth/state?platform=CLI").post(jsonBody(JsonObject())).build(),
        ).workBuddyData()
        val state = stateReply.workBuddyText("state")
            ?: throw invalidWorkBuddyResponse()
        val authUrl = stateReply.workBuddyText("authUrl")?.toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.port == 443 && !it.hasUserInfo() && isWorkBuddyHost(it.host) }
            ?: throw invalidWorkBuddyResponse()
        val ttl = stateReply.workBuddyLong("expiresIn")?.takeIf { it > 0 }
            ?.coerceAtMost(WORK_BUDDY_LOGIN_TIMEOUT_SECONDS) ?: WORK_BUDDY_LOGIN_TIMEOUT_SECONDS
        val deadline = TimeSource.Monotonic.markNow() + ttl.seconds
        openBrowser(authUrl.toString())
        val tokenRequest = workBuddyStateRequest("/v2/plugin/auth/token", state).build()
        while (!deadline.hasPassedNow()) {
            val reply = authClient.readWorkBuddyJson(tokenRequest)
            if (reply.workBuddyCode() == WORK_BUDDY_LOGIN_PENDING_CODE) {
                delay(2500)
                continue
            }
            val tokens = reply.workBuddyData()
            val accessToken = tokens.requiredToken("accessToken")
            val refreshToken = tokens.requiredToken("refreshToken")
            val profile = authClient.readWorkBuddyJson(
                workBuddyStateRequest("/v2/plugin/login/account", state)
                    .header("Authorization", "Bearer $accessToken").build(),
            ).workBuddyData()
            val account = AiWorkBuddyAccount(
                id = UUID.randomUUID().toString(),
                nickname = profile.workBuddyText("nickname"),
                uid = profile.workBuddyHeaderValue("uid"),
                enterpriseId = profile.workBuddyHeaderValue("enterpriseId"),
                domain = tokens.workBuddyDomain(),
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = tokens.expiration(nowSeconds()),
            )
            accountLock.withLock { withContext(IO) { storage.write(account) } }
            return account
        }
        throw AiWorkBuddyException(R.string.ai_chatgpt_sign_in_timeout)
    }

    suspend fun signOut(id: String?) {
        accountLock.withLock {
            if (account(id) != null) withContext(IO) { storage.write(null) }
        }
    }

    suspend fun <T> withAccount(
        id: String?,
        retryUnauthorized: Boolean = true,
        request: suspend (AiWorkBuddyAccount) -> T,
    ): T {
        val current = accountLock.withLock {
            val account = requireAccount(id)
            if (account.expiresAt?.let { it <= nowSeconds() + 60 } == true) refresh(account) else account
        }
        currentCoroutineContext().ensureActive()
        return try {
            request(current)
        } catch (error: AiWorkBuddyException) {
            if (!retryUnauthorized || error.status != 401) throw error
            val refreshed = accountLock.withLock {
                val latest = requireAccount(id)
                if (latest.accessToken != current.accessToken) latest else refresh(latest)
            }
            currentCoroutineContext().ensureActive()
            request(refreshed)
        }
    }

    suspend fun listModels(setting: AiProviderSetting): List<AiModel> =
        withAccount(setting.workBuddyAccountId) { account ->
            val reply = authClient.readWorkBuddyJson(
                workBuddyRequest(WORK_BUDDY_MODELS_PATH).workBuddyAuthorization(account).build(),
            )
            parseWorkBuddyModels(reply.workBuddyData(), setting.modelConfigurationKey())
        }

    suspend fun queryCredits(id: String?): AiWorkBuddyCredits =
        withAccount(id, retryUnauthorized = false) { account ->
            account.requirePersonalCredits()
            val request = workBuddyJsonRequest(WORK_BUDDY_CREDITS_URL)
                .workBuddyAuthorization(account)
                .post(jsonBody(workBuddyCreditsPayload(nowSeconds())))
                .build()
            parseWorkBuddyCredits(authClient.readWorkBuddyJson(request).workBuddyData())
        }

    suspend fun queryCheckInStatus(id: String?): AiWorkBuddyCheckIn =
        withAccount(id, retryUnauthorized = false, request = ::checkInStatus)

    suspend fun claimDailyCredits(id: String?): AiWorkBuddyCheckInResult = checkInLock.withLock {
        withAccount(id, retryUnauthorized = false) { account ->
            val checkIn = checkInStatus(account)
            if (checkIn.status != AiWorkBuddyCheckInStatus.AVAILABLE) return@withAccount AiWorkBuddyCheckInResult(checkIn)

            val request = workBuddyRequest(WORK_BUDDY_CHECK_IN_PATH)
                .workBuddyAuthorization(account)
                .post(dailyCheckInBody)
                .build()
            val data = authClient.readWorkBuddyJson(request).workBuddyData()
            val awarded = data.workBuddyLong("credit")?.takeIf { it > 0 } ?: throw invalidWorkBuddyResponse()
            AiWorkBuddyCheckInResult(checkIn.copy(status = AiWorkBuddyCheckInStatus.CLAIMED), awarded)
        }
    }

    private suspend fun checkInStatus(account: AiWorkBuddyAccount): AiWorkBuddyCheckIn {
        account.requirePersonalCredits()
        val request = workBuddyRequest(WORK_BUDDY_CHECK_IN_STATUS_PATH)
            .workBuddyAuthorization(account)
            .post(jsonBody(JsonObject()))
            .build()
        return parseWorkBuddyCheckIn(authClient.readWorkBuddyJson(request).workBuddyData())
    }

    private fun requireAccount(id: String?): AiWorkBuddyAccount {
        val account = account(id) ?: throw AiWorkBuddyException(R.string.ai_workbuddy_sign_in_required)
        if (account.reauthorizationRequired) throw AiWorkBuddyException(R.string.ai_workbuddy_session_expired)
        return account
    }

    private suspend fun refresh(account: AiWorkBuddyAccount): AiWorkBuddyAccount {
        currentCoroutineContext().ensureActive()
        // The service may rotate the token before the response reaches a cancelled caller.
        return withContext(IO + NonCancellable) {
            val request = workBuddyRequest("/v2/plugin/auth/token/refresh").apply {
                header("X-Refresh-Token", account.refreshToken)
                account.enterpriseId?.let { header("X-Enterprise-Id", it) }
                post(jsonBody(JsonObject()))
            }.build()
            val tokens = try {
                authClient.readWorkBuddyJson(request).workBuddyData()
            } catch (error: AiWorkBuddyException) {
                if (error.status != 401 && error.providerCode != WORK_BUDDY_INVALID_REFRESH_CODE) throw error
                storage.write(account.copy(reauthorizationRequired = true))
                throw AiWorkBuddyException(R.string.ai_workbuddy_session_expired)
            }
            account.copy(
                accessToken = tokens.requiredToken("accessToken"),
                refreshToken = tokens.workBuddyHeaderValue("refreshToken") ?: account.refreshToken,
                domain = tokens.workBuddyDomain() ?: account.domain,
                expiresAt = tokens.expiration(nowSeconds()),
            ).also(storage::write)
        }
    }
}

private fun AiWorkBuddyAccount.requirePersonalCredits() {
    if (enterpriseId != null) throw AiWorkBuddyException(R.string.ai_workbuddy_personal_credits_only)
}

private val dailyCheckInBody = object : RequestBody() {
    private val payload = jsonBody(JsonObject())

    override fun contentType() = payload.contentType()
    override fun contentLength() = payload.contentLength()
    override fun writeTo(sink: BufferedSink) = payload.writeTo(sink)
    override fun isOneShot() = true
}

internal fun workBuddyClient(client: OkHttpClient): OkHttpClient = client.newBuilder()
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

internal fun workBuddyRequest(path: String): Request.Builder = workBuddyJsonRequest(WORK_BUDDY_API + path)

private fun workBuddyJsonRequest(url: String): Request.Builder = Request.Builder().url(url)
    .header("User-Agent", "Reading-NG/Android")
    .header("Accept", "application/json")

private fun workBuddyStateRequest(path: String, state: String): Request.Builder =
    workBuddyRequest(path).url((WORK_BUDDY_API + path).toHttpUrl().newBuilder().addQueryParameter("state", state).build())

internal fun Request.Builder.workBuddyAuthorization(account: AiWorkBuddyAccount): Request.Builder = apply {
    header("Authorization", "Bearer ${account.accessToken}")
    account.uid?.let { header("X-User-Id", it) }
    account.enterpriseId?.let { header("X-Enterprise-Id", it) }
    account.domain?.let { header("X-Domain", it) }
}

private suspend fun OkHttpClient.readWorkBuddyJson(request: Request): JsonObject =
    newCall(request).readCancellableStream { response ->
        response.use {
            it.requireWorkBuddySuccess()
            it.workBuddyJson() ?: throw invalidWorkBuddyResponse()
        }
    }

internal fun Response.requireWorkBuddyStream() {
    requireWorkBuddySuccess()
    val type = body.contentType()
    if (type?.type == "text" && type.subtype == "event-stream") return
    val code = workBuddyJson()?.workBuddyCode()
    if (code != null && code != 0) throw workBuddyRejected(code)
    throw invalidWorkBuddyResponse()
}

private fun Response.requireWorkBuddySuccess() {
    if (!isSuccessful) throw AiWorkBuddyException(R.string.ai_workbuddy_request_rejected, status = code)
}

private fun Response.workBuddyJson(): JsonObject? =
    runCatching { JsonParser.parseString(peekBody(MAX_RESPONSE_BYTES).string()).asJsonObject }.getOrNull()

private fun JsonObject.workBuddyData(): JsonObject {
    val code = workBuddyCode() ?: throw invalidWorkBuddyResponse()
    if (code != 0) throw workBuddyRejected(code)
    return objectOrNull("data") ?: throw invalidWorkBuddyResponse()
}

internal fun JsonObject.workBuddyCode(): Int? = workBuddyLong("code")
    ?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()

internal fun workBuddyRejected(code: Int) =
    AiWorkBuddyException(R.string.ai_workbuddy_request_rejected, providerCode = code)

internal fun invalidWorkBuddyResponse() = AiWorkBuddyException(R.string.ai_workbuddy_invalid_response)

private fun JsonObject.requiredToken(key: String): String =
    workBuddyHeaderValue(key) ?: throw invalidWorkBuddyResponse()

private fun JsonObject.expiration(now: Long): Long? =
    workBuddyLong("expiresIn")?.takeIf { it in 1..315_360_000 }?.let { now + it }

private fun JsonObject.workBuddyHeaderValue(key: String): String? =
    workBuddyText(key)?.takeIf { value -> value.all { it in '!'..'~' } }

private fun JsonObject.workBuddyDomain(): String? = workBuddyHeaderValue("domain")?.let { value ->
    val url = (if ("://" in value) value else "https://$value").toHttpUrlOrNull()
    url?.host?.takeIf { url.isHttps && url.port == 443 && !url.hasUserInfo() && isWorkBuddyHost(it) }
}

private fun isWorkBuddyHost(host: String) =
    host == "copilot.tencent.com" || host == "codebuddy.cn" || host.endsWith(".codebuddy.cn") ||
        host == "workbuddy.cn" || host.endsWith(".workbuddy.cn")

internal fun JsonObject.workBuddyText(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonObject.workBuddyLong(key: String): Long? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
